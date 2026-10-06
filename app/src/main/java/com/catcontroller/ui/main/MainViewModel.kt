package com.catcontroller.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catcontroller.data.ProfileRepository
import com.catcontroller.model.*
import com.catcontroller.radio.RadioDevice
import com.catcontroller.radio.RadioDeviceList
import com.catcontroller.radio.RadioDriver
import com.catcontroller.radio.RadioRepository
import com.catcontroller.radio.RigCaps
import com.catcontroller.radio.protocol.RigctldDriver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val radioRepo: RadioRepository,
    private val profileRepo: ProfileRepository,
) : ViewModel() {

    private val _activeProfile = MutableStateFlow<RadioProfile?>(null)
    val activeProfile = _activeProfile.asStateFlow()

    /** Show the PTT arm toggle in the top bar whenever a profile is loaded. */
    val hasPttPort: StateFlow<Boolean> = _activeProfile.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The radio device entry for the active profile (null = no profile or unknown model). */
    val activeDevice: StateFlow<RadioDevice?> = _activeProfile.map { p ->
        p?.let { RadioDeviceList.byId(it.deviceModelId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Effective rig capabilities — uses capabilities discovered live from \dump_caps
     * (rigctld) when available, otherwise falls back to the static device table.
     */
    private val _capsOverride = MutableStateFlow<RigCaps?>(null)
    private var capsJob: Job? = null

    private val _isTuning = MutableStateFlow(false)
    val isTuning: StateFlow<Boolean> = _isTuning.asStateFlow()
    private var preTuneMode: RadioMode? = null
    private var preTunePower: Int? = null
    private var lastFreqEditMs = 0L

    val caps: StateFlow<RigCaps> = combine(activeDevice, _capsOverride) { device, override ->
        val static = device?.caps ?: RigCaps()
        if (override == null) {
            static
        } else {
            // Dynamic caps (dump_caps) have accurate mode list + feature flags.
            // Static caps have the full per-radio filter table (hamlib filter_list).
            // dump_caps Bandwidths section only reports 3 canonical values (Normal/Narrow/Wide),
            // so prefer the static list for any mode where it has more entries.
            val bws = buildMap<RadioMode, List<Int>> {
                putAll(override.bandwidthsByMode)
                static.bandwidthsByMode.forEach { (mode, staticBws) ->
                    val dyn = get(mode)
                    if (dyn == null || staticBws.size > dyn.size) put(mode, staticBws)
                }
            }
            override.copy(
                bandwidthsByMode   = bws,
                hasBandwidth       = bws.isNotEmpty(),
                modeDisplayLabels  = static.modeDisplayLabels,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RigCaps())

    val profiles = profileRepo.profiles.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _driverState = MutableStateFlow(RadioState())
    val radioState: StateFlow<RadioState> = _driverState

    private var driver: RadioDriver? = null
    private var pollJob: Job? = null
    private var stateJob: Job? = null

    init {
        viewModelScope.launch {
            val def = profileRepo.getDefault()
            if (def != null) loadProfile(def)
        }
    }

    fun loadProfile(profile: RadioProfile) {
        viewModelScope.launch {
            if (driver != null) {
                driver!!.disconnect()
                stopPolling()
            }
            _activeProfile.value = profile
            _capsOverride.value = null
            driver = radioRepo.createDriver(profile)

            stateJob?.cancel()
            stateJob = driver!!.state
                .onEach { incoming ->
                    // Suppress poll-driven freq resets for a grace window after a manual edit —
                    // the poll's IF; response can reflect a frequency read before a just-sent
                    // FA/FB command has taken effect on the radio, causing a visible snap-back.
                    _driverState.value = if (System.currentTimeMillis() - lastFreqEditMs < 800L) {
                        incoming.copy(freqA = _driverState.value.freqA, freqB = _driverState.value.freqB)
                    } else incoming
                }
                .launchIn(viewModelScope)

            // For rigctld, subscribe to live-discovered caps from \dump_caps
            capsJob?.cancel()
            capsJob = (driver as? RigctldDriver)?.discoveredCaps
                ?.filterNotNull()
                ?.onEach { _capsOverride.value = it }
                ?.launchIn(viewModelScope)
        }
    }

    fun connect() {
        val d = driver ?: return
        viewModelScope.launch {
            _driverState.value = _driverState.value.copy(connecting = true, error = null)
            val result = d.connect()
            if (result.isSuccess) {
                // Clamp displayed power into this rig's real range — a leftover value from a
                // previously connected rig (or the default) may be out of range for this one.
                val range = caps.value.minPowerWatts..caps.value.maxPowerWatts
                val clamped = _driverState.value.rfPower.coerceIn(range.first, range.last)
                if (clamped != _driverState.value.rfPower) driver?.setRfPower(clamped)
                startPolling()
            } else {
                _driverState.value = _driverState.value.copy(
                    connecting = false,
                    error = result.exceptionOrNull()?.message ?: "Connection failed",
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            stopPolling()
            driver?.disconnectPtt()
            driver?.disconnect()
        }
    }

    fun connectPtt() {
        val d = driver ?: return
        viewModelScope.launch {
            _driverState.value = _driverState.value.copy(pttConnecting = true, error = null)
            val result = d.connectPtt()
            if (result.isFailure) {
                _driverState.value = _driverState.value.copy(
                    pttConnecting = false,
                    error = result.exceptionOrNull()?.message ?: "PTT connection failed",
                )
            }
        }
    }

    fun disconnectPtt() {
        viewModelScope.launch { driver?.disconnectPtt() }
    }

    fun clearError() {
        _driverState.value = _driverState.value.copy(error = null)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(400)
                runCatching { driver?.pollStatus() }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun adjustFrequency(digitPosition: Int, delta: Int) {
        val state = _driverState.value
        val freq = when (state.activeVfo) {
            Vfo.B, Vfo.SUB -> state.freqB
            else -> state.freqA
        }
        val step    = digitStep(digitPosition)
        val newFreq = (freq + step * delta).coerceAtLeast(0)
        lastFreqEditMs = System.currentTimeMillis()
        // Update display immediately so tap feels responsive whether or not connected
        _driverState.value = when (state.activeVfo) {
            Vfo.B, Vfo.SUB -> state.copy(freqB = newFreq)
            else           -> state.copy(freqA = newFreq)
        }
        viewModelScope.launch { driver?.setFrequency(newFreq, state.activeVfo) }
    }

    fun setFrequencyDirect(freq: Long) {
        lastFreqEditMs = System.currentTimeMillis()
        val state = _driverState.value
        _driverState.value = when (state.activeVfo) {
            Vfo.B, Vfo.SUB -> state.copy(freqB = freq)
            else           -> state.copy(freqA = freq)
        }
        viewModelScope.launch { driver?.setFrequency(freq, state.activeVfo) }
    }

    fun setMode(mode: RadioMode) = viewModelScope.launch {
        driver?.setMode(mode)
        // If the new mode only has one bandwidth option, there's nothing to pick — apply it.
        caps.value.bandwidthsByMode[mode]?.singleOrNull()?.let { driver?.setBandwidth(it) }
    }
    fun setBandwidth(bw: Int)             = viewModelScope.launch { driver?.setBandwidth(bw) }
    fun setPtt(active: Boolean)           = viewModelScope.launch { driver?.setPtt(active) }
    fun setSplit(enabled: Boolean)        = viewModelScope.launch { driver?.setSplit(enabled) }
    fun setVfo(vfo: Vfo)                  = viewModelScope.launch { driver?.setVfo(vfo) }
    fun vfoAtoB()                         = viewModelScope.launch { driver?.vfoAtoB() }
    fun vfoBtoA()                         = viewModelScope.launch { driver?.vfoBtoA() }
    fun swapVfo()                         = viewModelScope.launch { driver?.swapVfo() }
    fun setRfPower(level: Int)            = viewModelScope.launch { driver?.setRfPower(level) }
    fun setAfGain(level: Int)             = viewModelScope.launch { driver?.setAfGain(level) }
    fun setRfGain(level: Int)             = viewModelScope.launch { driver?.setRfGain(level) }
    fun setMicGain(level: Int)            = viewModelScope.launch { driver?.setMicGain(level) }
    fun setSquelch(level: Int)            = viewModelScope.launch { driver?.setSquelch(level) }
    fun setRit(hz: Int)                   = viewModelScope.launch { driver?.setRit(hz) }
    fun setRitEnabled(on: Boolean)        = viewModelScope.launch { driver?.setRitEnabled(on) }
    fun setXit(hz: Int)                   = viewModelScope.launch { driver?.setXit(hz) }
    fun setXitEnabled(on: Boolean)        = viewModelScope.launch { driver?.setXitEnabled(on) }
    fun setIfShift(hz: Int)               = viewModelScope.launch { driver?.setIfShift(hz) }
    fun setIfShiftEnabled(on: Boolean)    = viewModelScope.launch { driver?.setIfShiftEnabled(on) }
    fun setPreamp(l: PreampLevel)         = viewModelScope.launch { driver?.setPreamp(l) }
    fun setAtt(l: AttLevel)               = viewModelScope.launch { driver?.setAttenuator(l) }
    fun setAgc(m: AgcMode)               = viewModelScope.launch { driver?.setAgc(m) }
    fun setNb(on: Boolean)                = viewModelScope.launch { driver?.setNoiseBlanker(on) }
    fun setNr(on: Boolean)                = viewModelScope.launch { driver?.setNoiseReduction(on) }
    fun setAnf(on: Boolean)               = viewModelScope.launch { driver?.setAutoNotch(on) }
    fun setComp(on: Boolean)              = viewModelScope.launch { driver?.setSpeechProc(on) }
    fun setCompLevel(l: Int)              = viewModelScope.launch { driver?.setSpeechProcLevel(l) }
    fun setVox(on: Boolean)               = viewModelScope.launch { driver?.setVox(on) }
    fun setTuner(on: Boolean)             = viewModelScope.launch { driver?.setTuner(on) }
    fun startTuning() {
        if (_isTuning.value) return
        val state = _driverState.value
        preTuneMode  = state.mode
        preTunePower = state.rfPower
        _isTuning.value = true
        viewModelScope.launch {
            driver?.setRfPower(state.rfPower.coerceAtMost(10).coerceAtLeast(1))
            driver?.setMode(RadioMode.CW)
            driver?.setPtt(true)
        }
    }

    fun stopTuning() {
        if (!_isTuning.value) return
        val savedMode  = preTuneMode
        val savedPower = preTunePower
        _isTuning.value = false
        preTuneMode  = null
        preTunePower = null
        viewModelScope.launch {
            driver?.setPtt(false)
            if (savedMode  != null) driver?.setMode(savedMode)
            if (savedPower != null) driver?.setRfPower(savedPower)
        }
    }

    fun startTune()                       = viewModelScope.launch { driver?.startTune() }
    fun setAntenna(p: Int)               = viewModelScope.launch { driver?.setAntenna(p) }
    fun setCwSpeed(w: Int)                = viewModelScope.launch { driver?.setCwSpeed(w) }
    fun setCwPitch(h: Int)                = viewModelScope.launch { driver?.setCwPitch(h) }

    // 9-digit display: positions 0–8 = 100MHz, 10MHz, 1MHz, 100kHz, 10kHz, 1kHz, 100Hz, 10Hz, 1Hz
    private fun digitStep(position: Int): Long = when (position) {
        0  -> 100_000_000L
        1  -> 10_000_000L
        2  -> 1_000_000L
        3  -> 100_000L
        4  -> 10_000L
        5  -> 1_000L
        6  -> 100L
        7  -> 10L
        else -> 1L
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        viewModelScope.launch { radioRepo.disconnect() }
    }
}

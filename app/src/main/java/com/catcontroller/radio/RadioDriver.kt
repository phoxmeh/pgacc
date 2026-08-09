package com.catcontroller.radio

import com.catcontroller.model.*
import kotlinx.coroutines.flow.StateFlow

interface RadioDriver {
    val state: StateFlow<RadioState>

    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun connectPtt(): Result<Unit> = Result.success(Unit)
    suspend fun disconnectPtt() {}

    suspend fun setFrequency(freq: Long, vfo: Vfo = Vfo.A): Result<Unit>
    suspend fun setMode(mode: RadioMode, bandwidth: Int = -1): Result<Unit>
    suspend fun setBandwidth(bw: Int): Result<Unit>
    suspend fun setPtt(active: Boolean): Result<Unit>
    suspend fun setSplit(enabled: Boolean): Result<Unit>
    suspend fun setVfo(vfo: Vfo): Result<Unit>
    suspend fun vfoAtoB()
    suspend fun vfoBtoA()
    suspend fun swapVfo()

    suspend fun setRfPower(level: Int): Result<Unit>
    suspend fun setAfGain(level: Int): Result<Unit>
    suspend fun setRfGain(level: Int): Result<Unit>
    suspend fun setMicGain(level: Int): Result<Unit>
    suspend fun setSquelch(level: Int): Result<Unit>

    suspend fun setRit(hz: Int): Result<Unit>
    suspend fun setRitEnabled(enabled: Boolean): Result<Unit>
    suspend fun setXit(hz: Int): Result<Unit>
    suspend fun setXitEnabled(enabled: Boolean): Result<Unit>
    suspend fun setIfShift(hz: Int): Result<Unit> = Result.success(Unit)
    suspend fun setIfShiftEnabled(on: Boolean): Result<Unit> = Result.success(Unit)

    suspend fun setPreamp(level: PreampLevel): Result<Unit>
    suspend fun setAttenuator(level: AttLevel): Result<Unit>
    suspend fun setAgc(mode: AgcMode): Result<Unit>
    suspend fun setNoiseBlanker(enabled: Boolean): Result<Unit>
    suspend fun setNoiseReduction(enabled: Boolean): Result<Unit>
    suspend fun setAutoNotch(enabled: Boolean): Result<Unit>
    suspend fun setSpeechProc(enabled: Boolean): Result<Unit>
    suspend fun setSpeechProcLevel(level: Int): Result<Unit>
    suspend fun setVox(enabled: Boolean): Result<Unit>
    suspend fun setTuner(enabled: Boolean): Result<Unit>
    suspend fun startTune(): Result<Unit>

    suspend fun setAntenna(port: Int): Result<Unit>
    suspend fun setCwSpeed(wpm: Int): Result<Unit>
    suspend fun setCwPitch(hz: Int): Result<Unit>

    suspend fun getSmeter(): Int
    suspend fun getRfMeter(): Int
    suspend fun getSwrMeter(): Int
    suspend fun getAlcMeter(): Int

    suspend fun pollStatus()
}

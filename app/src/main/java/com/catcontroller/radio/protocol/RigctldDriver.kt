package com.catcontroller.radio.protocol

import com.catcontroller.model.*
import com.catcontroller.radio.RadioDriver
import com.catcontroller.radio.RigCaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * rigctld network driver — connects to a Hamlib rigctld daemon via TCP.
 * Supports all 400+ hamlib radios; the daemon owns the hardware protocol.
 * Default port: 4532.
 *
 * On connect, sends \dump_caps to discover the rig's actual modes and filter
 * widths instead of maintaining our own static tables.
 */
class RigctldDriver(
    private val host: String = "localhost",
    private val port: Int = 4532,
) : RadioDriver {

    private val _state = MutableStateFlow(RadioState())
    override val state: StateFlow<RadioState> = _state.asStateFlow()

    /** Populated after connect() via \dump_caps. Null until first connect. */
    private val _discoveredCaps = MutableStateFlow<RigCaps?>(null)
    val discoveredCaps: StateFlow<RigCaps?> = _discoveredCaps.asStateFlow()

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val mutex = Mutex()

    // ── Connection ────────────────────────────────────────────────────────────

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = Socket(host, port)
            socket = s
            writer = PrintWriter(s.outputStream, true)
            reader = BufferedReader(InputStreamReader(s.inputStream))
            updateState { copy(connected = true, error = null) }
            discoverCaps()
            pollStatus()
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { writer?.close(); reader?.close(); socket?.close() }
        socket = null; writer = null; reader = null
        updateState { copy(connected = false) }
    }

    override suspend fun connectPtt(): Result<Unit> {
        updateState { copy(pttConnected = true, pttConnecting = false) }
        return Result.success(Unit)
    }

    override suspend fun disconnectPtt() {
        updateState { copy(pttConnected = false) }
    }

    // ── Low-level I/O ─────────────────────────────────────────────────────────

    private suspend fun sendCmd(cmd: String): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val w = writer ?: error("Not connected")
                val r = reader ?: error("Not connected")
                w.println(cmd)
                val sb = StringBuilder()
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.startsWith("RPRT ")) break
                    sb.appendLine(line)
                }
                sb.toString().trim()
            }
        }
    }

    // ── Capabilities discovery ────────────────────────────────────────────────

    private suspend fun discoverCaps() {
        val dump = sendCmd("\\dump_caps").getOrDefault("")
        parseDumpCaps(dump)?.let { _discoveredCaps.value = it }
    }

    /**
     * Parses \dump_caps output from rigctld (hamlib 4.x actual format).
     *
     * Key lines we rely on:
     *   "Mode list:   AM CW USB LSB RTTY FM CWR RTTYR PKTLSB PKTUSB FMN"
     *   "Bandwidths:"  followed by tab-indented per-mode lines
     *   "Get functions: NB COMP VOX ANF NR TUNER ..."
     *   "Get level: PREAMP(0..20/10) ATT(...) RF(...) SQL(...) IF(-1200..1200/20) ..."
     *   "Preamp: 10dB"  /  "Attenuator: 12dB"
     *   "Can set RIT:	Y"
     */
    private fun parseDumpCaps(dump: String): RigCaps? {
        if (dump.isBlank()) return null
        val lines = dump.lines()

        // "Mode list:   AM CW USB LSB RTTY FM CWR RTTYR PKTLSB PKTUSB FMN"
        val modeListLine = lines.firstOrNull { it.trimStart().startsWith("Mode list:", ignoreCase = true) }
            ?: return null

        val modes = modeListLine.substringAfter(":").trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .mapNotNull { rigctldModeFromStrOrNull(it) }
            .distinct()

        if (modes.isEmpty()) return null

        // "Bandwidths:" section — tab-indented lines:
        //   "\tAM\tNormal: 9.0000 kHz,\tNarrow: 6.0000 kHz,\tWide: 0.0 Hz"
        val bwsByMode = mutableMapOf<RadioMode, MutableList<Int>>()
        val bwValueRe = Regex("""([\d.]+)\s*(kHz|Hz)""", RegexOption.IGNORE_CASE)
        var inBandwidths = false
        for (line in lines) {
            when {
                line.trim().equals("Bandwidths:", ignoreCase = true) -> { inBandwidths = true; continue }
                inBandwidths && (line.isBlank() || !line.startsWith("\t")) -> inBandwidths = false
                inBandwidths -> {
                    val modeStr = line.trimStart().split(Regex("\\s+"), limit = 2).firstOrNull() ?: continue
                    val mode = rigctldModeFromStrOrNull(modeStr) ?: continue
                    bwValueRe.findAll(line).forEach { m ->
                        val v = m.groupValues[1].toDoubleOrNull() ?: return@forEach
                        val hz = if (m.groupValues[2].equals("kHz", ignoreCase = true)) (v * 1000).toInt() else v.toInt()
                        if (hz > 0) bwsByMode.getOrPut(mode) { mutableListOf() }.add(hz)
                    }
                }
            }
        }
        val bandwidthsByMode = bwsByMode.mapValues { (_, bws) -> bws.distinct().sortedDescending() }

        // "Get functions: NB COMP VOX ANF NR TUNER ..."  — simple space-separated names
        val funcLine = lines.firstOrNull { it.startsWith("Get functions:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.lowercase() ?: ""
        val funcs = funcLine.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

        // "Get level: PREAMP(0..20/10) ATT(...) SQL(...) IF(-1200..1200/20) ..."
        // Extract names before parentheses: "IF", "SQL", "RF", "MICGAIN", "AGC", etc.
        val levelLine = lines.firstOrNull { it.startsWith("Get level:", ignoreCase = true) }
            ?.substringAfter(":")?.trim() ?: ""
        val levelNames = Regex("([A-Z_]+)\\(").findAll(levelLine)
            .map { it.groupValues[1].lowercase() }.toSet()

        // "Preamp: 10dB"  /  "Preamp: 10dB 20dB"
        val preampLine = lines.firstOrNull { it.startsWith("Preamp:", ignoreCase = true) } ?: ""
        val preampDbs = Regex("(\\d+)\\s*dB").findAll(preampLine).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val preampLevels = buildList {
            add(PreampLevel.IPO)
            if (preampDbs.isNotEmpty()) add(PreampLevel.P1)
            if (preampDbs.size >= 2) add(PreampLevel.P2)
        }

        // "Attenuator: 12dB"  /  "Attenuator: 6dB 12dB 18dB"
        val attLine = lines.firstOrNull { it.startsWith("Attenuator:", ignoreCase = true) } ?: ""
        val attDbs = Regex("(\\d+)\\s*dB").findAll(attLine).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val attLevels = buildList {
            add(AttLevel.OFF)
            attDbs.forEach { db ->
                when (db) {
                    6 -> add(AttLevel.ATT6); 10 -> add(AttLevel.ATT10)
                    12 -> add(AttLevel.ATT12); 18 -> add(AttLevel.ATT18); 20 -> add(AttLevel.ATT20)
                }
            }
        }

        // RIT lives in its own line, NOT in functions: "Can set RIT:	Y"
        val hasRit = lines.any { it.contains("Can set RIT", ignoreCase = true) && it.contains("Y") }

        return RigCaps(
            modes              = modes,
            hasNoiseBlanker    = "nb"      in funcs,
            hasNoiseReduction  = "nr"      in funcs,
            hasAutoNotch       = "anf"     in funcs,
            hasIfShift         = "if"      in levelNames,
            hasRit             = hasRit,
            hasSpeechProc      = "comp"    in funcs,
            hasSpeechProcLevel = "comp"    in levelNames,
            hasVox             = "vox"     in funcs,
            hasTuner           = "tuner"   in funcs,
            preampLevels       = preampLevels,
            attLevels          = attLevels,
            antennaCount       = 1,
            hasCw              = RadioMode.CW in modes,
            hasMicGain         = "micgain" in levelNames,
            hasRfGain          = "rf"      in levelNames,
            hasSquelch         = "sql"     in levelNames,
            hasBandwidth       = bandwidthsByMode.isNotEmpty(),
            hasAgc             = "agc"     in levelNames,
            hasSplit           = true,
            bandwidthsByMode   = bandwidthsByMode,
        )
    }

    // ── Status polling ────────────────────────────────────────────────────────

    override suspend fun pollStatus() {
        // Frequency VFO A
        sendCmd("\\get_freq").getOrDefault("").let { r ->
            r.lines().firstOrNull()
                ?.removePrefix("Frequency:")?.trim()
                ?.toLongOrNull()
                ?.let { f -> updateState { copy(freqA = f) } }
        }

        // Mode + passband — \get_mode response is two lines:
        //   "Mode: USB\nPassband: 2700\nRPRT 0"  (long form)
        //   or just "USB\n2700\nRPRT 0"            (older rigctld)
        sendCmd("\\get_mode").getOrDefault("").let { r ->
            val ls = r.lines()
            ls.getOrNull(0)?.removePrefix("Mode:")?.trim()?.let { s ->
                rigctldModeFromStrOrNull(s)?.let { m -> updateState { copy(mode = m) } }
            }
            ls.getOrNull(1)?.removePrefix("Passband:")?.trim()?.toIntOrNull()?.let { bw ->
                if (bw > 0) updateState { copy(bandwidth = bw) }
            }
        }

        // PTT state — response: "PTT: 0" or just "0"
        sendCmd("\\get_ptt").getOrDefault("").let { r ->
            r.lines().firstOrNull()?.removePrefix("PTT:")?.trim()?.toIntOrNull()?.let { v ->
                updateState { copy(ptt = v != 0) }
            }
        }

        // S-meter: hamlib STRENGTH range is 0..60 per dump_caps (0=S0, 60=S9+60dB)
        sendCmd("\\get_level STRENGTH").getOrDefault("").let { r ->
            r.removePrefix("Level:").trim().toFloatOrNull()?.let { strength ->
                val sMeter = (strength / 60f * 30f).toInt().coerceIn(0, 30)
                updateState { copy(sMeter = sMeter) }
            }
        }

        // SQL (squelch) level — 0.0..1.0
        sendCmd("\\get_level SQL").getOrDefault("").let { r ->
            r.removePrefix("Level:").trim().toFloatOrNull()?.let { v ->
                updateState { copy(squelch = (v * 100).toInt().coerceIn(0, 100)) }
            }
        }

        // IF shift level — integer Hz, range -1200..+1200
        sendCmd("\\get_level IF").getOrDefault("").let { r ->
            r.removePrefix("Level:").trim().toIntOrNull()?.let { hz ->
                updateState { copy(ifShift = hz, ifShiftEnabled = hz != 0) }
            }
        }

        if (_state.value.ptt) {
            sendCmd("\\get_level RFPOWER_METER").getOrDefault("").let { r ->
                r.removePrefix("Level:").trim().toFloatOrNull()?.let { v ->
                    updateState { copy(rfMeter = (v * 30).toInt().coerceIn(0, 30)) }
                }
            }
            sendCmd("\\get_level SWR").getOrDefault("").let { r ->
                r.removePrefix("Level:").trim().toFloatOrNull()?.let { v ->
                    updateState { copy(swrMeter = (v * 30).toInt().coerceIn(0, 30)) }
                }
            }
            sendCmd("\\get_level ALC").getOrDefault("").let { r ->
                r.removePrefix("Level:").trim().toFloatOrNull()?.let { v ->
                    updateState { copy(alcMeter = (v * 30).toInt().coerceIn(0, 30)) }
                }
            }
        }
    }

    // ── Radio controls ────────────────────────────────────────────────────────

    override suspend fun setFrequency(freq: Long, vfo: Vfo): Result<Unit> = runCatching {
        val vfoStr = when (vfo) {
            Vfo.A, Vfo.MAIN -> "VFOA"
            Vfo.B, Vfo.SUB  -> "VFOB"
            else             -> "VFOA"
        }
        sendCmd("\\set_vfo $vfoStr").getOrThrow()
        sendCmd("\\set_freq $freq").getOrThrow()
        updateState {
            when (vfo) {
                Vfo.B, Vfo.SUB -> copy(freqB = freq)
                else           -> copy(freqA = freq)
            }
        }
    }

    override suspend fun setMode(mode: RadioMode, bandwidth: Int): Result<Unit> {
        val modeStr = rigctldModeStr(mode)
        val bwStr   = if (bandwidth > 0) bandwidth.toString() else "0"
        return sendCmd("\\set_mode $modeStr $bwStr").map {
            updateState { copy(mode = mode, bandwidth = if (bandwidth > 0) bandwidth else this.bandwidth) }
        }
    }

    override suspend fun setBandwidth(bw: Int): Result<Unit> {
        val modeStr = rigctldModeStr(_state.value.mode)
        return sendCmd("\\set_mode $modeStr $bw").map { updateState { copy(bandwidth = bw) } }
    }

    override suspend fun setPtt(active: Boolean): Result<Unit> {
        val code = if (active) 1 else 0
        return sendCmd("\\set_ptt $code").map { updateState { copy(ptt = active) } }
    }

    override suspend fun setSplit(enabled: Boolean): Result<Unit> {
        val code = if (enabled) 1 else 0
        return sendCmd("\\set_split_vfo $code VFOB").map { updateState { copy(split = enabled) } }
    }

    override suspend fun setVfo(vfo: Vfo): Result<Unit> {
        val str = when (vfo) { Vfo.A -> "VFOA"; Vfo.B -> "VFOB"; else -> "VFOA" }
        return sendCmd("\\set_vfo $str").map { updateState { copy(activeVfo = vfo) } }
    }

    override suspend fun vfoAtoB() { sendCmd("\\vfo_op CPY") }

    override suspend fun vfoBtoA() {
        sendCmd("\\set_vfo VFOB")
        sendCmd("\\vfo_op CPY")
        sendCmd("\\set_vfo VFOA")
    }

    override suspend fun swapVfo() { sendCmd("\\vfo_op XCHG") }

    override suspend fun setRfPower(level: Int): Result<Unit> =
        sendCmd("\\set_level RFPOWER ${level / 100.0f}").map { updateState { copy(rfPower = level) } }

    override suspend fun setAfGain(level: Int): Result<Unit> =
        sendCmd("\\set_level AF ${level / 100.0f}").map { updateState { copy(afGain = level) } }

    override suspend fun setRfGain(level: Int): Result<Unit> =
        sendCmd("\\set_level RF ${level / 100.0f}").map { updateState { copy(rfGain = level) } }

    override suspend fun setMicGain(level: Int): Result<Unit> =
        sendCmd("\\set_level MIC ${level / 100.0f}").map { updateState { copy(micGain = level) } }

    override suspend fun setSquelch(level: Int): Result<Unit> =
        sendCmd("\\set_level SQL ${level / 100.0f}").map { updateState { copy(squelch = level) } }

    override suspend fun setRit(hz: Int): Result<Unit> =
        sendCmd("\\set_rit $hz").map { updateState { copy(rit = hz) } }

    override suspend fun setRitEnabled(enabled: Boolean): Result<Unit> =
        sendCmd("\\set_func RIT ${if (enabled) 1 else 0}").map { updateState { copy(ritEnabled = enabled) } }

    override suspend fun setXit(hz: Int): Result<Unit> =
        sendCmd("\\set_xit $hz").map { updateState { copy(xit = hz) } }

    override suspend fun setXitEnabled(enabled: Boolean): Result<Unit> =
        sendCmd("\\set_func XIT ${if (enabled) 1 else 0}").map { updateState { copy(xitEnabled = enabled) } }

    override suspend fun setPreamp(level: PreampLevel): Result<Unit> {
        val code = when (level) {
            PreampLevel.OFF -> 0; PreampLevel.IPO -> 0; PreampLevel.P1 -> 1; PreampLevel.P2 -> 2
        }
        return sendCmd("\\set_level PREAMP $code").map { updateState { copy(preamp = level) } }
    }

    override suspend fun setAttenuator(level: AttLevel): Result<Unit> =
        sendCmd("\\set_level ATT ${level.db}").map { updateState { copy(attenuator = level) } }

    override suspend fun setAgc(mode: AgcMode): Result<Unit> {
        // Hamlib AGC codes: 0=OFF, 2=FAST, 5=MEDIUM, 3=SLOW, 6=AUTO
        val str = when (mode) {
            AgcMode.OFF -> "0"; AgcMode.FAST -> "2"; AgcMode.MID -> "5"
            AgcMode.SLOW -> "3"; AgcMode.AUTO -> "6"
        }
        return sendCmd("\\set_level AGC $str").map { updateState { copy(agc = mode) } }
    }

    override suspend fun setIfShift(hz: Int): Result<Unit> =
        sendCmd("\\set_level IF $hz").map { updateState { copy(ifShift = hz) } }

    override suspend fun setIfShiftEnabled(enabled: Boolean): Result<Unit> {
        val hz = if (enabled) _state.value.ifShift.takeIf { it != 0 } ?: 0 else 0
        return sendCmd("\\set_level IF $hz").map { updateState { copy(ifShiftEnabled = enabled) } }
    }

    override suspend fun setNoiseBlanker(enabled: Boolean): Result<Unit> =
        sendCmd("\\set_func NB ${if (enabled) 1 else 0}").map { updateState { copy(noiseBlanker = enabled) } }

    override suspend fun setNoiseReduction(enabled: Boolean): Result<Unit> =
        sendCmd("\\set_func NR ${if (enabled) 1 else 0}").map { updateState { copy(noiseReduction = enabled) } }

    override suspend fun setAutoNotch(enabled: Boolean): Result<Unit> =
        sendCmd("\\set_func ANF ${if (enabled) 1 else 0}").map { updateState { copy(autoNotch = enabled) } }

    override suspend fun setSpeechProc(enabled: Boolean): Result<Unit> =
        sendCmd("\\set_func COMP ${if (enabled) 1 else 0}").map { updateState { copy(speechProc = enabled) } }

    override suspend fun setSpeechProcLevel(level: Int): Result<Unit> =
        sendCmd("\\set_level COMP ${level / 100.0f}").map { updateState { copy(speechProcLevel = level) } }

    override suspend fun setVox(enabled: Boolean): Result<Unit> =
        sendCmd("\\set_func VOX ${if (enabled) 1 else 0}").map { updateState { copy(vox = enabled) } }

    override suspend fun setTuner(enabled: Boolean): Result<Unit> =
        sendCmd("\\set_func TUNER ${if (enabled) 1 else 0}").map { updateState { copy(tuner = enabled) } }

    override suspend fun startTune(): Result<Unit> = sendCmd("\\vfo_op TUNE").map {}

    override suspend fun setAntenna(port: Int): Result<Unit> =
        sendCmd("\\set_ant $port").map { updateState { copy(antennaPort = port) } }

    override suspend fun setCwSpeed(wpm: Int): Result<Unit> =
        sendCmd("\\set_level KEYSPD $wpm").map { updateState { copy(cwSpeed = wpm) } }

    override suspend fun setCwPitch(hz: Int): Result<Unit> =
        sendCmd("\\set_level CWPITCH $hz").map { updateState { copy(cwPitch = hz) } }

    override suspend fun getSmeter(): Int {
        val resp = sendCmd("\\get_level STRENGTH").getOrDefault("")
        return resp.removePrefix("Level:").trim().toFloatOrNull()?.let { strength ->
            ((strength + 54f) / 114f * 30f).toInt().coerceIn(0, 30)
                .also { updateState { copy(sMeter = it) } }
        } ?: 0
    }

    override suspend fun getRfMeter(): Int {
        val resp = sendCmd("\\get_level RFPOWER_METER").getOrDefault("")
        return resp.removePrefix("Level:").trim().toFloatOrNull()?.let { v ->
            (v * 30).toInt().coerceIn(0, 30).also { updateState { copy(rfMeter = it) } }
        } ?: 0
    }

    override suspend fun getSwrMeter(): Int {
        val resp = sendCmd("\\get_level SWR").getOrDefault("")
        return resp.removePrefix("Level:").trim().toFloatOrNull()?.let { v ->
            (v * 30).toInt().coerceIn(0, 30).also { updateState { copy(swrMeter = it) } }
        } ?: 0
    }

    override suspend fun getAlcMeter(): Int {
        val resp = sendCmd("\\get_level ALC").getOrDefault("")
        return resp.removePrefix("Level:").trim().toFloatOrNull()?.let { v ->
            (v * 30).toInt().coerceIn(0, 30).also { updateState { copy(alcMeter = it) } }
        } ?: 0
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateState(block: RadioState.() -> RadioState) {
        _state.value = _state.value.block()
    }

    // Canonical hamlib mode name strings (from rig.h rmode_t)
    private fun rigctldModeStr(mode: RadioMode) = when (mode) {
        RadioMode.LSB     -> "LSB";     RadioMode.USB     -> "USB";     RadioMode.AM      -> "AM"
        RadioMode.FM      -> "FM";      RadioMode.CW      -> "CW";      RadioMode.CWR     -> "CWR"
        RadioMode.RTTY    -> "RTTY";    RadioMode.RTTYR   -> "RTTYR";   RadioMode.WFM     -> "WFM"
        RadioMode.FMN     -> "FMN";     RadioMode.AMN     -> "AMN";     RadioMode.C4FM    -> "C4FM"
        RadioMode.PKTLSB  -> "PKTLSB"; RadioMode.PKTUSB  -> "PKTUSB";  RadioMode.PKTFM   -> "PKTFM"
        RadioMode.AMS     -> "AMS";     RadioMode.DSB     -> "DSB";     RadioMode.PSKUSB  -> "PSKUSB"
        RadioMode.PSKR    -> "PSKR";    RadioMode.SAM     -> "SAM";     RadioMode.SAL     -> "SAL"
        RadioMode.SAH     -> "SAH";     RadioMode.FAX     -> "FAX";
        RadioMode.ECSSUSB -> "ECSSUSB"; RadioMode.ECSSLSB -> "ECSSLSB"
        else -> "USB"
    }

    private fun rigctldModeFromStrOrNull(s: String): RadioMode? = when (s.uppercase().trim()) {
        "LSB"      -> RadioMode.LSB;     "USB"      -> RadioMode.USB;     "AM"       -> RadioMode.AM
        "FM"       -> RadioMode.FM;      "CW"       -> RadioMode.CW;      "CWR"      -> RadioMode.CWR
        "RTTY"     -> RadioMode.RTTY;    "RTTYR"    -> RadioMode.RTTYR;   "WFM"      -> RadioMode.WFM
        "FMN"      -> RadioMode.FMN;     "AMN"      -> RadioMode.AMN;     "C4FM"     -> RadioMode.C4FM
        "PKTLSB"   -> RadioMode.PKTLSB;  "PKTUSB"   -> RadioMode.PKTUSB;  "PKTFM"    -> RadioMode.PKTFM
        "AMS"      -> RadioMode.AMS;     "DSB"      -> RadioMode.DSB;     "PSKUSB"   -> RadioMode.PSKUSB
        "PSKR"     -> RadioMode.PSKR;    "SAM"      -> RadioMode.SAM;     "SAL"      -> RadioMode.SAL
        "SAH"      -> RadioMode.SAH;     "FAX"      -> RadioMode.FAX;
        "ECSSUSB"  -> RadioMode.ECSSUSB; "ECSSLSB"  -> RadioMode.ECSSLSB
        else -> null
    }
}

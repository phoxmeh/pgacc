package com.catcontroller.radio.protocol

import com.catcontroller.model.*
import com.catcontroller.model.PttMethod
import com.catcontroller.serial.UsbSerialManager
import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * Kenwood CAT protocol (ASCII text). Also used by Elecraft, QRP Labs QDX/QMX, and others
 * that implement the TS-480/TS-2000 command set.
 *
 * Command format: <CMD><params>; — e.g. "FA000014225000;" sets VFO-A to 14.225 MHz
 */
class KenwoodCatDriver(
    usbSerialManager: UsbSerialManager,
    private val baudRate: Int = 9600,
    private val catVid: Int = 0,
    private val catPid: Int = 0,
    private val catPortIndex: Int = 0,
    pttMethod: PttMethod = PttMethod.CAT,
    pttSeparatePort: Boolean = false,
    pttVid: Int = 0,
    pttPid: Int = 0,
    pttPortIndex: Int = 0,
) : BaseCatDriver(usbSerialManager, pttMethod, pttSeparatePort, pttVid, pttPid, pttPortIndex) {

    override suspend fun connect(): Result<Unit> = runCatching {
        port = usbSerialManager.open(
            baudRate     = baudRate,
            dataBits     = 8,
            stopBits     = UsbSerialPort.STOPBITS_1,
            parity       = UsbSerialPort.PARITY_NONE,
            portIndex    = catPortIndex,
            preferredVid = catVid,
            preferredPid = catPid,
        ).getOrThrow()
        updateState { copy(connected = true, error = null) }
        pollStatus()
    }

    override suspend fun disconnect() {
        port?.let { usbSerialManager.removePort(it) }
        port = null
        updateState { copy(connected = false) }
    }

    override suspend fun setFrequency(freq: Long, vfo: Vfo): Result<Unit> {
        val cmd = when (vfo) {
            Vfo.A, Vfo.MAIN -> "FA%011d;".format(freq)
            Vfo.B, Vfo.SUB  -> "FB%011d;".format(freq)
            else             -> "FA%011d;".format(freq)
        }
        return sendCommand(cmd).map {
            updateState {
                when (vfo) {
                    Vfo.B, Vfo.SUB -> copy(freqB = freq)
                    else           -> copy(freqA = freq)
                }
            }
        }
    }

    override suspend fun setMode(mode: RadioMode, bandwidth: Int): Result<Unit> {
        val modeCode = kenwoodModeCode(mode)
        return sendCommand("MD$modeCode;").map {
            updateState { copy(mode = mode) }
        }
    }

    override suspend fun setBandwidth(bw: Int): Result<Unit> {
        // TS-890/TS-2000 use SH command for filter width
        val code = when {
            bw <= 250  -> 0
            bw <= 500  -> 1
            bw <= 1000 -> 2
            bw <= 1800 -> 3
            bw <= 2400 -> 4
            bw <= 3000 -> 5
            else       -> 6
        }
        return sendCommand("SH%02d;".format(code)).map {
            updateState { copy(bandwidth = bw) }
        }
    }

    override suspend fun setPtt(active: Boolean): Result<Unit> {
        if (pttMethod != PttMethod.CAT) return setPttHardware(active)
        val cmd = if (active) "TX0;" else "RX;"
        return sendCommand(cmd).map { updateState { copy(ptt = active) } }
    }

    override suspend fun setSplit(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "SP1;" else "SP0;"
        return sendCommand(cmd).map { updateState { copy(split = enabled) } }
    }

    override suspend fun setVfo(vfo: Vfo): Result<Unit> {
        val cmd = when (vfo) {
            Vfo.A -> "FR0;FT0;"
            Vfo.B -> "FR1;FT1;"
            else  -> "FR0;FT0;"
        }
        return sendCommand(cmd).map { updateState { copy(activeVfo = vfo) } }
    }

    override suspend fun vfoAtoB() { sendCommand("VV;") }
    override suspend fun vfoBtoA() { /* Not all Kenwood support reverse; swap via memory */ }
    override suspend fun swapVfo() { sendCommand("SV;") }

    override suspend fun setRfPower(level: Int): Result<Unit> {
        val p = level.coerceIn(0, 100)
        return sendCommand("PC%03d;".format(p)).map { updateState { copy(rfPower = p) } }
    }

    override suspend fun setAfGain(level: Int): Result<Unit> {
        val v = level.coerceIn(0, 255)
        return sendCommand("AG0%03d;".format(v)).map { updateState { copy(afGain = level) } }
    }

    override suspend fun setRfGain(level: Int): Result<Unit> {
        val v = level.coerceIn(0, 255)
        return sendCommand("RG%03d;".format(v)).map { updateState { copy(rfGain = level) } }
    }

    override suspend fun setMicGain(level: Int): Result<Unit> {
        val v = level.coerceIn(0, 100)
        return sendCommand("MG%03d;".format(v)).map { updateState { copy(micGain = level) } }
    }

    override suspend fun setSquelch(level: Int): Result<Unit> {
        val v = level.coerceIn(0, 255)
        return sendCommand("SQ0%03d;".format(v)).map { updateState { copy(squelch = level) } }
    }

    override suspend fun setRit(hz: Int): Result<Unit> {
        val sign = if (hz >= 0) "+" else "-"
        return sendCommand("RC;RU%05d;".format(kotlin.math.abs(hz))).map {
            updateState { copy(rit = hz) }
        }
    }

    override suspend fun setRitEnabled(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "RT1;" else "RT0;"
        return sendCommand(cmd).map { updateState { copy(ritEnabled = enabled) } }
    }

    override suspend fun setXit(hz: Int): Result<Unit> =
        sendCommand("XT%05d;".format(hz.coerceIn(-9999, 9999))).map {
            updateState { copy(xit = hz) }
        }

    override suspend fun setXitEnabled(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "XT1;" else "XT0;"
        return sendCommand(cmd).map { updateState { copy(xitEnabled = enabled) } }
    }

    override suspend fun setPreamp(level: PreampLevel): Result<Unit> {
        val code = when (level) {
            PreampLevel.OFF -> 0
            PreampLevel.P1  -> 1
            PreampLevel.P2  -> 2
            PreampLevel.IPO -> 0
        }
        return sendCommand("PA0$code;").map { updateState { copy(preamp = level) } }
    }

    override suspend fun setAttenuator(level: AttLevel): Result<Unit> {
        val code = when (level) {
            AttLevel.OFF   -> 0
            AttLevel.ATT6  -> 1
            AttLevel.ATT10 -> 1
            AttLevel.ATT12 -> 2
            AttLevel.ATT18 -> 3
            AttLevel.ATT20 -> 2
            AttLevel.ATT24 -> 3
        }
        return sendCommand("RA%02d;".format(code)).map { updateState { copy(attenuator = level) } }
    }

    override suspend fun setAgc(mode: AgcMode): Result<Unit> {
        val code = when (mode) {
            AgcMode.OFF  -> 0
            AgcMode.FAST -> 1
            AgcMode.MID  -> 2
            AgcMode.SLOW -> 3
            AgcMode.AUTO -> 4
        }
        return sendCommand("GT0%02d;".format(code)).map { updateState { copy(agc = mode) } }
    }

    override suspend fun setNoiseBlanker(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "NB1;" else "NB0;"
        return sendCommand(cmd).map { updateState { copy(noiseBlanker = enabled) } }
    }

    override suspend fun setNoiseReduction(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "NR1;" else "NR0;"
        return sendCommand(cmd).map { updateState { copy(noiseReduction = enabled) } }
    }

    override suspend fun setAutoNotch(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "AN1;" else "AN0;"
        return sendCommand(cmd).map { updateState { copy(autoNotch = enabled) } }
    }

    override suspend fun setSpeechProc(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "PR1;" else "PR0;"
        return sendCommand(cmd).map { updateState { copy(speechProc = enabled) } }
    }

    override suspend fun setSpeechProcLevel(level: Int): Result<Unit> {
        val v = level.coerceIn(0, 100)
        return sendCommand("PL%03d%03d;".format(v, v)).map { updateState { copy(speechProcLevel = v) } }
    }

    override suspend fun setVox(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "VX1;" else "VX0;"
        return sendCommand(cmd).map { updateState { copy(vox = enabled) } }
    }

    override suspend fun setTuner(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "AC111;" else "AC110;"
        return sendCommand(cmd).map { updateState { copy(tuner = enabled) } }
    }

    override suspend fun startTune(): Result<Unit> = sendCommand("AC111;").map {}

    override suspend fun setAntenna(port: Int): Result<Unit> {
        return sendCommand("AN0$port;").map { updateState { copy(antennaPort = port) } }
    }

    override suspend fun setCwSpeed(wpm: Int): Result<Unit> {
        val v = wpm.coerceIn(4, 60)
        return sendCommand("KS%03d;".format(v)).map { updateState { copy(cwSpeed = v) } }
    }

    override suspend fun setCwPitch(hz: Int): Result<Unit> {
        val code = ((hz - 400) / 50).coerceIn(0, 16)
        return sendCommand("AP0%02d;".format(code)).map { updateState { copy(cwPitch = hz) } }
    }

    override suspend fun getSmeter(): Int {
        val resp = sendCommand("SM0;").getOrDefault("")
        return parseSmeter(resp)
    }

    override suspend fun getRfMeter(): Int {
        val resp = sendCommand("RM1;").getOrDefault("")
        return parseMeter(resp, "RM")
    }

    override suspend fun getSwrMeter(): Int {
        val resp = sendCommand("RM6;").getOrDefault("")
        return parseMeter(resp, "RM")
    }

    override suspend fun getAlcMeter(): Int {
        val resp = sendCommand("RM4;").getOrDefault("")
        return parseMeter(resp, "RM")
    }

    override suspend fun pollStatus() {
        // IF; gives a comprehensive status in one response
        val resp = sendCommand("IF;").getOrDefault("")
        parseIfResponse(resp)
        // Mode is queried separately via MD; rather than trusting IF's embedded mode byte —
        // per flrig's QDX.cxx get_IF(), some rigs (QRP Labs QDX) always report a fixed mode
        // code there regardless of actual mode, which would otherwise snap the UI back.
        sendCommand("MD;").getOrDefault("").let { r ->
            if (r.startsWith("MD") && r.length >= 3) {
                r[2].digitToIntOrNull()?.let { code -> updateState { copy(mode = kenwoodModeFromCode(code)) } }
            }
        }
    }

    private fun parseIfResponse(resp: String) {
        // IF response: "IF00014225000     +00000000000000 000;
        // Positions: 2-13 freq, 14-18 spaces, 19-24 rit offset, 25 rit on/off
        if (!resp.startsWith("IF") || resp.length < 38) return
        runCatching {
            val freq = resp.substring(2, 13).toLongOrNull() ?: return
            val ritHz = resp.substring(13, 19).trim().toIntOrNull() ?: 0
            val ritOn = resp.getOrNull(19) == '1'
            val xitOn = resp.getOrNull(20) == '1'
            updateState {
                copy(
                    freqA = freq,
                    rit = ritHz,
                    ritEnabled = ritOn,
                    xitEnabled = xitOn,
                )
            }
        }
    }

    private fun parseSmeter(resp: String): Int {
        // SM0xxxx; where xxxx is 0000-0030 (S0=0, S9=15, S9+60=30)
        if (!resp.startsWith("SM")) return 0
        return resp.drop(3).dropLast(1).toIntOrNull()?.also { v ->
            updateState { copy(sMeter = v) }
        } ?: 0
    }

    private fun parseMeter(resp: String, prefix: String): Int {
        if (!resp.startsWith(prefix)) return 0
        return resp.drop(prefix.length + 1).dropLast(1).toIntOrNull() ?: 0
    }

    private fun kenwoodModeCode(mode: RadioMode) = when (mode) {
        RadioMode.LSB    -> 1
        RadioMode.USB    -> 2
        RadioMode.CW     -> 3
        RadioMode.FM     -> 4
        RadioMode.AM     -> 5
        RadioMode.RTTY   -> 6
        RadioMode.CWR    -> 7
        RadioMode.RTTYR  -> 9
        RadioMode.PKTLSB -> 10
        RadioMode.PKTUSB -> 11
        RadioMode.PKTFM  -> 12
        else             -> 2
    }

    private fun kenwoodModeFromCode(code: Int) = when (code) {
        1  -> RadioMode.LSB
        2  -> RadioMode.USB
        3  -> RadioMode.CW
        4  -> RadioMode.FM
        5  -> RadioMode.AM
        6  -> RadioMode.RTTY
        7  -> RadioMode.CWR
        9  -> RadioMode.RTTYR
        10 -> RadioMode.PKTLSB
        11 -> RadioMode.PKTUSB
        12 -> RadioMode.PKTFM
        else -> RadioMode.USB
    }
}

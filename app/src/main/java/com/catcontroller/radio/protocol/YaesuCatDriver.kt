package com.catcontroller.radio.protocol

import com.catcontroller.model.*
import com.catcontroller.model.PttMethod
import com.catcontroller.serial.UsbSerialManager
import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * Yaesu CAT protocol (FT-450/817/857/897/991/891/DX series).
 * Commands are ASCII terminated with semicolons, largely compatible across the FT family.
 */
class YaesuCatDriver(
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
            stopBits     = UsbSerialPort.STOPBITS_2,
            parity       = UsbSerialPort.PARITY_NONE,
            portIndex    = catPortIndex,
            preferredVid = catVid,
            preferredPid = catPid,
        ).getOrThrow()
        updateState { copy(connected = true, error = null) }
        syncFromRadio()
    }

    override suspend fun disconnect() {
        port?.let { usbSerialManager.removePort(it) }
        port = null
        updateState { copy(connected = false) }
    }

    override suspend fun setFrequency(freq: Long, vfo: Vfo): Result<Unit> {
        // FT-891/991/DX series: 9-digit frequency, no echo on set commands.
        val cmd = when (vfo) {
            Vfo.A, Vfo.MAIN -> "FA%09d;".format(freq)
            Vfo.B, Vfo.SUB  -> "FB%09d;".format(freq)
            else             -> "FA%09d;".format(freq)
        }
        return sendSet(cmd).map {
            updateState {
                when (vfo) {
                    Vfo.B, Vfo.SUB -> copy(freqB = freq)
                    else           -> copy(freqA = freq)
                }
            }
        }
    }

    override suspend fun setMode(mode: RadioMode, bandwidth: Int): Result<Unit> {
        return sendSet("MD0${yaesuModeCode(mode)};").map {
            updateState { copy(mode = mode) }
        }
    }

    override suspend fun setBandwidth(bw: Int): Result<Unit> {
        val mode = _state.value.mode
        val p3 = yaesuBandwidthToP3(bw, mode)
        if (p3 < 0) return Result.success(Unit)  // mode doesn't support SH (FM/AM)

        // The FT-891 has narrow and wide filter banks (NA command switches them).
        // P3 codes 01-08 (SSB) / 01-09 (CW) only exist in the narrow bank;
        // P3 codes 10+ (SSB) / 11+ (CW) only exist in the wide bank.
        // Set NA first so the target P3 code is valid in the current bank.
        val useNarrow = when (mode) {
            RadioMode.LSB, RadioMode.USB, RadioMode.PKTLSB, RadioMode.PKTUSB -> bw <= 1800
            RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR     -> bw <= 500
            else -> false
        }
        sendSet("NA0${if (useNarrow) 1 else 0};")

        // SH format: SH P1(0=fixed) P2(1=on) P3(2 digits)
        return sendSet("SH01%02d;".format(p3)).map { updateState { copy(bandwidth = bw) } }
    }

    override suspend fun setPtt(active: Boolean): Result<Unit> {
        if (pttMethod != PttMethod.CAT) return setPttHardware(active)
        // TX1 = CAT TX ON, TX0 = CAT TX OFF (TX2 is answer-only: radio TX ON via front panel)
        val cmd = if (active) "TX1;" else "TX0;"
        return sendSet(cmd).map { updateState { copy(ptt = active) } }
    }

    override suspend fun setSplit(enabled: Boolean): Result<Unit> {
        // ST command: 0=off, 1=on, 2=on+5kHz (FT command does NOT exist on FT-891)
        val cmd = if (enabled) "ST1;" else "ST0;"
        return sendSet(cmd).map { updateState { copy(split = enabled) } }
    }

    override suspend fun setVfo(vfo: Vfo): Result<Unit> {
        val cmd = when (vfo) {
            Vfo.A -> "VS0;"
            Vfo.B -> "VS1;"
            else  -> "VS0;"
        }
        return sendSet(cmd).map { updateState { copy(activeVfo = vfo) } }
    }

    override suspend fun vfoAtoB() { sendSet("AB;") }
    override suspend fun vfoBtoA() { sendSet("BA;") }
    override suspend fun swapVfo() { sendSet("SV;") }

    override suspend fun setRfPower(level: Int): Result<Unit> {
        val v = level.coerceIn(5, 100)
        return sendSet("PC%03d;".format(v)).map { updateState { copy(rfPower = v) } }
    }

    override suspend fun setAfGain(level: Int): Result<Unit> {
        val raw = (level.coerceIn(0, 100) * 255 / 100)
        return sendSet("AG0%03d;".format(raw)).map { updateState { copy(afGain = level) } }
    }

    override suspend fun setRfGain(level: Int): Result<Unit> {
        // RG range is 000-030 per FT-891 CAT manual (not 0-255)
        val raw = (level.coerceIn(0, 100) * 30 / 100)
        return sendSet("RG0%03d;".format(raw)).map { updateState { copy(rfGain = level) } }
    }

    override suspend fun setMicGain(level: Int): Result<Unit> {
        val v = level.coerceIn(0, 100)
        return sendSet("MG%03d;".format(v)).map { updateState { copy(micGain = v) } }
    }

    override suspend fun setSquelch(level: Int): Result<Unit> {
        // SQ range is 000-100 per FT-891 CAT manual (not 0-255)
        return sendSet("SQ0%03d;".format(level.coerceIn(0, 100))).map { updateState { copy(squelch = level) } }
    }

    override suspend fun setIfShift(hz: Int): Result<Unit> {
        // IS format: IS0 P2(0/1 on/off) sign 4-digits ;  e.g. "IS01+1000;"
        val v = hz.coerceIn(-1200, 1200)
        val on = if (_state.value.ifShiftEnabled) "1" else "0"
        val sign = if (v >= 0) "+" else "-"
        return sendSet("IS0$on$sign%04d;".format(kotlin.math.abs(v))).map {
            updateState { copy(ifShift = v) }
        }
    }

    override suspend fun setIfShiftEnabled(on: Boolean): Result<Unit> {
        val hz = _state.value.ifShift.coerceIn(-1200, 1200)
        val sign = if (hz >= 0) "+" else "-"
        return sendSet("IS0${if (on) 1 else 0}$sign%04d;".format(kotlin.math.abs(hz))).map {
            updateState { copy(ifShiftEnabled = on) }
        }
    }

    override suspend fun setRit(hz: Int): Result<Unit> {
        val sign = if (hz >= 0) "+" else "-"
        return sendSet("RI$sign%05d;".format(kotlin.math.abs(hz))).map {
            updateState { copy(rit = hz) }
        }
    }

    override suspend fun setRitEnabled(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "RT1;" else "RT0;"
        return sendSet(cmd).map { updateState { copy(ritEnabled = enabled) } }
    }

    override suspend fun setXit(hz: Int): Result<Unit> {
        val sign = if (hz >= 0) "+" else "-"
        return sendSet("XI$sign%05d;".format(kotlin.math.abs(hz))).map {
            updateState { copy(xit = hz) }
        }
    }

    override suspend fun setXitEnabled(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "XT1;" else "XT0;"
        return sendSet(cmd).map { updateState { copy(xitEnabled = enabled) } }
    }

    override suspend fun setPreamp(level: PreampLevel): Result<Unit> {
        val code = when (level) {
            PreampLevel.OFF -> 0
            PreampLevel.IPO -> 0
            PreampLevel.P1  -> 1
            PreampLevel.P2  -> 2
        }
        return sendSet("PA0$code;").map { updateState { copy(preamp = level) } }
    }

    override suspend fun setAttenuator(level: AttLevel): Result<Unit> {
        // RA: P1=0(fixed) P2=0(off)/1(on). FT-891 only has single 12dB ATT.
        val code = if (level == AttLevel.OFF) 0 else 1
        return sendSet("RA0$code;").map { updateState { copy(attenuator = level) } }
    }

    override suspend fun setAgc(mode: AgcMode): Result<Unit> {
        // GT: P1=0(fixed) P2=single digit 0-4
        val code = when (mode) {
            AgcMode.OFF  -> 0
            AgcMode.FAST -> 1
            AgcMode.MID  -> 2
            AgcMode.SLOW -> 3
            AgcMode.AUTO -> 4
        }
        return sendSet("GT0$code;").map { updateState { copy(agc = mode) } }
    }

    override suspend fun setNoiseBlanker(enabled: Boolean): Result<Unit> {
        // NB: P1=0(fixed) P2=0/1
        return sendSet("NB0${if (enabled) 1 else 0};").map { updateState { copy(noiseBlanker = enabled) } }
    }

    override suspend fun setNoiseReduction(enabled: Boolean): Result<Unit> {
        // NR: P1=0(fixed) P2=0/1
        return sendSet("NR0${if (enabled) 1 else 0};").map { updateState { copy(noiseReduction = enabled) } }
    }

    override suspend fun setAutoNotch(enabled: Boolean): Result<Unit> {
        // BC: P1=0(fixed) P2=0(off)/1(on)
        return sendSet("BC0${if (enabled) 1 else 0};").map { updateState { copy(autoNotch = enabled) } }
    }

    override suspend fun setSpeechProc(enabled: Boolean): Result<Unit> {
        // PR: P1=0(speech proc) P2=0/1
        return sendSet("PR0${if (enabled) 1 else 0};").map { updateState { copy(speechProc = enabled) } }
    }

    override suspend fun setSpeechProcLevel(level: Int): Result<Unit> {
        val v = level.coerceIn(0, 100)
        return sendSet("PL%03d;".format(v)).map { updateState { copy(speechProcLevel = v) } }
    }

    override suspend fun setVox(enabled: Boolean): Result<Unit> {
        val cmd = if (enabled) "VX1;" else "VX0;"
        return sendSet(cmd).map { updateState { copy(vox = enabled) } }
    }

    override suspend fun setTuner(enabled: Boolean): Result<Unit> {
        val code = if (enabled) 1 else 0
        return sendSet("AC00$code;").map { updateState { copy(tuner = enabled) } }
    }

    override suspend fun startTune(): Result<Unit> = sendSet("AC002;").map {}

    override suspend fun setAntenna(port: Int): Result<Unit> {
        return sendSet("AN0$port;").map { updateState { copy(antennaPort = port) } }
    }

    override suspend fun setCwSpeed(wpm: Int): Result<Unit> {
        val v = wpm.coerceIn(4, 60)
        return sendSet("KS%03d;".format(v)).map { updateState { copy(cwSpeed = v) } }
    }

    override suspend fun setCwPitch(hz: Int): Result<Unit> {
        val v = hz.coerceIn(300, 1050)
        return sendSet("EX014%04d;".format(v)).map { updateState { copy(cwPitch = v) } }
    }

    override suspend fun getSmeter(): Int {
        val resp = sendCommand("SM0;").getOrDefault("")
        if (!resp.startsWith("SM")) return 0
        return resp.drop(3).dropLast(1).toIntOrNull()?.also { v ->
            updateState { copy(sMeter = v) }
        } ?: 0
    }

    override suspend fun getRfMeter(): Int {
        val resp = sendCommand("RM1;").getOrDefault("")
        return resp.drop(3).dropLast(1).toIntOrNull()?.also { v ->
            updateState { copy(rfMeter = v) }
        } ?: 0
    }

    override suspend fun getSwrMeter(): Int {
        val resp = sendCommand("RM6;").getOrDefault("")
        return resp.drop(3).dropLast(1).toIntOrNull()?.also { v ->
            updateState { copy(swrMeter = v) }
        } ?: 0
    }

    override suspend fun getAlcMeter(): Int {
        val resp = sendCommand("RM4;").getOrDefault("")
        return resp.drop(3).dropLast(1).toIntOrNull()?.also { v ->
            updateState { copy(alcMeter = v) }
        } ?: 0
    }

    override suspend fun pollStatus() {
        sendCommand("FA;").getOrDefault("").let { r ->
            if (r.startsWith("FA") && r.length >= 12)
                r.substring(2, r.length - 1).toLongOrNull()?.let { f -> updateState { copy(freqA = f) } }
        }
        sendCommand("FB;").getOrDefault("").let { r ->
            if (r.startsWith("FB") && r.length >= 12)
                r.substring(2, r.length - 1).toLongOrNull()?.let { f -> updateState { copy(freqB = f) } }
        }
        sendCommand("MD0;").getOrDefault("").let { r ->
            if (r.startsWith("MD") && r.length >= 4)
                r.getOrNull(3)?.let { c -> updateState { copy(mode = yaesuModeFromCode(c)) } }
        }
        sendCommand("TX;").getOrDefault("").let { r ->
            // TX answer: 0=RX, 1=CAT TX, 2=front-panel/VOX TX
            if (r.startsWith("TX") && r.length >= 3)
                updateState { copy(ptt = r[2] != '0') }
        }
        sendCommand("SM0;").getOrDefault("").let { r ->
            if (r.startsWith("SM0") && r.length >= 8)
                r.substring(3, 7).toIntOrNull()?.let { v -> updateState { copy(sMeter = v) } }
        }
        if (_state.value.ptt) {
            sendCommand("RM1;").getOrDefault("").let { r ->
                r.drop(3).dropLast(1).toIntOrNull()?.let { v -> updateState { copy(rfMeter = v) } }
            }
            sendCommand("RM6;").getOrDefault("").let { r ->
                r.drop(3).dropLast(1).toIntOrNull()?.let { v -> updateState { copy(swrMeter = v) } }
            }
            sendCommand("RM4;").getOrDefault("").let { r ->
                r.drop(3).dropLast(1).toIntOrNull()?.let { v -> updateState { copy(alcMeter = v) } }
            }
        }
        // IS answer: IS0 P2(on/off) sign 4-digit-Hz ;  e.g. "IS01+1000;"
        sendCommand("IS0;").getOrDefault("").let { r ->
            if (r.startsWith("IS0") && r.length >= 10) {
                val on   = r[3] != '0'
                val sign = if (r[4] == '-') -1 else 1
                r.substring(5, 9).toIntOrNull()?.let { abs ->
                    updateState { copy(ifShift = sign * abs, ifShiftEnabled = on) }
                }
            }
        }
    }

    // Reads full radio state on connect. Each command is independent — failures skip silently.
    private suspend fun syncFromRadio() {
        // VFO select
        sendCommand("VS;").getOrDefault("").let { r ->
            if (r.startsWith("VS") && r.length >= 3)
                updateState { copy(activeVfo = if (r[2] == '1') Vfo.B else Vfo.A) }
        }
        // Frequencies
        sendCommand("FA;").getOrDefault("").let { r ->
            if (r.startsWith("FA") && r.length >= 12)
                r.substring(2, r.length - 1).toLongOrNull()?.let { f -> updateState { copy(freqA = f) } }
        }
        sendCommand("FB;").getOrDefault("").let { r ->
            if (r.startsWith("FB") && r.length >= 12)
                r.substring(2, r.length - 1).toLongOrNull()?.let { f -> updateState { copy(freqB = f) } }
        }
        // Mode
        sendCommand("MD0;").getOrDefault("").let { r ->
            if (r.startsWith("MD") && r.length >= 4)
                r.getOrNull(3)?.let { c -> updateState { copy(mode = yaesuModeFromCode(c)) } }
        }
        // RF power (PC, 005–100W)
        sendCommand("PC;").getOrDefault("").let { r ->
            if (r.startsWith("PC") && r.length >= 5)
                r.substring(2, 5).toIntOrNull()?.let { v -> updateState { copy(rfPower = v) } }
        }
        // AF gain (AG0, 000–255 → scale to 0–100)
        sendCommand("AG0;").getOrDefault("").let { r ->
            if (r.startsWith("AG0") && r.length >= 7)
                r.substring(3, 6).toIntOrNull()?.let { v ->
                    updateState { copy(afGain = (v * 100 / 255).coerceIn(0, 100)) }
                }
        }
        // RF gain (RG0, 000–030 → scale to 0–100)
        sendCommand("RG0;").getOrDefault("").let { r ->
            if (r.startsWith("RG0") && r.length >= 7)
                r.substring(3, 6).toIntOrNull()?.let { v ->
                    updateState { copy(rfGain = (v * 100 / 30).coerceIn(0, 100)) }
                }
        }
        // MIC gain (MG, 000–100)
        sendCommand("MG;").getOrDefault("").let { r ->
            if (r.startsWith("MG") && r.length >= 6)
                r.substring(2, 5).toIntOrNull()?.let { v -> updateState { copy(micGain = v) } }
        }
        // Squelch (SQ0, 000–100 — no scaling needed)
        sendCommand("SQ0;").getOrDefault("").let { r ->
            if (r.startsWith("SQ0") && r.length >= 7)
                r.substring(3, 6).toIntOrNull()?.let { v ->
                    updateState { copy(squelch = v.coerceIn(0, 100)) }
                }
        }
        // Preamp (PA0X — 0=IPO, 1=P1, 2=P2)
        sendCommand("PA0;").getOrDefault("").let { r ->
            if (r.startsWith("PA0") && r.length >= 4)
                updateState {
                    copy(preamp = when (r[3]) {
                        '1' -> PreampLevel.P1
                        '2' -> PreampLevel.P2
                        else -> PreampLevel.IPO
                    })
                }
        }
        // ATT (RA0X — 0=off, 1=on/12dB). Read: "RA0;" → answer "RA00;" or "RA01;"
        sendCommand("RA0;").getOrDefault("").let { r ->
            if (r.startsWith("RA0") && r.length >= 5)
                updateState { copy(attenuator = if (r[3] != '0') AttLevel.ATT12 else AttLevel.OFF) }
        }
        // AGC (GT0X — single digit 0-4). Read: "GT0;" → answer "GT0X;"
        sendCommand("GT0;").getOrDefault("").let { r ->
            if (r.startsWith("GT0") && r.length >= 5)
                updateState {
                    copy(agc = when (r[3]) {
                        '0' -> AgcMode.OFF
                        '1' -> AgcMode.FAST
                        '2' -> AgcMode.MID
                        '3' -> AgcMode.SLOW
                        else -> AgcMode.AUTO  // 4-6 are AUTO sub-states
                    })
                }
        }
        // NB: P1=0(fixed) P2=0/1. Read: "NB0;" → answer "NB00;" or "NB01;"
        sendCommand("NB0;").getOrDefault("").let { r ->
            if (r.startsWith("NB0") && r.length >= 5)
                updateState { copy(noiseBlanker = r[3] != '0') }
        }
        // NR: P1=0(fixed) P2=0/1
        sendCommand("NR0;").getOrDefault("").let { r ->
            if (r.startsWith("NR0") && r.length >= 5)
                updateState { copy(noiseReduction = r[3] != '0') }
        }
        // BC: P1=0(fixed) P2=0/1
        sendCommand("BC0;").getOrDefault("").let { r ->
            if (r.startsWith("BC0") && r.length >= 5)
                updateState { copy(autoNotch = r[3] != '0') }
        }
        // PR: P1=0(speech proc) P2=0/1
        sendCommand("PR0;").getOrDefault("").let { r ->
            if (r.startsWith("PR0") && r.length >= 5)
                updateState { copy(speechProc = r[3] != '0') }
        }
        // Speech proc level (PLXXX)
        sendCommand("PL;").getOrDefault("").let { r ->
            if (r.startsWith("PL") && r.length >= 5)
                r.substring(2, 5).toIntOrNull()?.let { v -> updateState { copy(speechProcLevel = v) } }
        }
        // VOX (VXX)
        sendCommand("VX;").getOrDefault("").let { r ->
            if (r.startsWith("VX") && r.length >= 3)
                updateState { copy(vox = r[2] != '0') }
        }
        // Tuner (AC00X — 0=off, 1=on, 2=tuning)
        sendCommand("AC;").getOrDefault("").let { r ->
            if (r.startsWith("AC") && r.length >= 5)
                updateState { copy(tuner = r[4] != '0') }
        }
        // Split: ST command (not FT — FT doesn't exist on FT-891)
        sendCommand("ST;").getOrDefault("").let { r ->
            if (r.startsWith("ST") && r.length >= 3)
                updateState { copy(split = r[2] != '0') }
        }
        // CW speed (KSXXX)
        sendCommand("KS;").getOrDefault("").let { r ->
            if (r.startsWith("KS") && r.length >= 5)
                r.substring(2, 5).toIntOrNull()?.let { v -> updateState { copy(cwSpeed = v) } }
        }
        // IS answer: IS0 P2(on/off) sign 4-digit-Hz ;  e.g. "IS01+1000;"
        sendCommand("IS0;").getOrDefault("").let { r ->
            if (r.startsWith("IS0") && r.length >= 10) {
                val on   = r[3] != '0'
                val sign = if (r[4] == '-') -1 else 1
                r.substring(5, 9).toIntOrNull()?.let { abs ->
                    updateState { copy(ifShift = sign * abs, ifShiftEnabled = on) }
                }
            }
        }
        // RIT (RT0/RT1 = on/off)
        sendCommand("RT;").getOrDefault("").let { r ->
            if (r.startsWith("RT") && r.length >= 3)
                updateState { copy(ritEnabled = r[2] != '0') }
        }
    }

    // FT-891 MD command mode codes (from CAT manual page 11 + MR table page 12):
    // 1=LSB 2=USB 3=CW 4=FM 5=AM 6=RTTY-L 7=CW-R 8=DATA-L 9=RTTY-U
    // A=reserved/unused  B=FM-N  C=DATA-U  D=AM-N
    private fun yaesuModeCode(mode: RadioMode): String = when (mode) {
        RadioMode.LSB    -> "1"
        RadioMode.USB    -> "2"
        RadioMode.CW     -> "3"
        RadioMode.FM     -> "4"
        RadioMode.AM     -> "5"
        RadioMode.RTTY   -> "6"
        RadioMode.CWR    -> "7"
        RadioMode.PKTLSB -> "8"
        RadioMode.RTTYR  -> "9"
        RadioMode.FMN    -> "B"
        RadioMode.PKTUSB -> "C"
        RadioMode.AMN    -> "D"
        else             -> "2"
    }

    private fun yaesuModeFromCode(char: Char): RadioMode = when (char.uppercaseChar()) {
        '1' -> RadioMode.LSB
        '2' -> RadioMode.USB
        '3' -> RadioMode.CW
        '4' -> RadioMode.FM
        '5' -> RadioMode.AM
        '6' -> RadioMode.RTTY
        '7' -> RadioMode.CWR
        '8' -> RadioMode.PKTLSB
        '9' -> RadioMode.RTTYR
        'B' -> RadioMode.FMN
        'C' -> RadioMode.PKTUSB
        'D' -> RadioMode.AMN
        else -> RadioMode.USB  // 'A' is reserved/unused
    }

    // Returns SH P3 code for the given bandwidth + mode, or -1 if SH has no effect in this mode.
    // P3=0 means "use default" (no filter change). Source: FT-891 CAT manual SH command table.
    private fun yaesuBandwidthToP3(bw: Int, mode: RadioMode): Int = when (mode) {
        RadioMode.LSB, RadioMode.USB, RadioMode.PKTLSB, RadioMode.PKTUSB -> when (bw) {
            // SSB/DATA narrow: 01-09 (200–1800 Hz)
            200  -> 1;  400  -> 2;  600  -> 3;  850  -> 4
            1100 -> 5;  1350 -> 6;  1500 -> 7;  1650 -> 8
            // SSB/DATA wide:  09-21 (1800–3200 Hz)
            1800 -> 9;  1950 -> 10; 2100 -> 11; 2200 -> 12
            2300 -> 13; 2400 -> 14; 2500 -> 15; 2600 -> 16
            2700 -> 17; 2800 -> 18; 2900 -> 19; 3000 -> 20; 3200 -> 21
            else -> 0
        }
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR -> when (bw) {
            // CW/RTTY narrow: 01-10 (50–500 Hz)
            50   -> 1;  100  -> 2;  150  -> 3;  200  -> 4;  250  -> 5
            300  -> 6;  350  -> 7;  400  -> 8;  450  -> 9;  500  -> 10
            // CW/RTTY wide:  10-16 (500–2400 Hz)
            800  -> 11; 1200 -> 12; 1400 -> 13; 1700 -> 14; 2000 -> 15; 2400 -> 16
            else -> 0
        }
        else -> -1  // AM, FM, FMN, PKTFM: no SH filter control
    }
}

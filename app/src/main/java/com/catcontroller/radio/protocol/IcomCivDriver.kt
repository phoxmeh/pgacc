package com.catcontroller.radio.protocol

import com.catcontroller.model.*
import com.catcontroller.model.PttMethod
import com.catcontroller.serial.UsbSerialManager
import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * ICOM CI-V protocol (binary).
 * Frame format: 0xFE 0xFE [civAddr] 0xE0 [cmd] [sub] [data] 0xFD
 */
class IcomCivDriver(
    usbSerialManager: UsbSerialManager,
    private val civAddress: Int = 0x94,
    private val baudRate: Int = 19200,
    private val catVid: Int = 0,
    private val catPid: Int = 0,
    private val catPortIndex: Int = 0,
    pttMethod: PttMethod = PttMethod.CAT,
    pttSeparatePort: Boolean = false,
    pttVid: Int = 0,
    pttPid: Int = 0,
    pttPortIndex: Int = 0,
) : BaseCatDriver(usbSerialManager, pttMethod, pttSeparatePort, pttVid, pttPid, pttPortIndex) {

    private val ctrlAddr = 0xE0

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

    private fun buildFrame(cmd: Int, sub: Int = -1, data: ByteArray = byteArrayOf()): ByteArray {
        val frame = mutableListOf<Byte>(
            0xFE.toByte(), 0xFE.toByte(),
            civAddress.toByte(), ctrlAddr.toByte(),
            cmd.toByte(),
        )
        if (sub >= 0) frame.add(sub.toByte())
        frame.addAll(data.toList())
        frame.add(0xFD.toByte())
        return frame.toByteArray()
    }

    private fun freqToBytes(freq: Long): ByteArray {
        // BCD encoding, 5 bytes little-endian
        var f = freq
        val bytes = ByteArray(5)
        for (i in 0 until 5) {
            val low = (f % 10).toInt()
            f /= 10
            val high = (f % 10).toInt()
            f /= 10
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return bytes
    }

    private fun bytesToFreq(bytes: ByteArray): Long {
        var freq = 0L
        var multiplier = 1L
        for (b in bytes) {
            val low = (b.toInt() and 0x0F).toLong()
            val high = ((b.toInt() shr 4) and 0x0F).toLong()
            freq += low * multiplier
            multiplier *= 10
            freq += high * multiplier
            multiplier *= 10
        }
        return freq
    }

    override suspend fun setFrequency(freq: Long, vfo: Vfo): Result<Unit> {
        val freqBytes = freqToBytes(freq)
        val frame = buildFrame(0x05, data = freqBytes)
        return sendRaw(frame).map {
            updateState {
                when (vfo) {
                    Vfo.B, Vfo.SUB -> copy(freqB = freq)
                    else           -> copy(freqA = freq)
                }
            }
        }
    }

    override suspend fun setMode(mode: RadioMode, bandwidth: Int): Result<Unit> {
        val modeCode = civModeCode(mode)
        val filterCode = when {
            bandwidth <= 0    -> 0x01
            bandwidth <= 500  -> 0x03
            bandwidth <= 1500 -> 0x02
            else              -> 0x01
        }
        val frame = buildFrame(0x06, data = byteArrayOf(modeCode.toByte(), filterCode.toByte()))
        return sendRaw(frame).map { updateState { copy(mode = mode, bandwidth = bandwidth) } }
    }

    override suspend fun setBandwidth(bw: Int): Result<Unit> {
        val filterCode = when {
            bw <= 500  -> 0x03
            bw <= 1500 -> 0x02
            else       -> 0x01
        }
        val modeCode = civModeCode(_state.value.mode)
        val frame = buildFrame(0x06, data = byteArrayOf(modeCode.toByte(), filterCode.toByte()))
        return sendRaw(frame).map { updateState { copy(bandwidth = bw) } }
    }

    override suspend fun setPtt(active: Boolean): Result<Unit> {
        if (pttMethod != PttMethod.CAT) return setPttHardware(active)
        val code = if (active) 0x01 else 0x00
        val frame = buildFrame(0x1C, 0x00, byteArrayOf(code.toByte()))
        return sendRaw(frame).map { updateState { copy(ptt = active) } }
    }

    override suspend fun setSplit(enabled: Boolean): Result<Unit> {
        val code = if (enabled) 0x01 else 0x00
        val frame = buildFrame(0x0F, data = byteArrayOf(code.toByte()))
        return sendRaw(frame).map { updateState { copy(split = enabled) } }
    }

    override suspend fun setVfo(vfo: Vfo): Result<Unit> {
        val code = when (vfo) {
            Vfo.A, Vfo.MAIN -> 0x00
            Vfo.B, Vfo.SUB  -> 0x01
            else             -> 0x00
        }
        val frame = buildFrame(0x07, data = byteArrayOf(code.toByte()))
        return sendRaw(frame).map { updateState { copy(activeVfo = vfo) } }
    }

    override suspend fun vfoAtoB() {
        sendRaw(buildFrame(0x07, 0xA0))
    }

    override suspend fun vfoBtoA() {
        sendRaw(buildFrame(0x07, 0xB0))
    }

    override suspend fun swapVfo() {
        sendRaw(buildFrame(0x07, 0xB1))
    }

    override suspend fun setRfPower(level: Int): Result<Unit> {
        val v = (level * 255 / 100).coerceIn(0, 255)
        val frame = buildFrame(0x14, 0x0A, byteArrayOf(0x00.toByte(), v.toByte()))
        return sendRaw(frame).map { updateState { copy(rfPower = level) } }
    }

    override suspend fun setAfGain(level: Int): Result<Unit> {
        val v = (level * 255 / 100).coerceIn(0, 255)
        val frame = buildFrame(0x14, 0x01, byteArrayOf(0x00.toByte(), v.toByte()))
        return sendRaw(frame).map { updateState { copy(afGain = level) } }
    }

    override suspend fun setRfGain(level: Int): Result<Unit> {
        val v = (level * 255 / 100).coerceIn(0, 255)
        val frame = buildFrame(0x14, 0x02, byteArrayOf(0x00.toByte(), v.toByte()))
        return sendRaw(frame).map { updateState { copy(rfGain = level) } }
    }

    override suspend fun setMicGain(level: Int): Result<Unit> {
        val v = (level * 255 / 100).coerceIn(0, 255)
        val frame = buildFrame(0x14, 0x0B, byteArrayOf(0x00.toByte(), v.toByte()))
        return sendRaw(frame).map { updateState { copy(micGain = level) } }
    }

    override suspend fun setSquelch(level: Int): Result<Unit> {
        val v = (level * 255 / 100).coerceIn(0, 255)
        val frame = buildFrame(0x14, 0x03, byteArrayOf(0x00.toByte(), v.toByte()))
        return sendRaw(frame).map { updateState { copy(squelch = level) } }
    }

    override suspend fun setRit(hz: Int): Result<Unit> {
        val abs = kotlin.math.abs(hz)
        val sign: Byte = if (hz >= 0) 0x00 else 0x01
        val hi = ((abs / 100) % 10).toByte()
        val lo = (abs % 100).toByte()
        val frame = buildFrame(0x21, 0x01, byteArrayOf(lo, hi, sign))
        return sendRaw(frame).map { updateState { copy(rit = hz) } }
    }

    override suspend fun setRitEnabled(enabled: Boolean): Result<Unit> {
        val frame = buildFrame(0x21, 0x02, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendRaw(frame).map { updateState { copy(ritEnabled = enabled) } }
    }

    override suspend fun setXit(hz: Int): Result<Unit> = setRit(hz)
    override suspend fun setXitEnabled(enabled: Boolean): Result<Unit> = setRitEnabled(enabled)

    override suspend fun setPreamp(level: PreampLevel): Result<Unit> {
        val code: Byte = when (level) {
            PreampLevel.OFF -> 0x00
            PreampLevel.P1  -> 0x01
            PreampLevel.P2  -> 0x02
            PreampLevel.IPO -> 0x00
        }
        val frame = buildFrame(0x16, 0x02, byteArrayOf(code))
        return sendRaw(frame).map { updateState { copy(preamp = level) } }
    }

    override suspend fun setAttenuator(level: AttLevel): Result<Unit> {
        val code: Byte = when (level) {
            AttLevel.OFF   -> 0x00
            AttLevel.ATT6  -> 0x06
            AttLevel.ATT10 -> 0x10
            AttLevel.ATT12 -> 0x12
            AttLevel.ATT18 -> 0x18
            AttLevel.ATT20 -> 0x20
            AttLevel.ATT24 -> 0x24
        }
        val frame = buildFrame(0x11, data = byteArrayOf(code))
        return sendRaw(frame).map { updateState { copy(attenuator = level) } }
    }

    override suspend fun setAgc(mode: AgcMode): Result<Unit> {
        val code: Byte = when (mode) {
            AgcMode.OFF  -> 0x00
            AgcMode.FAST -> 0x01
            AgcMode.MID  -> 0x02
            AgcMode.SLOW -> 0x03
            AgcMode.AUTO -> 0x04
        }
        val frame = buildFrame(0x16, 0x12, byteArrayOf(code))
        return sendRaw(frame).map { updateState { copy(agc = mode) } }
    }

    override suspend fun setNoiseBlanker(enabled: Boolean): Result<Unit> {
        val frame = buildFrame(0x16, 0x22, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendRaw(frame).map { updateState { copy(noiseBlanker = enabled) } }
    }

    override suspend fun setNoiseReduction(enabled: Boolean): Result<Unit> {
        val frame = buildFrame(0x16, 0x40, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendRaw(frame).map { updateState { copy(noiseReduction = enabled) } }
    }

    override suspend fun setAutoNotch(enabled: Boolean): Result<Unit> {
        val frame = buildFrame(0x16, 0x41, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendRaw(frame).map { updateState { copy(autoNotch = enabled) } }
    }

    override suspend fun setSpeechProc(enabled: Boolean): Result<Unit> {
        val frame = buildFrame(0x16, 0x44, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendRaw(frame).map { updateState { copy(speechProc = enabled) } }
    }

    override suspend fun setSpeechProcLevel(level: Int): Result<Unit> {
        val v = (level * 255 / 100).coerceIn(0, 255)
        val frame = buildFrame(0x14, 0x1A, byteArrayOf(0x00, v.toByte()))
        return sendRaw(frame).map { updateState { copy(speechProcLevel = level) } }
    }

    override suspend fun setVox(enabled: Boolean): Result<Unit> {
        val frame = buildFrame(0x16, 0x46, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendRaw(frame).map { updateState { copy(vox = enabled) } }
    }

    override suspend fun setTuner(enabled: Boolean): Result<Unit> {
        val frame = buildFrame(0x1C, 0x01, byteArrayOf(if (enabled) 0x01 else 0x00))
        return sendRaw(frame).map { updateState { copy(tuner = enabled) } }
    }

    override suspend fun startTune(): Result<Unit> {
        val frame = buildFrame(0x1C, 0x01, byteArrayOf(0x02))
        return sendRaw(frame).map {}
    }

    override suspend fun setAntenna(port: Int): Result<Unit> {
        val frame = buildFrame(0x12, data = byteArrayOf(port.toByte()))
        return sendRaw(frame).map { updateState { copy(antennaPort = port) } }
    }

    override suspend fun setCwSpeed(wpm: Int): Result<Unit> {
        val v = wpm.coerceIn(6, 48)
        val frame = buildFrame(0x14, 0x0C, byteArrayOf(0x00, v.toByte()))
        return sendRaw(frame).map { updateState { copy(cwSpeed = v) } }
    }

    override suspend fun setCwPitch(hz: Int): Result<Unit> {
        val code = ((hz - 300) / 50).coerceIn(0, 30)
        val frame = buildFrame(0x14, 0x09, byteArrayOf(0x00, code.toByte()))
        return sendRaw(frame).map { updateState { copy(cwPitch = hz) } }
    }

    override suspend fun getSmeter(): Int {
        val frame = buildFrame(0x15, 0x02)
        val resp = sendRaw(frame).getOrDefault(byteArrayOf())
        return parseLevelResponse(resp).also { updateState { copy(sMeter = it) } }
    }

    override suspend fun getRfMeter(): Int {
        val frame = buildFrame(0x15, 0x11)
        val resp = sendRaw(frame).getOrDefault(byteArrayOf())
        return parseLevelResponse(resp).also { updateState { copy(rfMeter = it) } }
    }

    override suspend fun getSwrMeter(): Int {
        val frame = buildFrame(0x15, 0x12)
        val resp = sendRaw(frame).getOrDefault(byteArrayOf())
        return parseLevelResponse(resp).also { updateState { copy(swrMeter = it) } }
    }

    override suspend fun getAlcMeter(): Int {
        val frame = buildFrame(0x15, 0x13)
        val resp = sendRaw(frame).getOrDefault(byteArrayOf())
        return parseLevelResponse(resp).also { updateState { copy(alcMeter = it) } }
    }

    override suspend fun pollStatus() {
        // Request frequency
        val freqResp = sendRaw(buildFrame(0x03)).getOrDefault(byteArrayOf())
        parseFrequencyResponse(freqResp)
        // Request mode
        val modeResp = sendRaw(buildFrame(0x04)).getOrDefault(byteArrayOf())
        parseModeResponse(modeResp)
    }

    private fun parseFrequencyResponse(bytes: ByteArray) {
        if (bytes.size < 11) return
        val dataStart = findDataStart(bytes) ?: return
        if (dataStart + 5 > bytes.size) return
        val freq = bytesToFreq(bytes.copyOfRange(dataStart, dataStart + 5))
        updateState { copy(freqA = freq) }
    }

    private fun parseModeResponse(bytes: ByteArray) {
        if (bytes.size < 8) return
        val dataStart = findDataStart(bytes) ?: return
        if (dataStart >= bytes.size) return
        val modeCode = bytes[dataStart].toInt() and 0xFF
        updateState { copy(mode = civModeFromCode(modeCode)) }
    }

    private fun findDataStart(bytes: ByteArray): Int? {
        // Skip FE FE addr ctrl cmd [sub]
        if (bytes.size < 6) return null
        return 5
    }

    private fun parseLevelResponse(bytes: ByteArray): Int {
        if (bytes.size < 8) return 0
        val dataStart = findDataStart(bytes) ?: return 0
        if (dataStart + 1 >= bytes.size) return 0
        val hi = (bytes[dataStart].toInt() and 0xFF)
        val lo = (bytes[dataStart + 1].toInt() and 0xFF)
        return (hi * 256 + lo) * 100 / 255
    }

    override fun isRawResponseComplete(bytes: List<Byte>): Boolean =
        bytes.isNotEmpty() && bytes.last() == 0xFD.toByte()

    private fun civModeCode(mode: RadioMode) = when (mode) {
        RadioMode.LSB    -> 0x00
        RadioMode.USB    -> 0x01
        RadioMode.AM     -> 0x02
        RadioMode.CW     -> 0x03
        RadioMode.RTTY   -> 0x04
        RadioMode.FM     -> 0x05
        RadioMode.CWR    -> 0x07
        RadioMode.RTTYR  -> 0x08
        RadioMode.PKTLSB -> 0x0C
        RadioMode.PKTUSB -> 0x0D
        RadioMode.PKTFM  -> 0x11
        RadioMode.WFM    -> 0x06
        RadioMode.AMS    -> 0x11
        else             -> 0x01
    }

    private fun civModeFromCode(code: Int) = when (code) {
        0x00 -> RadioMode.LSB
        0x01 -> RadioMode.USB
        0x02 -> RadioMode.AM
        0x03 -> RadioMode.CW
        0x04 -> RadioMode.RTTY
        0x05 -> RadioMode.FM
        0x06 -> RadioMode.WFM
        0x07 -> RadioMode.CWR
        0x08 -> RadioMode.RTTYR
        0x0C -> RadioMode.PKTLSB
        0x0D -> RadioMode.PKTUSB
        0x11 -> RadioMode.PKTFM
        else -> RadioMode.USB
    }
}

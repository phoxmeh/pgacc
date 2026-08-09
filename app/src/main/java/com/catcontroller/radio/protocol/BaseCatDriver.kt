package com.catcontroller.radio.protocol

import com.catcontroller.model.PttMethod
import com.catcontroller.model.RadioState
import com.catcontroller.radio.RadioDriver
import com.catcontroller.serial.UsbSerialManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

abstract class BaseCatDriver(
    protected val usbSerialManager: UsbSerialManager,
    protected val pttMethod: PttMethod = PttMethod.CAT,
    private val pttSeparatePort: Boolean = false,
    private val pttVid: Int = 0,
    private val pttPid: Int = 0,
    private val pttPortIndex: Int = 0,
) : RadioDriver {

    protected val _state = MutableStateFlow(RadioState())
    override val state: StateFlow<RadioState> = _state.asStateFlow()

    protected var port: UsbSerialPort? = null
    protected var pttHwPort: UsbSerialPort? = null
    private val ioMutex = Mutex()
    private val readBuffer = ByteArray(4096)

    // ── PTT port management ───────────────────────────────────────────────────

    override suspend fun connectPtt(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _state.value = _state.value.copy(pttConnecting = true)
            if (pttSeparatePort && pttMethod != PttMethod.CAT) {
                // Open the dedicated hardware PTT port
                pttHwPort = usbSerialManager.openPttPort(
                    portIndex    = pttPortIndex,
                    preferredVid = pttVid,
                    preferredPid = pttPid,
                ).getOrThrow()
            } else if (pttMethod != PttMethod.CAT) {
                // RTS/DTR on the same port as CAT — port must already be open
                if (port == null) error("Connect CAT first before enabling PTT on the same port")
            }
            updateState { copy(pttConnected = true, pttConnecting = false) }
        }.onFailure {
            updateState { copy(pttConnecting = false) }
        }
    }

    override suspend fun disconnectPtt() {
        withContext(Dispatchers.IO) {
            pttHwPort?.let {
                runCatching { it.close() }
                usbSerialManager.removePort(it)
            }
            pttHwPort = null
            updateState { copy(pttConnected = false) }
        }
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    protected suspend fun sendCommand(cmd: String): Result<String> =
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    val p = port ?: error("Not connected")
                    p.write(cmd.toByteArray(Charsets.US_ASCII), 500)
                    readResponse(p)
                }
            }
        }

    // Send a SET command that gets no response (Yaesu protocol).
    protected suspend fun sendSet(cmd: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    val p = port ?: error("Not connected")
                    p.write(cmd.toByteArray(Charsets.US_ASCII), 500)
                }
            }
        }

    protected suspend fun sendRaw(bytes: ByteArray): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    val p = port ?: error("Not connected")
                    p.write(bytes, 500)
                    readRawResponse(p)
                }
            }
        }

    protected suspend fun setPttHardware(active: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = pttHwPort ?: port ?: error("Not connected")
                when (pttMethod) {
                    PttMethod.RTS -> p.setRTS(active)
                    PttMethod.DTR -> p.setDTR(active)
                    PttMethod.CAT -> Unit
                }
                updateState { copy(ptt = active) }
            }
        }

    private fun readResponse(p: UsbSerialPort): String {
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + 1500L
        while (System.currentTimeMillis() < deadline) {
            val n = try { p.read(readBuffer, 200) } catch (e: IOException) { break }
            if (n > 0) {
                sb.append(String(readBuffer, 0, n, Charsets.US_ASCII))
                if (isResponseComplete(sb.toString())) break
            }
        }
        return sb.toString()
    }

    private fun readRawResponse(p: UsbSerialPort): ByteArray {
        val result = mutableListOf<Byte>()
        val deadline = System.currentTimeMillis() + 1500L
        while (System.currentTimeMillis() < deadline) {
            val n = try { p.read(readBuffer, 200) } catch (e: IOException) { break }
            if (n > 0) {
                for (i in 0 until n) result.add(readBuffer[i])
                if (isRawResponseComplete(result)) break
            }
        }
        return result.toByteArray()
    }

    protected open fun isResponseComplete(response: String): Boolean = response.endsWith(";")
    protected open fun isRawResponseComplete(bytes: List<Byte>): Boolean = false

    protected fun updateState(block: RadioState.() -> RadioState) {
        _state.value = _state.value.block()
    }
}

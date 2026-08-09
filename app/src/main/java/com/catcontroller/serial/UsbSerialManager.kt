package com.catcontroller.serial

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val ACTION_USB_PERMISSION = "com.catcontroller.USB_PERMISSION"

data class UsbSerialDevice(
    val driverName: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val portCount: Int,
    val portIndex: Int,
) {
    val displayLabel: String get() {
        val portSuffix = if (portCount > 1) " port ${portIndex + 1}/${portCount}" else ""
        return "$driverName$portSuffix  " +
            "(VID:${vendorId.toString(16).uppercase().padStart(4, '0')}  " +
            "PID:${productId.toString(16).uppercase().padStart(4, '0')})"
    }
}

@Singleton
class UsbSerialManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val openPorts = mutableListOf<UsbSerialPort>()

    /** Returns one entry per serial port (multi-port chips like CP2105 expand to 2 entries). */
    fun listDevices(): List<UsbSerialDevice> {
        val prober = UsbSerialProber.getDefaultProber()
        return usbManager.deviceList.values.flatMap { device ->
            val driver = prober.probeDevice(device) ?: return@flatMap emptyList()
            val name = driver.javaClass.simpleName
                .removeSuffix("SerialDriver")
                .removeSuffix("Driver")
            driver.ports.indices.map { idx ->
                UsbSerialDevice(
                    driverName = name,
                    deviceName = device.deviceName,
                    vendorId   = device.vendorId,
                    productId  = device.productId,
                    portCount  = driver.ports.size,
                    portIndex  = idx,
                )
            }
        }
    }

    suspend fun open(
        baudRate: Int,
        dataBits: Int = 8,
        stopBits: Int = UsbSerialPort.STOPBITS_1,
        parity: Int   = UsbSerialPort.PARITY_NONE,
        portIndex: Int = 0,
        preferredVid: Int = 0,
        preferredPid: Int = 0,
    ): Result<UsbSerialPort> = openPort(baudRate, dataBits, stopBits, parity, portIndex, preferredVid, preferredPid)

    suspend fun openPttPort(
        baudRate: Int = 9600,
        portIndex: Int = 0,
        preferredVid: Int = 0,
        preferredPid: Int = 0,
    ): Result<UsbSerialPort> = openPort(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE, portIndex, preferredVid, preferredPid)

    private suspend fun openPort(
        baudRate: Int,
        dataBits: Int,
        stopBits: Int,
        parity: Int,
        portIndex: Int,
        preferredVid: Int,
        preferredPid: Int,
    ): Result<UsbSerialPort> = withContext(Dispatchers.IO) {
        runCatching {
            val prober = UsbSerialProber.getDefaultProber()
            val driver = usbManager.deviceList.values
                .filter { dev ->
                    preferredVid == 0 ||
                    (dev.vendorId == preferredVid && dev.productId == preferredPid)
                }
                .mapNotNull { prober.probeDevice(it) }
                .firstOrNull()
                ?: error(
                    if (preferredVid == 0) "No supported USB serial device found"
                    else "USB device VID:${preferredVid.toString(16).uppercase()} PID:${preferredPid.toString(16).uppercase()} not found"
                )

            // If permission not yet granted, show the system dialog and tell the user to try again.
            // We fire the request here (non-blocking) rather than awaiting it, because awaiting
            // a system broadcast with RECEIVER_NOT_EXPORTED silently blocks forever on Android 13+.
            if (!usbManager.hasPermission(driver.device)) {
                val permIntent = PendingIntent.getBroadcast(
                    context,
                    driver.device.deviceId,
                    Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                usbManager.requestPermission(driver.device, permIntent)
                error("USB permission requested — approve the dialog, then tap Connect again")
            }

            val connection = usbManager.openDevice(driver.device)
                ?: error("Could not open USB device (permission denied?)")

            val safeIndex = portIndex.coerceIn(0, driver.ports.size - 1)
            val port = driver.ports[safeIndex]
            port.open(connection)
            port.setParameters(baudRate, dataBits, stopBits, parity)
            openPorts.add(port)
            port
        }
    }

    fun removePort(port: UsbSerialPort) {
        runCatching { port.close() }
        openPorts.remove(port)
    }

    fun close() {
        openPorts.forEach { runCatching { it.close() } }
        openPorts.clear()
    }
}

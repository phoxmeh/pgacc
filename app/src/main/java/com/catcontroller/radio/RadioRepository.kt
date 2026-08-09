package com.catcontroller.radio

import com.catcontroller.model.*
import com.catcontroller.radio.protocol.IcomCivDriver
import com.catcontroller.radio.protocol.KenwoodCatDriver
import com.catcontroller.radio.protocol.RigctldDriver
import com.catcontroller.radio.protocol.YaesuCatDriver
import com.catcontroller.serial.UsbSerialManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val usbSerialManager: UsbSerialManager,
) {
    private var _driver: RadioDriver? = null
    val driver: RadioDriver? get() = _driver

    fun createDriver(profile: RadioProfile): RadioDriver {
        val device = RadioDeviceList.byId(profile.deviceModelId)
        val driver: RadioDriver = when {
            profile.connectionType == ConnectionType.NETWORK_RIGCTLD ->
                RigctldDriver(profile.networkHost, profile.networkPort)

            device?.protocol == CatProtocol.ICOM_CIV ->
                IcomCivDriver(
                    usbSerialManager = usbSerialManager,
                    civAddress       = profile.civAddress,
                    baudRate         = profile.baudRate,
                    catVid           = profile.catVid,
                    catPid           = profile.catPid,
                    catPortIndex     = profile.catPortIndex,
                    pttMethod        = profile.pttMethod,
                    pttSeparatePort  = profile.pttSeparatePort,
                    pttVid           = profile.pttVid,
                    pttPid           = profile.pttPid,
                    pttPortIndex     = profile.pttPortIndex,
                )

            device?.protocol == CatProtocol.KENWOOD ||
            device?.protocol == CatProtocol.ELECRAFT ->
                KenwoodCatDriver(
                    usbSerialManager = usbSerialManager,
                    baudRate         = profile.baudRate,
                    catVid           = profile.catVid,
                    catPid           = profile.catPid,
                    catPortIndex     = profile.catPortIndex,
                    pttMethod        = profile.pttMethod,
                    pttSeparatePort  = profile.pttSeparatePort,
                    pttVid           = profile.pttVid,
                    pttPid           = profile.pttPid,
                    pttPortIndex     = profile.pttPortIndex,
                )

            else ->
                YaesuCatDriver(
                    usbSerialManager = usbSerialManager,
                    baudRate         = profile.baudRate,
                    catVid           = profile.catVid,
                    catPid           = profile.catPid,
                    catPortIndex     = profile.catPortIndex,
                    pttMethod        = profile.pttMethod,
                    pttSeparatePort  = profile.pttSeparatePort,
                    pttVid           = profile.pttVid,
                    pttPid           = profile.pttPid,
                    pttPortIndex     = profile.pttPortIndex,
                )
        }
        _driver = driver
        return driver
    }

    suspend fun disconnect() {
        _driver?.disconnect()
        _driver = null
    }
}

package com.catcontroller.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.catcontroller.model.ConnectionType
import com.catcontroller.model.PttMethod
import com.catcontroller.model.RadioProfile

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val deviceModelId: Int,
    val connectionType: String,
    val baudRate: Int,
    val dataBits: Int,
    val stopBits: Int,
    val parity: Int,
    val catVid: Int,
    val catPid: Int,
    val catPortIndex: Int = 0,
    val pttMethod: String,
    val pttSeparatePort: Boolean,
    val pttVid: Int,
    val pttPid: Int,
    val pttPortIndex: Int = 0,
    val networkHost: String,
    val networkPort: Int,
    val isDefault: Boolean,
    val civAddress: Int,
) {
    fun toProfile() = RadioProfile(
        id              = id,
        name            = name,
        deviceModelId   = deviceModelId,
        connectionType  = ConnectionType.valueOf(connectionType),
        baudRate        = baudRate,
        dataBits        = dataBits,
        stopBits        = stopBits,
        parity          = parity,
        catVid          = catVid,
        catPid          = catPid,
        catPortIndex    = catPortIndex,
        pttMethod       = runCatching { PttMethod.valueOf(pttMethod) }.getOrDefault(PttMethod.CAT),
        pttSeparatePort = pttSeparatePort,
        pttVid          = pttVid,
        pttPid          = pttPid,
        pttPortIndex    = pttPortIndex,
        networkHost     = networkHost,
        networkPort     = networkPort,
        isDefault       = isDefault,
        civAddress      = civAddress,
    )

    companion object {
        fun from(p: RadioProfile) = ProfileEntity(
            id              = p.id,
            name            = p.name,
            deviceModelId   = p.deviceModelId,
            connectionType  = p.connectionType.name,
            baudRate        = p.baudRate,
            dataBits        = p.dataBits,
            stopBits        = p.stopBits,
            parity          = p.parity,
            catVid          = p.catVid,
            catPid          = p.catPid,
            catPortIndex    = p.catPortIndex,
            pttMethod       = p.pttMethod.name,
            pttSeparatePort = p.pttSeparatePort,
            pttVid          = p.pttVid,
            pttPid          = p.pttPid,
            pttPortIndex    = p.pttPortIndex,
            networkHost     = p.networkHost,
            networkPort     = p.networkPort,
            isDefault       = p.isDefault,
            civAddress      = p.civAddress,
        )
    }
}

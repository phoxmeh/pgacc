package com.catcontroller.model

data class RadioProfile(
    val id: Long = 0,
    val name: String = "",
    val deviceModelId: Int = 0,
    val connectionType: ConnectionType = ConnectionType.USB,
    // Serial port settings
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0,
    // CAT device selection (0 = auto-detect first found)
    val catVid: Int = 0,
    val catPid: Int = 0,
    val catPortIndex: Int = 0,
    // PTT control
    val pttMethod: PttMethod = PttMethod.CAT,
    val pttSeparatePort: Boolean = false,
    val pttVid: Int = 0,
    val pttPid: Int = 0,
    val pttPortIndex: Int = 0,
    // Network (rigctld)
    val networkHost: String = "192.168.1.100",
    val networkPort: Int = 4532,
    // Meta
    val isDefault: Boolean = false,
    val civAddress: Int = 0x94,
)

enum class ConnectionType { USB, NETWORK_RIGCTLD }

enum class PttMethod(val label: String, val description: String) {
    CAT("CAT", "PTT via CAT serial command"),
    RTS("RTS", "PTT via RTS hardware line"),
    DTR("DTR", "PTT via DTR hardware line"),
}

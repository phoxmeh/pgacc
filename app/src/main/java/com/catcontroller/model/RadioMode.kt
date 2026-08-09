package com.catcontroller.model

enum class RadioMode(val label: String, val hamlibId: Int) {
    NONE("---", 0),
    AM("AM", 1),
    CW("CW", 2),
    USB("USB", 3),
    LSB("LSB", 4),
    RTTY("RTTY", 5),
    FM("FM", 6),
    WFM("WFM", 7),
    CWR("CWR", 8),
    RTTYR("RTTYR", 9),
    AMS("AMS", 10),
    PKTLSB("PKTLSB", 11),
    PKTUSB("PKTUSB", 12),
    PKTFM("PKTFM", 13),
    ECSSUSB("ECSSUSB", 14),
    ECSSLSB("ECSSLSB", 15),
    FAX("FAX", 16),
    SAM("SAM", 17),
    SAL("SAL", 18),
    SAH("SAH", 19),
    DSB("DSB", 20),
    FMN("FMN", 21),
    AMN("AMN", 22),
    PSKUSB("PSKUSB", 23),
    PSKR("PSKR", 24),
    C4FM("C4FM", 25);

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: USB
        fun fromHamlibId(id: Int) = entries.firstOrNull { it.hamlibId == id } ?: USB
    }
}

val commonModes = listOf(
    RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
    RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
    RadioMode.PKTLSB, RadioMode.PKTUSB, RadioMode.PKTFM, RadioMode.FMN
)

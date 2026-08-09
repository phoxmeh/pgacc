package com.catcontroller.model

// label   = hamlib wire string (rig_strrmode / rigctld protocol — never change these)
// displayLabel = what the UI shows (flrig convention: CW-U/CW-L, RTTY-U/RTTY-L)
enum class RadioMode(val label: String, val hamlibId: Int, val displayLabel: String = label) {
    NONE("---",     0),
    AM("AM",        1),
    CW("CW",        2, "CW-U"),
    USB("USB",      3),
    LSB("LSB",      4),
    RTTY("RTTY",    5, "RTTY-U"),
    FM("FM",        6),
    WFM("WFM",      7),
    CWR("CWR",      8, "CW-L"),
    RTTYR("RTTYR",  9, "RTTY-L"),
    AMS("AMS",      10),
    PKTLSB("PKTLSB", 11),
    PKTUSB("PKTUSB", 12),
    PKTFM("PKTFM",  13),
    ECSSUSB("ECSSUSB", 14),
    ECSSLSB("ECSSLSB", 15),
    FAX("FAX",      16),
    SAM("SAM",      17),
    SAL("SAL",      18),
    SAH("SAH",      19),
    DSB("DSB",      20),
    FMN("FMN",      21, "FM-N"),
    AMN("AMN",      22, "AM-N"),
    PSKUSB("PSKUSB", 23),
    PSKR("PSKR",    24),
    C4FM("C4FM",    25);

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: USB
        fun fromHamlibId(id: Int) = entries.firstOrNull { it.hamlibId == id } ?: USB
    }
}

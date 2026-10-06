package com.catcontroller.radio

import com.catcontroller.model.*

enum class CatProtocol { YAESU, ICOM_CIV, KENWOOD, ELECRAFT, RIGCTLD, FLEXRADIO, ALINCO, TENTEC }

/**
 * Per-rig CAT feature capabilities. All fields default true so unknown rigs show every control;
 * known entries restrict to what the radio actually supports via CAT.
 */
data class RigCaps(
    val modes: List<RadioMode> = listOf(
        RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB, RadioMode.PKTFM, RadioMode.FMN,
    ),
    // Per-rig display label overrides (flrig naming convention).
    // UI shows caps.modeDisplayLabels[mode] ?: mode.displayLabel.
    val modeDisplayLabels: Map<RadioMode, String> = emptyMap(),
    val hasNoiseBlanker: Boolean = true,
    val hasNoiseReduction: Boolean = true,
    val hasAutoNotch: Boolean = true,
    val hasIfShift: Boolean = true,
    val hasRit: Boolean = true,
    val hasSpeechProc: Boolean = true,
    val hasSpeechProcLevel: Boolean = true,
    val hasVox: Boolean = true,
    val hasTuner: Boolean = true,
    val preampLevels: List<PreampLevel> = PreampLevel.entries.toList(),
    val attLevels: List<AttLevel> = AttLevel.entries.toList(),
    val antennaCount: Int = 4,
    val hasCw: Boolean = true,
    val hasMicGain: Boolean = true,
    val hasRfGain: Boolean = true,
    val hasSquelch: Boolean = true,
    val hasBandwidth: Boolean = true,
    val hasAgc: Boolean = true,
    val hasSplit: Boolean = true,
    val bandwidthsByMode: Map<RadioMode, List<Int>> = emptyMap(),
    val minPowerWatts: Int = 5,
    val maxPowerWatts: Int = 100,
)

data class RadioDevice(
    val modelId: Int,
    val manufacturer: String,
    val model: String,
    val protocol: CatProtocol,
    val defaultBaud: Int = 9600,
    val civAddress: Int = 0x00,
    val caps: RigCaps = RigCaps(),
) {
    val displayName get() = "$manufacturer $model"
}

// ── Shared capability profiles (sourced from hamlib rig_caps structs) ────────

private val yaesuBasic = RigCaps(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY),
    hasNoiseBlanker = true,  hasNoiseReduction = false, hasAutoNotch = false,
    hasIfShift = false,      hasRit = true,  hasSpeechProc = false, hasSpeechProcLevel = false,
    hasVox = false,          hasTuner = false,
    preampLevels = listOf(PreampLevel.OFF, PreampLevel.P1),
    attLevels    = listOf(AttLevel.OFF, AttLevel.ATT20),
    antennaCount = 1, hasCw = true, hasMicGain = false, hasRfGain = false,
    hasSquelch = false, hasAgc = true, hasSplit = true, hasBandwidth = false,
)

// ── Yaesu FT-891 — sourced from flrig FT891.cxx ─────────────────────────────
// Mode list, bandwidth tables, and feature flags match flrig exactly.
// Mode order follows FT891_mode_chr: '1'..'D' (the radio's MD command codes).

// flrig: vFT891modes_ display labels keyed by RadioMode (hamlib enum value)
private val ft891ModeLabels = mapOf(
    RadioMode.LSB    to "LSB",
    RadioMode.USB    to "USB",
    RadioMode.CW     to "CW-U",    // MD '3'
    RadioMode.FM     to "FM",
    RadioMode.AM     to "AM",
    RadioMode.RTTYR  to "RTTY-L",  // MD '6'
    RadioMode.CWR    to "CW-L",    // MD '7'
    RadioMode.PKTLSB to "DATA-L",  // MD '8'
    RadioMode.RTTY   to "RTTY-U",  // MD '9'
    RadioMode.FMN    to "FM-N",    // MD 'B'
    RadioMode.PKTUSB to "DATA-U",  // MD 'C'
    RadioMode.AMN    to "AM-N",    // MD 'D'
)

// flrig: vFT891_widths_SSB — SH codes 01–21
private val ft891SsbBw = listOf(
    200, 400, 600, 850, 1100, 1350, 1500, 1650, 1800, 1950,
    2100, 2200, 2300, 2400, 2500, 2600, 2700, 2800, 2900, 3000, 3200,
)

// flrig: vFT891_widths_CW and vFT891_widths_SSBD — both use SH codes 01–17
// CW/RTTY-U/RTTY-L use this table; DATA-U/DATA-L use the same values (SSBD table)
private val ft891CwBw = listOf(
    50, 100, 150, 200, 250, 300, 350, 400, 450, 500,
    800, 1200, 1400, 1700, 2000, 2400, 3000,
)

private val ft891Caps = RigCaps(
    modes = listOf(
        RadioMode.LSB,    RadioMode.USB,
        RadioMode.CWR,    RadioMode.CW,     // CW-L, CW-U
        RadioMode.RTTYR,  RadioMode.RTTY,   // RTTY-L, RTTY-U
        RadioMode.PKTLSB, RadioMode.PKTUSB, // DATA-L, DATA-U
        RadioMode.FM,     RadioMode.FMN,
        RadioMode.AM,     RadioMode.AMN,
    ),
    modeDisplayLabels = ft891ModeLabels,
    hasNoiseBlanker = true,  hasNoiseReduction = true, hasAutoNotch = true,
    hasIfShift = true,       hasRit = true, hasSpeechProc = true, hasSpeechProcLevel = true,
    hasVox = true,           hasTuner = true,
    preampLevels = listOf(PreampLevel.IPO, PreampLevel.P1),
    attLevels    = listOf(AttLevel.OFF, AttLevel.ATT12),
    antennaCount = 2, hasCw = true, hasMicGain = true, hasRfGain = true,
    hasSquelch = true, hasAgc = true, hasSplit = true, hasBandwidth = true,
    bandwidthsByMode = mapOf(
        RadioMode.LSB    to ft891SsbBw,
        RadioMode.USB    to ft891SsbBw,
        RadioMode.CW     to ft891CwBw,
        RadioMode.CWR    to ft891CwBw,
        RadioMode.RTTY   to ft891CwBw,
        RadioMode.RTTYR  to ft891CwBw,
        RadioMode.PKTLSB to ft891CwBw,  // flrig SSBD table — same values as CW
        RadioMode.PKTUSB to ft891CwBw,  // flrig SSBD table — same values as CW
        // AM/AMN, FM/FMN: bandwidth is implicit in the mode itself, no SH control
    ),
)

// Yaesu FT-991/991A — adds PKTFM, C4FM, AMN; hasRit via FT991_FUNCS; IPO+P1+P2
private val ft991aCaps = RigCaps(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB, RadioMode.PKTFM, RadioMode.FMN,
        RadioMode.C4FM),
    hasNoiseBlanker = true,  hasNoiseReduction = true, hasAutoNotch = true,
    hasIfShift = true,       hasRit = true, hasSpeechProc = true, hasSpeechProcLevel = true,
    hasVox = true,           hasTuner = true,
    preampLevels = listOf(PreampLevel.IPO, PreampLevel.P1, PreampLevel.P2),
    attLevels    = listOf(AttLevel.OFF, AttLevel.ATT12),
    antennaCount = 2, hasCw = true, hasMicGain = true, hasRfGain = true,
    hasSquelch = false, hasAgc = true, hasSplit = true, hasBandwidth = true,
)

// Yaesu FT-DX10 / FT-DX101 base — FTDX10_ALL_RX_MODES, 6/12/18 dB ATT, IPO+P1+P2
private val ftdx10Caps = RigCaps(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB, RadioMode.PKTFM, RadioMode.FMN),
    hasNoiseBlanker = true,  hasNoiseReduction = true, hasAutoNotch = true,
    hasIfShift = true,       hasRit = true, hasSpeechProc = true, hasSpeechProcLevel = true,
    hasVox = true,           hasTuner = true,
    preampLevels = listOf(PreampLevel.IPO, PreampLevel.P1, PreampLevel.P2),
    attLevels    = listOf(AttLevel.OFF, AttLevel.ATT6, AttLevel.ATT12, AttLevel.ATT18),
    antennaCount = 2, hasCw = true, hasMicGain = true, hasRfGain = true,
    hasSquelch = false, hasAgc = true, hasSplit = true, hasBandwidth = true,
)

// Generic Yaesu modern (FT-950, FT-2000 etc.) — no specific hamlib override
private val yaesuModern = ftdx10Caps.copy(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB, RadioMode.FMN),
    preampLevels = listOf(PreampLevel.OFF, PreampLevel.P1),
)

// Icom base — older CI-V rigs; no IF shift (uses PBT), no VOX/tuner by default
private val icomBase = RigCaps(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.PKTUSB, RadioMode.PKTLSB),
    hasNoiseBlanker = true,  hasNoiseReduction = true, hasAutoNotch = true,
    hasIfShift = false,      hasRit = true, hasSpeechProc = true, hasSpeechProcLevel = false,
    hasVox = false,          hasTuner = false,
    preampLevels = listOf(PreampLevel.OFF, PreampLevel.P1, PreampLevel.P2),
    attLevels    = listOf(AttLevel.OFF, AttLevel.ATT20),
    antennaCount = 1, hasCw = true, hasMicGain = true, hasRfGain = true,
    hasSquelch = true, hasAgc = true, hasSplit = true, hasBandwidth = true,
)

// IC-7300 — IC7300_ALL_RX_MODES + IC7300_FUNCS (has VOX + TUNER, no IF shift, 20 dB ATT)
private val ic7300Caps = icomBase.copy(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB, RadioMode.PKTFM),
    hasVox = true, hasTuner = true,
    attLevels = listOf(AttLevel.OFF, AttLevel.ATT20),
    antennaCount = 1,
)

// IC-705 — adds WFM (wide FM for broadcast RX) over IC-7300
private val ic705Caps = ic7300Caps.copy(
    modes = ic7300Caps.modes + listOf(RadioMode.WFM),
)

// IC-7610 — adds PSK modes, 2 antennas, 6/12/18 dB ATT
private val ic7610Caps = ic7300Caps.copy(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB, RadioMode.PKTFM,
        RadioMode.PSKUSB, RadioMode.PSKR),
    attLevels    = listOf(AttLevel.OFF, AttLevel.ATT6, AttLevel.ATT12, AttLevel.ATT18),
    antennaCount = 2,
)

// Kenwood base — TS-5xx/7xx/8xx/9xx series
private val kenwoodBase = RigCaps(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB),
    hasNoiseBlanker = true,  hasNoiseReduction = true, hasAutoNotch = true,
    hasIfShift = true,       hasRit = true, hasSpeechProc = true, hasSpeechProcLevel = false,
    hasVox = true,           hasTuner = true,
    preampLevels = listOf(PreampLevel.OFF, PreampLevel.P1, PreampLevel.P2),
    attLevels    = listOf(AttLevel.OFF, AttLevel.ATT6, AttLevel.ATT12, AttLevel.ATT18),
    antennaCount = 2, hasCw = true, hasMicGain = true, hasRfGain = true,
    hasSquelch = true, hasAgc = true, hasSplit = true, hasBandwidth = true,
)

// TS-890S — adds PSK/PKTFM, no ANF (has Beat Cancel instead), no IF shift
private val ts890sCaps = kenwoodBase.copy(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB, RadioMode.PKTFM,
        RadioMode.PSKUSB, RadioMode.PSKR),
    hasAutoNotch = false, hasIfShift = false,
    preampLevels = listOf(PreampLevel.OFF, PreampLevel.P1),
)

// Elecraft K3/KX3 — K3_MODES, no ANF, no IF shift, no TUNER (Elecraft-specific ATU), 10 dB ATT
private val elecraftBase = RigCaps(
    modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.AM, RadioMode.FM,
        RadioMode.CW, RadioMode.CWR, RadioMode.RTTY, RadioMode.RTTYR,
        RadioMode.PKTLSB, RadioMode.PKTUSB),
    hasNoiseBlanker = true,  hasNoiseReduction = true, hasAutoNotch = false,
    hasIfShift = false,      hasRit = true, hasSpeechProc = true, hasSpeechProcLevel = false,
    hasVox = true,           hasTuner = false,
    preampLevels = listOf(PreampLevel.OFF, PreampLevel.P1),
    attLevels    = listOf(AttLevel.OFF, AttLevel.ATT10),
    antennaCount = 2, hasCw = true, hasMicGain = true, hasRfGain = true,
    hasSquelch = false, hasAgc = true, hasSplit = true, hasBandwidth = true,
)

object RadioDeviceList {

    private val yaesu = listOf(
        RadioDevice(122, "Yaesu", "FT-100",     CatProtocol.YAESU, 4800,  caps = yaesuBasic),
        RadioDevice(138, "Yaesu", "FT-450",     CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(147, "Yaesu", "FT-450D",    CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(148, "Yaesu", "FT-747GX",   CatProtocol.YAESU, 4800,  caps = yaesuBasic),
        RadioDevice(103, "Yaesu", "FT-757GX",   CatProtocol.YAESU, 4800,  caps = yaesuBasic),
        RadioDevice(116, "Yaesu", "FT-817",     CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(244, "Yaesu", "FT-817ND",   CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(395, "Yaesu", "FT-818",     CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(104, "Yaesu", "FT-847",     CatProtocol.YAESU, 4800,  caps = yaesuBasic),
        RadioDevice(132, "Yaesu", "FT-857",     CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(242, "Yaesu", "FT-857D",    CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(133, "Yaesu", "FT-897",     CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(243, "Yaesu", "FT-897D",    CatProtocol.YAESU, 9600,  caps = yaesuBasic),
        RadioDevice(131, "Yaesu", "FT-900",     CatProtocol.YAESU, 4800,  caps = yaesuBasic),
        RadioDevice(151, "Yaesu", "FT-950",     CatProtocol.YAESU, 9600,  caps = yaesuModern),
        RadioDevice(160, "Yaesu", "FT-991",     CatProtocol.YAESU, 9600,  caps = ft991aCaps),
        RadioDevice(434, "Yaesu", "FT-991A",    CatProtocol.YAESU, 9600,  caps = ft991aCaps),
        RadioDevice(150, "Yaesu", "FT-1000D",   CatProtocol.YAESU, 4800,  caps = yaesuBasic),
        RadioDevice(107, "Yaesu", "FT-1000MP",  CatProtocol.YAESU, 4800,  caps = yaesuBasic),
        RadioDevice(152, "Yaesu", "FT-2000",    CatProtocol.YAESU, 9600,  caps = yaesuModern),
        RadioDevice(156, "Yaesu", "FT-5000",    CatProtocol.YAESU, 9600,  caps = yaesuModern),
        RadioDevice(196, "Yaesu", "FT-DX1200",  CatProtocol.YAESU, 9600,  caps = yaesuModern),
        RadioDevice(212, "Yaesu", "FT-DX3000",  CatProtocol.YAESU, 9600,  caps = yaesuModern),
        RadioDevice(153, "Yaesu", "FT-DX5000",  CatProtocol.YAESU, 9600,  caps = yaesuModern),
        RadioDevice(199, "Yaesu", "FT-DX9000",  CatProtocol.YAESU, 9600,  caps = yaesuModern),
        RadioDevice(1036, "Yaesu", "FT-891",    CatProtocol.YAESU, 9600,  caps = ft891Caps),
        RadioDevice(474, "Yaesu", "FT-DX10",    CatProtocol.YAESU, 38400, caps = ftdx10Caps),
        RadioDevice(464, "Yaesu", "FT-DX101D",  CatProtocol.YAESU, 38400, caps = ftdx10Caps.copy(antennaCount = 3)),
        RadioDevice(465, "Yaesu", "FT-DX101MP", CatProtocol.YAESU, 38400, caps = ftdx10Caps.copy(antennaCount = 3)),
    )

    private val icom = listOf(
        RadioDevice(3001, "ICOM", "IC-703",       CatProtocol.ICOM_CIV, 19200,  0x68, caps = icomBase),
        RadioDevice(3070, "ICOM", "IC-705",       CatProtocol.ICOM_CIV, 19200,  0xA4, caps = ic705Caps),
        RadioDevice(3009, "ICOM", "IC-706",       CatProtocol.ICOM_CIV, 9600,   0x48, caps = icomBase),
        RadioDevice(3010, "ICOM", "IC-706MkII",   CatProtocol.ICOM_CIV, 9600,   0x4E, caps = icomBase),
        RadioDevice(3011, "ICOM", "IC-706MkIIg",  CatProtocol.ICOM_CIV, 9600,   0x58, caps = icomBase),
        RadioDevice(3016, "ICOM", "IC-718",       CatProtocol.ICOM_CIV, 9600,   0x5E, caps = icomBase),
        RadioDevice(3020, "ICOM", "IC-725",       CatProtocol.ICOM_CIV, 1200,   0x28, caps = icomBase.copy(hasNoiseReduction = false, hasAutoNotch = false)),
        RadioDevice(3021, "ICOM", "IC-726",       CatProtocol.ICOM_CIV, 1200,   0x30, caps = icomBase.copy(hasNoiseReduction = false, hasAutoNotch = false)),
        RadioDevice(3022, "ICOM", "IC-728",       CatProtocol.ICOM_CIV, 1200,   0x38, caps = icomBase.copy(hasNoiseReduction = false, hasAutoNotch = false)),
        RadioDevice(3023, "ICOM", "IC-729",       CatProtocol.ICOM_CIV, 1200,   0x3A, caps = icomBase.copy(hasNoiseReduction = false, hasAutoNotch = false)),
        RadioDevice(3024, "ICOM", "IC-735",       CatProtocol.ICOM_CIV, 1200,   0x04, caps = icomBase.copy(hasNoiseReduction = false, hasAutoNotch = false)),
        RadioDevice(3025, "ICOM", "IC-736",       CatProtocol.ICOM_CIV, 1200,   0x40, caps = icomBase.copy(hasNoiseReduction = false)),
        RadioDevice(3026, "ICOM", "IC-737",       CatProtocol.ICOM_CIV, 1200,   0x3C, caps = icomBase.copy(hasNoiseReduction = false)),
        RadioDevice(3027, "ICOM", "IC-738",       CatProtocol.ICOM_CIV, 1200,   0x44, caps = icomBase.copy(hasNoiseReduction = false)),
        RadioDevice(3028, "ICOM", "IC-745",       CatProtocol.ICOM_CIV, 1200,   0x12, caps = icomBase.copy(hasNoiseReduction = false, hasAutoNotch = false)),
        RadioDevice(3029, "ICOM", "IC-746",       CatProtocol.ICOM_CIV, 9600,   0x56, caps = icomBase),
        RadioDevice(3030, "ICOM", "IC-756",       CatProtocol.ICOM_CIV, 9600,   0x50, caps = icomBase),
        RadioDevice(3031, "ICOM", "IC-756Pro",    CatProtocol.ICOM_CIV, 9600,   0x5C, caps = icomBase),
        RadioDevice(3032, "ICOM", "IC-756ProII",  CatProtocol.ICOM_CIV, 9600,   0x64, caps = icomBase),
        RadioDevice(3033, "ICOM", "IC-756ProIII", CatProtocol.ICOM_CIV, 9600,   0x6E, caps = icomBase),
        RadioDevice(3034, "ICOM", "IC-761",       CatProtocol.ICOM_CIV, 1200,   0x1E, caps = icomBase.copy(hasNoiseReduction = false)),
        RadioDevice(3035, "ICOM", "IC-765",       CatProtocol.ICOM_CIV, 1200,   0x2C, caps = icomBase.copy(hasNoiseReduction = false)),
        RadioDevice(3036, "ICOM", "IC-775",       CatProtocol.ICOM_CIV, 9600,   0x46, caps = icomBase),
        RadioDevice(3037, "ICOM", "IC-781",       CatProtocol.ICOM_CIV, 1200,   0x26, caps = icomBase.copy(hasNoiseReduction = false)),
        RadioDevice(3052, "ICOM", "IC-7000",      CatProtocol.ICOM_CIV, 19200,  0x70, caps = icomBase.copy(hasVox = true)),
        RadioDevice(3060, "ICOM", "IC-7100",      CatProtocol.ICOM_CIV, 19200,  0x88, caps = icomBase.copy(hasVox = true)),
        RadioDevice(3061, "ICOM", "IC-7200",      CatProtocol.ICOM_CIV, 19200,  0x76, caps = icomBase),
        RadioDevice(3073, "ICOM", "IC-7300",      CatProtocol.ICOM_CIV, 115200, 0x94, caps = ic7300Caps),
        RadioDevice(3063, "ICOM", "IC-7410",      CatProtocol.ICOM_CIV, 19200,  0x80, caps = icomBase.copy(hasVox = true, hasTuner = true)),
        RadioDevice(3064, "ICOM", "IC-7600",      CatProtocol.ICOM_CIV, 19200,  0x7A, caps = ic7300Caps),
        RadioDevice(3071, "ICOM", "IC-7610",      CatProtocol.ICOM_CIV, 115200, 0x98, caps = ic7610Caps),
        RadioDevice(3065, "ICOM", "IC-7700",      CatProtocol.ICOM_CIV, 19200,  0x74, caps = ic7300Caps),
        RadioDevice(3066, "ICOM", "IC-7800",      CatProtocol.ICOM_CIV, 19200,  0x6A, caps = ic7300Caps),
        RadioDevice(3072, "ICOM", "IC-7850",      CatProtocol.ICOM_CIV, 19200,  0x8E, caps = ic7300Caps),
        RadioDevice(3074, "ICOM", "IC-7851",      CatProtocol.ICOM_CIV, 19200,  0x8E, caps = ic7300Caps),
        RadioDevice(3067, "ICOM", "IC-9100",      CatProtocol.ICOM_CIV, 19200,  0x7C, caps = icomBase.copy(hasVox = true, hasTuner = true)),
        RadioDevice(3076, "ICOM", "IC-9700",      CatProtocol.ICOM_CIV, 115200, 0xA2, caps = ic7300Caps),
    )

    private val kenwood = listOf(
        RadioDevice(2001, "Kenwood", "TS-140S",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase.copy(hasNoiseReduction = false, hasAutoNotch = false, hasIfShift = false, hasTuner = false)),
        RadioDevice(2002, "Kenwood", "TS-440S",   CatProtocol.KENWOOD, 4800,   caps = kenwoodBase.copy(hasNoiseReduction = false, hasAutoNotch = false, hasIfShift = false, hasTuner = false)),
        RadioDevice(2003, "Kenwood", "TS-450S",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase.copy(hasNoiseReduction = false, hasTuner = false)),
        RadioDevice(2004, "Kenwood", "TS-50S",    CatProtocol.KENWOOD, 9600,   caps = kenwoodBase.copy(hasNoiseReduction = false, hasTuner = false)),
        RadioDevice(2005, "Kenwood", "TS-480HX",  CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2006, "Kenwood", "TS-480SAT", CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2007, "Kenwood", "TS-570D",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2008, "Kenwood", "TS-570S",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2009, "Kenwood", "TS-590S",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2010, "Kenwood", "TS-590SG",  CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2011, "Kenwood", "TS-680S",   CatProtocol.KENWOOD, 4800,   caps = kenwoodBase.copy(hasNoiseReduction = false, hasTuner = false)),
        RadioDevice(2012, "Kenwood", "TS-690S",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase.copy(hasTuner = false)),
        RadioDevice(2013, "Kenwood", "TS-711",    CatProtocol.KENWOOD, 4800,   caps = kenwoodBase.copy(hasNoiseReduction = false, hasAutoNotch = false)),
        RadioDevice(2014, "Kenwood", "TS-790",    CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2015, "Kenwood", "TS-811",    CatProtocol.KENWOOD, 4800,   caps = kenwoodBase.copy(hasNoiseReduction = false, hasAutoNotch = false)),
        RadioDevice(2016, "Kenwood", "TS-850",    CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2017, "Kenwood", "TS-870S",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2018, "Kenwood", "TS-890S",   CatProtocol.KENWOOD, 115200, caps = ts890sCaps),
        RadioDevice(2019, "Kenwood", "TS-940S",   CatProtocol.KENWOOD, 4800,   caps = kenwoodBase.copy(hasNoiseReduction = false)),
        RadioDevice(2020, "Kenwood", "TS-950S",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2021, "Kenwood", "TS-950SDX", CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2022, "Kenwood", "TS-990S",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
        RadioDevice(2023, "Kenwood", "TS-2000",   CatProtocol.KENWOOD, 9600,   caps = kenwoodBase),
    )

    private val elecraft = listOf(
        RadioDevice(2101, "Elecraft", "K2",  CatProtocol.ELECRAFT, 9600,  caps = elecraftBase.copy(hasVox = false, antennaCount = 1)),
        RadioDevice(2102, "Elecraft", "K3",  CatProtocol.ELECRAFT, 9600,  caps = elecraftBase),
        RadioDevice(2103, "Elecraft", "K3S", CatProtocol.ELECRAFT, 9600,  caps = elecraftBase),
        RadioDevice(2104, "Elecraft", "K4",  CatProtocol.ELECRAFT, 38400, caps = elecraftBase),
        RadioDevice(2105, "Elecraft", "KX2", CatProtocol.ELECRAFT, 9600,  caps = elecraftBase.copy(antennaCount = 1)),
        RadioDevice(2106, "Elecraft", "KX3", CatProtocol.ELECRAFT, 9600,  caps = elecraftBase),
    )

    // QDX — sourced from flrig QDX.cxx (vQDXmodes_: "LSB","USB"; fixed 3200 Hz filter,
    // no CAT-adjustable power — QRP Labs spec is a fixed ~5W nominal output).
    private val qdxBw = listOf(3200)
    private val qdxCaps = kenwoodBase.copy(
        modes = listOf(RadioMode.LSB, RadioMode.USB),
        bandwidthsByMode = mapOf(RadioMode.LSB to qdxBw, RadioMode.USB to qdxBw),
        hasNoiseBlanker = false, hasNoiseReduction = false, hasAutoNotch = false,
        hasIfShift = false, hasSpeechProc = false, hasVox = false, hasTuner = false,
        preampLevels = listOf(PreampLevel.OFF), attLevels = listOf(AttLevel.OFF),
        antennaCount = 1, hasMicGain = false, hasRfGain = false, hasSquelch = false,
        hasAgc = false, hasCw = false,
        minPowerWatts = 1, maxPowerWatts = 5,
    )

    // QMX — sourced from flrig QMX.cxx (QMXmodes_: "LSB","USB","CW-U","CW-L","DIGIU","DIGIL").
    // QRP Labs spec is a fixed ~10W nominal output, not CAT-adjustable.
    private val qmxCaps = kenwoodBase.copy(
        modes = listOf(RadioMode.LSB, RadioMode.USB, RadioMode.CW, RadioMode.CWR,
            RadioMode.PKTUSB, RadioMode.PKTLSB),
        hasNoiseBlanker = false, hasNoiseReduction = false, hasAutoNotch = false,
        hasIfShift = false, hasSpeechProc = false, hasVox = false, hasTuner = false,
        preampLevels = listOf(PreampLevel.OFF), attLevels = listOf(AttLevel.OFF),
        antennaCount = 1, hasMicGain = false, hasRfGain = false, hasSquelch = false,
        hasAgc = false, hasCw = true,
        minPowerWatts = 1, maxPowerWatts = 10,
    )

    private val qrpLabs = listOf(
        RadioDevice(2200, "QRP Labs", "QDX",      CatProtocol.KENWOOD, 115200, caps = qdxCaps),
        RadioDevice(2201, "QRP Labs", "QCX-mini", CatProtocol.KENWOOD, 9600, caps = kenwoodBase.copy(
            modes = listOf(RadioMode.CW),
            hasNoiseBlanker = false, hasNoiseReduction = false, hasAutoNotch = false,
            hasIfShift = false, hasSpeechProc = false, hasVox = false, hasTuner = false,
            preampLevels = listOf(PreampLevel.OFF), attLevels = listOf(AttLevel.OFF),
            antennaCount = 1, hasMicGain = false, hasRfGain = false, hasSquelch = false,
            hasAgc = false,
            minPowerWatts = 1, maxPowerWatts = 3,
        )),
        RadioDevice(2202, "QRP Labs", "QMX",      CatProtocol.KENWOOD, 115200, caps = qmxCaps),
    )

    private val tentec = listOf(
        RadioDevice(2300, "Ten-Tec", "Orion II",    CatProtocol.TENTEC, 57600),
        RadioDevice(2301, "Ten-Tec", "Jupiter",     CatProtocol.TENTEC, 57600),
        RadioDevice(2302, "Ten-Tec", "Eagle",       CatProtocol.TENTEC, 57600),
        RadioDevice(2303, "Ten-Tec", "Rebel",       CatProtocol.TENTEC, 9600),
        RadioDevice(2304, "Ten-Tec", "Argonaut VI", CatProtocol.TENTEC, 9600),
    )

    private val flexradio = listOf(
        RadioDevice(2400, "FlexRadio", "Flex-6300", CatProtocol.FLEXRADIO, 0),
        RadioDevice(2401, "FlexRadio", "Flex-6400", CatProtocol.FLEXRADIO, 0),
        RadioDevice(2402, "FlexRadio", "Flex-6500", CatProtocol.FLEXRADIO, 0),
        RadioDevice(2403, "FlexRadio", "Flex-6600", CatProtocol.FLEXRADIO, 0),
        RadioDevice(2404, "FlexRadio", "Flex-6700", CatProtocol.FLEXRADIO, 0),
    )

    private val alinco = listOf(
        RadioDevice(2500, "Alinco", "DX-70",  CatProtocol.ALINCO, 9600),
        RadioDevice(2501, "Alinco", "DX-77",  CatProtocol.ALINCO, 9600),
        RadioDevice(2502, "Alinco", "DX-SR8", CatProtocol.ALINCO, 9600),
    )

    private val generic = listOf(
        RadioDevice(1, "Generic", "rigctld / Network", CatProtocol.RIGCTLD, 0),
    )

    val all: List<RadioDevice> = buildList {
        addAll(yaesu); addAll(icom); addAll(kenwood); addAll(elecraft)
        addAll(qrpLabs); addAll(tentec); addAll(flexradio); addAll(alinco); addAll(generic)
    }

    fun byId(id: Int) = all.firstOrNull { it.modelId == id }
    fun byManufacturer(mfr: String) = all.filter { it.manufacturer == mfr }
    val manufacturers get() = all.map { it.manufacturer }.distinct().sorted()
}

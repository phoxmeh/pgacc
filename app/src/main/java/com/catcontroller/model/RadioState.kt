package com.catcontroller.model

data class RadioState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val pttConnected: Boolean = false,
    val pttConnecting: Boolean = false,
    val error: String? = null,

    val freqA: Long = 14225000L,
    val freqB: Long = 14225000L,
    val activeVfo: Vfo = Vfo.A,

    val mode: RadioMode = RadioMode.USB,
    val bandwidth: Int = 2400,

    val ptt: Boolean = false,
    val split: Boolean = false,

    val rfPower: Int = 100,
    val squelch: Int = 0,
    val afGain: Int = 100,
    val rfGain: Int = 100,
    val micGain: Int = 50,

    val rit: Int = 0,
    val ritEnabled: Boolean = false,
    val xit: Int = 0,
    val xitEnabled: Boolean = false,
    val ifShift: Int = 0,
    val ifShiftEnabled: Boolean = false,

    val preamp: PreampLevel = PreampLevel.OFF,
    val attenuator: AttLevel = AttLevel.OFF,
    val agc: AgcMode = AgcMode.MID,

    val noiseBlanker: Boolean = false,
    val noiseReduction: Boolean = false,
    val autoNotch: Boolean = false,
    val speechProc: Boolean = false,
    val speechProcLevel: Int = 0,
    val vox: Boolean = false,
    val tuner: Boolean = false,

    val sMeter: Int = 0,
    val rfMeter: Int = 0,
    val swrMeter: Int = 0,
    val alcMeter: Int = 0,

    val memoryChannel: Int = 0,
    val antennaPort: Int = 1,
    val cwSpeed: Int = 20,
    val cwPitch: Int = 600,
)

enum class Vfo { A, B, MAIN, SUB, MEM }

enum class PreampLevel(val label: String) {
    OFF("OFF"), P1("P1"), P2("P2"), IPO("IPO")
}

enum class AttLevel(val label: String, val db: Int) {
    OFF("OFF", 0),
    ATT6("-6dB", 6),
    ATT10("-10dB", 10),
    ATT12("-12dB", 12),
    ATT18("-18dB", 18),
    ATT20("-20dB", 20),
    ATT24("-24dB", 24),
}

enum class AgcMode(val label: String) {
    OFF("OFF"), FAST("FAST"), MID("MID"), SLOW("SLO"), AUTO("AUTO")
}

package com.catcontroller.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catcontroller.model.*
import com.catcontroller.radio.RigCaps
import com.catcontroller.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    vm: MainViewModel,
    onNavigateSettings: () -> Unit,
    onNavigateProfiles: () -> Unit,
) {
    val state      by vm.radioState.collectAsStateWithLifecycle()
    val profile    by vm.activeProfile.collectAsStateWithLifecycle()
    val hasPttPort by vm.hasPttPort.collectAsStateWithLifecycle()
    val caps       by vm.caps.collectAsStateWithLifecycle()
    val isTuning   by vm.isTuning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, actionLabel = "OK")
        vm.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ── Fixed section (no scroll — FrequencyDisplay needs clean vertical drag) ──
            TopBar(
                profileName   = profile?.name ?: "No profile",
                connected     = state.connected,
                connecting    = state.connecting,
                hasPttPort    = hasPttPort,
                pttConnected  = state.pttConnected,
                pttConnecting = state.pttConnecting,
                isTuning      = isTuning,
                onConnect     = { if (state.connected) vm.disconnect() else vm.connect() },
                onPtt         = { if (state.pttConnected) vm.disconnectPtt() else vm.connectPtt() },
                onStartTune   = vm::startTuning,
                onStopTune    = vm::stopTuning,
                onProfiles    = onNavigateProfiles,
                onSettings    = onNavigateSettings,
            )

            FrequencyDisplay(
                freqA     = state.freqA,
                freqB     = state.freqB,
                activeVfo = state.activeVfo,
                onAdjust  = vm::adjustFrequency,
            )

            MetersRow(
                sMeter  = state.sMeter,
                rfMeter = state.rfMeter,
                swr     = state.swrMeter,
                alc     = state.alcMeter,
                tx      = state.ptt,
            )

            VfoRow(
                activeVfo = state.activeVfo,
                split     = state.split,
                hasSplit  = caps.hasSplit,
                onVfo     = vm::setVfo,
                onAtoB    = vm::vfoAtoB,
                onBtoA    = vm::vfoBtoA,
                onSwap    = vm::swapVfo,
                onSplit   = { vm.setSplit(!state.split) },
            )

            // ── Scrollable section ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                ModeRow(
                    modes             = caps.modes,
                    modeDisplayLabels = caps.modeDisplayLabels,
                    hasBandwidth      = caps.hasBandwidth,
                    mode              = state.mode,
                    bandwidth         = state.bandwidth,
                    bandwidths        = caps.bandwidthsByMode[state.mode] ?: emptyList(),
                    onMode            = vm::setMode,
                    onBw              = vm::setBandwidth,
                )

                val isFmMode = state.mode in setOf(RadioMode.FM, RadioMode.FMN, RadioMode.WFM, RadioMode.C4FM)
                CardSection {
                    LabeledSlider("PWR", state.rfPower, caps.minPowerWatts, caps.maxPowerWatts, "%3dW") { vm.setRfPower(it) }
                    LabeledSlider("AF",  state.afGain,  0, 100, "%3d")  { vm.setAfGain(it) }
                    if (isFmMode) {
                        if (caps.hasSquelch) LabeledSlider("SQL", state.squelch, 0, 100, "%3d") { vm.setSquelch(it) }
                    } else {
                        if (caps.hasRfGain) LabeledSlider("RF",  state.rfGain,  0, 100, "%3d") { vm.setRfGain(it) }
                    }
                }

                PreampAttRow(
                    preamp       = state.preamp,
                    att          = state.attenuator,
                    preampLevels = caps.preampLevels,
                    attLevels    = caps.attLevels,
                    onPreamp     = vm::setPreamp,
                    onAtt        = vm::setAtt,
                )

                if (caps.hasAgc) {
                    AgcRow(agc = state.agc, onAgc = vm::setAgc)
                }

                if (caps.hasIfShift) {
                    CardSection {
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ToggleChip(
                                "IF SHIFT",
                                state.ifShiftEnabled,
                            ) { vm.setIfShiftEnabled(!state.ifShiftEnabled) }
                        }
                        if (state.ifShiftEnabled) {
                            // steps = 239: range -1200..+1200 = 2400Hz / 10Hz per step = 240 positions
                            LabeledSlider(
                                "SHIFT", state.ifShift, -1200, 1200, "%+dHz", steps = 239,
                            ) { vm.setIfShift(it) }
                        }
                    }
                }

                DspRow(
                    nb       = state.noiseBlanker,
                    nr       = state.noiseReduction,
                    anf      = state.autoNotch,
                    comp     = state.speechProc,
                    vox      = state.vox,
                    tuner    = state.tuner,
                    hasNb    = caps.hasNoiseBlanker,
                    hasNr    = caps.hasNoiseReduction,
                    hasAnf   = caps.hasAutoNotch,
                    hasComp  = caps.hasSpeechProc,
                    hasVox   = caps.hasVox,
                    hasTuner = caps.hasTuner,
                    onNb     = vm::setNb,
                    onNr     = vm::setNr,
                    onAnf    = vm::setAnf,
                    onComp   = vm::setComp,
                    onVox    = vm::setVox,
                    onTuner  = vm::setTuner,
                    onTune   = vm::startTune,
                )

                if (caps.antennaCount > 1) {
                    AntennaRow(
                        current = state.antennaPort,
                        count   = caps.antennaCount,
                        onSelect= vm::setAntenna,
                    )
                }

                if (caps.hasCw && (state.mode == RadioMode.CW || state.mode == RadioMode.CWR)) {
                    CardSection {
                        LabeledSlider("CW WPM",   state.cwSpeed, 4,   60,   "%3d")    { vm.setCwSpeed(it) }
                        LabeledSlider("CW PITCH", state.cwPitch, 300, 1050, "%4dHz")  { vm.setCwPitch(it) }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // PTT pinned at bottom, only shown when PTT is armed (top-bar PTT toggle is ON)
            if (state.pttConnected) {
                PttButton(
                    active    = state.ptt,
                    enabled   = true,
                    onPress   = { vm.setPtt(true) },
                    onRelease = { vm.setPtt(false) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    profileName: String,
    connected: Boolean,
    connecting: Boolean,
    hasPttPort: Boolean,
    pttConnected: Boolean,
    pttConnecting: Boolean,
    isTuning: Boolean,
    onConnect: () -> Unit,
    onPtt: () -> Unit,
    onStartTune: () -> Unit,
    onStopTune: () -> Unit,
    onProfiles: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onProfiles, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Default.Radio, null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(profileName, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1)
        }
        Spacer(Modifier.weight(1f))

        if (pttConnected) {
            TuneButton(tuning = isTuning, onPress = onStartTune, onRelease = onStopTune)
            Spacer(Modifier.width(4.dp))
        }

        TopBarButton(
            label   = "CAT",
            active  = connected,
            working = connecting,
            onClick = onConnect,
        )

        if (hasPttPort) {
            Spacer(Modifier.width(4.dp))
            TopBarButton(
                label   = "PTT",
                active  = pttConnected,
                working = pttConnecting,
                onClick = onPtt,
            )
        }

        IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Settings, "Settings", tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TuneButton(
    tuning: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val bgColor   by animateColorAsState(if (tuning) BtnPttActive else Color(0xFF2A2A2A), label = "tune_bg")
    val textColor by animateColorAsState(if (tuning) Color.White  else Muted,             label = "tune_txt")
    Surface(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when {
                            event.changes.any { it.pressed && !it.previousPressed }  -> onPress()
                            event.changes.any { !it.pressed && it.previousPressed }  -> onRelease()
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        color = bgColor,
        shape = RoundedCornerShape(5.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(
                if (tuning) "● TX" else "TUNE",
                color      = textColor,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TopBarButton(
    label: String,
    active: Boolean,
    working: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        when { active -> BtnOn; working -> Color(0xFF1A3A1A); else -> Color(0xFF2A2A2A) },
        label = "tbBtn_$label",
    )
    val textColor by animateColorAsState(
        when { active -> Color.Black; working -> Accent; else -> Muted },
        label = "tbTxt_$label",
    )
    Surface(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(enabled = !working, onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(5.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (working) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(10.dp),
                    color       = Accent,
                    strokeWidth = 1.5.dp,
                )
            }
            Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                if (active) "ON" else "OFF",
                color    = textColor.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Frequency display — each digit is a drag target
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FrequencyDisplay(
    freqA: Long,
    freqB: Long,
    activeVfo: Vfo,
    onAdjust: (digitPosition: Int, delta: Int) -> Unit,
) {
    val freq = if (activeVfo == Vfo.B || activeVfo == Vfo.SUB) freqB else freqA
    // 9 digits: XXX.XXX.XXX = 100MHz down to 1Hz; GHz digits dropped (not needed for ham radio)
    val clamped = freq.coerceIn(0L, 999_999_999L)
    val digits = "%09d".format(clamped)
    val groups = listOf(
        digits.substring(0, 3),   // 100MHz / 10MHz / 1MHz
        digits.substring(3, 6),   // 100kHz / 10kHz / 1kHz
        digits.substring(6, 9),   // 100Hz  / 10Hz  / 1Hz
    )
    val separators = listOf(".", ".", "")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "VFO ${activeVfo.name}",
                    color = Accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    if (activeVfo == Vfo.B || activeVfo == Vfo.SUB)
                        "A: " + formatFreq(freqA)
                    else
                        "B: " + formatFreq(freqB),
                    color = Muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var digitIndex = 0
                groups.forEachIndexed { gi, group ->
                    group.forEachIndexed { _, ch ->
                        val pos = digitIndex
                        FreqDigit(
                            digit  = ch,
                            dimmed = pos == 0 && clamped < 100_000_000L,
                            onUp   = { onAdjust(pos, 1) },
                            onDown = { onAdjust(pos, -1) },
                        )
                        digitIndex++
                    }
                    if (separators[gi].isNotEmpty()) {
                        Text(
                            separators[gi],
                            color      = DisplayAmber.copy(alpha = 0.6f),
                            fontSize   = 28.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Hz", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun FreqDigit(digit: Char, dimmed: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    val color = if (dimmed) DisplayAmber.copy(alpha = 0.2f) else DisplayAmber
    Column(
        modifier = Modifier.width(IntrinsicSize.Min),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clickable(onClick = onUp),
            contentAlignment = Alignment.Center,
        ) {
            Text("▲", color = color.copy(alpha = 0.45f), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
        Text(
            text       = digit.toString(),
            color      = color,
            fontSize   = 38.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 2.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clickable(onClick = onDown),
            contentAlignment = Alignment.Center,
        ) {
            Text("▼", color = color.copy(alpha = 0.45f), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}

private fun formatFreq(freq: Long): String {
    val d = "%09d".format(freq.coerceIn(0L, 999_999_999L))
    return "${d.substring(0,3)}.${d.substring(3,6)}.${d.substring(6,9)}"
}

// ─────────────────────────────────────────────────────────────────────────────
// VFO row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VfoRow(
    activeVfo: Vfo,
    split: Boolean,
    hasSplit: Boolean,
    onVfo: (Vfo) -> Unit,
    onAtoB: () -> Unit,
    onBtoA: () -> Unit,
    onSwap: () -> Unit,
    onSplit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ToggleChip("A", activeVfo == Vfo.A || activeVfo == Vfo.MAIN) { onVfo(Vfo.A) }
        ToggleChip("B", activeVfo == Vfo.B || activeVfo == Vfo.SUB)  { onVfo(Vfo.B) }
        SmallBtn("A→B", onClick = onAtoB)
        SmallBtn("B→A", onClick = onBtoA)
        SmallBtn("SWAP", onClick = onSwap)
        Spacer(Modifier.weight(1f))
        if (hasSplit) ToggleChip("SPLIT", split, onClick = onSplit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode row
// ─────────────────────────────────────────────────────────────────────────────

private fun bwLabel(bw: Int) = if (bw >= 1000) "${bw / 1000}.${(bw % 1000) / 100}kHz" else "${bw}Hz"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeRow(
    modes: List<RadioMode>,
    modeDisplayLabels: Map<RadioMode, String>,
    hasBandwidth: Boolean,
    mode: RadioMode,
    bandwidth: Int,
    bandwidths: List<Int>,
    onMode: (RadioMode) -> Unit,
    onBw: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(
            modifier              = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            modes.forEach { m ->
                ToggleChip(modeDisplayLabels[m] ?: m.displayLabel, mode == m) { onMode(m) }
            }
        }
        if (hasBandwidth && bandwidths.size > 1) {
            Spacer(Modifier.height(4.dp))
            var bwExpanded by remember { mutableStateOf(false) }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("BW:", color = Muted, fontSize = 12.sp)
                ExposedDropdownMenuBox(
                    expanded         = bwExpanded,
                    onExpandedChange = { bwExpanded = it },
                    modifier         = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value         = bwLabel(bandwidth),
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(bwExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        textStyle     = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Accent,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedTextColor     = OnSurface,
                            unfocusedTextColor   = OnSurface,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded         = bwExpanded,
                        onDismissRequest = { bwExpanded = false },
                        modifier         = Modifier.background(SurfaceCard),
                    ) {
                        bandwidths.forEach { bw ->
                            DropdownMenuItem(
                                text    = { Text(bwLabel(bw), color = OnSurface, fontSize = 13.sp) },
                                onClick = { onBw(bw); bwExpanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Labeled sliders
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LabeledSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    fmt: String,
    steps: Int = 0,
    onSet: (Int) -> Unit,
) {
    var local by remember { mutableIntStateOf(value) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    val step = if (steps > 0) ((max - min) / (steps + 1)).coerceAtLeast(1) else 1

    // Sync external state into local, but suppress for 800ms after a step-button tap so
    // poll cycles can't snap the display back before the radio has applied the command.
    LaunchedEffect(value) {
        if (System.currentTimeMillis() - lastTapMs > 800L) {
            local = value
        }
    }

    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Muted, fontSize = 12.sp,
            modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
        Spacer(Modifier.width(6.dp))
        SliderStepBtn("−") {
            lastTapMs = System.currentTimeMillis()
            local = (local - step).coerceIn(min, max)
            onSet(local)
        }
        Slider(
            value                = local.toFloat(),
            onValueChange        = { local = it.roundToInt() },
            onValueChangeFinished= { onSet(local) },
            valueRange           = min.toFloat()..max.toFloat(),
            steps                = steps,
            modifier             = Modifier.weight(1f),
            colors               = sliderColors(),
        )
        SliderStepBtn("+") {
            lastTapMs = System.currentTimeMillis()
            local = (local + step).coerceIn(min, max)
            onSet(local)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            fmt.format(local),
            color      = OnSurface,
            fontSize   = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(60.dp),
        )
    }
}

@Composable
private fun SliderStepBtn(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick),
    ) {
        Text(label, color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preamp / Attenuator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreampAttRow(
    preamp: PreampLevel,
    att: AttLevel,
    preampLevels: List<PreampLevel>,
    attLevels: List<AttLevel>,
    onPreamp: (PreampLevel) -> Unit,
    onAtt: (AttLevel) -> Unit,
) {
    CardSection {
        Row(
            modifier              = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("AMP:", color = Muted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically))
            preampLevels.forEach { lvl ->
                ToggleChip(lvl.label, preamp == lvl) { onPreamp(lvl) }
            }
            Spacer(Modifier.width(12.dp))
            Text("ATT:", color = Muted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically))
            attLevels.forEach { lvl ->
                ToggleChip(lvl.label, att == lvl) { onAtt(lvl) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AGC
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgcRow(agc: AgcMode, onAgc: (AgcMode) -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text("AGC:", color = Muted, fontSize = 12.sp)
        AgcMode.entries.forEach { m ->
            ToggleChip(m.label, agc == m) { onAgc(m) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DSP toggles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DspRow(
    nb: Boolean, nr: Boolean, anf: Boolean, comp: Boolean, vox: Boolean, tuner: Boolean,
    hasNb: Boolean, hasNr: Boolean, hasAnf: Boolean, hasComp: Boolean,
    hasVox: Boolean, hasTuner: Boolean,
    onNb: (Boolean) -> Unit, onNr: (Boolean) -> Unit, onAnf: (Boolean) -> Unit,
    onComp: (Boolean) -> Unit, onVox: (Boolean) -> Unit, onTuner: (Boolean) -> Unit,
    onTune: () -> Unit,
) {
    if (!hasNb && !hasNr && !hasAnf && !hasComp && !hasVox && !hasTuner) return
    CardSection {
        Row(
            modifier              = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (hasNb)    ToggleChip("NB",    nb)    { onNb(!nb) }
            if (hasNr)    ToggleChip("NR",    nr)    { onNr(!nr) }
            if (hasAnf)   ToggleChip("ANF",   anf)   { onAnf(!anf) }
            if (hasComp)  ToggleChip("COMP",  comp)  { onComp(!comp) }
            if (hasVox)   ToggleChip("VOX",   vox)   { onVox(!vox) }
            if (hasTuner) {
                ToggleChip("TUNER", tuner) { onTuner(!tuner) }
                SmallBtn("TUNE") { onTune() }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Antenna selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AntennaRow(current: Int, count: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text("ANT:", color = Muted, fontSize = 12.sp)
        (1..count).forEach { port ->
            ToggleChip("ANT$port", current == port) { onSelect(port) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Meters
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetersRow(sMeter: Int, rfMeter: Int, swr: Int, alc: Int, tx: Boolean) {
    CardSection {
        MeterBar(label = if (tx) "PWR" else " S ", value = sMeter, max = 30, tx = tx)
        if (tx) {
            MeterBar("SWR", swr, 100, tx = true)
            MeterBar("ALC", alc, 100, tx = true)
        }
    }
}

@Composable
private fun MeterBar(label: String, value: Int, max: Int, tx: Boolean) {
    val frac = (value.toFloat() / max).coerceIn(0f, 1f)
    val color = when {
        tx && frac > 0.9f -> MeterRed
        tx && frac > 0.7f -> MeterOrange
        frac > 0.85f      -> MeterOrange
        else              -> MeterGreen
    }
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Muted, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(36.dp))
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .background(color)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "%3d".format(value),
            color      = color,
            fontSize   = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(28.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PTT button — hold to transmit
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PttButton(
    active: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val bgColor by animateColorAsState(
        if (active) BtnPttActive else Color(0xFF3D0000),
        label = "ptt_bg"
    )
    val textColor = if (active) Color.White else BtnPtt

    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick  = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when {
                                event.changes.any { it.pressed && !it.previousPressed } ->
                                    onPress()
                                event.changes.any { !it.pressed && it.previousPressed } ->
                                    onRelease()
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
            enabled  = enabled,
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = bgColor),
        ) {
            Text(
                if (active) "● TX" else "PTT",
                fontSize   = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color      = textColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable primitives
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color    = SurfaceCard,
        shape    = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (active) BtnOn else BtnOff, label = "chip_$label")
    val fg = if (active) Color.Black else OnSurface
    Surface(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        color    = bg,
        shape    = RoundedCornerShape(5.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick        = onClick,
        modifier       = Modifier.height(30.dp),
        shape          = RoundedCornerShape(5.dp),
        border         = BorderStroke(1.dp, Color(0xFF444444)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Text(label, color = OnSurface, fontSize = 12.sp)
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor         = Accent,
    activeTrackColor   = Accent,
    inactiveTrackColor = Color(0xFF3A3A3A),
)

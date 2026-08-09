package com.catcontroller.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catcontroller.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title              = { Text("Settings", color = OnSurface) },
                navigationIcon     = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader("Display")

            SettingSwitch(
                label    = "Keep screen on while connected",
                checked  = settings.keepScreenOn,
                onToggle = vm::setKeepScreenOn,
            )
            SettingSwitch(
                label    = "Show S-meter / meter bar",
                checked  = settings.showSmeterBar,
                onToggle = vm::setShowSmeter,
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader("Connection")

            SettingSwitch(
                label    = "Auto-connect to default profile on start",
                checked  = settings.autoConnect,
                onToggle = vm::setAutoConnect,
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader("Polling")

            var pollSlider by remember(settings.pollIntervalMs) {
                mutableIntStateOf(settings.pollIntervalMs)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Poll interval", color = OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${pollSlider}ms", color = Accent, fontSize = 14.sp)
            }
            Slider(
                value          = pollSlider.toFloat(),
                onValueChange  = { pollSlider = it.roundToInt() },
                onValueChangeFinished = { vm.setPollInterval(pollSlider) },
                valueRange     = 200f..2000f,
                steps          = 17,
                colors         = SliderDefaults.colors(
                    thumbColor         = Accent,
                    activeTrackColor   = Accent,
                    inactiveTrackColor = Color(0xFF3A3A3A),
                ),
            )
            Text(
                "How often to poll the radio for S-meter and status updates.",
                color    = Muted,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(24.dp))
            SectionHeader("About")
            Text("CAT Controller", color = OnSurface, fontSize = 14.sp)
            Text("Supports Yaesu, ICOM, Kenwood, Elecraft, QRP Labs, and any hamlib radio via rigctld.",
                color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color      = Accent,
        fontSize   = 12.sp,
        modifier   = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
    HorizontalDivider(color = Color(0xFF333333))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor      = Color.Black,
                checkedTrackColor      = Accent,
                uncheckedThumbColor    = Muted,
                uncheckedTrackColor    = Color(0xFF3A3A3A),
            ),
        )
    }
}

package com.catcontroller.ui.profiles

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catcontroller.model.*
import com.catcontroller.radio.RadioDeviceList
import com.catcontroller.serial.UsbSerialDevice
import com.catcontroller.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(vm: ProfilesViewModel, onBack: () -> Unit, onSelect: (RadioProfile) -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val editing  by vm.editing.collectAsStateWithLifecycle()

    if (editing != null) {
        val usbDevices by vm.usbDevices.collectAsStateWithLifecycle()
        ProfileEditSheet(
            profile    = editing!!,
            usbDevices = usbDevices,
            onChange   = vm::updateEditing,
            onRefresh  = vm::refreshUsbDevices,
            onSave     = { vm.saveEditing() },
            onCancel   = vm::cancelEdit,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Profiles", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Accent)
                    }
                },
                actions = {
                    IconButton(onClick = vm::startNew) {
                        Icon(Icons.Default.Add, "Add", tint = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No profiles yet", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = vm::startNew,
                        colors  = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) { Text("Add profile", color = Color.Black) }
                }
            }
        } else {
            LazyColumn(
                modifier              = Modifier.fillMaxSize().padding(padding),
                contentPadding        = PaddingValues(12.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile   = profile,
                        onEdit    = { vm.startEdit(profile) },
                        onDelete  = { vm.delete(profile) },
                        onDefault = { vm.setDefault(profile) },
                        onSelect  = { onSelect(profile) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(
    profile: RadioProfile,
    onEdit: () -> Unit, onDelete: () -> Unit,
    onDefault: () -> Unit, onSelect: () -> Unit,
) {
    val device = RadioDeviceList.byId(profile.deviceModelId)
    Surface(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        color     = SurfaceCard,
        shape     = RoundedCornerShape(10.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (profile.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = Accent, shape = RoundedCornerShape(4.dp)) {
                            Text("DEFAULT", color = Color.Black, fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(device?.displayName ?: "Unknown device", color = Muted, fontSize = 12.sp)
                Text(
                    when (profile.connectionType) {
                        ConnectionType.USB ->
                            "USB  ${profile.baudRate} baud  PTT:${profile.pttMethod.label}" +
                            if (profile.catVid != 0) "  VID:${profile.catVid.toString(16).uppercase()}" else ""
                        ConnectionType.NETWORK_RIGCTLD ->
                            "rigctld  ${profile.networkHost}:${profile.networkPort}"
                    },
                    color = Muted, fontSize = 11.sp,
                )
            }
            Row {
                if (!profile.isDefault) {
                    IconButton(onClick = onDefault, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Star, "Default", tint = Muted, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "Edit", tint = Muted, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = BtnPtt, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile edit sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditSheet(
    profile: RadioProfile,
    usbDevices: List<UsbSerialDevice>,
    onChange: (RadioProfile.() -> RadioProfile) -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // Track manufacturer separately (not in the profile model)
    val manufacturers = remember { RadioDeviceList.manufacturers }
    var selectedMfr by remember {
        mutableStateOf(
            RadioDeviceList.byId(profile.deviceModelId)?.manufacturer
                ?: manufacturers.firstOrNull() ?: ""
        )
    }
    val mfrDevices = remember(selectedMfr) { RadioDeviceList.byManufacturer(selectedMfr) }
    val currentDevice = RadioDeviceList.byId(profile.deviceModelId)

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text(if (profile.id == 0L) "New Profile" else "Edit Profile", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel", tint = Muted)
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text("SAVE", color = Accent, fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Name ─────────────────────────────────────────────────────────
            CatTextField("Profile Name", profile.name) { v -> onChange { copy(name = v) } }

            // ── Connection type ───────────────────────────────────────────────
            SectionLabel("Connection Type")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionType.entries.forEach { ct ->
                    FilterChip(
                        selected = profile.connectionType == ct,
                        onClick  = { onChange { copy(connectionType = ct) } },
                        label    = { Text(if (ct == ConnectionType.NETWORK_RIGCTLD) "Network (rigctld)" else "USB Serial") },
                        colors   = filterChipColors(),
                    )
                }
            }

            if (profile.connectionType == ConnectionType.USB) {

                // ── Radio model ───────────────────────────────────────────────
                SectionLabel("Radio Model")
                CatDropdown(
                    label    = "Manufacturer",
                    selected = selectedMfr,
                    options  = manufacturers,
                ) { v ->
                    selectedMfr = v
                    RadioDeviceList.byManufacturer(v).firstOrNull()?.let { d ->
                        onChange { copy(deviceModelId = d.modelId, baudRate = d.defaultBaud, civAddress = d.civAddress) }
                    }
                }
                Spacer(Modifier.height(0.dp))
                CatDropdown(
                    label    = "Model",
                    selected = currentDevice?.model ?: mfrDevices.firstOrNull()?.model ?: "",
                    options  = mfrDevices.map { it.model },
                ) { v ->
                    mfrDevices.firstOrNull { it.model == v }?.let { d ->
                        onChange { copy(deviceModelId = d.modelId, baudRate = d.defaultBaud, civAddress = d.civAddress) }
                    }
                }

                // ── CAT serial device ─────────────────────────────────────────
                SectionLabel("CAT Serial Device")
                if (usbDevices.isEmpty()) {
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("No USB serial devices detected", color = Muted, fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, null, tint = Accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh", color = Accent, fontSize = 12.sp)
                        }
                    }
                    Text("Auto-detect will use the first recognized USB-to-serial adapter.",
                        color = Muted, fontSize = 11.sp)
                } else {
                    val catLabel = deviceLabel(usbDevices, profile.catVid, profile.catPid, profile.catPortIndex)
                    CatDropdown(
                        label   = "CAT port",
                        selected = catLabel,
                        options  = listOf("Auto-detect") + usbDevices.map { it.displayLabel },
                    ) { v ->
                        val match = usbDevices.firstOrNull { it.displayLabel == v }
                        onChange { copy(
                            catVid      = match?.vendorId  ?: 0,
                            catPid      = match?.productId ?: 0,
                            catPortIndex = match?.portIndex ?: 0,
                        ) }
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, null, tint = Accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh", color = Accent, fontSize = 12.sp)
                        }
                    }
                }

                // ── Serial port settings ──────────────────────────────────────
                SectionLabel("Serial Port Settings")
                CatDropdown(
                    label    = "Baud Rate",
                    selected = profile.baudRate.toString(),
                    options  = listOf("1200","2400","4800","9600","19200","38400","57600","115200"),
                ) { v -> onChange { copy(baudRate = v.toIntOrNull() ?: baudRate) } }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        CatDropdown(
                            label    = "Data bits",
                            selected = profile.dataBits.toString(),
                            options  = listOf("7", "8"),
                        ) { v -> onChange { copy(dataBits = v.toIntOrNull() ?: dataBits) } }
                    }
                    Column(Modifier.weight(1f)) {
                        CatDropdown(
                            label    = "Stop bits",
                            selected = profile.stopBits.toString(),
                            options  = listOf("1", "2"),
                        ) { v -> onChange { copy(stopBits = v.toIntOrNull() ?: stopBits) } }
                    }
                }
                CatDropdown(
                    label    = "Parity",
                    selected = parityLabel(profile.parity),
                    options  = listOf("None","Even","Odd","Mark","Space"),
                ) { v -> onChange { copy(parity = parityValue(v)) } }

                // CI-V address for ICOM
                if (currentDevice?.protocol?.name == "ICOM_CIV") {
                    SectionLabel("CI-V Address")
                    CatTextField(
                        label = "CI-V Address (hex, e.g. 94)",
                        value = "%02X".format(profile.civAddress),
                    ) { v -> onChange { copy(civAddress = v.toIntOrNull(16) ?: civAddress) } }
                }

                // ── PTT Control ───────────────────────────────────────────────
                SectionLabel("PTT Control Method")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PttMethod.entries.forEach { method ->
                        FilterChip(
                            selected = profile.pttMethod == method,
                            onClick  = { onChange { copy(pttMethod = method) } },
                            label    = {
                                Column {
                                    Text(method.label, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            colors   = filterChipColors(),
                        )
                    }
                }
                Text(profile.pttMethod.description, color = Muted, fontSize = 12.sp)

                if (profile.pttMethod != PttMethod.CAT) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Use separate port for PTT", color = OnSurface, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        Switch(
                            checked         = profile.pttSeparatePort,
                            onCheckedChange = { onChange { copy(pttSeparatePort = it) } },
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor   = Color.Black,
                                checkedTrackColor   = Accent,
                                uncheckedThumbColor = Muted,
                                uncheckedTrackColor = Color(0xFF3A3A3A),
                            ),
                        )
                    }

                    if (profile.pttSeparatePort) {
                        if (usbDevices.isEmpty()) {
                            Text("No USB devices detected. Connect the PTT interface and refresh.",
                                color = Muted, fontSize = 12.sp)
                        } else {
                            val pttLabel = deviceLabel(usbDevices, profile.pttVid, profile.pttPid, profile.pttPortIndex)
                            CatDropdown(
                                label    = "PTT port",
                                selected = pttLabel,
                                options  = listOf("Auto-detect") + usbDevices.map { it.displayLabel },
                            ) { v ->
                                val match = usbDevices.firstOrNull { it.displayLabel == v }
                                onChange { copy(
                                    pttVid      = match?.vendorId  ?: 0,
                                    pttPid      = match?.productId ?: 0,
                                    pttPortIndex = match?.portIndex ?: 0,
                                ) }
                            }
                        }
                        Text("The ${profile.pttMethod.label} line on this port will key your transmitter.",
                            color = Muted, fontSize = 11.sp)
                    } else {
                        Text("${profile.pttMethod.label} line on the CAT port will key your transmitter.",
                            color = Muted, fontSize = 11.sp)
                    }
                }

            } else {
                // ── Network (rigctld) ─────────────────────────────────────────
                SectionLabel("rigctld Connection")
                CatTextField("Host / IP", profile.networkHost) { v -> onChange { copy(networkHost = v) } }
                CatTextField("Port", profile.networkPort.toString(), KeyboardType.Number) { v ->
                    onChange { copy(networkPort = v.toIntOrNull() ?: networkPort) }
                }
                Surface(color = Color(0xFF1A2A1A), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "Connect to a Hamlib rigctld daemon running on any computer. " +
                        "Supports all 400+ hamlib radio models over Wi-Fi or USB tethering.\n\n" +
                        "Start rigctld on the host:\n  rigctld -m <model_id> -r /dev/ttyUSB0 -s 57600",
                        color    = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun deviceLabel(devices: List<UsbSerialDevice>, vid: Int, pid: Int, portIndex: Int = 0): String {
    if (vid == 0) return "Auto-detect"
    return devices.firstOrNull { it.vendorId == vid && it.productId == pid && it.portIndex == portIndex }?.displayLabel
        ?: devices.firstOrNull { it.vendorId == vid && it.productId == pid }?.displayLabel
        ?: "VID:${vid.toString(16).uppercase().padStart(4,'0')}  PID:${pid.toString(16).uppercase().padStart(4,'0')} (not connected)"
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared form widgets
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Column {
        Spacer(Modifier.height(4.dp))
        Text(text, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun CatTextField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value          = value,
        onValueChange  = onValueChange,
        label          = { Text(label, color = Muted) },
        modifier       = Modifier.fillMaxWidth(),
        singleLine     = true,
        keyboardOptions= KeyboardOptions(keyboardType = keyboardType),
        colors         = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = Accent,
            unfocusedBorderColor  = Color(0xFF444444),
            focusedTextColor      = OnSurface,
            unfocusedTextColor    = OnSurface,
            cursorColor           = Accent,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value           = selected,
            onValueChange   = {},
            readOnly        = true,
            label           = { Text(label, color = Muted) },
            trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier        = Modifier.fillMaxWidth().menuAnchor(),
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Accent,
                unfocusedBorderColor = Color(0xFF444444),
                focusedTextColor     = OnSurface,
                unfocusedTextColor   = OnSurface,
            ),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(SurfaceCard),
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text    = { Text(opt, color = OnSurface, fontSize = 14.sp) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Accent,
    selectedLabelColor     = Color.Black,
    containerColor         = BtnOff,
    labelColor             = OnSurface,
)

private fun parityLabel(v: Int) = when (v) {
    1 -> "Even"; 2 -> "Odd"; 3 -> "Mark"; 4 -> "Space"; else -> "None"
}
private fun parityValue(label: String) = when (label) {
    "Even" -> 1; "Odd" -> 2; "Mark" -> 3; "Space" -> 4; else -> 0
}

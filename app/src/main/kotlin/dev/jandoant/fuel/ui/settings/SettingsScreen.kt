package dev.jandoant.fuel.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.MeasurementType
import dev.jandoant.fuel.data.healthconnect.HcAvailability
import dev.jandoant.fuel.data.healthconnect.HealthConnectGateway
import dev.jandoant.fuel.data.healthconnect.OriginReport
import dev.jandoant.fuel.data.settings.UserSettings
import dev.jandoant.fuel.ui.components.Divider
import dev.jandoant.fuel.ui.components.FilterChip
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.NumberField
import dev.jandoant.fuel.ui.components.SecondaryButton
import dev.jandoant.fuel.ui.components.SectionTitle
import dev.jandoant.fuel.ui.components.TextInput
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.nav.EnergyRoute
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.TargetsRoute
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import dev.jandoant.fuel.work.Jobs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class SettingsViewModel(val c: AppContainer, private val appContext: android.content.Context) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val model = c.model.state
    val slots = c.plan.slots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealSlot.DEFAULTS)
    val hcGranted = MutableStateFlow<Set<String>>(emptySet())
    val report = MutableStateFlow<List<OriginReport>?>(null)
    val working = MutableStateFlow<String?>(null)

    fun refreshHc() = viewModelScope.launch { runCatching { hcGranted.value = c.healthConnect.grantedPermissions() } }
    fun update(f: (UserSettings) -> UserSettings) = viewModelScope.launch { c.settings.update(f) }

    fun runReport() = viewModelScope.launch {
        working.value = "Reading Health Connect…"
        report.value = runCatching { c.healthConnect.originReport() }.getOrElse { c.messages.show("Could not read: ${it.message}"); null }
        working.value = null
    }

    fun testWrite() = viewModelScope.launch {
        val ok = runCatching { c.healthConnect.writeTestRecord() }.getOrDefault(false)
        c.messages.show(if (ok) "Test NutritionRecord written and deleted again" else "Nutrition write permission missing")
    }

    fun resync() = viewModelScope.launch { c.healthConnect.resetSync(); Jobs.syncNow(appContext) }

    fun setPhaseStart(d: LocalDate) = viewModelScope.launch { model.value?.phase?.let { c.model.setPhaseStart(it.id, d) } }
    fun setStartingMaintenance(k: Double) = viewModelScope.launch { model.value?.phase?.let { c.model.setStartingMaintenance(it.id, k) } }
    fun startFresh() = viewModelScope.launch {
        c.model.startFresh(model.value?.snapshotToday?.maintenance?.kcal ?: model.value?.phase?.startingMaintenanceKcal ?: 2500.0)
        c.messages.show("New phase started today. Nothing was deleted.")
    }

    fun extraSlot(enabled: Boolean, name: String, after: String?) = viewModelScope.launch { c.plan.configureExtraSlot(enabled, name, after) }

    fun setBackupFolder(uri: Uri) = viewModelScope.launch {
        appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        c.settings.update { it.copy(backupFolderUri = uri.toString()) }
        Jobs.backupNow(appContext)
    }

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching { c.backup.exportTo(uri) }.onSuccess { c.messages.show("Exported") }.onFailure { c.messages.show("Export failed: ${it.message}") }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        working.value = "Restoring…"
        runCatching { c.backup.importFrom(uri) }.onSuccess { c.messages.show("Backup restored") }.onFailure { c.messages.show("Import failed: ${it.message}") }
        working.value = null
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Settings (PRD §7.12, Settings.dc.html). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: Navigator) {
    val context = LocalContext.current
    val vm = fuelViewModel { c -> SettingsViewModel(c, context.applicationContext) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val slots by vm.slots.collectAsStateWithLifecycle()
    val granted by vm.hcGranted.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshHc() }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refreshHc(); Jobs.syncNow(context) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setBackupFolder) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmImport = it }
    var pickStart by remember { mutableStateOf(false) }
    var confirmFresh by remember { mutableStateOf(false) }
    val st = s ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MainScreenHeader("Settings", nav.openDrawer)

        // --- Tracking phase ---
        val m = model
        FuelCard {
            SectionTitle("Tracking phase")
            if (m != null) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Current phase since", style = FuelType.Meta, color = FuelColors.Muted)
                        Text("${m.phase.startDate.format(DATE)} · ${m.phase.name}", style = FuelType.BodyStrong, color = FuelColors.Ink)
                    }
                    SecondaryButton("Change", { pickStart = true })
                }
                Text("The model (trend, maintenance, targets, check-ins) only uses food logs and weigh-ins from this date on. Older data is kept and stays visible in Body, Trends and Calendar.", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Starting maintenance", style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.weight(1f))
                    NumberField(m.phase.startingMaintenanceKcal, { it?.let { k -> if (k in 1000.0..6000.0) vm.setStartingMaintenance(k) } }, "Starting maintenance in kcal", width = 72.dp)
                    Text(" kcal", style = FuelType.Meta, color = FuelColors.Muted)
                }
                SecondaryButton("Start fresh from today", { confirmFresh = true }, Modifier.fillMaxWidth().padding(top = 10.dp))
                val previous = m.phases.filter { it.id != m.phase.id }
                val firstWeight = m.dailyWeights.firstOrNull()?.date
                val firstPhase = m.phases.minOfOrNull { it.startDate }
                if (previous.isNotEmpty() || (firstWeight != null && firstPhase != null && firstWeight.isBefore(firstPhase))) {
                    Text("Previous phases", style = FuelType.MetaStrong, color = FuelColors.Muted, modifier = Modifier.padding(top = 12.dp))
                    previous.sortedByDescending { it.startDate }.forEach { p ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(p.name, style = FuelType.Small, modifier = Modifier.weight(1f))
                            Text("${p.startDate.format(DATE)} – ${p.endDate?.format(DATE) ?: "now"}", style = FuelType.Meta, color = FuelColors.Muted)
                        }
                    }
                    if (firstWeight != null && firstPhase != null && firstWeight.isBefore(firstPhase)) Row(Modifier.padding(vertical = 4.dp)) {
                        Text("Scale history only", style = FuelType.Small, modifier = Modifier.weight(1f))
                        Text("${firstWeight.format(DATE)} – ${firstPhase.minusDays(1).format(DATE)}", style = FuelType.Meta, color = FuelColors.Muted)
                    }
                }
            }
        }

        // --- Goal & targets ---
        FuelCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            LinkRow("Goal", model?.settings?.goal?.let { dev.jandoant.fuel.ui.energy.goalLabel(it) + " per week" } ?: "") { nav.go(EnergyRoute) }
            Divider()
            LinkRow("Macro & fiber targets", "Protein ${st.proteinMinGPerKg}–${st.proteinMaxGPerKg} g/kg · fat ${st.fatMinGPerKg}–${st.fatMaxGPerKg} · fiber ≥${st.fiberMinG} g") { nav.go(TargetsRoute) }
        }

        // --- Adaptive model ---
        FuelCard {
            SectionTitle("Adaptive model")
            NumberRow("Max target change per day", st.maxDailyChangeKcal, "kcal") { v -> if (v in 10.0..300.0) vm.update { it.copy(maxDailyChangeKcal = v) } }
            NumberRow("Calorie floor", st.calorieFloorKcal, "kcal") { v -> if (v in 1000.0..4000.0) vm.update { it.copy(calorieFloorKcal = v) } }
        }

        // --- Data sources ---
        FuelCard {
            SectionTitle("Data sources")
            val avail = vm.c.healthConnect.availability()
            val hasRead = granted.containsAll(HealthConnectGateway.READ_PERMISSIONS.take(1))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Health Connect", style = FuelType.BodyStrong, color = FuelColors.Ink)
                    Text(
                        when {
                            avail != HcAvailability.AVAILABLE -> "Not available on this device"
                            !hasRead -> "Not connected"
                            st.lastSyncError != null -> "Sync failed: ${st.lastSyncError}"
                            st.lastSyncAt != null -> "Synced " + Instant.ofEpochMilli(st.lastSyncAt!!).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH))
                            else -> "Connected · waiting for first sync"
                        },
                        style = FuelType.Meta, color = if (st.lastSyncError != null) FuelColors.WarningText else FuelColors.Muted,
                    )
                }
                if (avail == HcAvailability.AVAILABLE) SecondaryButton(if (granted.containsAll(HealthConnectGateway.ALL_PERMISSIONS)) "Sync now" else "Connect", {
                    if (granted.containsAll(HealthConnectGateway.ALL_PERMISSIONS)) Jobs.syncNow(context) else hcLauncher.launch(HealthConnectGateway.ALL_PERMISSIONS)
                }, emphasized = !hasRead)
            }
            if (hasRead) {
                Text(
                    "Weigh-ins stored: ${model?.measurements?.count { it.type == MeasurementType.WEIGHT } ?: 0} · body fat: ${model?.measurements?.count { it.type == MeasurementType.BODY_FAT } ?: 0} · lean mass: ${model?.measurements?.count { it.type == MeasurementType.LEAN_MASS } ?: 0}",
                    style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Data origins", vm::runReport)
                    SecondaryButton("Test write", vm::testWrite)
                    SecondaryButton("Re-import all", vm::resync)
                }
                report?.let { r ->
                    Column(Modifier.padding(top = 8.dp)) {
                        r.forEach { o ->
                            Text("${o.recordType} · ${o.origin} · ${o.count}" + (o.first?.let { f -> " · ${f.atZone(ZoneId.systemDefault()).toLocalDate().format(DATE)} → ${o.last!!.atZone(ZoneId.systemDefault()).toLocalDate().format(DATE)}" } ?: ""), style = FuelType.Tiny, color = FuelColors.Ink2)
                        }
                    }
                }
            }
            Divider(Modifier.padding(vertical = 8.dp))
            ToggleRow("Garmin calibration", "Compare Garmin energy with your real expenditure (display only)", st.garminCalibration) { v -> vm.update { it.copy(garminCalibration = v) } }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Open Food Facts country", style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.weight(1f))
                listOf("de", "at", "ch", "world").forEach { cc ->
                    FilterChip(cc.uppercase(), st.offCountry == cc, { vm.update { it.copy(offCountry = cc) } }, Modifier.padding(start = 4.dp))
                }
            }
        }

        // --- Meal plan ---
        ExtraSlotCard(slots, vm)

        // --- General ---
        FuelCard {
            SectionTitle("General")
            Row(Modifier.padding(top = 8.dp)) { Text("Units", style = FuelType.Small, modifier = Modifier.weight(1f)); Text("kg · kcal · g", style = FuelType.Small, color = FuelColors.Muted) }
            Divider(Modifier.padding(vertical = 8.dp))
            Text("Daily backup", style = FuelType.BodyStrong)
            Text(
                (st.backupFolderUri?.let { "Folder: " + Uri.parse(it).lastPathSegment?.substringAfterLast(':') } ?: "No folder chosen") +
                    (st.lastBackupAt?.let { " · last " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)) } ?: ""),
                style = FuelType.Meta, color = FuelColors.Muted,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(if (st.backupFolderUri == null) "Choose folder" else "Change folder", { folderLauncher.launch(null) }, emphasized = st.backupFolderUri == null)
                if (st.backupFolderUri != null) SecondaryButton("Back up now", { Jobs.backupNow(context) })
            }
            NumberRow("Keep newest", st.backupKeep.toDouble(), "files") { v -> vm.update { it.copy(backupKeep = v.toInt().coerceIn(1, 365)) } }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Export…", { exportLauncher.launch("fuel-export-${LocalDate.now()}.json") }, Modifier.weight(1f))
                SecondaryButton("Import…", { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f))
            }
            working?.let { Text(it, style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
            Divider(Modifier.padding(vertical = 8.dp))
            Text("About", style = FuelType.BodyStrong)
            Text("Fuel 1.0 · personal app, data stays on this phone.", style = FuelType.Meta, color = FuelColors.Muted)
            Text("Food data from Open Food Facts (openfoodfacts.org), available under the Open Database License (ODbL); product images and texts under CC BY-SA.", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 4.dp))
            Text("Manrope font © The Manrope Project Authors, SIL Open Font License 1.1.", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }

    val mm = model
    if (pickStart && mm != null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = mm.phase.startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickStart = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { vm.setPhaseStart(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; pickStart = false }) { Text("OK", color = FuelColors.AccentText) } },
            dismissButton = { TextButton({ pickStart = false }) { Text("Cancel", color = FuelColors.Muted) } },
        ) { DatePicker(state) }
    }
    if (confirmFresh) AlertDialog(
        onDismissRequest = { confirmFresh = false }, containerColor = FuelColors.Surface,
        title = { Text("Start fresh from today?", style = FuelType.SectionHeading) },
        text = { Text("Start a new phase today? Maintenance restarts from ${fmt(model?.snapshotToday?.maintenance?.kcal ?: 2500.0)} kcal and adapts again over the next 3 weeks. Nothing gets deleted.", style = FuelType.Body) },
        confirmButton = { TextButton({ confirmFresh = false; vm.startFresh() }) { Text("Start new phase", color = FuelColors.AccentText, style = FuelType.BodyStrong) } },
        dismissButton = { TextButton({ confirmFresh = false }) { Text("Cancel", color = FuelColors.Muted) } },
    )
    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null }, containerColor = FuelColors.Surface,
            title = { Text("Replace all data?", style = FuelType.SectionHeading) },
            text = { Text("Importing restores the backup and replaces everything currently in Fuel (foods, plans, recipes, weigh-ins, settings). This cannot be undone.", style = FuelType.Body) },
            confirmButton = { TextButton({ confirmImport = null; vm.import(uri) }) { Text("Replace", color = FuelColors.Danger, style = FuelType.BodyStrong) } },
            dismissButton = { TextButton({ confirmImport = null }) { Text("Cancel", color = FuelColors.Muted) } },
        )
    }
}

@Composable
private fun LinkRow(title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FuelType.BodyStrong, color = FuelColors.Ink)
            Text(sub, style = FuelType.Meta, color = FuelColors.Muted)
        }
        FuelIcon(FuelIcons.FORWARD, null, FuelColors.Faint, size = 18.dp)
    }
}

@Composable
private fun NumberRow(label: String, value: Double, unit: String, onValue: (Double) -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.weight(1f))
        NumberField(value, { it?.let(onValue) }, label, width = 72.dp)
        Text(" $unit", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FuelType.BodyStrong, color = FuelColors.Ink)
            Text(sub, style = FuelType.Meta, color = FuelColors.Muted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = FuelColors.Accent, uncheckedTrackColor = FuelColors.Line, uncheckedBorderColor = FuelColors.InputBorder, uncheckedThumbColor = FuelColors.Surface))
    }
}

@Composable
private fun ExtraSlotCard(slots: List<MealSlot>, vm: SettingsViewModel) {
    val extra = slots.firstOrNull { it.isExtra } ?: MealSlot.DEFAULTS.last()
    val fixed = slots.filter { !it.isExtra }.sortedBy { it.order }
    val after = fixed.lastOrNull { it.order < extra.order }?.id
    var name by remember(extra.name) { mutableStateOf(extra.name) }
    FuelCard {
        SectionTitle("Meal plan")
        Text("Breakfast, Lunch, Dinner and Snacks are fixed. One optional extra slot can be added.", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        ToggleRow("Extra meal slot", "Shown on Today and in the plan when on", extra.enabled) { vm.extraSlot(it, name, after) }
        if (extra.enabled) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextInput(name, { name = it }, "Name", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SecondaryButton("Save", { vm.extraSlot(true, name, after) }, Modifier.padding(top = 18.dp))
            }
            Text("Position", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip("First", after == null, { vm.extraSlot(true, name, null) })
                fixed.forEach { f -> FilterChip("After ${f.name}", after == f.id, { vm.extraSlot(true, name, f.id) }) }
            }
        }
    }
}


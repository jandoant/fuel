package dev.jandoant.fuel.ui.addfood

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.ui.components.Divider
import dev.jandoant.fuel.ui.components.EatenCheckbox
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.MacroInline
import dev.jandoant.fuel.ui.components.PortionInputRow
import dev.jandoant.fuel.ui.components.PrimaryButton
import dev.jandoant.fuel.ui.components.SecondaryButton
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType

/** State of the dictation overlay. */
@Stable
class VoiceUiState {
    var active by mutableStateOf(false)
    var listening by mutableStateOf(false)
    var partial by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    internal var start: () -> Unit = {}
    internal var finish: () -> Unit = {}
    internal var cancel: () -> Unit = {}
    fun startOrAsk() = start()
}

@Composable
fun rememberVoiceUi(onText: (String) -> Unit): VoiceUiState {
    val context = LocalContext.current
    val state = remember { VoiceUiState() }
    val callback by rememberUpdatedState(onText)
    val recognizer = remember {
        VoiceRecognizer(
            context,
            onPartial = { state.partial = it },
            onResult = { state.active = false; state.partial = ""; callback(it) },
            onError = { state.error = it; state.listening = false },
            onListening = { state.listening = it },
        )
    }
    fun begin() { state.active = true; state.error = null; state.partial = ""; recognizer.start() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else { state.active = true; state.error = "Microphone permission is needed for voice input" }
    }
    state.start = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    state.finish = { recognizer.finish() }
    state.cancel = { recognizer.stop(); state.active = false; state.partial = ""; state.error = null }
    DisposableEffect(Unit) { onDispose { recognizer.stop() } }
    return state
}

/** "Sprich jetzt": what was heard so far, plus Done / Cancel. */
@Composable
fun VoiceOverlay(state: VoiceUiState) {
    if (!state.active) return
    Dialog(onDismissRequest = state.cancel) {
        Column(
            Modifier.fillMaxWidth().background(FuelColors.Surface, FuelShapes.Card).padding(20.dp).testTag("voiceOverlay"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(64.dp).background(if (state.listening) FuelColors.Accent else FuelColors.AccentBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) { FuelIcon(FuelIcons.MIC, null, if (state.listening) Color.White else FuelColors.AccentText, size = 30.dp) }
            Text(
                when {
                    state.error != null -> state.error!!
                    state.partial.isNotBlank() -> "„${state.partial}“"
                    state.listening -> "Sprich jetzt …"
                    else -> "Einen Moment …"
                },
                style = FuelType.Body, color = if (state.error != null) FuelColors.WarningText else FuelColors.Ink, textAlign = TextAlign.Center,
            )
            Text("z. B. „200 Gramm Skyr und eine Banane“ oder „ein Esslöffel Olivenöl“", style = FuelType.Meta, color = FuelColors.Muted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cancel", state.cancel)
                if (state.error != null) PrimaryButton("Try again", state.start) else PrimaryButton("Done", state.finish)
            }
        }
    }
}

/** Review: each spoken item with its matched food and amount; nothing is added until the user confirms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceReviewSheet(vm: AddFoodViewModel, destination: String) {
    val review by vm.voice.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val r = review ?: return
    ModalBottomSheet(
        onDismissRequest = vm::closeVoice,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FuelColors.Surface,
        shape = FuelShapes.Sheet,
        scrimColor = FuelColors.Scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).background(FuelColors.InputBorder, RoundedCornerShape(2.dp))) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 18.dp).navigationBarsPadding().testTag("voiceReview"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("„${r.heard}“", style = FuelType.Number.copy(fontWeight = FontWeight.Bold), color = FuelColors.Ink)
            Text("Check the matches, then add them to $destination.", style = FuelType.Meta, color = FuelColors.Muted)
            r.lines.forEachIndexed { i, line ->
                Divider()
                VoiceLineRow(i, line, vm)
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.weight(1f))
                MacroInline(r.numbers, showKcal = true)
            }
            PrimaryButton(
                if (r.loading) "Matching foods …" else "Add ${r.toAdd.size} ${if (r.toAdd.size == 1) "food" else "foods"}",
                vm::addVoice, Modifier.fillMaxWidth(), enabled = !busy && !r.loading && r.toAdd.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun VoiceLineRow(index: Int, line: VoiceLine, vm: AddFoodViewModel) {
    val spoken = listOfNotNull(line.item.amount?.let { Amounts.formatNumber(it) }, line.item.unit?.synonyms?.first(), line.item.query).joinToString(" ")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EatenCheckbox(line.include, spoken, { vm.toggleVoiceLine(index) }, enabled = line.portion != null)
            Column(Modifier.weight(1f)) {
                Text("„$spoken“", style = FuelType.Meta, color = FuelColors.Muted, maxLines = 1)
                when {
                    line.loading -> Text("Looking for a match …", style = FuelType.Small, color = FuelColors.Muted)
                    line.candidates.isEmpty() -> Text("No match found — search for it by hand", style = FuelType.Small, color = FuelColors.WarningText)
                    else -> CandidatePicker(line) { vm.chooseCandidate(index, it) }
                }
            }
            line.portion?.let { Text("${it.numbers.kcal} kcal", style = FuelType.Small.copy(fontWeight = FontWeight.Bold), color = FuelColors.Ink) }
        }
        line.portion?.let { p ->
            PortionInputRow(p.amount, p.unit, p.food.units, p.food.name, { u, a -> vm.changeVoicePortion(index, u, a) }, Modifier.padding(start = 44.dp))
        }
        line.note?.let { Text(it, style = FuelType.Tiny, color = FuelColors.WarningText, modifier = Modifier.padding(start = 44.dp)) }
    }
}

@Composable
private fun CandidatePicker(line: VoiceLine, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.DropdownList) { open = true }
                .semantics { contentDescription = "Matched food: ${line.candidates[line.chosen].label}. Change" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(line.candidates[line.chosen].label, style = FuelType.BodyStrong, color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (line.candidates.size > 1) { Spacer(Modifier.width(4.dp)); FuelIcon(FuelIcons.CHEVRON_DOWN, null, FuelColors.Muted, size = 14.dp) }
        }
        DropdownMenu(open, { open = false }, containerColor = FuelColors.Surface) {
            line.candidates.forEachIndexed { i, c ->
                DropdownMenuItem(
                    text = { Text(c.label + if (c is VoiceCandidate.Remote) "  · Open Food Facts" else "", style = FuelType.Small) },
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}


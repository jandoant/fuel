package dev.jandoant.fuel.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.domain.RecipeTemplate
import dev.jandoant.fuel.core.model.DaySummary
import dev.jandoant.fuel.core.model.MicroScope
import dev.jandoant.fuel.core.model.NutrientLine
import dev.jandoant.fuel.core.model.NutrientStatus
import dev.jandoant.fuel.data.repo.toDraft
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.IconButton44
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.MealTimelineSection
import dev.jandoant.fuel.ui.components.MicrosSection
import dev.jandoant.fuel.ui.components.MiniBar
import dev.jandoant.fuel.ui.components.SecondaryButton
import dev.jandoant.fuel.ui.components.StatusChip
import dev.jandoant.fuel.ui.components.StatusText
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.components.scaleMax
import dev.jandoant.fuel.ui.nav.AddFoodRoute
import dev.jandoant.fuel.ui.nav.AddTarget
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import dev.jandoant.fuel.ui.theme.Macros
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class WeekDay(val date: LocalDate, val summary: DaySummary)

data class PlanUiState(
    val today: LocalDate,
    val selected: LocalDate,
    val week: List<WeekDay>,
    val day: DayState,
    val slots: List<MealSlot>,
    val recipes: List<RecipeTemplate>,
    val weekEntries: List<PlanEntry>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(private val c: AppContainer, initial: LocalDate?, onAddIngredient: (Long) -> Unit) : ViewModel() {
    val selected = MutableStateFlow(initial ?: LocalDate.now())
    val expanded = MutableStateFlow<Long?>(null)
    val actions = EntryActionsHandler(c.plan, c.messages, viewModelScope) { onAddIngredient(it.id) }

    private val weekStart = selected.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.distinctUntilChanged()

    val state: StateFlow<PlanUiState?> = weekStart.flatMapLatest { ws ->
        combine(c.plan.observeRange(ws, ws.plusDays(6)), c.plan.slots, c.model.state, selected, c.recipes.all) { entries, slots, model, sel, recipes ->
            val today = model?.today ?: LocalDate.now()
            val byDay = entries.groupBy { it.date }
            val week = (0L..6L).map { ws.plusDays(it) }.map { d ->
                WeekDay(d, DayStates.build(d, today, byDay[d].orEmpty(), slots, model?.zoneFor(d), model?.targetsFor(d)).summary)
            }
            PlanUiState(
                today, sel, week,
                DayStates.build(sel, today, byDay[sel].orEmpty(), slots, model?.zoneFor(sel), model?.targetsFor(sel)),
                slots, recipes, entries,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(d: LocalDate) { selected.value = d }
    fun shiftWeek(weeks: Long) { selected.value = selected.value.plusWeeks(weeks) }
    fun toggleExpanded(id: Long) { expanded.value = if (expanded.value == id) null else id }

    fun copyDay(from: LocalDate, entries: List<PlanEntry>, to: LocalDate) = viewModelScope.launch {
        val ids = c.plan.copy(entries, to)
        c.messages.show("Copied ${entries.size} entries to ${to.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}", "Undo") { c.plan.deleteEntries(ids) }
    }

    fun useTemplate(recipe: RecipeTemplate, slotId: String) = viewModelScope.launch {
        c.plan.addRecipe(selected.value, slotId, recipe, recipe.ingredients.map { it.toDraft() })
    }
}

/** Meal plan (PRD §7.4, Plan.dc.html). */
@Composable
fun PlanScreen(nav: Navigator, date: Long?) {
    val vm = fuelViewModel { c -> PlanViewModel(c, date?.let { LocalDate.ofEpochDay(it) }) { id -> nav.go(AddFoodRoute(AddTarget.ENTRY, targetId = id)) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val expanded by vm.expanded.collectAsStateWithLifecycle()
    val move by vm.actions.moveRequest.collectAsStateWithLifecycle()
    var copyDialog by remember { mutableStateOf(false) }
    var templateDialog by remember { mutableStateOf(false) }
    var shopping by remember { mutableStateOf(false) }
    val s = state ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            MainScreenHeader("Plan", nav.openDrawer) {
                IconButton44(FuelIcons.BACK, "Previous week", { vm.shiftWeek(-1) }, iconSize = 20.dp)
                val ws = s.week.first().date
                val we = s.week.last().date
                Text(
                    if (ws.month == we.month) "${ws.dayOfMonth} – ${we.dayOfMonth} ${we.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))}"
                    else "${ws.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} – ${we.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}",
                    style = FuelType.Small, color = FuelColors.Ink,
                )
                IconButton44(FuelIcons.FORWARD, "Next week", { vm.shiftWeek(1) }, iconSize = 20.dp)
            }
        }
        item { WeekStrip(s, vm::select) }
        item { DaySummaryCard(s.day) }
        itemsIndexed(s.day.sections, key = { _, sec -> sec.slot.id }) { i, sec ->
            MealTimelineSection(
                slot = sec.slot, entries = sec.entries, expandedId = expanded, onExpand = vm::toggleExpanded, actions = vm.actions,
                onAdd = { nav.go(AddFoodRoute(AddTarget.PLAN, s.selected.toEpochDay(), sec.slot.id)) },
                showCheckboxes = !s.selected.isAfter(s.today), isLast = i == s.day.sections.lastIndex,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Copy day", { copyDialog = true }, Modifier.weight(1f), enabled = s.day.entries.isNotEmpty())
                SecondaryButton("Use template", { templateDialog = true }, Modifier.weight(1f), enabled = s.recipes.isNotEmpty())
                SecondaryButton("Shopping list", { shopping = true }, Modifier.weight(1f), emphasized = true)
            }
        }
    }

    move?.let { e -> MoveDialog(e, s.slots, s.today, { d, slot -> vm.actions.confirmMove(e, d, slot) }, { vm.actions.moveRequest.value = null }) }
    if (copyDialog) DatePickDialog("Copy ${dayLabel(s.selected, s.today)} to…", s.selected.plusDays(1), s.today, { d -> copyDialog = false; vm.copyDay(s.selected, s.day.entries, d) }, { copyDialog = false })
    if (templateDialog) TemplateDialog(s.recipes, s.slots, { r, slot -> templateDialog = false; vm.useTemplate(r, slot) }, { templateDialog = false })
    if (shopping) ShoppingListDialog(s.weekEntries.filter { !it.eaten }, { shopping = false })
}

@Composable
private fun WeekStrip(s: PlanUiState, onSelect: (LocalDate) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        s.week.forEach { wd ->
            val on = wd.date == s.selected
            val cal = wd.summary.calories
            val target = cal.zone?.target ?: 0
            val fill = if (target > 0) (cal.planned.toFloat() / target).coerceIn(0f, 1f) else if (cal.planned > 0) 1f else 0f
            val fits = cal.status is NutrientStatus.Fits
            Column(
                Modifier.weight(1f).heightIn(min = 64.dp).clip(FuelShapes.Item)
                    .background(if (on) FuelColors.AccentBg else FuelColors.Surface)
                    .border(1.dp, if (on) FuelColors.Accent else FuelColors.Line, FuelShapes.Item)
                    .clickable(role = Role.Tab) { onSelect(wd.date) }
                    .semantics {
                        selected = on
                        contentDescription = wd.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)) + ", ${cal.planned} kcal planned, " + StatusText.planDay(cal)
                    }
                    .padding(top = 7.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(wd.date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)), style = FuelType.Tiny, color = if (wd.date == s.today) FuelColors.AccentText else FuelColors.Muted)
                Text("${wd.date.dayOfMonth}", style = FuelType.SectionHeading.copy(fontWeight = FontWeight.Bold), color = FuelColors.Ink)
                Box(Modifier.size(26.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(FuelColors.Line)) {
                    Box(Modifier.width(26.dp * fill).size(26.dp * fill, 4.dp).background(if (fits) FuelColors.Accent else FuelColors.Fat))
                }
            }
        }
    }
}

@Composable
fun DaySummaryCard(day: DayState) {
    val cal = day.summary.calories
    FuelCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append("Planned ")
                        withStyle(SpanStyle(fontSize = FuelType.PageTitle.fontSize, fontWeight = FontWeight.Bold, color = FuelColors.Ink)) { append(fmt(cal.planned)) }
                        append(cal.zone?.let { " / ${fmt(it.target)} kcal" } ?: " kcal")
                    },
                    style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.weight(1f),
                )
                StatusChip(StatusText.planDay(cal), if (cal.planned == 0) dev.jandoant.fuel.ui.components.Tone.NEUTRAL else StatusText.tone(cal.status))
            }
            val lines = day.summary.macros
            for (row in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        MacroCell(lines[i], Macros.All[i], Modifier.weight(1f))
                    }
                }
            }
            MicrosSection(day.micros, MicroScope.DAY)
        }
    }
}

@Composable
private fun MacroCell(line: NutrientLine, pal: dev.jandoant.fuel.ui.theme.MacroPalette, modifier: Modifier) {
    val r = line.range
    val inRange = line.status is NutrientStatus.Fits
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = pal.color)) { append(pal.name) }
                append(" ")
                withStyle(SpanStyle(color = if (r == null) FuelColors.Ink else if (inRange) FuelColors.AccentDeep else FuelColors.WarningText)) { append("${line.planned}") }
                append(" ")
                append(StatusText.range(r))
            },
            style = FuelType.Meta, color = FuelColors.Muted, maxLines = 1,
        )
        val max = scaleMax(r, line.planned)
        MiniBar(line.planned.toFloat(), max, pal.color, bandLow = r?.min?.toFloat(), bandHigh = r?.max?.toFloat(), band = pal.soft)
    }
}

@Composable
fun DatePickDialog(title: String, initial: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var d by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FuelColors.Surface,
        title = { Text(title, style = FuelType.SectionHeading) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton44(FuelIcons.BACK, "Previous day", { d = d.minusDays(1) })
                Text(dayLabel(d, today), style = FuelType.BodyStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton44(FuelIcons.FORWARD, "Next day", { d = d.plusDays(1) })
            }
        },
        confirmButton = { TextButton({ onPick(d) }) { Text("Copy", color = FuelColors.AccentText, style = FuelType.BodyStrong) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = FuelColors.Muted) } },
    )
}

@Composable
private fun TemplateDialog(recipes: List<RecipeTemplate>, slots: List<MealSlot>, onPick: (RecipeTemplate, String) -> Unit, onDismiss: () -> Unit) {
    var recipe by remember { mutableStateOf<RecipeTemplate?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FuelColors.Surface,
        title = { Text(if (recipe == null) "Use template" else "Add ${recipe!!.name} to…", style = FuelType.SectionHeading) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (recipe == null) recipes.forEach { r ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(FuelShapes.Item).clickable { recipe = r }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, style = FuelType.Body, modifier = Modifier.weight(1f))
                        Text("${r.numbers.kcal} kcal", style = FuelType.Meta, color = FuelColors.Muted)
                    }
                } else slots.filter { it.enabled }.forEach { sl ->
                    Text(sl.name, style = FuelType.Body, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(FuelShapes.Item).clickable { onPick(recipe!!, sl.id) }.padding(12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = FuelColors.Muted) } },
    )
}

/** Simple shopping list: grams per food over the week's not-yet-eaten entries (v1 stub per PRD §7.4). */
@Composable
private fun ShoppingListDialog(entries: List<PlanEntry>, onDismiss: () -> Unit) {
    val totals = entries.flatMap { it.portions }.groupBy { it.food.id }.values
        .map { ps -> ps.first().food.displayName to ps.sumOf { it.grams } }.sortedBy { it.first.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FuelColors.Surface,
        title = { Text("Shopping list · this week", style = FuelType.SectionHeading) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (totals.isEmpty()) Text("Nothing planned that is not eaten yet.", style = FuelType.Small, color = FuelColors.Muted)
                totals.forEach { (name, g) ->
                    Row {
                        Text(name, style = FuelType.Small, modifier = Modifier.weight(1f))
                        Text("${fmt(g)} g", style = FuelType.Small.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done", color = FuelColors.AccentText, style = FuelType.BodyStrong) } },
    )
}


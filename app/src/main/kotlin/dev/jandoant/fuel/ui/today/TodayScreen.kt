package dev.jandoant.fuel.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.model.DaySummary
import dev.jandoant.fuel.core.model.NutrientStatus
import dev.jandoant.fuel.core.model.MicroScope
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.LegendDot
import dev.jandoant.fuel.ui.components.MacroRing
import dev.jandoant.fuel.ui.components.MealTimelineSection
import dev.jandoant.fuel.ui.components.MicrosSection
import dev.jandoant.fuel.ui.components.RoundIconButton
import dev.jandoant.fuel.ui.components.StatusChip
import dev.jandoant.fuel.ui.components.StatusText
import dev.jandoant.fuel.ui.components.TextLink
import dev.jandoant.fuel.ui.components.ZoneBar
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.components.fmt1
import dev.jandoant.fuel.ui.nav.AddFoodRoute
import dev.jandoant.fuel.ui.nav.AddTarget
import dev.jandoant.fuel.ui.nav.BodyRoute
import dev.jandoant.fuel.ui.nav.EnergyRoute
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.PlanRoute
import dev.jandoant.fuel.ui.nav.TargetsRoute
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.plan.DayState
import dev.jandoant.fuel.ui.plan.DayStates
import dev.jandoant.fuel.ui.plan.EntryActionsHandler
import dev.jandoant.fuel.ui.plan.MoveDialog
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import dev.jandoant.fuel.ui.theme.Macros
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TodayUiState(
    val day: DayState,
    val today: LocalDate,
    val trendKg: Double?,
    val slots: List<MealSlot>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(c: AppContainer, onAddIngredient: (Long) -> Unit) : ViewModel() {
    val actions = EntryActionsHandler(c.plan, c.messages, viewModelScope) { onAddIngredient(it.id) }
    val expanded = MutableStateFlow<Long?>(null)

    private val today = c.model.state.map { it?.today ?: LocalDate.now() }.distinctUntilChanged()

    val state: StateFlow<TodayUiState?> = today.flatMapLatest { date ->
        combine(c.plan.observeDay(date), c.plan.slots, c.model.state) { entries, slots, model ->
            TodayUiState(
                day = DayStates.build(date, date, entries, slots, model?.zoneToday, model?.targetsToday),
                today = date,
                trendKg = model?.trendToday,
                slots = slots,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleExpanded(id: Long) { expanded.value = if (expanded.value == id) null else id }
}

/** Today (PRD §7.1, Main.dc.html). Always today — no day switching. */
@Composable
fun TodayScreen(nav: Navigator) {
    val vm = fuelViewModel { c -> TodayViewModel(c) { entryId -> nav.go(AddFoodRoute(AddTarget.ENTRY, targetId = entryId)) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val expanded by vm.expanded.collectAsStateWithLifecycle()
    val move by vm.actions.moveRequest.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize())

    // One scroll area so the plan stays reachable on short screens and in landscape.
    LazyColumn(Modifier.fillMaxSize().testTag("today"), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp)) {
        item {
            Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Header(s, nav)
                CaloriesBlock(s.day.summary) { nav.go(EnergyRoute) }
                MacrosBlock(s.day.summary) { nav.go(TargetsRoute) }
                MicrosSection(s.day.micros, MicroScope.TODAY)
            }
        }
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's plan", style = FuelType.ScreenTitle, color = FuelColors.Ink, modifier = Modifier.weight(1f).semantics { heading() })
                    TextLink("Edit plan", { nav.go(PlanRoute(s.today.toEpochDay())) })
                }
            }
            itemsIndexed(s.day.sections, key = { _, sec -> sec.slot.id }) { i, sec ->
                MealTimelineSection(
                    slot = sec.slot,
                    entries = sec.entries,
                    expandedId = expanded,
                    onExpand = vm::toggleExpanded,
                    actions = vm.actions,
                    onAdd = { nav.go(AddFoodRoute(AddTarget.PLAN, s.today.toEpochDay(), sec.slot.id)) },
                    showCheckboxes = true,
                    isLast = i == s.day.sections.lastIndex,
                )
            }
    }
    move?.let { e -> MoveDialog(e, s.slots, s.today, { d, slot -> vm.actions.confirmMove(e, d, slot) }, { vm.actions.moveRequest.value = null }) }
}

@Composable
private fun Header(s: TodayUiState, nav: Navigator) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(FuelIcons.MENU, "Open menu", nav.openDrawer)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Today", style = FuelType.ScreenTitle, color = FuelColors.Ink, modifier = Modifier.semantics { heading() })
            Text(s.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), style = FuelType.Meta, color = FuelColors.Muted)
        }
        val trend = s.trendKg
        Row(
            Modifier
                .height(44.dp)
                .shadow(1.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x10281E14), spotColor = Color(0x10281E14))
                .clip(RoundedCornerShape(22.dp))
                .background(FuelColors.Surface)
                .clickable(role = Role.Button) { nav.go(BodyRoute) }
                .padding(horizontal = 14.dp)
                .semantics { contentDescription = if (trend != null) "Trend weight ${fmt1(trend)} kg" else "No weigh-in yet, open Body" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(trend?.let { fmt1(it) } ?: "—", style = FuelType.Small.copy(fontWeight = FontWeight.ExtraBold), color = FuelColors.Ink)
            Spacer(Modifier.width(4.dp))
            Text("kg", style = FuelType.Small, color = FuelColors.Muted)
        }
    }
}

@Composable
private fun CaloriesBlock(summary: DaySummary, onClick: () -> Unit) {
    val cal = summary.calories
    val zone = cal.zone
    val head = cal.headline
    val over = head?.overGoal == true
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "See energy details", onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .testTag("calories"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Calories", style = FuelType.SectionHeading, color = FuelColors.Ink, modifier = Modifier.weight(1f).semantics { heading() })
            if (head != null) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = FuelType.Number.fontSize, fontWeight = FontWeight.ExtraBold, color = FuelColors.Ink)) { append(fmt(head.value)) }
                        append(if (over) " over goal" else " left")
                    },
                    style = FuelType.Small, color = if (over) FuelColors.Danger else FuelColors.Muted,
                )
            }
        }
        val max = maxOf((zone?.high ?: 2500) * 1.18f, cal.planned * 1.05f, 1f)
        ZoneBar(
            eaten = cal.eaten.toFloat(), planned = cal.planned.toFloat(),
            zoneLow = zone?.low?.toFloat(), zoneHigh = zone?.high?.toFloat(), max = max,
            color = FuelColors.Accent, soft = FuelColors.AccentSoft, dots = zone != null,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = FuelColors.Ink, fontWeight = FontWeight.ExtraBold)) { append(fmt(cal.eaten)) }
                    append(" eaten · ")
                    withStyle(SpanStyle(color = FuelColors.Ink, fontWeight = FontWeight.ExtraBold)) { append("+" + fmt(cal.stillPlanned)) }
                    append(" planned")
                },
                style = FuelType.Tiny, color = FuelColors.Muted, modifier = Modifier.weight(1f),
            )
            val st = cal.status
            val color = when (st) {
                is NutrientStatus.EatenOver -> FuelColors.Danger
                is NutrientStatus.Short, is NutrientStatus.Over -> FuelColors.WarningText
                is NutrientStatus.Fits -> FuelColors.AccentDeep
                NutrientStatus.NoTarget -> FuelColors.Muted
            }
            if (st is NutrientStatus.EatenOver) FuelIcon(FuelIcons.WARNING, "Warning", FuelColors.Danger, size = 11.dp, strokeWidth = 2.5f, modifier = Modifier.padding(end = 3.dp))
            Text(StatusText.calories(cal), style = FuelType.TinyStrong, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun MacrosBlock(summary: DaySummary, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Macros", style = FuelType.SectionHeading, color = FuelColors.Ink, modifier = Modifier.weight(1f).semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendDot(FuelColors.Muted, "eaten")
                LegendDot(FuelColors.Dashed, "planned")
                LegendDot(FuelColors.Ink, "goal", wide = true)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = "Edit macro and fiber targets", onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            summary.macros.zip(Macros.All).forEach { (line, pal) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MacroRing(pal, line.eaten, line.stillPlanned, line.range)
                    Text(pal.name, style = FuelType.Meta.copy(fontWeight = FontWeight.ExtraBold), color = pal.color)
                    StatusChip(StatusText.macroShort(line.status), StatusText.tone(line.status), small = true)
                }
            }
        }
    }
}

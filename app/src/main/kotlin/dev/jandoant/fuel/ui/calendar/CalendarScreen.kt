package dev.jandoant.fuel.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.model.CalorieZone
import dev.jandoant.fuel.core.model.MacroNumbers
import dev.jandoant.fuel.core.model.MicroScope
import dev.jandoant.fuel.data.repo.ModelState
import dev.jandoant.fuel.ui.components.FoodKindTile
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.IconButton44
import dev.jandoant.fuel.ui.components.LegendDot
import dev.jandoant.fuel.ui.components.MacroInline
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.MicrosSection
import dev.jandoant.fuel.ui.components.SegmentedTabs
import dev.jandoant.fuel.ui.components.TextLink
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.plan.DayState
import dev.jandoant.fuel.ui.plan.DayStates
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class DayMark { ON_TARGET, OFF_TARGET, PLANNED, NONE }

/** Status dot of a day (month view): eaten within the zone, outside, only planned, or nothing. */
fun dayMark(entries: List<PlanEntry>, zone: CalorieZone?, isFuture: Boolean): DayMark {
    if (entries.isEmpty()) return DayMark.NONE
    val eaten = entries.filter { it.eaten }
    if (eaten.isEmpty()) return if (isFuture || zone == null) DayMark.PLANNED else DayMark.PLANNED
    val kcal = MacroNumbers.sum(eaten.map { it.numbers }).kcal
    val z = zone?.display() ?: return DayMark.PLANNED
    return if (kcal in z.low..z.high) DayMark.ON_TARGET else DayMark.OFF_TARGET
}

data class CalendarUiState(
    val today: LocalDate,
    val month: Boolean,
    val anchor: LocalDate,
    val selected: LocalDate,
    val entries: Map<LocalDate, List<PlanEntry>>,
    val model: ModelState?,
    val day: DayState,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val c: AppContainer) : ViewModel() {
    val month = MutableStateFlow(false)
    val anchor = MutableStateFlow(LocalDate.now())
    val selected = MutableStateFlow(LocalDate.now())

    private val range = combine(month, anchor) { m, a ->
        if (m) a.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to a.withDayOfMonth(a.lengthOfMonth()).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        else a.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
    }

    val state: StateFlow<CalendarUiState?> = combine(range, selected) { r, s -> r to s }.flatMapLatest { (r, sel) ->
        val from = minOf(r.first, sel)
        val to = maxOf(r.second, sel)
        combine(c.plan.observeRange(from, to), c.plan.slots, c.model.state, month, anchor) { entries, slots, model, m, a ->
            val today = model?.today ?: LocalDate.now()
            val byDay = entries.groupBy { it.date }
            CalendarUiState(today, m, a, sel, byDay, model, DayStates.build(sel, today, byDay[sel].orEmpty(), slots, model?.zoneFor(sel), model?.targetsFor(sel)))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shift(n: Long) { anchor.value = if (month.value) anchor.value.plusMonths(n) else anchor.value.plusWeeks(n) }

    fun copy(entries: List<PlanEntry>, what: String) = viewModelScope.launch {
        val today = LocalDate.now()
        val ids = c.plan.copy(entries, today)
        c.messages.show("$what copied to today", "Undo") { c.plan.deleteEntries(ids) }
    }
}

/** Calendar (PRD §7.5, Calendar.dc.html): week bars (default) and month dots, day details with copy actions. */
@Composable
fun CalendarScreen(nav: Navigator) {
    val vm = fuelViewModel { c -> CalendarViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MainScreenHeader("Calendar", nav.openDrawer)
        SegmentedTabs(listOf("Week", "Month"), if (s.month) 1 else 0, { vm.month.value = it == 1 })
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton44(FuelIcons.BACK, if (s.month) "Previous month" else "Previous week", { vm.shift(-1) })
            Text(periodLabel(s), style = FuelType.BodyStrong, color = FuelColors.Ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton44(FuelIcons.FORWARD, if (s.month) "Next month" else "Next week", { vm.shift(1) })
        }
        if (s.month) MonthGrid(s) { vm.selected.value = it } else WeekBars(s) { vm.selected.value = it }
        DayDetails(s, vm)
    }
}

private fun periodLabel(s: CalendarUiState): String =
    if (s.month) s.anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    else s.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { ws ->
        "${ws.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} – ${ws.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}"
    }

@Composable
private fun WeekBars(s: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val ws = s.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0L..6L).map { ws.plusDays(it) }
    val target = s.model?.zoneToday?.target ?: 2500.0
    val maxKcal = maxOf(target * 1.25, days.maxOf { d -> MacroNumbers.sum(s.entries[d].orEmpty().map { it.numbers }).kcal.toDouble() } * 1.1)
    val logged = days.filter { d -> s.entries[d].orEmpty().any { it.eaten } && d.isBefore(s.today) }
    FuelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                val list = s.entries[d].orEmpty()
                val eaten = MacroNumbers.sum(list.filter { it.eaten }.map { it.numbers })
                val planned = MacroNumbers.sum(list.map { it.numbers })
                val zt = s.model?.zoneFor(d)?.target
                val on = d == s.selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) FuelColors.AccentBg else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelect(d) }
                        .semantics { selected = on; contentDescription = "${d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH))}: ${eaten.kcal} kcal eaten, ${planned.kcal} planned" }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.fillMaxWidth().height(130.dp).padding(horizontal = 6.dp)) {
                        fun y(k: Double) = (size.height * (1 - k / maxKcal)).toFloat()
                        val bw = size.width
                        // Stacked by macro calories (bottom → top: protein, fat, carbs)
                        val shown = if (eaten.kcal > 0) eaten else MacroNumbers.ZERO
                        var base = size.height
                        listOf(shown.protein * 4.0 to FuelColors.Protein, shown.fat * 9.0 to FuelColors.Fat, shown.carbs * 4.0 to FuelColors.Carbs).forEach { (k, c) ->
                            val h = (size.height * k / maxKcal).toFloat()
                            drawRect(c, Offset(0f, base - h), Size(bw, h))
                            base -= h
                        }
                        if (planned.kcal > eaten.kcal) {
                            drawRoundRect(FuelColors.Muted, Offset(0f, y(planned.kcal.toDouble())), Size(bw, size.height - y(planned.kcal.toDouble())), CornerRadius(6f), style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                        }
                        zt?.let { t -> drawLine(FuelColors.Ink, Offset(-6f, y(t)), Offset(bw + 6f, y(t)), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))) }
                    }
                    Text(if (eaten.kcal > 0) fmt(eaten.kcal) else if (planned.kcal > 0) fmt(planned.kcal) else "–", style = FuelType.Micro, color = if (eaten.kcal > 0) FuelColors.Ink else FuelColors.Muted, maxLines = 1)
                    Text(d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)), style = FuelType.Tiny, color = FuelColors.Muted)
                    Text("${d.dayOfMonth}", style = FuelType.Small.copy(fontWeight = FontWeight.Bold), color = if (d == s.today) FuelColors.AccentText else FuelColors.Ink)
                }
            }
        }
        val onTarget = logged.count { d -> dayMark(s.entries[d].orEmpty(), s.model?.zoneFor(d), false) == DayMark.ON_TARGET }
        val avg = logged.map { d -> MacroNumbers.sum(s.entries[d].orEmpty().filter { it.eaten }.map { it.numbers }).kcal }.average()
        Text(
            buildAnnotatedString {
                append("Avg ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FuelColors.Ink)) { append(if (logged.isEmpty()) "—" else fmt(avg)) }
                append(" kcal · $onTarget/${logged.size} on target")
            },
            style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendDot(FuelColors.Protein, "Protein"); LegendDot(FuelColors.Fat, "Fat"); LegendDot(FuelColors.Carbs, "Carbs"); LegendDot(FuelColors.Ink, "target", wide = true)
        }
    }
}

@Composable
private fun MonthGrid(s: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val first = s.anchor.withDayOfMonth(1)
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = s.anchor.withDayOfMonth(s.anchor.lengthOfMonth()).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val weeks = ((end.toEpochDay() - start.toEpochDay() + 1) / 7).toInt()
    FuelCard(padding = PaddingValues(10.dp)) {
        Row { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, style = FuelType.Tiny, color = FuelColors.Muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        repeat(weeks) { w ->
            Row {
                (0..6).forEach { i ->
                    val d = start.plusDays((w * 7 + i).toLong())
                    val inMonth = d.month == s.anchor.month
                    val mark = dayMark(s.entries[d].orEmpty(), s.model?.zoneFor(d), d.isAfter(s.today))
                    val on = d == s.selected
                    Column(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(if (on) FuelColors.AccentBg else Color.Transparent)
                            .clickable(role = Role.Tab) { onSelect(d) }.semantics { selected = on; contentDescription = "${d.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))}, ${mark.name.lowercase().replace('_', ' ')}" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text("${d.dayOfMonth}", style = FuelType.Small.copy(fontWeight = if (d == s.today) FontWeight.ExtraBold else FontWeight.SemiBold), color = if (!inMonth) FuelColors.Faint else if (d == s.today) FuelColors.AccentText else FuelColors.Ink)
                        Spacer(Modifier.height(3.dp))
                        MarkDot(mark)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MarkLegend(DayMark.ON_TARGET, "on target"); MarkLegend(DayMark.OFF_TARGET, "off target"); MarkLegend(DayMark.PLANNED, "planned")
        }
    }
}

@Composable
private fun MarkDot(mark: DayMark) {
    when (mark) {
        DayMark.ON_TARGET -> Box(Modifier.size(6.dp).background(FuelColors.Accent, CircleShape))
        DayMark.OFF_TARGET -> Box(Modifier.size(6.dp).background(FuelColors.Fat, CircleShape))
        DayMark.PLANNED -> Box(Modifier.size(6.dp).border(1.5.dp, FuelColors.Muted, CircleShape))
        DayMark.NONE -> Box(Modifier.size(6.dp))
    }
}

@Composable
private fun MarkLegend(mark: DayMark, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { MarkDot(mark); Spacer(Modifier.width(4.dp)); Text(label, style = FuelType.Micro, color = FuelColors.Muted) }
}

@Composable
private fun DayDetails(s: CalendarUiState, vm: CalendarViewModel) {
    val day = s.day
    val cal = day.summary.calories
    val showEaten = !s.selected.isAfter(s.today) && day.entries.any { it.eaten }
    val totals = if (showEaten) MacroNumbers.sum(day.entries.filter { it.eaten }.map { it.numbers }) else MacroNumbers.sum(day.entries.map { it.numbers })
    FuelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), style = FuelType.CardTitle, color = FuelColors.Ink, modifier = Modifier.weight(1f))
            if (day.entries.isNotEmpty() && s.selected != s.today) TextLink("Copy day", { vm.copy(day.entries, "Day") })
        }
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FuelColors.Ink)) { append(fmt(totals.kcal)) }
            append(cal.zone?.let { " / ${fmt(it.target)} kcal · " } ?: " kcal · ")
            append(if (showEaten) "eaten" else "planned")
        }, style = FuelType.Small, color = FuelColors.Muted)
        MacroInline(totals, style = FuelType.Meta)
        if (day.entries.isEmpty()) {
            Text("Nothing logged on this day.", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.padding(vertical = 8.dp))
            return@FuelCard
        }
        MicrosSection(day.micros, MicroScope.DAY)
        day.sections.filter { it.entries.isNotEmpty() }.forEach { sec ->
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FuelColors.Ink)) { append(sec.slot.name) }
                    append(" · ${fmt(MacroNumbers.sum(sec.entries.map { it.numbers }).kcal)} kcal")
                }, style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.weight(1f))
                if (s.selected != s.today) TextLink("Copy meal", { vm.copy(sec.entries, sec.slot.name) }, style = FuelType.Meta)
            }
            sec.entries.forEach { e ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FoodKindTile(e.isRecipe)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = FuelType.Small, color = if (e.eaten) FuelColors.Ink else FuelColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row { Text(e.amountText + "  ", style = FuelType.Tiny, color = FuelColors.Muted, maxLines = 1); MacroInline(e.numbers) }
                    }
                    Text("${e.numbers.kcal}", style = FuelType.Small.copy(fontWeight = FontWeight.Bold), color = FuelColors.Ink)
                    if (s.selected != s.today) TextLink("+ Today", { vm.copy(listOf(e), e.title) }, style = FuelType.Meta, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

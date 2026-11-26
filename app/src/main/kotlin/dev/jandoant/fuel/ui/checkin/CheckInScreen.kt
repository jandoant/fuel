package dev.jandoant.fuel.ui.checkin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jandoant.fuel.core.model.CheckInNutrient
import dev.jandoant.fuel.core.model.CheckInResult
import dev.jandoant.fuel.core.model.Diagnosis
import dev.jandoant.fuel.core.model.Forecast
import dev.jandoant.fuel.core.model.NutrientWeek
import dev.jandoant.fuel.core.model.RateStatus
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.IconButton44
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.StatusChip
import dev.jandoant.fuel.ui.components.Tone
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.components.fmt1
import dev.jandoant.fuel.ui.energy.goalLabel
import dev.jandoant.fuel.ui.energy.pctLabel
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.appContainer
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs

/** Weekly check-in (PRD §6.7, §7.9, CheckIn.dc.html). */
@Composable
fun CheckInScreen(nav: Navigator, index: Int) {
    val model by appContainer().model.state.collectAsStateWithLifecycle()
    var i by rememberSaveable { mutableIntStateOf(index) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val m = model
        val list = m?.checkIns.orEmpty()
        if (list.isEmpty()) {
            MainScreenHeader("Check-in", nav.openDrawer)
            FuelCard {
                Text("The first check-in appears after the first full week of the phase.", style = FuelType.Small, color = FuelColors.Ink2)
                m?.let { Text("Phase started ${it.phase.startDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}. Log your food and weigh in daily for a verdict (≥5 logged days and ≥4 weigh-ins per week).", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp)) }
            }
            return@Column
        }
        val idx = i.coerceIn(0, list.lastIndex)
        val c = list[idx]
        MainScreenHeader("Week ${c.weekEnd.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", nav.openDrawer) {
            IconButton44(FuelIcons.BACK, "Previous week", { i = idx + 1 }, enabled = idx < list.lastIndex, iconSize = 20.dp)
            IconButton44(FuelIcons.FORWARD, "Next week", { i = idx - 1 }, enabled = idx > 0, iconSize = 20.dp)
        }
        Text(
            "${c.weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} – ${c.weekEnd.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · ${c.loggedDays}/7 days logged · ${c.weighIns}/7 weigh-ins",
            style = FuelType.Meta, color = FuelColors.Muted,
        )
        GoalSection(c, m!!.settings.goal.let { goalLabel(it) })
        ExecutionSection(c)
        ForecastSection(m.forecasts, c)
        AdaptedSection(c)
        c.composition?.let { comp ->
            fun word(v: Double) = when { v < -0.2 -> "tending down"; v > 0.2 -> "tending up"; else -> "steady" }
            Text(
                "Composition (${comp.weeks} weeks, low confidence): fat mass ${word(comp.fatMassChangeKg)} (${String.format(Locale.US, "%+.1f", comp.fatMassChangeKg)} kg), lean mass ${word(comp.leanMassChangeKg)} (${String.format(Locale.US, "%+.1f", comp.leanMassChangeKg)} kg).",
                style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, chip: Pair<String, Tone>? = null, border: Color = FuelColors.CardBorder, content: @Composable () -> Unit) {
    FuelCard(border = border, shape = RoundedCornerShape(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FuelType.CardTitle, color = FuelColors.Ink, modifier = Modifier.weight(1f))
            chip?.let { StatusChip(it.first, it.second) }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun GoalSection(c: CheckInResult, goal: String) {
    val r = c.rate
    val chip = when (r?.status) {
        null -> "No data" to Tone.NEUTRAL
        RateStatus.ON_TRACK -> "On track" to Tone.OK
        RateStatus.FASTER_THAN_GOAL -> "Faster" to Tone.WARNING
        RateStatus.SLOWER_THAN_GOAL -> "Slower" to Tone.WARNING
        RateStatus.LOSING -> "Losing" to Tone.WARNING
        RateStatus.GAINING -> "Gaining" to Tone.WARNING
    }
    Section("1 · Goal reached?", chip) {
        Text(buildAnnotatedString {
            append("Actual ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(r?.let { pctLabel(it.actualPctPerWeek) } ?: "—") }
            withStyle(SpanStyle(color = FuelColors.Muted)) { append(c.trendChangeKg?.let { " (${String.format(Locale.US, "%+.2f", it)} kg)" } ?: ""); append(" · goal $goal") }
        }, style = FuelType.Small, color = FuelColors.Ink)
        val weeks = c.weeksToGoal
        if (weeks != null && c.goalWeightKg != null) {
            val date = c.weekEnd.plusDays((weeks * 7).toLong())
            Text("At this rate: ${fmt1(c.goalWeightKg!!)} kg around ${date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))}", style = FuelType.Meta, color = FuelColors.Muted)
        } else if (c.goalWeightKg == null) Text("Set a goal weight on Energy to see a projection.", style = FuelType.Meta, color = FuelColors.Muted)
    }
}

@Composable
private fun ExecutionSection(c: CheckInResult) {
    val chip = when (c.ateOnTarget) {
        true -> "Ate on target" to Tone.OK
        false -> "Off target" to Tone.WARNING
        null -> "Not enough data" to Tone.NEUTRAL
    }
    Section("2 · Plan or execution?", chip) {
        c.nutrients.forEach { NutrientRow(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            Legend(Color(0xFFDCDDFB), "target")
            Legend(FuelColors.Protein, "day", round = true)
            Legend(FuelColors.Ink, "actual avg", thin = true)
            Legend(FuelColors.Surface, "plan avg", thin = true, outlined = true)
        }
        val fiber = c.nutrients.first { it.nutrient == CheckInNutrient.FIBER }
        if (fiber.loggedDays > 0 && fiber.daysInRange < 5) {
            Text("Fiber below the minimum on ${fiber.loggedDays - fiber.daysInRange} of ${fiber.loggedDays} logged days", style = FuelType.Meta, color = FuelColors.WarningText, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private data class Style(val name: String, val unit: String, val band: Color, val dot: Color)

private fun style(n: CheckInNutrient) = when (n) {
    CheckInNutrient.CALORIES -> Style("Calories", "kcal", FuelColors.Line, FuelColors.Subtle)
    CheckInNutrient.PROTEIN -> Style("Protein", "g", Color(0xFFDCDDFB), FuelColors.Protein)
    CheckInNutrient.FAT -> Style("Fat", "g", Color(0xFFFBE0D0), FuelColors.Fat)
    CheckInNutrient.CARBS -> Style("Carbs", "g", FuelColors.CarbsSoft, FuelColors.Carbs)
    CheckInNutrient.FIBER -> Style("Fiber", "g", FuelColors.FiberSoft, FuelColors.Fiber)
}

@Composable
private fun NutrientRow(n: NutrientWeek) {
    val st = style(n.nutrient)
    fun f(v: Double?) = v?.let { if (n.nutrient == CheckInNutrient.CALORIES) fmt(Math.round(it / 10.0) * 10.0) else fmt(it) } ?: "—"
    val ref = n.bandHigh?.let { (n.bandLow!! + it) / 2 } ?: n.bandLow ?: n.actualAvg ?: 1.0
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(124.dp)) {
            Text(buildAnnotatedString { append("${st.name} "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${f(n.actualAvg)} ${st.unit}") } }, style = FuelType.Meta, color = FuelColors.Ink, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FuelIcon(FuelIcons.TARGET, "Planned", FuelColors.Muted, size = 11.dp, strokeWidth = 2.5f)
                Spacer(Modifier.width(4.dp))
                Text("${f(n.plannedAvg)} ${st.unit}", style = FuelType.Tiny, color = FuelColors.Muted)
            }
        }
        Canvas(Modifier.weight(1f).height(24.dp).semantics { contentDescription = "${st.name}: ${n.loggedDays} daily values against the target range, weekly average ${f(n.actualAvg)}" }) {
            val w = size.width
            fun x(v: Double) = (((v / ref) - 0.6) / 0.8 * w).toFloat().coerceIn(2f, w - 2f)
            drawRect(FuelColors.Line2, Offset(0f, size.height / 2 - 1), Size(w, 2f))
            val x100 = x(ref)
            drawLine(FuelColors.InputBorder, Offset(x100, 3f), Offset(x100, size.height - 3f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            n.bandLow?.let { lo ->
                val bx = x(lo)
                val bw = (n.bandHigh?.let { x(it) } ?: w) - bx
                drawRoundRect(st.band, Offset(bx, size.height * 0.2f), Size(bw, size.height * 0.6f), CornerRadius(8f))
            }
            val ys = listOf(0.33f, 0.62f, 0.42f, 0.66f, 0.38f, 0.58f, 0.5f)
            n.dailyEaten.forEachIndexed { k, v ->
                drawCircle(Color.White, 4.5f * density / 1.5f, Offset(x(v), size.height * ys[k % 7]))
                drawCircle(st.dot, 3f * density / 1.5f, Offset(x(v), size.height * ys[k % 7]))
            }
            n.plannedAvg?.let { p -> drawRoundRect(Color.White, Offset(x(p) - 4f, 3f), Size(8f, size.height - 6f), CornerRadius(4f)); drawRoundRect(FuelColors.Muted, Offset(x(p) - 4f, 3f), Size(8f, size.height - 6f), CornerRadius(4f), style = Stroke(2f)) }
            n.actualAvg?.let { a -> drawRoundRect(FuelColors.Ink, Offset(x(a) - 3f, 2f), Size(6f, size.height - 4f), CornerRadius(3f)) }
        }
        Text("${n.daysInRange}/${n.loggedDays}", style = FuelType.Meta, color = if (n.daysInRange >= 5) FuelColors.AccentDeep else FuelColors.WarningText, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun Legend(color: Color, label: String, round: Boolean = false, thin: Boolean = false, outlined: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(if (round) 3.dp else 2.dp)
        Box(
            Modifier.size(if (thin) 4.dp else if (round) 6.dp else 12.dp, if (thin) 10.dp else if (round) 6.dp else 8.dp)
                .background(color, shape).then(if (outlined) Modifier.border(1.5.dp, FuelColors.Muted, shape) else Modifier),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = FuelType.Tiny, color = FuelColors.Muted)
    }
}

@Composable
private fun ForecastSection(all: List<Forecast>, c: CheckInResult) {
    val past = all.filter { it.actualTrendChangeKg != null && !it.weekEnd.isAfter(c.weekEnd) }.takeLast(6)
    val current = all.firstOrNull { it.weekEnd == c.weekEnd }
    Section("3 · Forecast vs outcome") {
        if (past.isNotEmpty()) Text("${past.count { it.hit == true }} of ${past.size} within ±0.1 kg", style = FuelType.Meta, color = FuelColors.Muted)
        Text(buildAnnotatedString {
            append("Forecast ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(current?.let { String.format(Locale.US, "%+.2f kg", it.expectedTrendChangeKg) } ?: "—") }
            append(" · actual ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(c.trendChangeKg?.let { String.format(Locale.US, "%+.2f kg", it) } ?: "—") }
        }, style = FuelType.Small, color = FuelColors.Ink)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            past.forEach { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val hit = f.hit == true
                    Box(Modifier.size(34.dp, 22.dp).background(if (hit) FuelColors.AccentBg else Color(0xFFFBEBDD), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text(if (hit) "✓" else "✗", style = FuelType.TinyStrong, color = if (hit) FuelColors.AccentDeep else FuelColors.WarningText)
                    }
                    Text("W${f.weekEnd.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", style = FuelType.Micro, color = FuelColors.Subtle)
                }
            }
        }
    }
}

@Composable
private fun AdaptedSection(c: CheckInResult) {
    Section("4 · What was adapted", border = if (c.diagnosis == Diagnosis.NOT_ENOUGH_DATA) FuelColors.CardBorder else FuelColors.Accent) {
        Row { Text("Maintenance", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.weight(1f)); Change(c.maintenanceBefore, c.maintenanceAfter) }
        if (c.targetBefore != null && c.targetAfter != null) Row {
            Text("Daily target", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.weight(1f)); Change(c.targetBefore!!, c.targetAfter!!)
        }
        Text(c.explanation, style = FuelType.Meta, color = FuelColors.Ink2, modifier = Modifier.padding(top = 4.dp))
        if (c.targetBefore != null && c.targetAfter != null && abs(c.targetAfter!! - c.targetBefore!!) > 50) {
            Text("The daily target moves at most 50 kcal per day.", style = FuelType.Tiny, color = FuelColors.Muted)
        }
    }
}

@Composable
private fun Change(before: Double, after: Double) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = FuelColors.Muted)) { append("${fmt(Math.round(before / 10.0) * 10.0)} → ") }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(fmt(Math.round(after / 10.0) * 10.0)) }
    }, style = FuelType.Small, color = FuelColors.Ink)
}

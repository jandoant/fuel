package dev.jandoant.fuel.ui.energy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jandoant.fuel.core.model.GoalRange
import dev.jandoant.fuel.core.model.RateStatus
import dev.jandoant.fuel.data.repo.ModelState
import dev.jandoant.fuel.ui.components.ChartAxis
import dev.jandoant.fuel.ui.components.FilterChip
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.LegendDot
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.NumberField
import dev.jandoant.fuel.ui.components.SecondaryButton
import dev.jandoant.fuel.ui.components.SectionTitle
import dev.jandoant.fuel.ui.components.Series
import dev.jandoant.fuel.ui.components.StatusChip
import dev.jandoant.fuel.ui.components.TimeChart
import dev.jandoant.fuel.ui.components.Tone
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.components.fmt1
import dev.jandoant.fuel.ui.nav.CheckInRoute
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.appContainer
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

fun goalLabel(g: GoalRange): String = when {
    g.isMaintain -> "Maintain"
    g.highPct <= 0 -> "Lose ${trim(-g.highPct)}–${trim(-g.lowPct)} %"
    else -> "Gain ${trim(g.lowPct)}–${trim(g.highPct)} %"
}

private fun trim(v: Double) = if (v == Math.floor(v)) "${v.toInt()}.0" else v.toString()

fun pctLabel(v: Double) = (if (v > 0) "+" else if (v < 0) "−" else "±") + String.format(Locale.US, "%.2f", kotlin.math.abs(v)) + " %/wk"

/** Energy (PRD §7.7, Energy.dc.html). */
@Composable
fun EnergyScreen(nav: Navigator) {
    val c = appContainer()
    val model by c.model.state.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MainScreenHeader("Energy", nav.openDrawer) {
            if ((model?.checkIns?.size ?: 0) > 0) SecondaryButton("Check-in", { nav.go(CheckInRoute()) })
        }
        val m = model ?: return@Column
        MaintenanceCard(m)
        FuelCard {
            SectionTitle("Goal")
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GoalRange.PRESETS.forEach { g ->
                    FilterChip(goalLabel(g), m.settings.goal == g, { scope.launch { c.settings.update { it.copy(goalLowPct = g.pctPerWeekA, goalHighPct = g.pctPerWeekB) } } })
                }
            }
            val trend = m.trendToday
            if (trend != null) {
                val g = m.settings.goal
                val a = g.lowPct / 100 * trend
                val b = g.highPct / 100 * trend
                Text(
                    if (g.isMaintain) "Keep weight stable at ${fmt1(trend)} kg trend weight"
                    else "${String.format(Locale.US, "%.2f", kotlin.math.abs(b))}–${String.format(Locale.US, "%.2f", kotlin.math.abs(a))} kg/week at ${fmt1(trend)} kg trend weight",
                    style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Goal weight (optional, for projections)", style = FuelType.Meta, color = FuelColors.Ink2, modifier = Modifier.weight(1f))
                NumberField(m.settings.goalWeightKg, { v -> scope.launch { c.settings.update { it.copy(goalWeightKg = v?.takeIf { x -> x in 30.0..250.0 }) } } }, "Goal weight in kg", width = 64.dp, allowEmpty = true)
                Text(" kg", style = FuelType.Meta, color = FuelColors.Muted)
            }
        }
        TargetCard(m)
        RateCard(m)
        if (m.settings.garminCalibration) GarminCard(m)
    }
}

@Composable
private fun MaintenanceCard(m: ModelState) {
    val est = m.snapshotToday.maintenance
    FuelCard {
        Text("Maintenance", style = FuelType.Meta, color = FuelColors.Muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = FuelType.BigNumber.fontSize, fontWeight = FontWeight.ExtraBold)) { append(fmt(est.kcal)) }
                append(" kcal/day")
            }, style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.weight(1f))
            Text("±${fmt(est.sd)}", style = FuelType.BodyStrong, color = FuelColors.Muted)
        }
        val from = maxOf(m.phase.startDate, m.today.minusWeeks(8))
        val days = m.run.days.filter { !it.date.isBefore(from) }
        Spacer(Modifier.height(8.dp))
        TimeChart(
            from = from, to = m.today,
            left = ChartAxis.around(days.flatMap { listOf(it.maintenance.kcal - it.maintenance.sd, it.maintenance.kcal + it.maintenance.sd) }, minSpan = 200.0),
            series = listOf(
                Series.Band(days.map { Triple(it.date, it.maintenance.kcal - it.maintenance.sd, it.maintenance.kcal + it.maintenance.sd) }, FuelColors.AccentBg),
                Series.Line(days.map { it.date to it.maintenance.kcal }, FuelColors.Accent, 2.5.dp, stepped = true),
            ),
            description = "Maintenance estimate over the last weeks: now ${fmt(est.kcal)} plus or minus ${fmt(est.sd)} kcal per day",
            startLabel = if (from == m.phase.startDate) "Phase start" else "8 weeks ago",
            height = 120.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { LegendDot(FuelColors.Accent, "estimate", wide = true); LegendDot(FuelColors.AccentBg, "likely range") }
        val intake = m.intakeByDay.filterKeys { !it.isBefore(m.today.minusDays(28)) }.values
        val b = m.balance
        Text(
            if (intake.isNotEmpty() && b != null) "Avg intake ${fmt(intake.average())} + trend ${String.format(Locale.US, "%+.2f", b.slope.kgPerDay * 7)} kg/wk · last 28 days"
            else "Adapts weekly from logged intake and the weight trend. Starts from ${fmt(m.phase.startingMaintenanceKcal)} kcal.",
            style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TargetCard(m: ModelState) {
    val z = m.zoneToday?.display()
    FuelCard {
        Text("Today's target", style = FuelType.Meta, color = FuelColors.Muted)
        if (z == null) {
            Text("Weigh in once in this phase to get a calorie zone.", style = FuelType.Small, color = FuelColors.Ink2, modifier = Modifier.padding(top = 4.dp))
            return@FuelCard
        }
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontSize = FuelType.PageTitle.fontSize, fontWeight = FontWeight.ExtraBold)) { append(fmt(z.target)) }
            append(" kcal · band ${fmt(z.low)}–${fmt(z.high)}")
        }, style = FuelType.Small, color = FuelColors.Ink)
        if (m.snapshotToday.targetHeld) Text("Held: no weigh-in for 7 days or more.", style = FuelType.Meta, color = FuelColors.WarningText)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Protein and fat") }
            append(" from your g/kg ranges · carbs fill the rest")
        }, style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RateCard(m: ModelState) {
    val r = m.run.rate
    FuelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Actual rate ", style = FuelType.Small, color = FuelColors.Muted)
            Text(r?.let { pctLabel(it.actualPctPerWeek) } ?: "—", style = FuelType.BodyStrong, color = FuelColors.Ink, modifier = Modifier.weight(1f))
            if (r != null) {
                val (t, tone) = when (r.status) {
                    RateStatus.ON_TRACK -> "On track" to Tone.OK
                    RateStatus.FASTER_THAN_GOAL -> "Faster than goal" to Tone.WARNING
                    RateStatus.SLOWER_THAN_GOAL -> "Slower than goal" to Tone.WARNING
                    RateStatus.LOSING -> "Losing" to Tone.WARNING
                    RateStatus.GAINING -> "Gaining" to Tone.WARNING
                }
                StatusChip(t, tone)
            }
        }
        Text("Goal ${goalLabel(m.settings.goal)} per week · from the 28-day weigh-in regression", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 4.dp))
        if (r?.fastLossWarning == true) Text("Losing faster than 1 % per week — consider eating a bit more.", style = FuelType.Meta, color = FuelColors.Danger, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GarminCard(m: ModelState) {
    val g = m.garmin
    FuelCard {
        SectionTitle("Garmin calibration")
        if (g == null) {
            val weeks = m.garminDays.map { it.date }.distinct().size / 7
            Text("Needs at least 8 weeks of Garmin energy and food logs (have ~$weeks weeks of Garmin data).", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text("Real expenditure ≈ ${fmt(g.intercept)} + ${String.format(Locale.US, "%.2f", g.factor)} × Garmin active kcal", style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.padding(top = 6.dp))
            Text("Factor likely ${String.format(Locale.US, "%.2f–%.2f", g.factorLow, g.factorHigh)} · Garmin total is ${fmt((g.totalRatio * 100))} % of real (${g.weeks} weeks)", style = FuelType.Meta, color = FuelColors.Muted)
        }
        Text("Display only — never used for targets. Logging bias cannot be separated from Garmin's error.", style = FuelType.Micro.copy(fontWeight = FontWeight.Medium), color = FuelColors.Subtle, modifier = Modifier.padding(top = 6.dp))
    }
}


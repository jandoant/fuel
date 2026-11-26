package dev.jandoant.fuel.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jandoant.fuel.core.model.Aggregation
import dev.jandoant.fuel.core.model.Bucket
import dev.jandoant.fuel.core.model.MicroReferences
import dev.jandoant.fuel.core.model.MicroScope
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.data.repo.ModelState
import dev.jandoant.fuel.ui.body.RANGES
import dev.jandoant.fuel.ui.body.rangeStart
import dev.jandoant.fuel.ui.components.ChartAxis
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.LegendDot
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.MicrosSection
import dev.jandoant.fuel.ui.components.SectionTitle
import dev.jandoant.fuel.ui.components.SegmentedTabs
import dev.jandoant.fuel.ui.components.Series
import dev.jandoant.fuel.ui.components.Side
import dev.jandoant.fuel.ui.components.TimeChart
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.components.fmt1
import dev.jandoant.fuel.ui.components.signed1
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.appContainer
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Trends (PRD §7.8, Trends.dc.html). Unlogged days are gaps, never 0 kcal. */
@Composable
fun TrendsScreen(nav: Navigator) {
    val model by appContainer().model.state.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MainScreenHeader("Trends", nav.openDrawer)
        SegmentedTabs(RANGES, range, { range = it })
        val m = model ?: return@Column
        val firstLog = m.days.values.filter { it.eaten != null }.minOfOrNull { it.date }
        val first = listOfNotNull(firstLog, m.dailyWeights.firstOrNull()?.date).minOrNull()
        val from = rangeStart(range, m.today, first)
        val bucket = Aggregation.bucketFor(Aggregation.daysBetween(from, m.today))
        CaloriesWeight(m, from, bucket)
        Fiber(m, from, bucket)
        val logged = m.days.values.filter { it.eaten != null && !it.date.isBefore(from) && it.date.isBefore(m.today) }
        val avg = if (logged.isEmpty()) Nutrients.ZERO else Nutrients.sum(logged.map { it.eatenNutrients }) * (1.0 / logged.size)
        val target = logged.mapNotNull { m.zoneFor(it.date)?.target }.average().takeIf { !it.isNaN() } ?: MicroReferences.REFERENCE_ENERGY_KCAL
        MicrosSection(
            if (logged.isEmpty()) null else MicroReferences.report(avg, target, MicroScope.AVERAGE, 0, logged.sumOf { it.itemCount }),
            MicroScope.AVERAGE, framed = true,
        )
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@Composable
private fun CaloriesWeight(m: ModelState, from: LocalDate, bucket: Bucket) {
    val intake = m.intakeByDay
    val bars = Aggregation.average(intake, from, m.today, bucket)
    val targetByDay = m.snapshots.filter { it.target != null }.associate { LocalDate.ofEpochDay(it.date) to it.target!! }
        .filterKeys { !it.isBefore(from) && !it.isAfter(m.today) }
    val targetSteps = Aggregation.average(targetByDay, from, m.today, bucket).mapNotNull { b -> b.value?.let { b.start to it } }
    val trend = m.trendAll.filter { !it.date.isBefore(from) }
    val leftValues = bars.mapNotNull { it.value } + targetSteps.map { it.second }
    val unit = when (bucket) { Bucket.DAY -> "daily"; Bucket.WEEK -> "weekly avg"; Bucket.MONTH -> "monthly avg" }
    FuelCard {
        SectionTitle("Calories & weight") { Text(unit, style = FuelType.Meta, color = FuelColors.Muted) }
        TimeChart(
            from = from, to = m.today,
            left = ChartAxis(0.0, (leftValues.maxOrNull() ?: 2500.0) * 1.15, listOf((leftValues.maxOrNull() ?: 2500.0), (leftValues.maxOrNull() ?: 2500.0) / 2)),
            right = if (trend.isEmpty()) null else ChartAxis.around(trend.map { it.trendKg }, pad = 0.3, format = { fmt1(it) }),
            series = listOf(
                Series.Bars(bars.map { it.start to it.value }, when (bucket) { Bucket.DAY -> 1; Bucket.WEEK -> 7; Bucket.MONTH -> 30 }, FuelColors.AccentSoft),
                Series.Line(targetSteps, FuelColors.Ink, 1.5.dp, stepped = true, dashed = true),
                Series.Line(trend.map { it.date to it.trendKg }, FuelColors.Protein, 2.dp, side = Side.RIGHT),
                Series.Dots(trend.map { it.date to it.weightKg }, FuelColors.ProteinSoft, 1.8.dp, side = Side.RIGHT),
            ),
            description = "Intake bars, stepped calorie target and weight trend since ${from.format(DAY)}",
            startLabel = from.format(DAY), height = 170.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendDot(FuelColors.AccentSoft, "intake (kcal, left)"); LegendDot(FuelColors.Ink, "target", wide = true); LegendDot(FuelColors.Protein, "weight trend (kg, right)", wide = true)
        }
        val inRange = intake.filterKeys { !it.isBefore(from) }.values
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Avg intake", if (inRange.isEmpty()) "—" else fmt(inRange.average()))
            Stat("Avg target", if (targetByDay.isEmpty()) "—" else fmt(targetByDay.values.average()))
            Stat("Weight", if (trend.size < 2) "—" else "${signed1(trend.last().trendKg - trend.first().trendKg)} kg")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = FuelType.Meta, color = FuelColors.Muted)
        Text(value, style = FuelType.BodyStrong, color = FuelColors.Ink)
    }
}

@Composable
private fun Fiber(m: ModelState, from: LocalDate, bucket: Bucket) {
    val fiber = m.days.values.filter { it.eaten != null && it.date.isBefore(m.today) && !it.date.isBefore(from) }.associate { it.date to it.eaten!!.fiber.toDouble() }
    val bars = Aggregation.average(fiber, from, m.today, bucket)
    val min = m.settings.fiberMinG.toDouble()
    val hits = bars.count { (it.value ?: -1.0) >= min }
    val withData = bars.count { it.value != null }
    val unit = if (bucket == Bucket.DAY) "days" else if (bucket == Bucket.WEEK) "weeks" else "months"
    FuelCard {
        SectionTitle("Fiber") {
            Text(buildAnnotatedString {
                append("avg ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FuelColors.Ink)) { append(if (fiber.isEmpty()) "—" else "${fmt(fiber.values.average())} g") }
                append(" · ≥${min.toInt()} g on $hits/$withData $unit")
            }, style = FuelType.Meta, color = FuelColors.Muted)
        }
        TimeChart(
            from = from, to = m.today,
            left = ChartAxis(0.0, maxOf(min * 1.6, (bars.mapNotNull { it.value }.maxOrNull() ?: 0.0) * 1.1), listOf(min), format = { "${it.toInt()}g" }),
            series = listOf(
                Series.Bars(bars.map { it.start to it.value }, when (bucket) { Bucket.DAY -> 1; Bucket.WEEK -> 7; Bucket.MONTH -> 30 }, FuelColors.FiberSoft),
                Series.HLine(min, FuelColors.Fiber),
            ),
            description = "Fiber per ${unit.removeSuffix("s")} against the ${min.toInt()} gram minimum",
            startLabel = from.format(DAY), height = 110.dp,
        )
    }
}

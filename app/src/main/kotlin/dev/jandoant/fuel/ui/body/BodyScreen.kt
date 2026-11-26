package dev.jandoant.fuel.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jandoant.fuel.core.domain.MeasurementType
import dev.jandoant.fuel.core.model.BalanceLabel
import dev.jandoant.fuel.data.repo.ModelState
import dev.jandoant.fuel.ui.components.ChartAxis
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.LegendDot
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.SectionTitle
import dev.jandoant.fuel.ui.components.SegmentedTabs
import dev.jandoant.fuel.ui.components.Series
import dev.jandoant.fuel.ui.components.Side
import dev.jandoant.fuel.ui.components.TimeChart
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.components.fmt1
import dev.jandoant.fuel.ui.components.signed1
import dev.jandoant.fuel.ui.components.signedInt
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.appContainer
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val RANGES = listOf("4 weeks", "6 months", "All time")

fun rangeStart(index: Int, today: LocalDate, first: LocalDate?): LocalDate = when (index) {
    0 -> today.minusDays(27)
    1 -> today.minusDays(181)
    else -> minOf(first ?: today.minusDays(27), today.minusDays(27))
}

/** Body (PRD §7.6, Body.dc.html). */
@Composable
fun BodyScreen(nav: Navigator) {
    val model by appContainer().model.state.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MainScreenHeader("Body", nav.openDrawer)
        SegmentedTabs(RANGES, range, { range = it })
        val m = model
        if (m == null || m.dailyWeights.isEmpty()) {
            FuelCard {
                Text("No weigh-ins yet", style = FuelType.CardTitle)
                Text("Connect Health Connect in Settings to import your scale history (Fitdays → Google Health → Health Connect).", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            return@Column
        }
        val from = rangeStart(range, m.today, m.dailyWeights.firstOrNull()?.date)
        TrendCard(m, from)
        BalanceCard(m)
        CompositionCard(m, from)
        RecentCard(m)
    }
}

@Composable
private fun TrendCard(m: ModelState, from: LocalDate) {
    val pts = m.trendAll.filter { !it.date.isBefore(from) }
    val startTrend = pts.firstOrNull()?.trendKg
    val now = m.trendAll.lastOrNull()?.trendKg
    FuelCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Trend weight", style = FuelType.Meta, color = FuelColors.Muted)
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = FuelType.BigNumber.fontSize, fontWeight = FontWeight.ExtraBold)) { append(now?.let { fmt1(it) } ?: "—") }
                    append(" kg")
                }, style = FuelType.Small, color = FuelColors.Ink)
            }
            if (startTrend != null && now != null) Column(horizontalAlignment = Alignment.End) {
                Text("${signed1(now - startTrend)} kg", style = FuelType.BodyStrong, color = FuelColors.Ink)
                Text("change in period", style = FuelType.Meta, color = FuelColors.Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        val values = pts.flatMap { listOf(it.weightKg, it.trendKg) }
        TimeChart(
            from = from, to = m.today,
            left = ChartAxis.around(values, format = { fmt1(it) }),
            series = listOf(
                Series.Dots(pts.map { it.date to it.weightKg }, FuelColors.Faint),
                Series.Line(pts.map { it.date to it.trendKg }, FuelColors.Accent, 2.5.dp),
            ),
            description = "Trend weight chart: ${pts.size} weigh-ins, trend ${startTrend?.let { fmt1(it) }} to ${now?.let { fmt1(it) }} kg",
            startLabel = from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { LegendDot(FuelColors.Faint, "Daily weigh-in"); LegendDot(FuelColors.Accent, "Trend", wide = true) }
    }
}

@Composable
private fun BalanceCard(m: ModelState) {
    val b = m.balance
    FuelCard {
        SectionTitle("Balance from weight") { Text("last 28 days", style = FuelType.Meta, color = FuelColors.Muted) }
        if (b == null) {
            Text("Needs at least 3 weigh-ins in the last 28 days.", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp))
            return@FuelCard
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = FuelType.PageTitle.fontSize, fontWeight = FontWeight.ExtraBold)) { append("≈ ${signedInt(Math.round(b.kcalPerDay).toInt())}") }
                append(" kcal/day")
            }, style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.weight(1f))
            val (label, color) = when (b.label) {
                BalanceLabel.DEFICIT -> "deficit" to FuelColors.AccentDeep
                BalanceLabel.MAINTENANCE -> "maintenance" to FuelColors.Ink2
                BalanceLabel.SURPLUS -> "surplus" to FuelColors.Fat
            }
            Text(label, style = FuelType.BodyStrong, color = color)
        }
        // Scale −600 … +600 with the likely range
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp).height(14.dp)) {
            val w = maxWidth
            fun pos(v: Double) = ((v + 600) / 1200).toFloat().coerceIn(0f, 1f)
            Box(Modifier.offset(y = 5.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(FuelColors.TrackStrong))
            Box(Modifier.offset(x = w * 0.5f - 0.5.dp).size(1.dp, 14.dp).background(FuelColors.Faint))
            Box(Modifier.offset(x = w * pos(b.likelyLowKcal), y = 3.dp).width(w * (pos(b.likelyHighKcal) - pos(b.likelyLowKcal)).coerceAtLeast(0.01f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(FuelColors.AccentSoft))
            Box(Modifier.offset(x = w * pos(b.kcalPerDay) - 2.dp).size(4.dp, 14.dp).clip(RoundedCornerShape(2.dp)).background(FuelColors.Ink))
        }
        Row { Text("−600", style = FuelType.Micro, color = FuelColors.Muted, modifier = Modifier.weight(1f)); Text("+600", style = FuelType.Micro, color = FuelColors.Muted) }
        Text(
            "Likely ${signedInt(Math.round(b.likelyLowKcal).toInt())} to ${signedInt(Math.round(b.likelyHighKcal).toInt())} · trend ${signed1(b.slope.kgPerDay * 7)} kg/week · no calorie data used",
            style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CompositionCard(m: ModelState, from: LocalDate) {
    val fat = m.bodyFat.filter { !it.date.isBefore(from) }
    val lean = m.leanMass.filter { !it.date.isBefore(from) }
    FuelCard {
        SectionTitle("Composition")
        if (fat.isEmpty() && lean.isEmpty()) {
            Text("No body fat or lean mass readings from the scale yet.", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp))
            return@FuelCard
        }
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            fat.lastOrNull()?.let { CompValue("Body fat", "≈ ${fmt1(it.trend)} %", fat.first().trend.let { f -> "${signed1(it.trend - f)} %" }, FuelColors.Fat) }
            lean.lastOrNull()?.let { CompValue("Lean mass", "≈ ${fmt1(it.trend)} kg", lean.first().trend.let { l -> "${signed1(it.trend - l)} kg" }, FuelColors.Protein) }
        }
        TimeChart(
            from = from, to = m.today,
            left = ChartAxis(0.0, 30.0, listOf(30.0, 15.0, 0.0), format = { "${it.toInt()}%" }),
            right = ChartAxis(0.0, 80.0, listOf(80.0, 40.0, 0.0), format = { "${it.toInt()}kg" }),
            series = listOf(
                Series.Band(fat.map { Triple(it.date, it.trend - it.band, it.trend + it.band) }, FuelColors.FatSoft.copy(alpha = 0.5f)),
                Series.Line(fat.map { it.date to it.trend }, FuelColors.Fat),
                Series.Band(lean.map { Triple(it.date, it.trend - it.band, it.trend + it.band) }, FuelColors.ProteinSoft.copy(alpha = 0.5f), side = Side.RIGHT),
                Series.Line(lean.map { it.date to it.trend }, FuelColors.Protein, side = Side.RIGHT),
            ),
            description = "Body composition chart: body fat on the left axis, lean mass on the right axis",
            startLabel = from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
            height = 130.dp,
        )
        Text("Estimated by the scale · line = smoothed trend, band = likely range · not used in calculations", style = FuelType.Micro.copy(fontWeight = FontWeight.Medium), color = FuelColors.Subtle, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CompValue(label: String, value: String, delta: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = FuelType.MetaStrong, color = color)
        Text(value, style = FuelType.BodyStrong, color = FuelColors.Ink)
        Text("$delta in period", style = FuelType.Tiny, color = FuelColors.Muted)
    }
}

@Composable
private fun RecentCard(m: ModelState) {
    val z = ZoneId.systemDefault()
    val byDay = m.measurements.groupBy { it.time.atZone(z).toLocalDate() }
    val days = byDay.keys.sortedDescending().take(10)
    FuelCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        SectionTitle("Recent measurements")
        days.forEach { d ->
            val ms = byDay.getValue(d).sortedBy { it.time }
            val w = ms.firstOrNull { it.type == MeasurementType.WEIGHT }
            val bf = ms.firstOrNull { it.type == MeasurementType.BODY_FAT }
            Row(Modifier.padding(vertical = 5.dp)) {
                Text(d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), style = FuelType.Small, color = FuelColors.Ink2, modifier = Modifier.weight(1f))
                Text(w?.let { "${fmt1(it.value)} kg" } ?: "—", style = FuelType.Small.copy(fontWeight = FontWeight.Bold), color = FuelColors.Ink, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                Text(bf?.let { "${fmt1(it.value)} %" } ?: "", style = FuelType.Small, color = FuelColors.Muted, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
            }
        }
        val origins = m.measurements.map { it.origin }.distinct()
        if (origins.isNotEmpty()) Text("Source: ${origins.joinToString()}", style = FuelType.Micro.copy(fontWeight = FontWeight.Medium), color = FuelColors.Subtle, modifier = Modifier.padding(top = 4.dp))
    }
}


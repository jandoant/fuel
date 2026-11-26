package dev.jandoant.fuel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.model.MicroReport
import dev.jandoant.fuel.core.model.MicroRow
import dev.jandoant.fuel.core.model.MicroScope
import dev.jandoant.fuel.core.model.NutrientUnit
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import java.util.Locale

/** Collapsible micronutrient section (PRD §7.13, Micros.dc.html). */
@Composable
fun MicrosSection(report: MicroReport?, scope: MicroScope, modifier: Modifier = Modifier, framed: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    val summary = report?.summary(scope) ?: "no data"
    val flagged = report != null && (report.lowCount > 0 || report.overCount > 0)
    val frame = if (framed) Modifier.background(FuelColors.Surface, FuelShapes.Entry).border(1.dp, FuelColors.Line, FuelShapes.Entry).padding(horizontal = 14.dp, vertical = 2.dp)
    else Modifier
    Column(modifier.fillMaxWidth().then(frame)) {
        if (!framed) Divider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "expanded" else "collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append("Micronutrients ")
                    withStyle(SpanStyle(color = FuelColors.Muted, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)) { append("· ${scope.label}") }
                },
                style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.weight(1f),
            )
            Text(summary, style = FuelType.Meta, color = if (flagged) FuelColors.WarningText else FuelColors.Muted)
            FuelIcon(FuelIcons.CHEVRON_DOWN, null, FuelColors.Muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp).rotate(if (open) 180f else 0f))
        }
        AnimatedVisibility(open && report != null) {
            if (report != null) Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GroupTitle("Limits")
                report.limits.forEach { MicroRowView(it) }
                GroupTitle("Vitamins & minerals")
                report.vitamins.forEach { MicroRowView(it) }
                Text("Reference: DGE values for adults · limits as maximum · ${report.coverageText}", style = FuelType.Micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = FuelColors.Subtle, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun GroupTitle(t: String) =
    Text(t.uppercase(), style = FuelType.TinyStrong.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp)), color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))

@Composable
private fun MicroRowView(r: MicroRow) {
    val unit = r.nutrient.unit
    val dec = r.reference < 30 && unit != NutrientUnit.MG || r.nutrient.name == "IRON" || r.nutrient.name == "SALT"
    fun f(v: Double) = if (dec) String.format(Locale.US, "%.1f", v) else fmt(v)
    val bar: Color = when {
        r.over -> FuelColors.WarningText
        r.isLimit -> FuelColors.Faint
        r.low -> Color(0xFFC98A5A)
        else -> FuelColors.Accent
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(r.name, style = FuelType.Meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = FuelColors.Ink, modifier = Modifier.weight(1f))
        MiniBar(r.value.toFloat(), r.reference.toFloat().coerceAtLeast(0.001f), bar, Modifier.width(70.dp))
        Text(
            "${f(r.value)} / ${if (r.isLimit) "≤" else ""}${fmt(r.reference)} ${unit.symbol}",
            style = FuelType.Tiny, color = if (r.over || r.low) FuelColors.WarningText else FuelColors.Ink2,
            textAlign = TextAlign.End, modifier = Modifier.width(100.dp),
        )
    }
}

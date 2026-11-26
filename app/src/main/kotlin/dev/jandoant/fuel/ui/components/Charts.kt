package dev.jandoant.fuel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.model.GramRange
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import dev.jandoant.fuel.ui.theme.MacroPalette

/** Scale used by bars and rings: the goal's top plus headroom (fiber: 1.6 × minimum). */
fun scaleMax(range: GramRange?, planned: Int): Float {
    val base = when {
        range == null -> planned * 1.3f
        range.max == null -> range.min * 1.6f
        else -> range.max!! * 1.3f
    }
    return maxOf(base, 1f)
}

/**
 * Horizontal bar: eaten solid, still planned soft, goal zone with white hatching (+ optional accent dots above
 * the zone edges). Values are in the same unit; [max] sets the scale.
 */
@Composable
fun ZoneBar(
    eaten: Float,
    planned: Float,
    zoneLow: Float?,
    zoneHigh: Float?,
    max: Float,
    color: Color,
    soft: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    track: Color = FuelColors.TrackStrong,
    dots: Boolean = false,
    zoneTint: Color = Color.Transparent,
) {
    fun pct(v: Float) = (v / max).coerceIn(0f, 1f)
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            if (dots && zoneLow != null) {
                Box(Modifier.fillMaxWidth().height(8.dp)) {
                    Box(Modifier.offset(x = w * pct(zoneLow) - 2.5.dp, y = 2.dp).size(5.dp).background(FuelColors.Accent, CircleShape))
                    if (zoneHigh != null) Box(Modifier.offset(x = w * pct(zoneHigh) - 2.5.dp, y = 2.dp).size(5.dp).background(FuelColors.Accent, CircleShape))
                }
            }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .offset(y = if (dots) (-2).dp else 0.dp)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(track),
        ) {
            val w = maxWidth
            val e = pct(eaten)
            val p = pct(maxOf(planned, eaten))
            Box(Modifier.offset(x = w * e).width(w * (p - e)).fillMaxHeight().background(soft))
            Box(Modifier.width(w * e).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(color))
            if (zoneLow != null) {
                val zl = pct(zoneLow)
                val zh = if (zoneHigh == null) 1f else pct(zoneHigh)
                Box(Modifier.offset(x = w * zl).width(w * (zh - zl)).fillMaxHeight().background(zoneTint).hatched())
            }
        }
    }
}

/**
 * Macro ring (Today): eaten arc solid, planned arc soft, goal range as a thin dark outer arc; centre text eaten,
 * "+planned" below.
 */
@Composable
fun MacroRing(
    palette: MacroPalette,
    eaten: Int,
    stillPlanned: Int,
    range: GramRange?,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
) {
    val max = scaleMax(range, eaten + stillPlanned)
    val goal = when {
        range == null -> "no goal"
        range.max == null -> "at least ${range.min}"
        else -> "${range.min} to ${range.max}"
    }
    Box(
        modifier.size(size).semantics { contentDescription = "${palette.name}: $eaten g eaten, $stillPlanned g more planned, goal $goal g" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val unit = s / 76f
            val r = 28f * unit
            val r2 = 36f * unit
            val sw = 10f * unit
            val tl = Offset(center.x - r, center.y - r)
            val arcSize = Size(2 * r, 2 * r)
            drawCircle(palette.tint, radius = r, style = Stroke(sw))
            val e = (eaten / max).coerceIn(0f, 1f)
            val t = ((eaten + stillPlanned) / max).coerceIn(0f, 1f)
            if (t > e) drawArc(palette.soft, -90f + 360f * e, 360f * (t - e), false, tl, arcSize, style = Stroke(sw))
            if (e > 0) drawArc(palette.color, -90f, 360f * e, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (range != null) {
                val lo = (range.min / max).coerceIn(0f, 1f)
                val hi = if (range.max == null) 1f else (range.max!! / max).coerceIn(0f, 1f)
                drawArc(
                    FuelColors.Ink, -90f + 360f * lo, 360f * (hi - lo), false,
                    Offset(center.x - r2, center.y - r2), Size(2 * r2, 2 * r2), style = Stroke(2.5f * unit, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$eaten", style = FuelType.NumberSmall, color = FuelColors.Ink)
            Text("+$stillPlanned", style = FuelType.Micro, color = palette.color)
        }
    }
}

/** Small progress bar used in the Plan 2×2 grid and the micronutrient rows. */
@Composable
fun MiniBar(value: Float, max: Float, color: Color, modifier: Modifier = Modifier, bandLow: Float? = null, bandHigh: Float? = null, band: Color = Color.Transparent, height: Dp = 5.dp) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(3.dp)).background(FuelColors.Line)) {
        val w = maxWidth
        fun pct(v: Float) = (v / max).coerceIn(0f, 1f)
        if (bandLow != null) {
            val hi = bandHigh?.let { pct(it) } ?: 1f
            Box(Modifier.offset(x = w * pct(bandLow)).width(w * (hi - pct(bandLow))).fillMaxHeight().background(band))
        }
        Box(Modifier.width(w * pct(value)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
}

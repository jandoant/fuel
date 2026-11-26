package dev.jandoant.fuel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import java.time.LocalDate

/** A value axis: [min]..[max] with tick labels. */
data class ChartAxis(val min: Double, val max: Double, val ticks: List<Double> = emptyList(), val format: (Double) -> String = { fmt(it) }) {
    val span: Double get() = (max - min).takeIf { it > 0 } ?: 1.0

    companion object {
        /** A padded axis around [values] with three ticks. */
        fun around(values: List<Double>, pad: Double = 0.08, minSpan: Double = 1.0, format: (Double) -> String = { fmt(it) }): ChartAxis {
            if (values.isEmpty()) return ChartAxis(0.0, 1.0, emptyList(), format)
            var lo = values.min()
            var hi = values.max()
            if (hi - lo < minSpan) { val m = (hi + lo) / 2; lo = m - minSpan / 2; hi = m + minSpan / 2 }
            val p = (hi - lo) * pad
            lo -= p; hi += p
            return ChartAxis(lo, hi, listOf(hi - p, (lo + hi) / 2, lo + p), format)
        }
    }
}

enum class Side { LEFT, RIGHT }

sealed interface Series {
    val side: Side
    data class Dots(val points: List<Pair<LocalDate, Double>>, val color: Color, val radius: Dp = 2.5.dp, override val side: Side = Side.LEFT) : Series
    data class Line(val points: List<Pair<LocalDate, Double>>, val color: Color, val width: Dp = 2.dp, val stepped: Boolean = false, val dashed: Boolean = false, override val side: Side = Side.LEFT) : Series
    data class Band(val points: List<Triple<LocalDate, Double, Double>>, val color: Color, override val side: Side = Side.LEFT) : Series
    /** Bars starting at [Pair.first] spanning [days] days; null values are gaps. */
    data class Bars(val points: List<Pair<LocalDate, Double?>>, val days: Int, val color: Color, override val side: Side = Side.LEFT) : Series
    data class HLine(val value: Double, val color: Color, val dashed: Boolean = true, override val side: Side = Side.LEFT) : Series
}

/**
 * Small time chart drawn on a Canvas (Body, Energy, Trends). Left/right axes, start/end x labels.
 * [description] is announced to screen readers instead of the drawing.
 */
@Composable
fun TimeChart(
    from: LocalDate,
    to: LocalDate,
    left: ChartAxis,
    series: List<Series>,
    description: String,
    modifier: Modifier = Modifier,
    right: ChartAxis? = null,
    height: Dp = 150.dp,
    startLabel: String? = null,
    endLabel: String? = "Today",
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = FuelType.Micro.copy(color = FuelColors.Muted, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val padL = 34.dp.toPx()
        val padR = if (right != null) 34.dp.toPx() else 6.dp.toPx()
        val padT = 6.dp.toPx()
        val padB = 18.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val days = (to.toEpochDay() - from.toEpochDay()).coerceAtLeast(1).toFloat()
        fun x(d: LocalDate) = padL + w * ((d.toEpochDay() - from.toEpochDay()) / days)
        fun y(v: Double, side: Side): Float {
            val a = if (side == Side.RIGHT && right != null) right else left
            return padT + h * (1f - ((v - a.min) / a.span).toFloat().coerceIn(-0.05f, 1.05f))
        }
        // Grid + tick labels
        left.ticks.forEach { t ->
            val yy = y(t, Side.LEFT)
            drawLine(FuelColors.Line2, Offset(padL, yy), Offset(padL + w, yy), 1.dp.toPx())
            val tl = measurer.measure(left.format(t), labelStyle)
            drawText(tl, topLeft = Offset(padL - tl.size.width - 4.dp.toPx(), yy - tl.size.height / 2))
        }
        right?.ticks?.forEach { t ->
            val tl = measurer.measure(right.format(t), labelStyle)
            drawText(tl, topLeft = Offset(padL + w + 4.dp.toPx(), y(t, Side.RIGHT) - tl.size.height / 2))
        }
        startLabel?.let { val tl = measurer.measure(it, labelStyle); drawText(tl, topLeft = Offset(padL, size.height - tl.size.height)) }
        endLabel?.let { val tl = measurer.measure(it, labelStyle); drawText(tl, topLeft = Offset(padL + w - tl.size.width, size.height - tl.size.height)) }

        series.forEach { s ->
            when (s) {
                is Series.Band -> if (s.points.size > 1) {
                    val p = Path()
                    s.points.forEachIndexed { i, (d, lo, _) -> if (i == 0) p.moveTo(x(d), y(lo, s.side)) else p.lineTo(x(d), y(lo, s.side)) }
                    s.points.asReversed().forEach { (d, _, hi) -> p.lineTo(x(d), y(hi, s.side)) }
                    p.close()
                    drawPath(p, s.color)
                }
                is Series.Bars -> {
                    val bw = (w / days * s.days * 0.7f).coerceAtLeast(2f)
                    s.points.forEach { (d, v) ->
                        if (v != null) {
                            val cx = x(d) + (w / days * s.days) / 2 - (w / days) / 2
                            val top = y(v, s.side)
                            drawRect(s.color, Offset(cx - bw / 2, top), Size(bw, padT + h - top))
                        }
                    }
                }
                is Series.Line -> drawSeriesLine(s, ::x, { v -> y(v, s.side) })
                is Series.Dots -> s.points.forEach { (d, v) -> drawCircle(s.color, s.radius.toPx(), Offset(x(d), y(v, s.side))) }
                is Series.HLine -> {
                    val yy = y(s.value, s.side)
                    drawLine(s.color, Offset(padL, yy), Offset(padL + w, yy), 1.5.dp.toPx(), pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null)
                }
            }
        }
    }
}

private fun DrawScope.drawSeriesLine(s: Series.Line, x: (LocalDate) -> Float, y: (Double) -> Float) {
    if (s.points.isEmpty()) return
    val p = Path()
    s.points.forEachIndexed { i, (d, v) ->
        if (i == 0) p.moveTo(x(d), y(v))
        else {
            if (s.stepped) p.lineTo(x(d), y(s.points[i - 1].second))
            p.lineTo(x(d), y(v))
        }
    }
    drawPath(
        p, s.color,
        style = Stroke(s.width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null),
    )
}

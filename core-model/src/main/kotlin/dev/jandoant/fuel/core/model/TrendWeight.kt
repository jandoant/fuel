package dev.jandoant.fuel.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** One weigh-in as it comes from Health Connect. */
data class WeightReading(val time: LocalDateTime, val kg: Double)

/** The single weigh-in used for a calendar day. */
data class DailyWeight(val date: LocalDate, val kg: Double)

data class TrendPoint(val date: LocalDate, val weightKg: Double, val trendKg: Double)

/** PRD §6.1 — time-aware exponential smoothing. */
object TrendWeight {

    /**
     * Reduces readings to one per day. Choice (PRD asks to document it): the **first reading of the day**,
     * because Jan weighs in standardised in the morning; later readings are after food/drink.
     */
    fun dailyWeights(readings: List<WeightReading>): List<DailyWeight> =
        readings
            .groupBy { it.time.toLocalDate() }
            .map { (date, rs) -> DailyWeight(date, rs.minBy { it.time }.kg) }
            .sortedBy { it.date }

    /**
     * Smooths daily weights. The first point seeds the trend. For a gap of `d` days the step weight is
     * `1 − (1 − α)^d`, i.e. the trend decays by elapsed days instead of inventing values for missing days.
     */
    fun smooth(daily: List<DailyWeight>, alphaPerDay: Double = ModelDefaults().trendAlphaPerDay): List<TrendPoint> {
        require(alphaPerDay > 0 && alphaPerDay <= 1) { "alpha must be in (0, 1]" }
        val sorted = daily.sortedBy { it.date }
        val out = ArrayList<TrendPoint>(sorted.size)
        var trend = 0.0
        var prev: LocalDate? = null
        for (d in sorted) {
            trend = if (prev == null) {
                d.kg
            } else {
                val days = ChronoUnit.DAYS.between(prev, d.date).toDouble()
                val weight = 1 - (1 - alphaPerDay).pow(days)
                trend + weight * (d.kg - trend)
            }
            out += TrendPoint(d.date, d.kg, trend)
            prev = d.date
        }
        return out
    }

    /** Trend as of [date]: the latest trend point on or before it (the trend is held through gaps). */
    fun trendOn(points: List<TrendPoint>, date: LocalDate): Double? =
        points.lastOrNull { !it.date.isAfter(date) }?.trendKg

    /**
     * PRD §6.5 reference weight. CHOICE: the **average trend weight** of the weigh-in days within the last
     * [windowDays] days up to [asOf] (matches the Targets prototype: 7 d 78.1, 14 d 78.4, 28 d 78.9 while
     * losing). Falls back to the latest trend if the window holds no weigh-in.
     */
    fun referenceWeight(points: List<TrendPoint>, asOf: LocalDate, windowDays: Int): Double? {
        val from = asOf.minusDays(windowDays - 1L)
        val inWindow = points.filter { !it.date.isBefore(from) && !it.date.isAfter(asOf) }
        return if (inWindow.isEmpty()) trendOn(points, asOf) else inWindow.map { it.trendKg }.average()
    }
}

package dev.jandoant.fuel.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.sqrt

/** One week for the Garmin calibration (PRD §6.8). */
data class GarminWeek(
    val weekEnd: LocalDate,
    /** Logged intake − balance from weight, kcal/day. */
    val realExpenditure: Double,
    val garminActive: Double,
    val garminTotal: Double,
)

data class GarminCalibration(
    val weeks: Int,
    val intercept: Double,
    /** Factor b in real = a + b × Garmin active, with its standard error. */
    val factor: Double,
    val factorSe: Double,
    /** Garmin total ÷ real expenditure. */
    val totalRatio: Double,
) {
    val factorLow: Double get() = factor - factorSe
    val factorHigh: Double get() = factor + factorSe
}

object GarminCalibrationModel {
    const val MIN_WEEKS = 8

    /**
     * Builds weekly points: for each week end, the 28-day regression balance and the average logged intake of
     * the week (logged days only) give the real expenditure; Garmin values are weekly averages of days that
     * have them. Weeks with fewer than 5 logged days, no balance or no Garmin data are skipped.
     */
    fun weeks(
        intakeByDay: Map<LocalDate, Double>,
        daily: List<DailyWeight>,
        garmin: Map<LocalDate, Pair<Double, Double>>,
        weekEnds: List<LocalDate>,
        defaults: ModelDefaults = ModelDefaults(),
    ): List<GarminWeek> = weekEnds.mapNotNull { end ->
        val start = end.minusDays(6)
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        val intake = days.mapNotNull { intakeByDay[it] }
        val g = days.mapNotNull { garmin[it] }
        if (intake.size < 5 || g.isEmpty()) return@mapNotNull null
        val balance = WeightRegression.balance(daily, end, defaults) ?: return@mapNotNull null
        GarminWeek(end, intake.average() - balance.kcalPerDay, g.map { it.second }.average(), g.map { it.first }.average())
    }

    fun calibrate(weeks: List<GarminWeek>): GarminCalibration? {
        if (weeks.size < MIN_WEEKS) return null
        val xs = weeks.map { it.garminActive }
        val ys = weeks.map { it.realExpenditure }
        val fit = LinearFit.of(xs, ys) ?: return null
        val ratio = weeks.map { it.garminTotal }.average() / ys.average()
        return GarminCalibration(weeks.size, fit.intercept, fit.slope, fit.slopeSe, ratio)
    }
}

/** Ordinary least squares y = a + b·x with the standard error of b. */
data class LinearFit(val intercept: Double, val slope: Double, val slopeSe: Double) {
    companion object {
        fun of(xs: List<Double>, ys: List<Double>): LinearFit? {
            require(xs.size == ys.size)
            if (xs.size < 3) return null
            val xm = xs.average()
            val ym = ys.average()
            val sxx = xs.sumOf { (it - xm) * (it - xm) }
            if (sxx == 0.0) return null
            val sxy = xs.indices.sumOf { (xs[it] - xm) * (ys[it] - ym) }
            val b = sxy / sxx
            val a = ym - b * xm
            val sse = xs.indices.sumOf { val r = ys[it] - (a + b * xs[it]); r * r }
            return LinearFit(a, b, sqrt(sse / (xs.size - 2) / sxx))
        }
    }
}

/** A smoothed series with a ± band (body composition, PRD §6.9). */
data class BandPoint(val date: LocalDate, val value: Double, val trend: Double, val band: Double)

object CompositionTrend {
    /**
     * Same time-aware smoothing as the weight trend; the band is the rolling standard deviation of the
     * residuals (value − trend) over the last [bandWindow] readings. Informational only.
     */
    fun smooth(daily: List<DailyWeight>, alpha: Double = ModelDefaults().trendAlphaPerDay, bandWindow: Int = 14): List<BandPoint> {
        val trend = TrendWeight.smooth(daily, alpha)
        return trend.mapIndexed { i, p ->
            val window = trend.subList(maxOf(0, i - bandWindow + 1), i + 1)
            val res = window.map { it.weightKg - it.trendKg }
            val sd = if (res.size < 2) 0.0 else sqrt(res.sumOf { it * it } / (res.size - 1))
            BandPoint(p.date, p.weightKg, p.trendKg, sd)
        }
    }
}

enum class Bucket { DAY, WEEK, MONTH }

data class BucketValue(val start: LocalDate, val value: Double?, val days: Int)

/** Averages for the Trends charts: only logged days count; a bucket without logged days is a gap (null). */
object Aggregation {

    fun bucketStart(date: LocalDate, bucket: Bucket): LocalDate = when (bucket) {
        Bucket.DAY -> date
        Bucket.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Bucket.MONTH -> date.withDayOfMonth(1)
    }

    fun next(start: LocalDate, bucket: Bucket): LocalDate = when (bucket) {
        Bucket.DAY -> start.plusDays(1)
        Bucket.WEEK -> start.plusWeeks(1)
        Bucket.MONTH -> start.plusMonths(1)
    }

    fun average(values: Map<LocalDate, Double>, from: LocalDate, to: LocalDate, bucket: Bucket): List<BucketValue> {
        val out = mutableListOf<BucketValue>()
        var s = bucketStart(from, bucket)
        while (!s.isAfter(to)) {
            val e = next(s, bucket)
            val inB = values.filterKeys { !it.isBefore(s) && it.isBefore(e) && !it.isBefore(from) && !it.isAfter(to) }.values
            out += BucketValue(s, inB.takeIf { it.isNotEmpty() }?.average(), inB.size)
            s = e
        }
        return out
    }

    /** Bucket size the Trends screen uses for a range of [days] days. */
    fun bucketFor(days: Long): Bucket = when {
        days <= 31 -> Bucket.DAY
        days <= 200 -> Bucket.WEEK
        else -> Bucket.MONTH
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}

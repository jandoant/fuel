package dev.jandoant.fuel.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

data class WeightSlope(
    val kgPerDay: Double,
    /** Standard error of the slope (kg/day). */
    val slopeSe: Double,
    val weighIns: Int,
)

/** PRD §6.2 "Balance from weight", shown on Body. */
data class BalanceFromWeight(
    val kcalPerDay: Double,
    val likelyLowKcal: Double,
    val likelyHighKcal: Double,
    val slope: WeightSlope,
) {
    val label: BalanceLabel get() = when {
        likelyHighKcal < 0 -> BalanceLabel.DEFICIT
        likelyLowKcal > 0 -> BalanceLabel.SURPLUS
        else -> BalanceLabel.MAINTENANCE
    }
}

/** CHOICE: the label is "maintenance" whenever the likely range includes 0. */
enum class BalanceLabel { DEFICIT, MAINTENANCE, SURPLUS }

object WeightRegression {

    /** Least-squares slope of daily weigh-ins in the [windowDays] days ending at [end] (inclusive). */
    fun fit(
        daily: List<DailyWeight>,
        end: LocalDate,
        windowDays: Int = ModelDefaults().regressionWindowDays,
        minWeighIns: Int = ModelDefaults().minWeighInsForRegression,
        from: LocalDate = end.minusDays(windowDays - 1L),
    ): WeightSlope? {
        val start = maxOf(from, end.minusDays(windowDays - 1L))
        val pts = daily.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        if (pts.size < maxOf(minWeighIns, 3)) return null
        val xs = pts.map { ChronoUnit.DAYS.between(start, it.date).toDouble() }
        val ys = pts.map { it.kg }
        val xMean = xs.average()
        val yMean = ys.average()
        val sxx = xs.sumOf { (it - xMean) * (it - xMean) }
        if (sxx == 0.0) return null
        val sxy = xs.indices.sumOf { (xs[it] - xMean) * (ys[it] - yMean) }
        val slope = sxy / sxx
        val intercept = yMean - slope * xMean
        val sse = xs.indices.sumOf { val r = ys[it] - (intercept + slope * xs[it]); r * r }
        val residualVar = sse / (pts.size - 2)
        return WeightSlope(kgPerDay = slope, slopeSe = sqrt(residualVar / sxx), weighIns = pts.size)
    }

    /** Always uses the regression window (28 d by default), regardless of any chart range. */
    fun balance(
        daily: List<DailyWeight>,
        end: LocalDate,
        defaults: ModelDefaults = ModelDefaults(),
        from: LocalDate = end.minusDays(defaults.regressionWindowDays - 1L),
    ): BalanceFromWeight? {
        val s = fit(daily, end, defaults.regressionWindowDays, defaults.minWeighInsForRegression, from) ?: return null
        val kcal = s.kgPerDay * defaults.kcalPerKg
        val half = defaults.likelyRangeZ * s.slopeSe * defaults.kcalPerKg
        return BalanceFromWeight(kcal, kcal - half, kcal + half, s)
    }
}

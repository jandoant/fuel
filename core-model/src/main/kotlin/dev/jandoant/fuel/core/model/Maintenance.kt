package dev.jandoant.fuel.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/** Maintenance (expenditure) estimate with its uncertainty. */
data class MaintenanceEstimate(val kcal: Double, val variance: Double) {
    /** One standard deviation in kcal/day (the "± kcal" on Energy). */
    val sd: Double get() = sqrt(variance)
}

enum class MaintenanceOutcome {
    /** Normal update. */
    UPDATED,
    /** Updated, but trusted less because of a 2–6 day weight gap. */
    UPDATED_CAUTIOUSLY,
    /** More than the allowed number of food-log days missing in the week: not adapted. */
    SKIPPED_MISSING_LOGS,
    /** Too few weigh-ins in the window for a regression: not adapted. */
    SKIPPED_NO_WEIGHT_TREND,
    /** Weight gap of ≥ 7 days: maintenance frozen, target held, check-in "not enough data". */
    FROZEN_WEIGHT_GAP,
}

/** What the week contributed; kept so the weekly check-in can explain "what was adapted". */
data class MaintenanceUpdate(
    val weekEnd: LocalDate,
    val prior: MaintenanceEstimate,
    val posterior: MaintenanceEstimate,
    val outcome: MaintenanceOutcome,
    /** intake − balance for the window, if computed. */
    val observedKcal: Double? = null,
    /** Share of the gap (observed − prior) that was applied, 0..1. */
    val gain: Double? = null,
    val loggedDaysInWeek: Int = 0,
    val longestWeightGapDays: Int = 0,
) {
    val adapted: Boolean get() = outcome == MaintenanceOutcome.UPDATED || outcome == MaintenanceOutcome.UPDATED_CAUTIOUSLY
}

/**
 * PRD §6.3. One Kalman-style update per week:
 *
 * - observation = average logged intake (logged days only) − balance from weight, over the rolling window
 *   clipped to the phase;
 * - observation noise = regression uncertainty + intake scatter / n + a logging-bias floor, inflated during
 *   bootstrap (< 21 days in the phase) and during 2–6 day weight gaps;
 * - process noise lets true maintenance drift between weeks.
 *
 * Intake is a map of **logged days only**. A day that is absent was not logged and is never treated as 0 kcal.
 */
object MaintenanceModel {

    fun initial(startingMaintenanceKcal: Double, defaults: ModelDefaults = ModelDefaults()) =
        MaintenanceEstimate(startingMaintenanceKcal, defaults.startingMaintenanceSd * defaults.startingMaintenanceSd)

    fun weeklyUpdate(
        prior: MaintenanceEstimate,
        intakeByDay: Map<LocalDate, Double>,
        daily: List<DailyWeight>,
        weekEnd: LocalDate,
        phaseStart: LocalDate,
        defaults: ModelDefaults = ModelDefaults(),
    ): MaintenanceUpdate {
        val weekStart = maxOf(phaseStart, weekEnd.minusDays(6))
        val weekDays = ChronoUnit.DAYS.between(weekStart, weekEnd).toInt() + 1
        val loggedInWeek = intakeByDay.keys.count { !it.isBefore(weekStart) && !it.isAfter(weekEnd) }
        val gap = longestWeightGap(daily.map { it.date }.filter { !it.isBefore(phaseStart) }, weekStart, weekEnd)
        val q = defaults.maintenanceDriftSdPerWeek * defaults.maintenanceDriftSdPerWeek

        fun result(outcome: MaintenanceOutcome, posterior: MaintenanceEstimate, obs: Double? = null, k: Double? = null) =
            MaintenanceUpdate(weekEnd, prior, posterior, outcome, obs, k, loggedInWeek, gap)

        if (gap >= defaults.freezeWeightGapDays) {
            return result(MaintenanceOutcome.FROZEN_WEIGHT_GAP, prior)
        }
        // Not adapting still lets uncertainty grow: we know less about this week.
        val predicted = MaintenanceEstimate(prior.kcal, prior.variance + q)
        if (weekDays - loggedInWeek > defaults.maxMissingLogDaysPerWeek) {
            return result(MaintenanceOutcome.SKIPPED_MISSING_LOGS, predicted)
        }

        val windowStart = maxOf(phaseStart, weekEnd.minusDays(defaults.maintenanceWindowDays - 1L))
        val intake = intakeByDay.filterKeys { !it.isBefore(windowStart) && !it.isAfter(weekEnd) }.values
        val slope = WeightRegression.fit(
            daily, weekEnd, defaults.maintenanceWindowDays, defaults.minWeighInsForRegression, from = windowStart,
        ) ?: return result(MaintenanceOutcome.SKIPPED_NO_WEIGHT_TREND, predicted)

        val n = intake.size
        val meanIntake = intake.average()
        val intakeVar = if (n > 1) intake.sumOf { (it - meanIntake) * (it - meanIntake) } / (n - 1) else 0.0
        val balance = slope.kgPerDay * defaults.kcalPerKg
        val observed = meanIntake - balance

        val balanceSe = slope.slopeSe * defaults.kcalPerKg
        var r = balanceSe * balanceSe + intakeVar / n + defaults.loggingNoiseFloorSd * defaults.loggingNoiseFloorSd
        val phaseDays = ChronoUnit.DAYS.between(phaseStart, weekEnd).toInt() + 1
        if (phaseDays < defaults.bootstrapDays) r *= defaults.bootstrapDays.toDouble() / phaseDays
        val cautious = gap >= defaults.cautiousWeightGapDays
        if (cautious) r *= defaults.cautiousNoiseFactor

        val k = predicted.variance / (predicted.variance + r)
        val posterior = MaintenanceEstimate(
            kcal = predicted.kcal + k * (observed - predicted.kcal),
            variance = (1 - k) * predicted.variance,
        )
        return result(
            if (cautious) MaintenanceOutcome.UPDATED_CAUTIOUSLY else MaintenanceOutcome.UPDATED,
            posterior, observed, k,
        )
    }

    /**
     * Longest run of consecutive days without a weigh-in that touches [from]..[to]. A run that started before
     * [from] counts in full (a gap that began last week is still a gap). Returns the full span if no weigh-in exists.
     */
    fun longestWeightGap(weighInDates: List<LocalDate>, from: LocalDate, to: LocalDate): Int {
        val dates = weighInDates.filter { !it.isAfter(to) }.distinct().sorted()
        if (dates.isEmpty()) return ChronoUnit.DAYS.between(from, to).toInt() + 1
        var longest = 0
        // Gaps between consecutive weigh-ins, plus the open gap from the last weigh-in to `to`.
        val bounds = dates + to.plusDays(1)
        for (i in 0 until bounds.size - 1) {
            val a = bounds[i]
            val b = bounds[i + 1]
            if (b.isBefore(from)) continue
            longest = maxOf(longest, ChronoUnit.DAYS.between(a, b).toInt() - 1)
        }
        // Days in the window before the first weigh-in ever.
        if (dates.first().isAfter(from)) longest = maxOf(longest, ChronoUnit.DAYS.between(from, dates.first()).toInt())
        return longest
    }
}

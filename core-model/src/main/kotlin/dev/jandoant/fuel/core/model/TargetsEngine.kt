package dev.jandoant.fuel.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Inputs that come from Settings and the current tracking phase. */
data class ModelSettings(
    val phaseStart: LocalDate,
    val startingMaintenanceKcal: Double,
    val goal: GoalRange,
    val defaults: ModelDefaults = ModelDefaults(),
)

/** What the model knows on one day (feeds the daily ModelSnapshot in Room). */
data class DaySnapshot(
    val date: LocalDate,
    val trendKg: Double?,
    val referenceKg: Double?,
    val maintenance: MaintenanceEstimate,
    /** Null until the first weigh-in of the phase. */
    val zone: CalorieZone?,
    val macroTargets: MacroTargets?,
    /** True when the target is held because of a ≥ 7 day weight gap. */
    val targetHeld: Boolean,
)

data class ModelRun(
    val days: List<DaySnapshot>,
    val weeklyUpdates: List<MaintenanceUpdate>,
    val trend: List<TrendPoint>,
    val balance: BalanceFromWeight?,
    val rate: RateCheck?,
) {
    val today: DaySnapshot get() = days.last()
}

/**
 * Replays the model day by day from the phase start, so the result is a pure function of the stored data
 * (weigh-ins, logged intake, settings). Maintenance updates once per 7 days of the phase; the zone is
 * recomputed every day with the ±50 kcal/day cap against the previous day's target.
 *
 * Only data inside the current phase is used.
 */
object TargetsEngine {

    fun run(
        readings: List<WeightReading>,
        intakeByDay: Map<LocalDate, Double>,
        settings: ModelSettings,
        today: LocalDate,
    ): ModelRun {
        require(!today.isBefore(settings.phaseStart)) { "today is before the phase start" }
        val d = settings.defaults
        val start = settings.phaseStart
        val daily = TrendWeight.dailyWeights(readings)
            .filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        val trend = TrendWeight.smooth(daily, d.trendAlphaPerDay)
        val intake = intakeByDay.filterKeys { !it.isBefore(start) && !it.isAfter(today) }
        val weighInDates = daily.map { it.date }

        var maintenance = MaintenanceModel.initial(settings.startingMaintenanceKcal, d)
        val updates = mutableListOf<MaintenanceUpdate>()
        val days = mutableListOf<DaySnapshot>()
        var previousTarget: Double? = null
        var previousZone: CalorieZone? = null

        var day = start
        while (!day.isAfter(today)) {
            val phaseDay = ChronoUnit.DAYS.between(start, day).toInt() + 1
            if (phaseDay % 7 == 0) {
                val u = MaintenanceModel.weeklyUpdate(maintenance, intake, daily, day, start, d)
                updates += u
                maintenance = u.posterior
            }

            val trendKg = TrendWeight.trendOn(trend, day)
            val lastWeighIn = weighInDates.lastOrNull { !it.isAfter(day) }
            val held = lastWeighIn != null &&
                ChronoUnit.DAYS.between(lastWeighIn, day) >= d.freezeWeightGapDays &&
                previousZone != null

            val zone = when {
                held -> previousZone
                trendKg == null -> null
                else -> CalorieZoneModel.applyLimits(
                    CalorieZoneModel.rawZone(maintenance.kcal, trendKg, settings.goal, d.kcalPerKg),
                    previousTarget, d,
                )
            }
            val refKg = TrendWeight.referenceWeight(trend, day, d.macros.referenceWindowDays)
            days += DaySnapshot(
                date = day,
                trendKg = trendKg,
                referenceKg = refKg,
                maintenance = maintenance,
                zone = zone,
                macroTargets = refKg?.let { MacroTargetModel.targets(it, d.macros) },
                targetHeld = held,
            )
            if (zone != null) {
                previousTarget = zone.target
                previousZone = zone
            }
            day = day.plusDays(1)
        }

        val balance = WeightRegression.balance(daily, today, d, from = start)
        val trendToday = TrendWeight.trendOn(trend, today)
        val rate = if (balance != null && trendToday != null) {
            GoalRate.check(GoalRate.actualPctPerWeek(balance.slope, trendToday), settings.goal, d)
        } else null
        return ModelRun(days, updates, trend, balance, rate)
    }
}

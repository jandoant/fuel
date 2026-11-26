package dev.jandoant.fuel.core.model

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** One day of the check-in week. Days without a food log have [eaten] = null and are excluded from averages. */
data class CheckInDay(
    val date: LocalDate,
    val planned: MacroNumbers?,
    val eaten: MacroNumbers?,
    val zone: CalorieZone?,
    val targets: MacroTargets?,
)

enum class CheckInNutrient { CALORIES, PROTEIN, FAT, CARBS, FIBER }

/** One row of the "Plan or execution?" chart. */
data class NutrientWeek(
    val nutrient: CheckInNutrient,
    /** Daily eaten values of logged days (dots). */
    val dailyEaten: List<Double>,
    val actualAvg: Double?,
    val plannedAvg: Double?,
    /** Average target band over the week; carbs use each day's dynamic zone. */
    val bandLow: Double?,
    val bandHigh: Double?,
    val daysInRange: Int,
    val loggedDays: Int,
)

enum class Diagnosis {
    /** Fewer than 4 weigh-ins or 5 logged days, or a weight gap froze the model. */
    NOT_ENOUGH_DATA,
    /** Ate on target and the rate is on target: no change needed. */
    ON_TRACK,
    /** Ate on target but the rate is off: the maintenance estimate was wrong and gets corrected. */
    ESTIMATE_WRONG,
    /** Ate off target: execution, not the estimate, explains the rate. */
    EXECUTION,
}

data class Forecast(val weekEnd: LocalDate, val expectedTrendChangeKg: Double, val actualTrendChangeKg: Double?) {
    /** Hit = within ±0.1 kg (PRD §6.7). Null while the outcome is unknown. */
    val hit: Boolean? get() = actualTrendChangeKg?.let { abs(it - expectedTrendChangeKg) <= CheckInModel.FORECAST_TOLERANCE_KG }
}

data class CompositionNote(val fatMassChangeKg: Double, val leanMassChangeKg: Double, val weeks: Int)

data class CheckInResult(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val loggedDays: Int,
    val weighIns: Int,
    val rate: RateCheck?,
    val trendChangeKg: Double?,
    /** Weeks until [goalWeightKg] at the current rate, if it moves towards it. */
    val weeksToGoal: Double?,
    val goalWeightKg: Double?,
    val ateOnTarget: Boolean?,
    val nutrients: List<NutrientWeek>,
    val diagnosis: Diagnosis,
    val maintenanceBefore: Double,
    val maintenanceAfter: Double,
    val targetBefore: Double?,
    val targetAfter: Double?,
    val update: MaintenanceUpdate?,
    val explanation: String,
    val composition: CompositionNote?,
)

/** PRD §6.7. Pure: everything it needs is passed in. */
object CheckInModel {
    const val MIN_WEIGH_INS = 4
    const val MIN_LOGGED_DAYS = 5
    const val FORECAST_TOLERANCE_KG = 0.1

    fun evaluate(
        days: List<CheckInDay>,
        weighInsInWeek: Int,
        rate: RateCheck?,
        trendStartKg: Double?,
        trendEndKg: Double?,
        goal: GoalRange,
        goalWeightKg: Double?,
        update: MaintenanceUpdate?,
        targetBefore: Double?,
        targetAfter: Double?,
        composition: CompositionNote?,
    ): CheckInResult {
        val sorted = days.sortedBy { it.date }
        val logged = sorted.filter { it.eaten != null }
        val nutrients = CheckInNutrient.entries.map { nutrientWeek(it, sorted) }
        val cal = nutrients.first { it.nutrient == CheckInNutrient.CALORIES }
        val ateOnTarget = if (cal.actualAvg == null || cal.bandLow == null || cal.bandHigh == null) null
        else cal.actualAvg >= cal.bandLow && cal.actualAvg <= cal.bandHigh

        val enough = weighInsInWeek >= MIN_WEIGH_INS && logged.size >= MIN_LOGGED_DAYS &&
            update?.outcome != MaintenanceOutcome.FROZEN_WEIGHT_GAP && rate != null
        val diagnosis = when {
            !enough || ateOnTarget == null -> Diagnosis.NOT_ENOUGH_DATA
            !ateOnTarget -> Diagnosis.EXECUTION
            rate!!.status == RateStatus.ON_TRACK -> Diagnosis.ON_TRACK
            else -> Diagnosis.ESTIMATE_WRONG
        }
        val trendChange = if (trendStartKg != null && trendEndKg != null) trendEndKg - trendStartKg else null
        val weeksToGoal = weeksTo(goalWeightKg, trendEndKg, rate)
        val before = update?.prior?.kcal ?: 0.0
        val after = update?.posterior?.kcal ?: before
        return CheckInResult(
            weekStart = sorted.first().date,
            weekEnd = sorted.last().date,
            loggedDays = logged.size,
            weighIns = weighInsInWeek,
            rate = rate,
            trendChangeKg = trendChange,
            weeksToGoal = weeksToGoal,
            goalWeightKg = goalWeightKg,
            ateOnTarget = ateOnTarget,
            nutrients = nutrients,
            diagnosis = diagnosis,
            maintenanceBefore = before,
            maintenanceAfter = after,
            targetBefore = targetBefore,
            targetAfter = targetAfter,
            update = update,
            explanation = explain(diagnosis, update, logged.size, weighInsInWeek),
            composition = composition,
        )
    }

    private fun value(n: CheckInNutrient, m: MacroNumbers): Double = when (n) {
        CheckInNutrient.CALORIES -> m.kcal.toDouble()
        CheckInNutrient.PROTEIN -> m.protein.toDouble()
        CheckInNutrient.FAT -> m.fat.toDouble()
        CheckInNutrient.CARBS -> m.carbs.toDouble()
        CheckInNutrient.FIBER -> m.fiber.toDouble()
    }

    /** Band for one day; carbs use that day's planned protein/fat (PRD §6.5). Max is null for fiber. */
    private fun band(n: CheckInNutrient, d: CheckInDay): Pair<Double, Double?>? = when (n) {
        CheckInNutrient.CALORIES -> d.zone?.let { it.low to it.high }
        CheckInNutrient.PROTEIN -> d.targets?.protein?.let { it.min.toDouble() to it.max?.toDouble() }
        CheckInNutrient.FAT -> d.targets?.fat?.let { it.min.toDouble() to it.max?.toDouble() }
        CheckInNutrient.FIBER -> d.targets?.fiber?.let { it.min.toDouble() to null }
        CheckInNutrient.CARBS -> {
            val basis = d.planned ?: d.eaten
            if (d.zone == null || basis == null) null
            else MacroTargetModel.carbZone(d.zone, basis.protein.toDouble(), basis.fat.toDouble())
                ?.let { it.min.toDouble() to it.max?.toDouble() }
        }
    }

    fun nutrientWeek(n: CheckInNutrient, days: List<CheckInDay>): NutrientWeek {
        val logged = days.filter { it.eaten != null }
        val eaten = logged.map { value(n, it.eaten!!) }
        val planned = days.mapNotNull { it.planned }.filter { it.kcal > 0 }.map { value(n, it) }
        val bands = days.mapNotNull { band(n, it) }
        val inRange = logged.count { d ->
            val b = band(n, d) ?: return@count false
            val v = value(n, d.eaten!!)
            v >= b.first && (b.second == null || v <= b.second!!)
        }
        val highs = bands.mapNotNull { it.second }
        return NutrientWeek(
            nutrient = n,
            dailyEaten = eaten,
            actualAvg = eaten.takeIf { it.isNotEmpty() }?.average(),
            plannedAvg = planned.takeIf { it.isNotEmpty() }?.average(),
            bandLow = bands.takeIf { it.isNotEmpty() }?.map { it.first }?.average(),
            bandHigh = highs.takeIf { it.isNotEmpty() }?.average(),
            daysInRange = inRange,
            loggedDays = logged.size,
        )
    }

    private fun weeksTo(goalKg: Double?, trendKg: Double?, rate: RateCheck?): Double? {
        if (goalKg == null || trendKg == null || rate == null) return null
        val kgPerWeek = rate.actualPctPerWeek / 100.0 * trendKg
        val needed = goalKg - trendKg
        if (kgPerWeek == 0.0 || needed == 0.0 || (needed > 0) != (kgPerWeek > 0)) return null
        return needed / kgPerWeek
    }

    fun explain(d: Diagnosis, u: MaintenanceUpdate?, logged: Int, weighIns: Int): String {
        val applied = if (u?.gain != null && u.observedKcal != null) {
            val gap = (u.observedKcal - u.prior.kcal).roundToInt()
            "${(u.gain * 100).roundToInt()} % of the ~${abs(gap)} kcal/day gap applied"
        } else null
        return when (d) {
            Diagnosis.NOT_ENOUGH_DATA -> when (u?.outcome) {
                MaintenanceOutcome.FROZEN_WEIGHT_GAP -> "No weigh-in for 7 days or more: maintenance frozen and target held."
                MaintenanceOutcome.SKIPPED_MISSING_LOGS -> "Too many days without a food log ($logged logged): maintenance not adapted."
                else -> "Not enough data for a verdict ($logged/7 days logged, $weighIns weigh-ins; needs ≥5 and ≥4)."
            }
            Diagnosis.ON_TRACK -> "On target and on track" + (applied?.let { ": $it." } ?: ": no change needed.")
            Diagnosis.ESTIMATE_WRONG -> "Ate on target but the rate is off, so the estimate was off" +
                (applied?.let { ": $it." } ?: ".")
            Diagnosis.EXECUTION -> "Intake was off target, which explains the rate. Maintenance stays based on what you " +
                "actually ate" + (applied?.let { " ($it)." } ?: ".")
        }
    }

    /**
     * Expected trend change for a week at the planned intake: (planned avg − maintenance) × 7 / 7700.
     */
    fun forecastKg(plannedAvgKcal: Double, maintenanceKcal: Double, defaults: ModelDefaults = ModelDefaults()): Double =
        (plannedAvgKcal - maintenanceKcal) * 7.0 / defaults.kcalPerKg

    /**
     * Composition note over ≥ 4 weeks: change of fat mass (weight × body fat %) and lean mass between the
     * trend at the start and the end. Low confidence by design; never used in calculations.
     */
    fun composition(
        fatPct: List<TrendPoint>,
        leanKg: List<TrendPoint>,
        weight: List<TrendPoint>,
        from: LocalDate,
        to: LocalDate,
    ): CompositionNote? {
        val weeks = ((to.toEpochDay() - from.toEpochDay()) / 7).toInt()
        if (weeks < 4) return null
        val w0 = TrendWeight.trendOn(weight, from) ?: return null
        val w1 = TrendWeight.trendOn(weight, to) ?: return null
        val f0 = TrendWeight.trendOn(fatPct, from) ?: return null
        val f1 = TrendWeight.trendOn(fatPct, to) ?: return null
        val fatChange = w1 * f1 / 100 - w0 * f0 / 100
        val l0 = TrendWeight.trendOn(leanKg, from)
        val l1 = TrendWeight.trendOn(leanKg, to)
        val leanChange = if (l0 != null && l1 != null) l1 - l0 else (w1 - w0) - fatChange
        return CompositionNote(fatChange, leanChange, weeks)
    }
}

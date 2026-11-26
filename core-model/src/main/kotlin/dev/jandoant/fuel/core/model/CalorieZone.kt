package dev.jandoant.fuel.core.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Goal as a range of body-weight change in **% per week**, signed (negative = lose).
 * The order of the two ends does not matter.
 */
data class GoalRange(val pctPerWeekA: Double, val pctPerWeekB: Double) {
    val lowPct: Double get() = minOf(pctPerWeekA, pctPerWeekB)
    val highPct: Double get() = maxOf(pctPerWeekA, pctPerWeekB)
    val isMaintain: Boolean get() = lowPct == 0.0 && highPct == 0.0

    companion object {
        val MAINTAIN = GoalRange(0.0, 0.0)
        val LOSE_0_25_TO_0_5 = GoalRange(-0.25, -0.5)
        val LOSE_0_5_TO_1_0 = GoalRange(-0.5, -1.0)
        val GAIN_0_25_TO_0_5 = GoalRange(0.25, 0.5)
        val PRESETS = listOf(MAINTAIN, LOSE_0_25_TO_0_5, LOSE_0_5_TO_1_0, GAIN_0_25_TO_0_5)
    }
}

/** Calorie zone in kcal/day. Kept unrounded; use [display] for the numbers on screen. */
data class CalorieZone(val low: Double, val high: Double, val target: Double) {
    fun display(step: Int = ModelDefaults().calorieDisplayStep) =
        DisplayZone(roundTo(low, step), roundTo(high, step), roundTo(target, step))
}

data class DisplayZone(val low: Int, val high: Int, val target: Int)

internal fun roundTo(v: Double, step: Int): Int = (v / step).roundToInt() * step

/** PRD §6.4. */
object CalorieZoneModel {

    /** kcal/day difference for a rate of [pctPerWeek] at [trendWeightKg]. */
    fun kcalDelta(pctPerWeek: Double, trendWeightKg: Double, kcalPerKg: Double = ModelDefaults().kcalPerKg): Double =
        pctPerWeek / 100.0 * trendWeightKg * kcalPerKg / 7.0

    /** Zone before the daily change cap and the floor. Target = midpoint. */
    fun rawZone(
        maintenanceKcal: Double,
        trendWeightKg: Double,
        goal: GoalRange,
        kcalPerKg: Double = ModelDefaults().kcalPerKg,
    ): CalorieZone {
        val low = maintenanceKcal + kcalDelta(goal.lowPct, trendWeightKg, kcalPerKg)
        val high = maintenanceKcal + kcalDelta(goal.highPct, trendWeightKg, kcalPerKg)
        return CalorieZone(low, high, (low + high) / 2)
    }

    /**
     * Applies the daily change cap relative to yesterday's target, then the calorie floor.
     * CHOICE: when the cap bites, the whole zone shifts with the target (its width is kept).
     * CHOICE: the floor lifts the target and both zone ends to at least the floor.
     */
    fun applyLimits(
        raw: CalorieZone,
        previousTarget: Double?,
        defaults: ModelDefaults = ModelDefaults(),
    ): CalorieZone {
        var z = raw
        if (previousTarget != null) {
            val max = defaults.maxDailyTargetChangeKcal
            val capped = raw.target.coerceIn(previousTarget - max, previousTarget + max)
            val shift = capped - raw.target
            z = CalorieZone(raw.low + shift, raw.high + shift, capped)
        }
        val floor = defaults.calorieFloorKcal
        return CalorieZone(maxOf(z.low, floor), maxOf(z.high, floor), maxOf(z.target, floor))
    }
}

enum class RateStatus {
    ON_TRACK, FASTER_THAN_GOAL, SLOWER_THAN_GOAL,
    /** Maintain goal only: drifting down / up beyond the tolerance. */
    LOSING, GAINING,
}

data class RateCheck(
    val actualPctPerWeek: Double,
    val status: RateStatus,
    /** True when losing faster than 1 %/week (PRD §6.4 warning). */
    val fastLossWarning: Boolean,
)

object GoalRate {

    /**
     * Actual rate in % of body weight per week, from the same 28-day weigh-in regression as "Balance from
     * weight" (so Energy and Body agree), relative to the current trend weight.
     */
    fun actualPctPerWeek(slope: WeightSlope, trendWeightKg: Double): Double =
        slope.kgPerDay * 7.0 / trendWeightKg * 100.0

    /**
     * "Faster" means further from zero in the goal's direction (more loss when losing, more gain when gaining).
     * The Energy prototype treated gain goals like loss goals; this version is direction-aware.
     */
    fun check(actualPctPerWeek: Double, goal: GoalRange, defaults: ModelDefaults = ModelDefaults()): RateCheck {
        val status = if (goal.isMaintain) {
            when {
                abs(actualPctPerWeek) <= defaults.maintainTolerancePctPerWeek -> RateStatus.ON_TRACK
                actualPctPerWeek < 0 -> RateStatus.LOSING
                else -> RateStatus.GAINING
            }
        } else {
            val losing = goal.highPct <= 0
            when {
                actualPctPerWeek in goal.lowPct..goal.highPct -> RateStatus.ON_TRACK
                losing && actualPctPerWeek < goal.lowPct -> RateStatus.FASTER_THAN_GOAL
                !losing && actualPctPerWeek > goal.highPct -> RateStatus.FASTER_THAN_GOAL
                else -> RateStatus.SLOWER_THAN_GOAL
            }
        }
        return RateCheck(actualPctPerWeek, status, actualPctPerWeek < -defaults.fastLossWarningPctPerWeek)
    }
}

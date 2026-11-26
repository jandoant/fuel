package dev.jandoant.fuel.core.model

/**
 * PRD §6.6 statuses. "planned" is always the **whole-day plan including eaten entries** (eaten ⊂ plan).
 * Inputs are the rounded numbers shown on screen, so the status never contradicts the displayed values.
 * Precedence (as in the Today prototype): eaten over > short > over > fits.
 */
sealed interface NutrientStatus {
    /** Planned total inside the zone. For calories, [vsTarget] > 0 means "X spare", < 0 "X above target". */
    data class Fits(val vsTarget: Int? = null) : NutrientStatus
    data class Short(val amount: Int) : NutrientStatus
    data class Over(val amount: Int) : NutrientStatus
    /** Eaten alone already exceeds the maximum: red + warning icon. */
    data class EatenOver(val amount: Int) : NutrientStatus
    /** No zone to compare against (carbs with no protein/fat planned). */
    data object NoTarget : NutrientStatus
}

enum class StatusTone { OK, WARNING, DANGER, NONE }

val NutrientStatus.tone: StatusTone
    get() = when (this) {
        is NutrientStatus.Fits -> StatusTone.OK
        is NutrientStatus.Short, is NutrientStatus.Over -> StatusTone.WARNING
        is NutrientStatus.EatenOver -> StatusTone.DANGER
        NutrientStatus.NoTarget -> StatusTone.NONE
    }

/** The big calorie number on Today: "N left" or, once eaten passes the zone's top, "Over goal by N". */
data class CalorieHeadline(val value: Int, val overGoal: Boolean)

object Statuses {

    /** Protein, fat, carbs (zone may be null) and fiber (max = null). */
    fun nutrient(eaten: Int, planned: Int, range: GramRange?): NutrientStatus {
        if (range == null) return NutrientStatus.NoTarget
        val max = range.max
        return when {
            max != null && eaten > max -> NutrientStatus.EatenOver(eaten - max)
            planned < range.min -> NutrientStatus.Short(range.min - planned)
            max != null && planned > max -> NutrientStatus.Over(planned - max)
            else -> NutrientStatus.Fits()
        }
    }

    fun calories(eaten: Int, planned: Int, zone: DisplayZone): NutrientStatus = when {
        eaten > zone.high -> NutrientStatus.EatenOver(eaten - zone.high)
        planned < zone.low -> NutrientStatus.Short(zone.low - planned)
        planned > zone.high -> NutrientStatus.Over(planned - zone.high)
        else -> NutrientStatus.Fits(vsTarget = zone.target - planned)
    }

    /**
     * "N left" = target − eaten, never below 0 (between target and the zone's top it shows "0 left" and the
     * status line says "X above target"). "Over goal by" counts from the zone's top.
     */
    fun calorieHeadline(eaten: Int, zone: DisplayZone): CalorieHeadline =
        if (eaten > zone.high) CalorieHeadline(eaten - zone.high, overGoal = true)
        else CalorieHeadline(maxOf(0, zone.target - eaten), overGoal = false)
}

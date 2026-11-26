package dev.jandoant.fuel.core.model

/** One plan entry as the summary sees it: its rounded numbers and whether it was ticked. */
data class PlannedItem(val numbers: MacroNumbers, val eaten: Boolean)

/** One nutrient on Today / Plan: eaten, the whole-day plan (eaten ⊂ plan) and the goal. */
data class NutrientLine(
    val eaten: Int,
    val planned: Int,
    val range: GramRange?,
    val status: NutrientStatus,
) {
    /** "+N planned" = planned but not yet ticked. */
    val stillPlanned: Int get() = planned - eaten
}

data class CalorieLine(
    val eaten: Int,
    val planned: Int,
    /** Null until the model has a zone (first weigh-in of the phase). */
    val zone: DisplayZone?,
    val status: NutrientStatus,
    val headline: CalorieHeadline?,
) {
    val stillPlanned: Int get() = planned - eaten
}

data class DaySummary(
    val calories: CalorieLine,
    val protein: NutrientLine,
    val fat: NutrientLine,
    val carbs: NutrientLine,
    val fiber: NutrientLine,
) {
    /** In the fixed order P · F · C · Fib. */
    val macros: List<NutrientLine> get() = listOf(protein, fat, carbs, fiber)
    val isEmpty: Boolean get() = calories.planned == 0
}

/** PRD §5.2, §6.5, §6.6: turns a day's entries and targets into the numbers and statuses on screen. */
object DaySummaries {

    fun summarize(items: List<PlannedItem>, zone: CalorieZone?, targets: MacroTargets?): DaySummary {
        val all = MacroNumbers.sum(items.map { it.numbers })
        val eaten = MacroNumbers.sum(items.filter { it.eaten }.map { it.numbers })
        return summarize(eaten, all, zone, targets)
    }

    fun summarize(eaten: MacroNumbers, planned: MacroNumbers, zone: CalorieZone?, targets: MacroTargets?): DaySummary {
        val dz = zone?.display()
        val calStatus = if (dz == null) NutrientStatus.NoTarget else Statuses.calories(eaten.kcal, planned.kcal, dz)
        val carbRange = if (zone == null) null
        else MacroTargetModel.carbZone(zone, planned.protein.toDouble(), planned.fat.toDouble())

        fun line(e: Int, p: Int, r: GramRange?) = NutrientLine(e, p, r, Statuses.nutrient(e, p, r))
        return DaySummary(
            calories = CalorieLine(eaten.kcal, planned.kcal, dz, calStatus, dz?.let { Statuses.calorieHeadline(eaten.kcal, it) }),
            protein = line(eaten.protein, planned.protein, targets?.protein),
            fat = line(eaten.fat, planned.fat, targets?.fat),
            carbs = line(eaten.carbs, planned.carbs, carbRange),
            fiber = line(eaten.fiber, planned.fiber, targets?.fiber),
        )
    }

    /**
     * "Effect on today's plan" in the add sheet (PRD §7.2): the day before and after adding [added].
     * The carb zone is recomputed from the new planned protein and fat.
     */
    fun effect(items: List<PlannedItem>, added: MacroNumbers, zone: CalorieZone?, targets: MacroTargets?): Pair<DaySummary, DaySummary> {
        val before = summarize(items, zone, targets)
        val after = summarize(items + PlannedItem(added, eaten = false), zone, targets)
        return before to after
    }

    /**
     * Fill indicator of a day in the plan week strip and calendar: planned kcal relative to the target,
     * green when the plan fits the zone.
     */
    fun planFits(summary: DaySummary): Boolean = summary.calories.status is NutrientStatus.Fits
}

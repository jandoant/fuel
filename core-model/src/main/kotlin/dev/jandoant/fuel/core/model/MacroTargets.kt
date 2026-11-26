package dev.jandoant.fuel.core.model

import kotlin.math.roundToInt

/** A gram range; [max] is null for a minimum-only target (fiber). */
data class GramRange(val min: Int, val max: Int?) {
    init {
        require(max == null || max >= min) { "max must be ≥ min" }
    }
}

data class MacroTargets(val protein: GramRange, val fat: GramRange, val fiber: GramRange)

/** PRD §6.5. */
object MacroTargetModel {

    /** Protein and fat from g/kg of the reference (trend) weight; fiber is a fixed minimum. */
    fun targets(referenceWeightKg: Double, settings: MacroSettings = MacroSettings()): MacroTargets =
        MacroTargets(
            protein = gramsFor(settings.proteinGPerKg, referenceWeightKg),
            fat = gramsFor(settings.fatGPerKg, referenceWeightKg),
            fiber = GramRange(settings.fiberMinG, null),
        )

    private fun gramsFor(gPerKg: ClosedFloatingPointRange<Double>, kg: Double) =
        GramRange((gPerKg.start * kg).roundToInt(), (gPerKg.endInclusive * kg).roundToInt())

    /**
     * Carbs have no target of their own: they fill what the day's **planned** protein and fat leave of the
     * calorie zone. `carbs = (zone − P×4 − F×9) / 4` for both ends.
     *
     * Returns null (hide the carb zone) when no protein and no fat is planned.
     * CHOICE: ends below 0 g are clamped to 0.
     */
    fun carbZone(zone: CalorieZone, plannedProteinG: Double, plannedFatG: Double): GramRange? {
        if (plannedProteinG <= 0.0 && plannedFatG <= 0.0) return null
        val used = plannedProteinG * 4 + plannedFatG * 9
        val low = ((zone.low - used) / 4).roundToInt().coerceAtLeast(0)
        val high = ((zone.high - used) / 4).roundToInt().coerceAtLeast(0)
        return GramRange(low, high)
    }

    /**
     * Indicative carb range for screens without a plan (the Targets prototype shows one): the low end assumes
     * protein and fat at their maxima, the high end at their minima.
     */
    fun indicativeCarbRange(zoneTargetKcal: Double, targets: MacroTargets): GramRange {
        val pMax = targets.protein.max ?: targets.protein.min
        val fMax = targets.fat.max ?: targets.fat.min
        val low = ((zoneTargetKcal - pMax * 4 - fMax * 9) / 4).roundToInt().coerceAtLeast(0)
        val high = ((zoneTargetKcal - targets.protein.min * 4 - targets.fat.min * 9) / 4).roundToInt().coerceAtLeast(0)
        return GramRange(low, high)
    }
}

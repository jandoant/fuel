package dev.jandoant.fuel.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

enum class UnitSource { BUILT_IN, OPEN_FOOD_FACTS, USER }

/**
 * A serving unit of one food (PRD §5.1, §7.3). Grams always exist ([isGrams]); its name and weight are locked,
 * step and range are editable. Volume units (ml) count as grams.
 */
data class ServingUnit(
    val id: Long,
    val name: String,
    val grams: Double,
    val step: Double,
    val min: Double,
    val max: Double?,
    val isDefault: Boolean = false,
    val source: UnitSource = UnitSource.USER,
) {
    /** The plain base unit ("g" or "ml" with 1 g per unit): shown as an integer, no "= x g" hint. */
    val isGrams: Boolean get() = grams == 1.0 && (name == "g" || name == "ml")

    /** Name without a "(150 g)" suffix, used in "1.5 cup". */
    val shortName: String get() = name.substringBefore(" (").trim()

    companion object {
        fun grams(id: Long = 0, step: Double = 10.0, min: Double = 1.0, max: Double? = 2000.0, isDefault: Boolean = false) =
            ServingUnit(id, "g", 1.0, step, min, max, isDefault, UnitSource.BUILT_IN)
    }
}

/** Amount maths for the stepper (PRD §7.13 "Amount input"). All functions are pure. */
object Amounts {

    private const val EPS = 1e-9

    fun grams(amount: Double, unit: ServingUnit): Double = amount * unit.grams

    fun clamp(amount: Double, unit: ServingUnit): Double {
        val lo = unit.min
        val hi = unit.max ?: Double.MAX_VALUE
        return amount.coerceIn(lo, maxOf(lo, hi))
    }

    fun canDecrement(amount: Double, unit: ServingUnit) = amount - unit.step >= unit.min - EPS
    fun canIncrement(amount: Double, unit: ServingUnit) = unit.max == null || amount + unit.step <= unit.max + EPS

    /** −: one step down, never below the minimum (stays put if that would undershoot). */
    fun decrement(amount: Double, unit: ServingUnit): Double =
        if (canDecrement(amount, unit)) round2(amount - unit.step) else amount

    /** +: one step up, never above the maximum. */
    fun increment(amount: Double, unit: ServingUnit): Double =
        if (canIncrement(amount, unit)) round2(amount + unit.step) else amount

    /**
     * Switching units keeps the grams as close as possible: the new amount is rounded to the new unit's step and
     * clamped to its range (same rule as the prototypes).
     */
    fun convert(amount: Double, from: ServingUnit, to: ServingUnit): Double {
        val g = grams(amount, from)
        val steps = (g / to.grams / to.step).roundToLong()
        return round2(clamp(steps * to.step, to))
    }

    /** Parses typed text; accepts "1,5" and "1.5". Returns null for anything that is not a positive number. */
    fun parse(text: String): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (v.isFinite() && v >= 0) v else null
    }

    /**
     * Typed value accepted while editing: only if it parses and lies inside the range. Otherwise the raw text is
     * kept on screen and the last valid amount stays in effect; on blur the field shows the valid amount again.
     */
    fun acceptTyped(text: String, unit: ServingUnit): Double? {
        val v = parse(text) ?: return null
        val max = unit.max ?: Double.MAX_VALUE
        return if (v < unit.min - EPS || v > max + EPS) null else v
    }

    /** Number shown in the stepper: integers for grams, otherwise up to 2 decimals without trailing zeros. */
    fun format(amount: Double, unit: ServingUnit): String =
        if (unit.isGrams) amount.roundToLong().toString() else formatNumber(amount)

    /** "150 g", "1.5 cup", "1 piece". */
    fun label(amount: Double, unit: ServingUnit): String =
        if (unit.isGrams) "${amount.roundToLong()} ${unit.name}" else "${formatNumber(amount)} ${unit.shortName}"

    /** "= 150 g" next to the stepper, empty for the gram unit itself. */
    fun gramsHint(amount: Double, unit: ServingUnit): String {
        if (unit.isGrams) return ""
        val suffix = if (unit.name.contains("ml")) "ml" else "g"
        return "= ${grams(amount, unit).roundToLong()} $suffix"
    }

    fun formatNumber(v: Double): String {
        if (abs(v - v.roundToLong()) < EPS) return v.roundToLong().toString()
        return BigDecimal(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    fun round2(v: Double): Double = BigDecimal(v).setScale(2, RoundingMode.HALF_UP).toDouble()

    /** Default amount for a newly added food: 1 of a non-gram default unit, 100 g otherwise (clamped). */
    fun defaultAmount(unit: ServingUnit): Double = clamp(if (unit.isGrams) 100.0 else 1.0, unit)

    /**
     * Sensible stepper settings for a unit of [grams] g: integer-ish units step by 0.5, the gram unit by 10.
     */
    fun suggestedStep(grams: Double): Double = when {
        grams <= 1.0 -> 10.0
        grams < 20 -> 1.0
        else -> 0.5
    }
}

package dev.jandoant.fuel.core.model

import kotlin.math.roundToInt

enum class NutrientUnit(val symbol: String) { KCAL("kcal"), G("g"), MG("mg"), UG("µg") }

/** Every nutrient the app knows, in display order. Values are stored per 100 g in [unit]. */
enum class Nutrient(val label: String, val unit: NutrientUnit) {
    ENERGY("Energy", NutrientUnit.KCAL),
    PROTEIN("Protein", NutrientUnit.G),
    FAT("Fat", NutrientUnit.G),
    SAT_FAT("of which saturated", NutrientUnit.G),
    CARBS("Carbohydrates", NutrientUnit.G),
    SUGAR("of which sugars", NutrientUnit.G),
    FIBER("Fibre", NutrientUnit.G),
    SALT("Salt", NutrientUnit.G),
    POTASSIUM("Potassium", NutrientUnit.MG),
    CALCIUM("Calcium", NutrientUnit.MG),
    MAGNESIUM("Magnesium", NutrientUnit.MG),
    IRON("Iron", NutrientUnit.MG),
    ZINC("Zinc", NutrientUnit.MG),
    VITAMIN_C("Vitamin C", NutrientUnit.MG),
    VITAMIN_D("Vitamin D", NutrientUnit.UG),
    VITAMIN_B12("Vitamin B12", NutrientUnit.UG),
    FOLATE("Folate", NutrientUnit.UG);

    companion object {
        /** Values a food should have; a missing one is flagged ("Fibre missing — fill in"). */
        val LABEL = listOf(ENERGY, PROTEIN, FAT, SAT_FAT, CARBS, SUGAR, FIBER, SALT)
        val MICROS = listOf(POTASSIUM, CALCIUM, MAGNESIUM, IRON, ZINC, VITAMIN_C, VITAMIN_D, VITAMIN_B12, FOLATE)
        fun fromKey(key: String): Nutrient? = entries.firstOrNull { it.name == key }
    }
}

/**
 * A set of nutrient amounts. An absent key means "unknown" (not 0) — that matters for missing-value hints and
 * micronutrient coverage. Arithmetic treats unknown as 0.
 */
data class Nutrients(val values: Map<Nutrient, Double> = emptyMap()) {
    operator fun get(n: Nutrient): Double = values[n] ?: 0.0
    fun has(n: Nutrient): Boolean = values.containsKey(n)

    val kcal: Double get() = get(Nutrient.ENERGY)
    val protein: Double get() = get(Nutrient.PROTEIN)
    val fat: Double get() = get(Nutrient.FAT)
    val carbs: Double get() = get(Nutrient.CARBS)
    val fiber: Double get() = get(Nutrient.FIBER)

    operator fun plus(o: Nutrients): Nutrients {
        val keys = values.keys + o.values.keys
        return Nutrients(keys.associateWith { get(it) + o.get(it) })
    }

    operator fun times(f: Double): Nutrients = Nutrients(values.mapValues { it.value * f })

    /** Local overrides win over the source values. */
    fun overriddenBy(overrides: Nutrients): Nutrients = Nutrients(values + overrides.values)

    fun missing(required: List<Nutrient> = Nutrient.LABEL): List<Nutrient> = required.filterNot { has(it) }

    /** The five numbers shown everywhere (kcal, P, F, C, Fib), rounded. */
    fun macros(): MacroNumbers = MacroNumbers(kcal.roundToInt(), protein.roundToInt(), fat.roundToInt(), carbs.roundToInt(), fiber.roundToInt())

    companion object {
        val ZERO = Nutrients()
        fun of(vararg pairs: Pair<Nutrient, Double>) = Nutrients(mapOf(*pairs))

        /** Nutrients of [grams] of a food with [per100g]. */
        fun forGrams(per100g: Nutrients, grams: Double): Nutrients = per100g * (grams / 100.0)

        fun sum(items: Iterable<Nutrients>): Nutrients = items.fold(ZERO) { a, b -> a + b }
    }
}

/** Rounded kcal + macros, the unit of display. Sums of rounded values keep lists and totals consistent. */
data class MacroNumbers(val kcal: Int, val protein: Int, val fat: Int, val carbs: Int, val fiber: Int) {
    operator fun plus(o: MacroNumbers) =
        MacroNumbers(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs, fiber + o.fiber)

    companion object {
        val ZERO = MacroNumbers(0, 0, 0, 0, 0)
        fun sum(items: Iterable<MacroNumbers>) = items.fold(ZERO) { a, b -> a + b }
    }
}

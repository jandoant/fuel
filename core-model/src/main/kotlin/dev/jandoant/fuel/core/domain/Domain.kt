package dev.jandoant.fuel.core.domain

import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.MacroNumbers
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.PlannedItem
import dev.jandoant.fuel.core.model.ServingUnit
import java.time.Instant
import java.time.LocalDate

/*
 * Plain domain models shared by the data layer and the UI. No Android types.
 */

enum class FoodSource(val label: String) { OPEN_FOOD_FACTS("Open Food Facts"), CUSTOM("Your food") }

data class Food(
    val id: Long,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val source: FoodSource,
    /** Values from the source (Open Food Facts or what the user typed when creating the food). */
    val sourcePer100g: Nutrients,
    /** Local fixes ("fill in"); never written back to Open Food Facts. */
    val overrides: Nutrients,
    val favourite: Boolean,
    val units: List<ServingUnit>,
    val lastUsed: Instant? = null,
) {
    val per100g: Nutrients get() = sourcePer100g.overriddenBy(overrides)
    val defaultUnit: ServingUnit get() = units.firstOrNull { it.isDefault } ?: units.firstOrNull { it.isGrams } ?: units.first()
    val gramUnit: ServingUnit get() = units.firstOrNull { it.isGrams } ?: units.first()
    val missing: List<Nutrient> get() = per100g.missing()
    fun unit(id: Long): ServingUnit = units.firstOrNull { it.id == id } ?: defaultUnit
    val displayName: String get() = if (brand.isNullOrBlank()) name else "$name ($brand)"
}

/** A food at an amount — used by template ingredients, plan-entry ingredients and the add sheet. */
data class Portion(
    val id: Long,
    val food: Food,
    val unit: ServingUnit,
    val amount: Double,
) {
    val grams: Double get() = Amounts.grams(amount, unit)
    val nutrients: Nutrients get() = Nutrients.forGrams(food.per100g, grams)
    val numbers: MacroNumbers get() = nutrients.macros()
    val label: String get() = Amounts.label(amount, unit)

    /** Snapshot used for the template "edited" comparison. */
    val key: PortionKey get() = PortionKey(food.id, unit.id, amount)
}

data class PortionKey(val foodId: Long, val unitId: Long, val amount: Double)

enum class RecipeTag(val label: String) { BREAKFAST("Breakfast"), MAIN("Main"), SNACK("Snack") }

data class RecipeTemplate(
    val id: Long,
    val name: String,
    val tags: Set<RecipeTag>,
    val favourite: Boolean,
    val ingredients: List<Portion>,
    /** Number of plan entries created from this template. */
    val usageCount: Int = 0,
) {
    val nutrients: Nutrients get() = Nutrients.sum(ingredients.map { it.nutrients })
    /** Entry numbers are the sum of rounded ingredient numbers, so ingredient rows add up visibly. */
    val numbers: MacroNumbers get() = nutrients.macros()
}

/**
 * A meal slot stored as data (PRD §5.1): four fixed slots plus one optional Extra slot that the user names and
 * positions. [order] is the display position.
 */
data class MealSlot(
    val id: String,
    val name: String,
    val order: Int,
    val enabled: Boolean,
    val isExtra: Boolean,
) {
    companion object {
        const val BREAKFAST = "breakfast"
        const val LUNCH = "lunch"
        const val DINNER = "dinner"
        const val SNACKS = "snacks"
        const val EXTRA = "extra"

        val DEFAULTS = listOf(
            MealSlot(BREAKFAST, "Breakfast", 0, true, false),
            MealSlot(LUNCH, "Lunch", 10, true, false),
            MealSlot(DINNER, "Dinner", 20, true, false),
            MealSlot(SNACKS, "Snacks", 30, true, false),
            MealSlot(EXTRA, "Pre-workout", 25, false, true),
        )

        /** Display order for the Extra slot placed after the fixed slot [afterSlotId] (null = first). */
        fun extraOrderAfter(afterSlotId: String?): Int = when (afterSlotId) {
            null -> -5
            BREAKFAST -> 5
            LUNCH -> 15
            DINNER -> 25
            else -> 35
        }
    }
}

enum class EntryKind { SINGLE, RECIPE }

data class PlanEntry(
    val id: Long,
    val date: LocalDate,
    val slotId: String,
    val kind: EntryKind,
    /** Recipe name (copied at planning time); single entries use the food name. */
    val name: String,
    /** Reference only — the entry owns its ingredients. */
    val templateId: Long?,
    val portions: List<Portion>,
    val eaten: Boolean,
    val sortOrder: Int,
    /** Template ingredients at planning time, for the "edited" chip. */
    val templateSnapshot: List<PortionKey>?,
) {
    val isRecipe: Boolean get() = kind == EntryKind.RECIPE
    val title: String get() = if (isRecipe) name else portions.firstOrNull()?.food?.name ?: name
    val nutrients: Nutrients get() = Nutrients.sum(portions.map { it.nutrients })
    val numbers: MacroNumbers get() = MacroNumbers.sum(portions.map { it.numbers })
    val edited: Boolean get() = isRecipe && templateSnapshot != null && templateSnapshot != portions.map { it.key }
    val amountText: String get() = if (isRecipe) "1 portion" else portions.firstOrNull()?.label.orEmpty()
    val plannedItem: PlannedItem get() = PlannedItem(numbers, eaten)
}

enum class MeasurementType { WEIGHT, BODY_FAT, LEAN_MASS }

data class Measurement(val time: Instant, val type: MeasurementType, val value: Double, val origin: String)

data class Phase(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val startingMaintenanceKcal: Double,
    /** Legacy weigh-ins imported before the first tracking phase ("Scale history only"). */
    val scaleHistoryOnly: Boolean = false,
)

data class GarminDay(val date: LocalDate, val totalKcal: Double, val activeKcal: Double, val origin: String)

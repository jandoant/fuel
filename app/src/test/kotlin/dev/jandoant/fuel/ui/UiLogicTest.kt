package dev.jandoant.fuel.ui

import dev.jandoant.fuel.core.domain.EntryKind
import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.FoodSource
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.domain.RecipeTag
import dev.jandoant.fuel.core.domain.RecipeTemplate
import dev.jandoant.fuel.core.model.CalorieLine
import dev.jandoant.fuel.core.model.CalorieZone
import dev.jandoant.fuel.core.model.GramRange
import dev.jandoant.fuel.core.model.MacroTargets
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.NutrientStatus
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.ui.calendar.DayMark
import dev.jandoant.fuel.ui.calendar.dayMark
import dev.jandoant.fuel.ui.components.StatusText
import dev.jandoant.fuel.ui.energy.goalLabel
import dev.jandoant.fuel.ui.plan.DayStates
import dev.jandoant.fuel.ui.recipes.filterRecipes
import dev.jandoant.fuel.core.model.GoalRange
import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiLogicTest {
    private val day = LocalDate.of(2026, 11, 26)
    private val food = Food(1, "Skyr", null, null, FoodSource.CUSTOM,
        Nutrients.of(Nutrient.ENERGY to 100.0, Nutrient.PROTEIN to 10.0, Nutrient.FAT to 2.0, Nutrient.CARBS to 5.0), Nutrients.ZERO, false,
        listOf(ServingUnit.grams(1, isDefault = true)))

    private fun entry(id: Long, slot: String, grams: Double, eaten: Boolean) =
        PlanEntry(id, day, slot, EntryKind.SINGLE, "Skyr", null, listOf(Portion(id, food, food.units[0], grams)), eaten, 0, null)

    private val zone = CalorieZone(2340.0, 2550.0, 2445.0)
    private val targets = MacroTargets(GramRange(141, 157), GramRange(39, 63), GramRange(30, null))

    @Test fun `disabled extra slot is hidden unless it has entries`() {
        val slots = MealSlot.DEFAULTS
        assertEquals(listOf("breakfast", "lunch", "dinner", "snacks"), DayStates.sections(emptyList(), slots).map { it.slot.id })
        val withExtra = DayStates.sections(listOf(entry(1, MealSlot.EXTRA, 100.0, false)), slots)
        assertEquals(listOf("breakfast", "lunch", "dinner", "extra", "snacks"), withExtra.map { it.slot.id })
    }

    @Test fun `today's micronutrients count eaten only, other days the plan`() {
        val entries = listOf(entry(1, MealSlot.LUNCH, 100.0, true), entry(2, MealSlot.DINNER, 200.0, false))
        val today = DayStates.build(day, day, entries, MealSlot.DEFAULTS, zone, targets)
        assertEquals(100, today.summary.calories.eaten)
        assertEquals(300, today.summary.calories.planned)
        assertEquals("0 of 1 foods have full data", today.micros.coverageText)
        assertEquals(0, today.micros.lowCount) // "today" never flags low
        val past = DayStates.build(day, day.plusDays(1), entries, MealSlot.DEFAULTS, zone, targets)
        assertEquals("0 of 2 foods have full data", past.micros.coverageText)
        assertTrue(past.micros.lowCount > 0)
    }

    @Test fun `status wording matches the prototype`() {
        assertEquals("fits", StatusText.macroShort(NutrientStatus.Fits()))
        assertEquals("11 g short", StatusText.macroShort(NutrientStatus.Short(11)))
        assertEquals("eaten 3 g over", StatusText.macroShort(NutrientStatus.EatenOver(3)))
        val line = CalorieLine(500, 2400, zone.display(), NutrientStatus.Fits(50), null)
        assertEquals("fits · 50 spare", StatusText.calories(line))
        assertEquals("fits · 60 above target", StatusText.calories(line.copy(status = NutrientStatus.Fits(-60))))
        assertEquals("plan 1,140 short", StatusText.calories(line.copy(status = NutrientStatus.Short(1140))))
        assertEquals("Empty", StatusText.planDay(line.copy(planned = 0)))
        assertEquals("≥30", StatusText.range(GramRange(30, null)))
    }

    @Test fun `month dots`() {
        assertEquals(DayMark.NONE, dayMark(emptyList(), zone, false))
        assertEquals(DayMark.PLANNED, dayMark(listOf(entry(1, MealSlot.LUNCH, 100.0, false)), zone, true))
        assertEquals(DayMark.ON_TARGET, dayMark(listOf(entry(1, MealSlot.LUNCH, 2400.0, true)), zone, false))
        assertEquals(DayMark.OFF_TARGET, dayMark(listOf(entry(1, MealSlot.LUNCH, 1000.0, true)), zone, false))
    }

    @Test fun `recipe filters`() {
        val r = listOf(
            RecipeTemplate(1, "Overnight oats", setOf(RecipeTag.BREAKFAST), true, emptyList()),
            RecipeTemplate(2, "Lentil curry", setOf(RecipeTag.MAIN), false, emptyList()),
        )
        assertEquals(listOf(1L), filterRecipes(r, "", 1).map { it.id })
        assertEquals(listOf(2L), filterRecipes(r, "", 3).map { it.id })
        assertEquals(listOf(2L), filterRecipes(r, "curry", 0).map { it.id })
    }

    @Test fun `goal labels`() {
        assertEquals("Maintain", goalLabel(GoalRange.MAINTAIN))
        assertEquals("Lose 0.25–0.5 %", goalLabel(GoalRange.LOSE_0_25_TO_0_5))
        assertEquals("Gain 0.25–0.5 %", goalLabel(GoalRange.GAIN_0_25_TO_0_5))
    }
}

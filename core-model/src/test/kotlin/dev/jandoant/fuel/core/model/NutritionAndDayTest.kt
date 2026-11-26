package dev.jandoant.fuel.core.model

import dev.jandoant.fuel.core.model.NutrientStatus.EatenOver
import dev.jandoant.fuel.core.model.NutrientStatus.Fits
import dev.jandoant.fuel.core.model.NutrientStatus.NoTarget
import dev.jandoant.fuel.core.model.NutrientStatus.Short
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionAndDayTest {
    private val skyr = Nutrients.of(
        Nutrient.ENERGY to 63.0, Nutrient.PROTEIN to 11.0, Nutrient.FAT to 0.2, Nutrient.CARBS to 4.0,
    )

    @Test fun `scaling and summing`() {
        val portion = Nutrients.forGrams(skyr, 150.0)
        assertEquals(94.5, portion.kcal, 1e-9)
        assertEquals(16.5, portion.protein, 1e-9)
        val two = Nutrients.sum(listOf(portion, portion))
        assertEquals(189.0, two.kcal, 1e-9)
        assertEquals(MacroNumbers(95, 17, 0, 6, 0), portion.macros())
    }

    @Test fun `unknown values are flagged, not zero`() {
        assertFalse(skyr.has(Nutrient.FIBER))
        assertEquals(0.0, skyr.fiber)
        assertEquals(listOf(Nutrient.SAT_FAT, Nutrient.SUGAR, Nutrient.FIBER, Nutrient.SALT), skyr.missing())
        val fixed = skyr.overriddenBy(Nutrients.of(Nutrient.FIBER to 0.0))
        assertTrue(fixed.has(Nutrient.FIBER))
    }

    private val zone = CalorieZone(2340.0, 2550.0, 2445.0) // displays 2340 / 2550 / 2450
    private val targets = MacroTargets(GramRange(141, 157), GramRange(39, 63), GramRange(30, null))

    @Test fun `eaten is a subset of the plan`() {
        val items = listOf(
            PlannedItem(MacroNumbers(500, 40, 15, 50, 8), eaten = true),
            PlannedItem(MacroNumbers(1900, 110, 40, 250, 25), eaten = false),
        )
        val s = DaySummaries.summarize(items, zone, targets)
        assertEquals(500, s.calories.eaten)
        assertEquals(2400, s.calories.planned)
        assertEquals(1900, s.calories.stillPlanned)
        assertEquals(Fits(vsTarget = 50), s.calories.status)
        assertEquals(1950, s.calories.headline!!.value)
        assertEquals(Fits(), s.protein.status)
        assertEquals(Fits(), s.fat.status)
        // carbs: (2340 − 150×4 − 55×9)/4 = 311, (2550 − 600 − 495)/4 = 364
        assertEquals(GramRange(311, 364), s.carbs.range)
        assertEquals(Short(11), s.carbs.status) // 300 planned
        assertEquals(Fits(), s.fiber.status)
    }

    @Test fun `no zone before the first weigh-in`() {
        val s = DaySummaries.summarize(listOf(PlannedItem(MacroNumbers(100, 10, 1, 5, 1), true)), null, null)
        assertEquals(NoTarget, s.calories.status)
        assertNull(s.calories.headline)
        assertEquals(NoTarget, s.carbs.status)
    }

    @Test fun `carb zone hidden when no protein or fat is planned`() {
        val s = DaySummaries.summarize(listOf(PlannedItem(MacroNumbers(400, 0, 0, 100, 2), false)), zone, targets)
        assertNull(s.carbs.range)
    }

    @Test fun `effect preview recomputes the carb zone`() {
        val items = listOf(PlannedItem(MacroNumbers(2000, 120, 50, 230, 20), false))
        val (before, after) = DaySummaries.effect(items, MacroNumbers(330, 62, 7, 0, 0), zone, targets)
        assertEquals(2000, before.calories.planned)
        assertEquals(2330, after.calories.planned)
        assertEquals(Short(10), after.calories.status)
        assertTrue(after.carbs.range!!.max!! < before.carbs.range!!.max!!)
    }

    @Test fun `eating over the zone switches to over goal`() {
        val s = DaySummaries.summarize(listOf(PlannedItem(MacroNumbers(2700, 200, 70, 250, 30), true)), zone, targets)
        assertEquals(EatenOver(150), s.calories.status)
        assertTrue(s.calories.headline!!.overGoal)
        assertEquals(EatenOver(43), s.protein.status)
    }

    @Test fun `micro report flags low only after the day is over`() {
        val total = Nutrients.of(Nutrient.SALT to 7.0, Nutrient.VITAMIN_D to 4.0, Nutrient.SUGAR to 30.0)
        val today = MicroReferences.report(total, 2450.0, MicroScope.TODAY, 3, 4)
        assertEquals(0, today.lowCount)
        assertEquals(1, today.overCount)
        assertEquals("1 over limit", today.summary(MicroScope.TODAY))
        assertEquals("3 of 4 foods have full data", today.coverageText)
        val day = MicroReferences.report(total, 2450.0, MicroScope.DAY, 3, 4)
        assertEquals(9, day.lowCount) // nothing but vitamin D (also low) is known
        assertEquals(61.25, day.limits.first { it.nutrient == Nutrient.SUGAR }.reference, 1e-9)
    }
}

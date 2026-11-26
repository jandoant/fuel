package dev.jandoant.fuel.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MacroTargetsTest {

    @Test fun `protein and fat from g per kg of reference weight`() {
        val t = MacroTargetModel.targets(78.4)
        assertEquals(GramRange(141, 157), t.protein) // matches Today prototype
        assertEquals(GramRange(39, 63), t.fat)
        assertEquals(GramRange(30, null), t.fiber)
    }

    @Test fun `custom ranges and fiber minimum`() {
        val t = MacroTargetModel.targets(80.0, MacroSettings(proteinGPerKg = 1.6..2.2, fatGPerKg = 0.6..1.0, fiberMinG = 35))
        assertEquals(GramRange(128, 176), t.protein)
        assertEquals(GramRange(48, 80), t.fat)
        assertEquals(35, t.fiber.min)
    }

    @Test fun `reference window must be 7, 14 or 28`() {
        assertFailsWith<IllegalArgumentException> { MacroSettings(referenceWindowDays = 10) }
    }

    @Test fun `carbs fill the zone left by planned protein and fat`() {
        val zone = CalorieZone(2340.0, 2550.0, 2445.0)
        // 150 g P = 600 kcal, 60 g F = 540 kcal → 1140 used
        val c = MacroTargetModel.carbZone(zone, plannedProteinG = 150.0, plannedFatG = 60.0)
        assertEquals(GramRange(300, 353), c) // (2340−1140)/4 = 300, (2550−1140)/4 = 352.5 → 353
    }

    @Test fun `carb zone is hidden when nothing is planned`() {
        assertNull(MacroTargetModel.carbZone(CalorieZone(2000.0, 2200.0, 2100.0), 0.0, 0.0))
    }

    @Test fun `carb zone never goes below zero`() {
        val c = MacroTargetModel.carbZone(CalorieZone(1800.0, 1900.0, 1850.0), 300.0, 100.0)
        assertEquals(GramRange(0, 0), c)
    }

    @Test fun `indicative carb range matches the Targets prototype`() {
        val t = MacroTargetModel.targets(78.4)
        assertEquals(GramRange(314, 384), MacroTargetModel.indicativeCarbRange(2450.0, t))
    }
}

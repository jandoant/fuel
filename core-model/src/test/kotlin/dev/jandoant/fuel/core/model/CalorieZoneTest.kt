package dev.jandoant.fuel.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalorieZoneTest {

    @Test fun `delta formula matches the PRD`() {
        // −0.5 %/wk at 78.4 kg = −0.392 kg/wk × 7700 / 7 = −431.2 kcal/day
        assertEquals(-431.2, CalorieZoneModel.kcalDelta(-0.5, 78.4), 1e-9)
    }

    @Test fun `zone from goal range, target is the midpoint`() {
        val z = CalorieZoneModel.rawZone(2770.0, 78.4, GoalRange.LOSE_0_25_TO_0_5)
        assertEquals(2770.0 - 431.2, z.low, 1e-9)
        assertEquals(2770.0 - 215.6, z.high, 1e-9)
        assertEquals((z.low + z.high) / 2, z.target, 1e-9)
        // Same numbers as the Energy prototype (rounded to 10): 2340–2550, target 2450.
        assertEquals(DisplayZone(2340, 2550, 2450), z.display())
    }

    @Test fun `maintain gives a zero-width zone at maintenance`() {
        val z = CalorieZoneModel.rawZone(2700.0, 80.0, GoalRange.MAINTAIN)
        assertEquals(CalorieZone(2700.0, 2700.0, 2700.0), z)
    }

    @Test fun `gain goal puts the zone above maintenance and order of ends does not matter`() {
        val a = CalorieZoneModel.rawZone(2700.0, 80.0, GoalRange(0.5, 0.25))
        val b = CalorieZoneModel.rawZone(2700.0, 80.0, GoalRange.GAIN_0_25_TO_0_5)
        assertEquals(a, b)
        assertTrue(a.low > 2700.0)
    }

    @Test fun `daily target change is capped and the zone shifts with it`() {
        val raw = CalorieZone(2200.0, 2400.0, 2300.0)
        val z = CalorieZoneModel.applyLimits(raw, previousTarget = 2500.0)
        assertEquals(CalorieZone(2350.0, 2550.0, 2450.0), z)
        val up = CalorieZoneModel.applyLimits(raw, previousTarget = 2100.0)
        assertEquals(2150.0, up.target)
    }

    @Test fun `small changes pass through the cap untouched`() {
        val raw = CalorieZone(2200.0, 2400.0, 2300.0)
        assertEquals(raw, CalorieZoneModel.applyLimits(raw, previousTarget = 2280.0))
    }

    @Test fun `never below the calorie floor`() {
        val z = CalorieZoneModel.applyLimits(CalorieZone(1500.0, 1900.0, 1700.0), previousTarget = null)
        assertEquals(CalorieZone(1800.0, 1900.0, 1800.0), z)
        val custom = CalorieZoneModel.applyLimits(CalorieZone(1500.0, 1600.0, 1550.0), null, ModelDefaults(calorieFloorKcal = 1600.0))
        assertEquals(1600.0, custom.target)
    }

    @Test fun `rate status is direction aware`() {
        val lose = GoalRange.LOSE_0_25_TO_0_5
        assertEquals(RateStatus.ON_TRACK, GoalRate.check(-0.38, lose).status)
        assertEquals(RateStatus.FASTER_THAN_GOAL, GoalRate.check(-0.7, lose).status)
        assertEquals(RateStatus.SLOWER_THAN_GOAL, GoalRate.check(-0.1, lose).status)
        val gain = GoalRange.GAIN_0_25_TO_0_5
        assertEquals(RateStatus.FASTER_THAN_GOAL, GoalRate.check(0.7, gain).status)
        assertEquals(RateStatus.SLOWER_THAN_GOAL, GoalRate.check(0.1, gain).status)
        assertEquals(RateStatus.ON_TRACK, GoalRate.check(0.05, GoalRange.MAINTAIN).status)
        assertEquals(RateStatus.LOSING, GoalRate.check(-0.3, GoalRange.MAINTAIN).status)
        assertEquals(RateStatus.GAINING, GoalRate.check(0.3, GoalRange.MAINTAIN).status)
    }

    @Test fun `warns when losing faster than 1 percent per week`() {
        assertTrue(GoalRate.check(-1.2, GoalRange.LOSE_0_5_TO_1_0).fastLossWarning)
        assertFalse(GoalRate.check(-0.9, GoalRange.LOSE_0_5_TO_1_0).fastLossWarning)
    }

    @Test fun `actual rate comes from the 28-day regression slope`() {
        val slope = WeightRegression.fit(linearWeights(0..27, 80.0, -0.4 / 7), day(27))!!
        assertEquals(-0.5, GoalRate.actualPctPerWeek(slope, 80.0), 1e-9)
    }
}

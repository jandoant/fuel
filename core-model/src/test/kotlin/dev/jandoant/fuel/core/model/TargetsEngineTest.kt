package dev.jandoant.fuel.core.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetsEngineTest {
    private val settings = ModelSettings(phaseStart = day(0), startingMaintenanceKcal = 2500.0, goal = GoalRange.LOSE_0_25_TO_0_5)

    @Test fun `no zone before the first weigh-in`() {
        val run = TargetsEngine.run(emptyList(), emptyMap(), settings, day(3))
        assertNull(run.today.zone)
        assertEquals(2500.0, run.today.maintenance.kcal)
    }

    @Test fun `daily target never moves more than the cap`() {
        val weights = linearWeights(0..55, 85.0, -0.06, noise = 0.3).asReadings()
        val run = TargetsEngine.run(weights, intake(0..55, 2300.0), settings, day(55))
        val targets = run.days.mapNotNull { it.zone?.target }
        targets.zipWithNext().forEach { (a, b) -> assertTrue(abs(b - a) <= 50.0 + 1e-9, "$a → $b") }
        assertEquals(8, run.weeklyUpdates.size)
    }

    @Test fun `maintenance learns from intake and weight trend`() {
        // Truth: 2900 kcal maintenance; eats 2400 → −500 kcal/day.
        val weights = linearWeights(0..83, 90.0, -500.0 / 7700, noise = 0.25).asReadings()
        val run = TargetsEngine.run(weights, intake(0..83, 2400.0), settings, day(83))
        assertEquals(2900.0, run.today.maintenance.kcal, 60.0)
        val zone = assertNotNull(run.today.zone)
        val expected = CalorieZoneModel.rawZone(run.today.maintenance.kcal, run.today.trendKg!!, settings.goal)
        assertEquals(expected.target, zone.target, 50.0)
        assertNotNull(run.balance)
        assertNotNull(run.rate)
        assertNotNull(run.today.macroTargets)
    }

    @Test fun `weight gap of a week holds the target`() {
        val weights = linearWeights(0..20, 85.0, -0.05).asReadings() // nothing after day 20
        val run = TargetsEngine.run(weights, intake(0..34, 2400.0), settings, day(34))
        val heldDays = run.days.filter { it.targetHeld }
        assertTrue(heldDays.isNotEmpty())
        val first = run.days.first { it.targetHeld }
        val before = run.days[run.days.indexOf(first) - 1]
        heldDays.forEach { assertEquals(before.zone, it.zone) }
        assertTrue(run.weeklyUpdates.last().outcome == MaintenanceOutcome.FROZEN_WEIGHT_GAP)
    }

    @Test fun `data before the phase is ignored`() {
        val old = linearWeights(0..27, 100.0, 0.2).asReadings()
        val recent = linearWeights(28..55, 85.0, 0.0).asReadings()
        val run = TargetsEngine.run(old + recent, intake(0..55, 2600.0), settings.copy(phaseStart = day(28)), day(55))
        assertEquals(day(28), run.days.first().date)
        assertEquals(85.0, run.today.trendKg!!, 1e-9)
    }

    @Test fun `the floor holds even for aggressive goals`() {
        val s = settings.copy(startingMaintenanceKcal = 1900.0, goal = GoalRange.LOSE_0_5_TO_1_0)
        val run = TargetsEngine.run(linearWeights(0..6, 60.0, 0.0).asReadings(), intake(0..6, 1900.0), s, day(6))
        assertTrue(run.days.mapNotNull { it.zone }.all { it.target >= 1800.0 && it.low >= 1800.0 })
    }
}

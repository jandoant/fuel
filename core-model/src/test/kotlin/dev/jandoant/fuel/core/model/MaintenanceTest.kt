package dev.jandoant.fuel.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaintenanceTest {
    private val defaults = ModelDefaults()
    private val prior = MaintenanceModel.initial(2500.0)

    @Test fun `observation is average intake minus balance`() {
        // Eating 2400 while losing 0.05 kg/day (−385 kcal/day) → maintenance ≈ 2785.
        val u = MaintenanceModel.weeklyUpdate(
            prior, intake(0..27, 2400.0), linearWeights(0..27, 80.0, -0.05), day(27), phaseStart = day(0),
        )
        assertEquals(MaintenanceOutcome.UPDATED, u.outcome)
        assertEquals(2785.0, u.observedKcal!!, 1e-6)
        assertTrue(u.posterior.kcal > prior.kcal && u.posterior.kcal < 2785.0)
        assertTrue(u.posterior.variance < prior.variance)
        assertEquals(u.gain!!, (u.posterior.kcal - 2500.0) / (2785.0 - 2500.0), 1e-9)
    }

    @Test fun `missing log days are excluded, not counted as zero`() {
        // 2 of 7 days missing in every week: average stays 2400, not 2400 × 5/7.
        val logged = intake(0..27, 2400.0).filterKeys { (it.dayOfWeek.value) !in setOf(6, 7) }
        val u = MaintenanceModel.weeklyUpdate(prior, logged, linearWeights(0..27, 80.0, -0.05), day(27), day(0))
        assertEquals(MaintenanceOutcome.UPDATED, u.outcome)
        assertEquals(2785.0, u.observedKcal!!, 1e-6)
    }

    @Test fun `more than two missing log days in the week does not adapt`() {
        val logged = intake(0..23, 2400.0) // last week only 3 of 7 logged
        val u = MaintenanceModel.weeklyUpdate(prior, logged, linearWeights(0..27, 80.0, -0.05), day(27), day(0))
        assertEquals(MaintenanceOutcome.SKIPPED_MISSING_LOGS, u.outcome)
        assertEquals(prior.kcal, u.posterior.kcal)
        assertTrue(u.posterior.variance > prior.variance) // we know a bit less
    }

    @Test fun `weight gap of 7 days or more freezes maintenance`() {
        val weights = linearWeights(0..19, 80.0, -0.05) // nothing in the last 8 days
        val u = MaintenanceModel.weeklyUpdate(prior, intake(0..27, 2400.0), weights, day(27), day(0))
        assertEquals(MaintenanceOutcome.FROZEN_WEIGHT_GAP, u.outcome)
        assertEquals(prior, u.posterior)
        assertEquals(8, u.longestWeightGapDays)
    }

    @Test fun `weight gap of 2 to 6 days adapts cautiously`() {
        val weights = linearWeights(0..27, 80.0, -0.05).filter { it.date !in setOf(day(23), day(24), day(25)) }
        val normal = MaintenanceModel.weeklyUpdate(prior, intake(0..27, 2400.0), linearWeights(0..27, 80.0, -0.05), day(27), day(0))
        val cautious = MaintenanceModel.weeklyUpdate(prior, intake(0..27, 2400.0), weights, day(27), day(0))
        assertEquals(MaintenanceOutcome.UPDATED_CAUTIOUSLY, cautious.outcome)
        assertEquals(3, cautious.longestWeightGapDays)
        assertTrue(cautious.gain!! < normal.gain!!)
    }

    @Test fun `bootstrap trusts early data less`() {
        val week1 = MaintenanceModel.weeklyUpdate(prior, intake(0..6, 2400.0), linearWeights(0..6, 80.0, -0.05), day(6), day(0))
        val later = MaintenanceModel.weeklyUpdate(prior, intake(0..27, 2400.0), linearWeights(0..27, 80.0, -0.05), day(27), day(0))
        assertTrue(week1.gain!! < later.gain!!)
    }

    @Test fun `only data inside the phase is used`() {
        // Before the phase: very different intake that must be ignored.
        val all = intake(0..13, 4000.0) + intake(14..41, 2400.0)
        val weights = linearWeights(14..41, 80.0, -0.05)
        val u = MaintenanceModel.weeklyUpdate(prior, all, weights, day(41), phaseStart = day(14))
        assertEquals(2785.0, u.observedKcal!!, 1e-6)
    }

    @Test fun `repeated weeks converge on the true maintenance`() {
        var m = prior
        // Truth 2800: eating 2400 → −400 kcal/day → −0.0519 kg/day.
        val slope = -400.0 / defaults.kcalPerKg
        val weights = linearWeights(0..139, 85.0, slope, noise = 0.3)
        val food = intake(0..139, 2400.0)
        for (w in 1..20) m = MaintenanceModel.weeklyUpdate(m, food, weights, day(w * 7 - 1), day(0)).posterior
        assertEquals(2800.0, m.kcal, 40.0)
        assertTrue(m.sd < 150)
    }

    @Test fun `longest gap counts a gap that started before the week`() {
        val dates = listOf(day(0), day(10))
        assertEquals(9, MaintenanceModel.longestWeightGap(dates, day(7), day(13)))
        assertEquals(2, MaintenanceModel.longestWeightGap(listOf(day(10), day(13)), day(11), day(13)))
        assertEquals(0, MaintenanceModel.longestWeightGap((0..13).map { day(it) }, day(7), day(13)))
    }
}

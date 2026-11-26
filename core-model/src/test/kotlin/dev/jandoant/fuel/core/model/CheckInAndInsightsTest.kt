package dev.jandoant.fuel.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckInAndInsightsTest {
    private val zone = CalorieZone(2380.0, 2600.0, 2490.0)
    private val targets = MacroTargets(GramRange(141, 157), GramRange(39, 63), GramRange(30, null))
    private val goal = GoalRange.LOSE_0_25_TO_0_5

    private fun week(kcal: Int, logged: Int = 7) = (0 until 7).map { i ->
        val m = MacroNumbers(kcal, 150, 58, 345, 26)
        CheckInDay(day(i), planned = m, eaten = if (i < logged) m else null, zone = zone, targets = targets)
    }

    private fun update(prior: Double, post: Double, observed: Double, gain: Double) = MaintenanceUpdate(
        day(6), MaintenanceEstimate(prior, 100.0), MaintenanceEstimate(post, 90.0), MaintenanceOutcome.UPDATED,
        observedKcal = observed, gain = gain, loggedDaysInWeek = 7,
    )

    private fun rate(pct: Double) = GoalRate.check(pct, goal)

    private fun eval(days: List<CheckInDay>, weighIns: Int = 7, r: RateCheck? = rate(-0.31), u: MaintenanceUpdate? = update(2810.0, 2770.0, 2745.0, 0.6)) =
        CheckInModel.evaluate(days, weighIns, r, 79.0, 78.76, goal, 75.0, u, 2490.0, 2450.0, null)

    @Test fun `on target and on track`() {
        val c = eval(week(2490))
        assertEquals(Diagnosis.ON_TRACK, c.diagnosis)
        assertEquals(true, c.ateOnTarget)
        assertEquals(7, c.loggedDays)
        assertEquals(-0.24, c.trendChangeKg!!, 1e-9)
        assertTrue(c.explanation.contains("60 % of the ~65 kcal/day gap applied"))
    }

    @Test fun `on target but rate off means the estimate was wrong`() {
        assertEquals(Diagnosis.ESTIMATE_WRONG, eval(week(2490), r = rate(-0.1)).diagnosis)
    }

    @Test fun `off target means execution`() {
        val c = eval(week(2900), r = rate(-0.1))
        assertEquals(Diagnosis.EXECUTION, c.diagnosis)
        assertEquals(false, c.ateOnTarget)
    }

    @Test fun `too few logged days or weigh-ins give no verdict`() {
        assertEquals(Diagnosis.NOT_ENOUGH_DATA, eval(week(2490, logged = 4)).diagnosis)
        assertEquals(Diagnosis.NOT_ENOUGH_DATA, eval(week(2490), weighIns = 3).diagnosis)
    }

    @Test fun `unlogged days are excluded from averages and day counts`() {
        val days = week(2490, logged = 5)
        val cal = CheckInModel.nutrientWeek(CheckInNutrient.CALORIES, days)
        assertEquals(5, cal.loggedDays)
        assertEquals(2490.0, cal.actualAvg!!, 1e-9)
        assertEquals(5, cal.daysInRange)
        val fiber = CheckInModel.nutrientWeek(CheckInNutrient.FIBER, days)
        assertEquals(0, fiber.daysInRange) // 26 g < 30 g minimum
        assertNull(fiber.bandHigh)
    }

    @Test fun `carb band follows each day's planned protein and fat`() {
        val carbs = CheckInModel.nutrientWeek(CheckInNutrient.CARBS, week(2490))
        // (2380 − 150×4 − 58×9)/4 = 314.5 → 315 ; (2600 − 600 − 522)/4 = 369.5 → 370
        assertEquals(315.0, carbs.bandLow!!, 1e-9)
        assertEquals(370.0, carbs.bandHigh!!, 1e-9)
    }

    @Test fun `forecast and hit tolerance`() {
        assertEquals(-0.3, CheckInModel.forecastKg(2440.0, 2770.0), 1e-9)
        assertEquals(true, Forecast(day(6), -0.30, -0.24).hit)
        assertEquals(false, Forecast(day(6), -0.30, -0.45).hit)
        assertNull(Forecast(day(6), -0.30, null).hit)
    }

    @Test fun `weeks to goal only when moving towards it`() {
        val c = eval(week(2490))
        assertNotNull(c.weeksToGoal)
        val away = CheckInModel.evaluate(week(2490), 7, rate(0.2), 79.0, 79.1, goal, 75.0, null, null, null, null)
        assertNull(away.weeksToGoal)
    }

    @Test fun `garmin calibration recovers the factor`() {
        // real = 2000 + 0.8 × active, exactly
        val weeks = (0 until 10).map { i ->
            val active = 400.0 + i * 50
            GarminWeek(day(7 * i + 6), 2000 + 0.8 * active, active, 2100 + active)
        }
        val c = GarminCalibrationModel.calibrate(weeks)!!
        assertEquals(0.8, c.factor, 1e-9)
        assertEquals(2000.0, c.intercept, 1e-6)
        assertEquals(0.0, c.factorSe, 1e-9)
        assertNull(GarminCalibrationModel.calibrate(weeks.take(5)))
    }

    @Test fun `garmin weeks skip weeks with few logged days`() {
        val daily = linearWeights(0..27, 80.0, -0.05)
        val intake = intake(0..27, 2400.0).filterKeys { it != day(26) && it != day(25) && it != day(24) }
        val garmin = (0..27).associate { day(it) to (2800.0 to 600.0) }
        val w = GarminCalibrationModel.weeks(intake, daily, garmin, listOf(day(20), day(27)))
        assertEquals(listOf(day(20)), w.map { it.weekEnd })
        assertEquals(2400.0 + 0.05 * 7700, w.first().realExpenditure, 1e-6)
    }

    @Test fun `aggregation leaves gaps for unlogged buckets`() {
        val values = mapOf(day(0) to 2000.0, day(1) to 2200.0, day(9) to 2500.0)
        val weeks = Aggregation.average(values, day(0), day(20), Bucket.WEEK)
        assertEquals(3, weeks.size)
        assertEquals(2100.0, weeks[0].value!!, 1e-9)
        assertEquals(2500.0, weeks[1].value!!, 1e-9)
        assertNull(weeks[2].value)
    }

    @Test fun `composition trend smooths with a band`() {
        val pts = CompositionTrend.smooth(linearWeights(0..20, 20.0, -0.01, noise = 0.3))
        assertEquals(21, pts.size)
        assertTrue(pts.last().band > 0)
    }
}

package dev.jandoant.fuel.core.model

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrendWeightTest {

    @Test fun `first reading of the day is used`() {
        val daily = TrendWeight.dailyWeights(listOf(reading(0, 79.5, hour = 19), reading(0, 78.9, hour = 7), reading(1, 78.8)))
        assertEquals(listOf(DailyWeight(day(0), 78.9), DailyWeight(day(1), 78.8)), daily)
    }

    @Test fun `first point seeds the trend`() {
        val t = TrendWeight.smooth(listOf(DailyWeight(day(0), 80.0)), 0.1)
        assertEquals(80.0, t.single().trendKg)
    }

    @Test fun `consecutive days move trend by alpha`() {
        val t = TrendWeight.smooth(listOf(DailyWeight(day(0), 80.0), DailyWeight(day(1), 79.0)), 0.1)
        assertEquals(79.9, t[1].trendKg, 1e-9)
    }

    @Test fun `a gap decays by elapsed days instead of inventing values`() {
        val t = TrendWeight.smooth(listOf(DailyWeight(day(0), 80.0), DailyWeight(day(4), 79.0)), 0.1)
        val w = 1 - 0.9.pow(4)
        assertEquals(80.0 - w, t[1].trendKg, 1e-9)
    }

    @Test fun `gap step equals stepping daily with the same reading`() {
        // Same result as if the 4-day-later reading had been seen on each intermediate day.
        val a = TrendWeight.smooth(listOf(DailyWeight(day(0), 80.0), DailyWeight(day(3), 79.0)), 0.15).last().trendKg
        var trend = 80.0
        repeat(3) { trend += 0.15 * (79.0 - trend) }
        assertEquals(trend, a, 1e-9)
    }

    @Test fun `trend converges on a constant weight`() {
        val t = TrendWeight.smooth(listOf(DailyWeight(day(0), 82.0)) + (1..80).map { DailyWeight(day(it), 78.0) }, 0.1)
        assertEquals(78.0, t.last().trendKg, 0.01)
    }

    @Test fun `trendOn holds the last value through gaps and is null before data`() {
        val t = TrendWeight.smooth(listOf(DailyWeight(day(2), 80.0)), 0.1)
        assertNull(TrendWeight.trendOn(t, day(1)))
        assertEquals(80.0, TrendWeight.trendOn(t, day(9)))
    }

    @Test fun `reference weight averages trend over the window`() {
        val t = TrendWeight.smooth(linearWeights(0..27, 80.0, -0.05), 0.1)
        val r7 = TrendWeight.referenceWeight(t, day(27), 7)!!
        val r28 = TrendWeight.referenceWeight(t, day(27), 28)!!
        assertEquals(t.takeLast(7).map { it.trendKg }.average(), r7, 1e-9)
        // While losing, a longer window gives a higher reference weight (as in the Targets prototype).
        kotlin.test.assertTrue(r28 > r7)
    }

    @Test fun `reference weight falls back to latest trend when the window is empty`() {
        val t = TrendWeight.smooth(listOf(DailyWeight(day(0), 80.0)), 0.1)
        assertEquals(80.0, TrendWeight.referenceWeight(t, day(30), 7))
    }
}

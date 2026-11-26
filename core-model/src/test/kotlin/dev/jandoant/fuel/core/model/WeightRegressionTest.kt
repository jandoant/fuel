package dev.jandoant.fuel.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightRegressionTest {

    @Test fun `exact line gives exact slope and zero error`() {
        val s = assertNotNull(WeightRegression.fit(linearWeights(0..27, 80.0, -0.05), day(27)))
        assertEquals(-0.05, s.kgPerDay, 1e-9)
        assertEquals(0.0, s.slopeSe, 1e-9)
        assertEquals(28, s.weighIns)
    }

    @Test fun `balance is slope times 7700`() {
        val b = assertNotNull(WeightRegression.balance(linearWeights(0..27, 80.0, -0.05), day(27)))
        assertEquals(-385.0, b.kcalPerDay, 1e-6)
        assertEquals(BalanceLabel.DEFICIT, b.label)
    }

    @Test fun `only the last 28 days are used`() {
        // Gaining before the window, losing inside it.
        val old = linearWeights(0..29, 70.0, 0.3)
        val recent = (30..57).map { DailyWeight(day(it), 90.0 - 0.1 * (it - 30)) }
        val s = assertNotNull(WeightRegression.fit(old + recent, day(57)))
        assertEquals(-0.1, s.kgPerDay, 1e-9)
    }

    @Test fun `noise widens the likely range`() {
        val b = assertNotNull(WeightRegression.balance(linearWeights(0..27, 80.0, 0.0, noise = 0.4), day(27)))
        assertTrue(b.likelyHighKcal - b.likelyLowKcal > 50)
        assertTrue(b.likelyLowKcal < 0 && b.likelyHighKcal > 0)
        assertEquals(BalanceLabel.MAINTENANCE, b.label)
    }

    @Test fun `too few weigh-ins gives no result`() {
        assertNull(WeightRegression.fit(linearWeights(0..1, 80.0, -0.1), day(27)))
    }
}

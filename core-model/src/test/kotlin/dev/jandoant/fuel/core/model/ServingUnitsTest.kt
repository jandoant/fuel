package dev.jandoant.fuel.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServingUnitsTest {
    private val g = ServingUnit(1, "g", 1.0, step = 10.0, min = 10.0, max = 1000.0, source = UnitSource.BUILT_IN)
    private val cup = ServingUnit(2, "cup (150 g)", 150.0, step = 0.5, min = 0.5, max = 4.0)
    private val tub = ServingUnit(3, "tub (500 g)", 500.0, step = 0.25, min = 0.25, max = 2.0)
    private val piece = ServingUnit(4, "piece (64 g)", 64.0, step = 1.0, min = 1.0, max = null)

    @Test fun `grams and labels`() {
        assertEquals(225.0, Amounts.grams(1.5, cup))
        assertEquals("150 g", Amounts.label(150.0, g))
        assertEquals("1.5 cup", Amounts.label(1.5, cup))
        assertEquals("= 225 g", Amounts.gramsHint(1.5, cup))
        assertEquals("", Amounts.gramsHint(150.0, g))
    }

    @Test fun `steps respect the range`() {
        assertEquals(1.0, Amounts.increment(0.5, cup))
        assertEquals(4.0, Amounts.increment(4.0, cup))      // at max: stays
        assertEquals(0.5, Amounts.decrement(0.5, cup))      // at min: stays
        assertEquals(20.0, Amounts.decrement(30.0, g))
        assertFalse(Amounts.canDecrement(10.0, g))
        assertTrue(Amounts.canIncrement(500.0, piece))     // no max
    }

    @Test fun `floating point steps do not drift`() {
        var a = 0.25
        repeat(5) { a = Amounts.increment(a, tub) }
        assertEquals(1.5, a)
    }

    @Test fun `switching units keeps grams, rounds to step and clamps`() {
        assertEquals(1.0, Amounts.convert(150.0, g, cup))   // 150 g = 1 cup
        assertEquals(1.5, Amounts.convert(230.0, g, cup))   // 1.53 → 1.5
        assertEquals(4.0, Amounts.convert(1000.0, g, cup))  // 6.7 cups → clamped to 4
        assertEquals(0.25, Amounts.convert(50.0, g, tub))   // 0.1 → rounds to 0 → clamped to min
        assertEquals(500.0, Amounts.convert(1.0, tub, g))
    }

    @Test fun `typed input accepts comma and point, rejects out of range`() {
        assertEquals(1.5, Amounts.parse("1,5"))
        assertEquals(1.5, Amounts.parse(" 1.5 "))
        assertNull(Amounts.parse("abc"))
        assertNull(Amounts.parse("-1"))
        assertEquals(2.5, Amounts.acceptTyped("2,5", cup))
        assertNull(Amounts.acceptTyped("5", cup))           // above max
        assertNull(Amounts.acceptTyped("0.2", cup))         // below min
    }

    @Test fun `format shows integers for grams and trims decimals`() {
        assertEquals("150", Amounts.format(150.4, g))
        assertEquals("1.5", Amounts.format(1.5, cup))
        assertEquals("2", Amounts.format(2.0, cup))
        assertEquals("0.25", Amounts.format(0.25, tub))
    }

    @Test fun `default amount is one unit or 100 g`() {
        assertEquals(1.0, Amounts.defaultAmount(cup))
        assertEquals(100.0, Amounts.defaultAmount(g))
    }
}

package dev.jandoant.fuel.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VoiceInputTest {

    @Test fun `german phrases`() {
        assertEquals(listOf(SpokenItem(200.0, SpokenUnit.GRAM, "skyr")), VoiceParser.parse("200 Gramm Skyr"))
        assertEquals(listOf(SpokenItem(1.0, SpokenUnit.TABLESPOON, "olivenöl")), VoiceParser.parse("ein Esslöffel Olivenöl"))
        assertEquals(listOf(SpokenItem(2.0, SpokenUnit.TEASPOON, "honig")), VoiceParser.parse("2 TL Honig"))
        assertEquals(listOf(SpokenItem(1.0, SpokenUnit.PINCH, "salz")), VoiceParser.parse("eine Prise Salz"))
        assertEquals(listOf(SpokenItem(2.0, SpokenUnit.SLICE, "vollkornbrot")), VoiceParser.parse("zwei Scheiben Vollkornbrot"))
        assertEquals(listOf(SpokenItem(0.5, SpokenUnit.KILOGRAM, "kartoffeln")), VoiceParser.parse("ein halbes Kilo Kartoffeln"))
        assertEquals(listOf(SpokenItem(1.5, SpokenUnit.CUP, "milch")), VoiceParser.parse("1,5 Tassen Milch"))
    }

    @Test fun `several items in one sentence`() {
        val items = VoiceParser.parse("zwei Eier und eine Banane, 30 g Walnüsse sowie 250 ml Milch")
        assertEquals(
            listOf(
                SpokenItem(2.0, null, "eier"),
                SpokenItem(1.0, null, "banane"),
                SpokenItem(30.0, SpokenUnit.GRAM, "walnüsse"),
                SpokenItem(250.0, SpokenUnit.MILLILITER, "milch"),
            ),
            items,
        )
    }

    @Test fun `recogniser formatting and english`() {
        assertEquals(listOf(SpokenItem(200.0, SpokenUnit.GRAM, "skyr")), VoiceParser.parse("200g Skyr"))
        assertEquals(listOf(SpokenItem(1.0, SpokenUnit.TABLESPOON, "olive oil")), VoiceParser.parse("1 tablespoon of olive oil"))
        assertEquals(listOf(SpokenItem(200.0, SpokenUnit.GRAM, "skyr"), SpokenItem(1.0, null, "banana")), VoiceParser.parse("200 grams of skyr and a banana"))
        assertEquals(listOf(SpokenItem(null, null, "apfel")), VoiceParser.parse("Apfel"))
        assertEquals(emptyList(), VoiceParser.parse("und"))
    }

    private val g = ServingUnit(1, "g", 1.0, 10.0, 1.0, 2000.0, false, UnitSource.BUILT_IN)
    private val el = ServingUnit(2, "EL (10 g)", 10.0, 1.0, 1.0, 10.0)
    private val medium = ServingUnit(3, "medium (120 g)", 120.0, 0.5, 0.5, 6.0, isDefault = true)

    @Test fun `mass units go to grams`() {
        val r = VoiceResolver.resolve(SpokenItem(0.5, SpokenUnit.KILOGRAM, "kartoffeln"), listOf(g, medium))
        assertEquals(g, r.unit)
        assertEquals(500.0, r.amount)
        assertNull(r.note)
    }

    @Test fun `spoons use the food's own unit when it exists`() {
        val r = VoiceResolver.resolve(SpokenItem(2.0, SpokenUnit.TABLESPOON, "öl"), listOf(g, el))
        assertEquals(el, r.unit)
        assertEquals(2.0, r.amount)
    }

    @Test fun `spoons fall back to a standard weight with a note`() {
        val r = VoiceResolver.resolve(SpokenItem(1.0, SpokenUnit.TABLESPOON, "öl"), listOf(g))
        assertEquals(g, r.unit)
        assertEquals(15.0, r.amount)
        assertNotNull(r.note)
    }

    @Test fun `counts use the piece unit`() {
        val r = VoiceResolver.resolve(SpokenItem(2.0, null, "banane"), listOf(g, medium))
        assertEquals(medium, r.unit)
        assertEquals(2.0, r.amount)
        val bare = VoiceResolver.resolve(SpokenItem(null, null, "banane"), listOf(g, medium))
        assertEquals(1.0, bare.amount)
        val noPiece = VoiceResolver.resolve(SpokenItem(2.0, null, "ei"), listOf(g))
        assertEquals(200.0, noPiece.amount)
        assertNotNull(noPiece.note)
    }

    @Test fun `amounts are clamped to the unit range`() {
        assertEquals(10.0, VoiceResolver.resolve(SpokenItem(50.0, SpokenUnit.TABLESPOON, "öl"), listOf(g, el)).amount)
    }
}

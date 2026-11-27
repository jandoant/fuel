package dev.jandoant.fuel.ui

import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.FoodSource
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.core.model.SpokenItem
import dev.jandoant.fuel.core.model.SpokenUnit
import dev.jandoant.fuel.ui.addfood.VoiceLine
import dev.jandoant.fuel.ui.addfood.VoiceMatcher
import dev.jandoant.fuel.ui.addfood.VoiceReview
import dev.jandoant.fuel.ui.addfood.withPortion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceReviewTest {
    @Test fun `plural German words also search their singular`() {
        assertTrue("ei" in VoiceMatcher.queryVariants("eier"))
        assertTrue("banane" in VoiceMatcher.queryVariants("bananen"))
        assertTrue("walnuss" in VoiceMatcher.queryVariants("walnüsse"))
        assertTrue("apple" in VoiceMatcher.queryVariants("apples"))
        assertEquals("skyr", VoiceMatcher.queryVariants("skyr").first())
    }

    private val g = ServingUnit.grams(1)
    private val skyr = Food(1, "Skyr", null, null, FoodSource.CUSTOM, Nutrients.of(Nutrient.ENERGY to 63.0, Nutrient.PROTEIN to 11.0), Nutrients.ZERO, false, listOf(g))

    @Test fun `only included, matched lines are added`() {
        val matched = VoiceLine(SpokenItem(200.0, SpokenUnit.GRAM, "skyr"), portion = Portion(0, skyr, g, 200.0), loading = false)
        val excluded = matched.copy(include = false)
        val unmatched = VoiceLine(SpokenItem(1.0, null, "xyz"), loading = false, include = false)
        val review = VoiceReview("…", listOf(matched, excluded, unmatched))
        assertEquals(1, review.toAdd.size)
        assertEquals(126, review.numbers.kcal)
        assertEquals(300.0, matched.withPortion(g, 300.0).portion!!.amount)
    }
}

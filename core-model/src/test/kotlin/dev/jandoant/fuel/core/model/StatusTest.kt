package dev.jandoant.fuel.core.model

import dev.jandoant.fuel.core.model.NutrientStatus.EatenOver
import dev.jandoant.fuel.core.model.NutrientStatus.Fits
import dev.jandoant.fuel.core.model.NutrientStatus.NoTarget
import dev.jandoant.fuel.core.model.NutrientStatus.Over
import dev.jandoant.fuel.core.model.NutrientStatus.Short
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTest {
    private val protein = GramRange(141, 157)
    private val fiber = GramRange(30, null)
    private val zone = DisplayZone(2340, 2550, 2450)

    @Test fun `nutrient statuses`() {
        assertEquals(Fits(), Statuses.nutrient(eaten = 80, planned = 150, range = protein))
        assertEquals(Short(11), Statuses.nutrient(80, 130, protein))
        assertEquals(Over(8), Statuses.nutrient(80, 165, protein))
        assertEquals(EatenOver(3), Statuses.nutrient(160, 170, protein))
    }

    @Test fun `zone edges count as fitting`() {
        assertEquals(Fits(), Statuses.nutrient(0, 141, protein))
        assertEquals(Fits(), Statuses.nutrient(157, 157, protein))
    }

    @Test fun `fiber has no maximum`() {
        assertEquals(Fits(), Statuses.nutrient(80, 80, fiber))
        assertEquals(Short(6), Statuses.nutrient(10, 24, fiber))
    }

    @Test fun `carbs without a zone have no status`() {
        assertEquals(NoTarget, Statuses.nutrient(50, 200, null))
    }

    @Test fun `calorie statuses with spare and above target`() {
        assertEquals(Fits(vsTarget = 50), Statuses.calories(1200, 2400, zone))   // "fits · 50 spare"
        assertEquals(Fits(vsTarget = -60), Statuses.calories(1200, 2510, zone))  // "fits · 60 above target"
        assertEquals(Short(140), Statuses.calories(1200, 2200, zone))
        assertEquals(Over(50), Statuses.calories(1200, 2600, zone))
        assertEquals(EatenOver(30), Statuses.calories(2580, 2600, zone))
    }

    @Test fun `tones map to colours`() {
        assertEquals(StatusTone.OK, Fits().tone)
        assertEquals(StatusTone.WARNING, Short(1).tone)
        assertEquals(StatusTone.DANGER, EatenOver(1).tone)
    }

    @Test fun `calorie headline switches to over goal`() {
        assertEquals(CalorieHeadline(1250, false), Statuses.calorieHeadline(1200, zone))
        assertEquals(CalorieHeadline(30, true), Statuses.calorieHeadline(2580, zone))
        // Between target and zone top: "0 left", not negative.
        assertEquals(CalorieHeadline(0, false), Statuses.calorieHeadline(2500, zone))
    }
}

package dev.jandoant.fuel.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jandoant.fuel.core.domain.EntryKind
import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.FoodSource
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.domain.PortionKey
import dev.jandoant.fuel.core.model.GramRange
import dev.jandoant.fuel.core.model.MicroReferences
import dev.jandoant.fuel.core.model.MicroScope
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.ui.components.AmountStepper
import dev.jandoant.fuel.ui.components.EntryActions
import dev.jandoant.fuel.ui.components.EntryCard
import dev.jandoant.fuel.ui.components.MacroRing
import dev.jandoant.fuel.ui.components.MicrosSection
import dev.jandoant.fuel.ui.theme.FuelTheme
import dev.jandoant.fuel.ui.theme.Macros
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    private val cup = ServingUnit(2, "cup (150 g)", 150.0, step = 0.5, min = 0.5, max = 4.0)
    private val food = Food(1, "Skyr", null, null, FoodSource.CUSTOM,
        Nutrients.of(Nutrient.ENERGY to 63.0, Nutrient.PROTEIN to 11.0, Nutrient.FAT to 0.2, Nutrient.CARBS to 4.0), Nutrients.ZERO, false,
        listOf(ServingUnit.grams(1), cup))

    @Test fun stepperRespectsStepRangeAndCommaInput() {
        var amount by mutableDoubleStateOf(0.5)
        rule.setContent { FuelTheme { AmountStepper(amount, cup, { amount = it }, "Skyr") } }
        rule.onNodeWithContentDescription("More Skyr").performClick()
        rule.runOnIdle { assertEquals(1.0, amount) }
        rule.onNodeWithContentDescription("Less Skyr").performClick()
        rule.onNodeWithContentDescription("Less Skyr").performClick() // already at the minimum
        rule.runOnIdle { assertEquals(0.5, amount) }
        rule.onNodeWithContentDescription("Amount of Skyr").performTextReplacement("2,5")
        rule.runOnIdle { assertEquals(2.5, amount) }
        rule.onNodeWithContentDescription("Amount of Skyr").performTextReplacement("9") // above max: ignored
        rule.runOnIdle { assertEquals(2.5, amount) }
    }

    private class RecordingActions : EntryActions {
        val calls = mutableListOf<String>()
        override fun toggleEaten(entry: PlanEntry) { calls += "toggle" }
        override fun changePortion(entry: PlanEntry, portion: Portion, unit: ServingUnit, amount: Double) { calls += "change $amount" }
        override fun addIngredient(entry: PlanEntry) { calls += "add" }
        override fun removeIngredient(entry: PlanEntry, portion: Portion) { calls += "remove" }
        override fun saveToTemplate(entry: PlanEntry) { calls += "save" }
        override fun delete(entry: PlanEntry) { calls += "delete" }
        override fun move(entry: PlanEntry) { calls += "move" }
    }

    @Test fun recipeEntryTogglesAndExpandsWithItsOwnCopy() {
        val actions = RecordingActions()
        var expanded by mutableStateOf(false)
        val entry = PlanEntry(
            1, LocalDate.of(2026, 11, 26), "breakfast", EntryKind.RECIPE, "Overnight oats", 7,
            listOf(Portion(11, food, cup, 1.0)), eaten = false, sortOrder = 0,
            templateSnapshot = listOf(PortionKey(1, 2, 2.0)), // differs → "edited"
        )
        rule.setContent { FuelTheme { EntryCard(entry, expanded, { expanded = !expanded }, actions) } }
        rule.onNodeWithText("RECIPE").assertExists()
        rule.onNodeWithText("edited").assertExists()
        rule.onNodeWithContentDescription("Overnight oats eaten").assertIsOff().performClick()
        rule.runOnIdle { assertEquals(listOf("toggle"), actions.calls) }
        rule.onNodeWithText("Overnight oats").performClick()
        rule.onNodeWithText("Your copy of the recipe. Changes apply only to this entry, not to the template.").assertExists()
        rule.onNodeWithContentDescription("More Skyr").performClick()
        rule.onNodeWithText("Save changes to template…").performClick()
        rule.runOnIdle { assertEquals(listOf("toggle", "change 1.5", "save"), actions.calls) }
    }

    @Test fun eatenEntryShowsCheckedState() {
        val entry = PlanEntry(2, LocalDate.of(2026, 11, 26), "lunch", EntryKind.SINGLE, "Skyr", null, listOf(Portion(12, food, cup, 1.0)), true, 0, null)
        rule.setContent { FuelTheme { EntryCard(entry, false, {}, RecordingActions()) } }
        rule.onNodeWithContentDescription("Skyr eaten").assertIsOn()
        rule.onNodeWithText("1 cup").assertExists()
    }

    @Test fun macroRingDescribesEatenPlannedAndGoal() {
        rule.setContent { FuelTheme { MacroRing(Macros.Protein, 80, 70, GramRange(141, 157)) } }
        rule.onNodeWithContentDescription("Protein: 80 g eaten, 70 g more planned, goal 141 to 157 g").assertExists()
    }

    @Test fun microsSectionIsCollapsedWithSummaryAndExpands() {
        val report = MicroReferences.report(Nutrients.of(Nutrient.SALT to 7.0), 2450.0, MicroScope.DAY, 1, 2)
        rule.setContent { FuelTheme { MicrosSection(report, MicroScope.DAY) } }
        rule.onNodeWithText("Salt").assertDoesNotExist()
        rule.onNodeWithText("9 low · 1 over limit").performClick()
        rule.onNodeWithText("Salt").assertExists()
    }
}

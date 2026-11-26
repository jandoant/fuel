package dev.jandoant.fuel.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.UnitSource
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.db.MeasurementEntity
import dev.jandoant.fuel.data.off.OpenFoodFactsClient
import dev.jandoant.fuel.data.repo.FoodRepository
import dev.jandoant.fuel.data.repo.PlanRepository
import dev.jandoant.fuel.data.repo.PortionDraft
import dev.jandoant.fuel.data.repo.RecipeRepository
import dev.jandoant.fuel.data.repo.UnitDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Repository behaviour against a real (in-memory) Room database on the device. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: FuelDatabase
    private lateinit var foods: FoodRepository
    private lateinit var plan: PlanRepository
    private lateinit var recipes: RecipeRepository
    private val day = LocalDate.of(2026, 11, 26)

    @Before fun setUp() = runBlocking {
        db = FuelDatabase.build(InstrumentationRegistry.getInstrumentation().targetContext, inMemory = true)
        db.ensureSeeded()
        foods = FoodRepository(db, OpenFoodFactsClient())
        plan = PlanRepository(db, foods)
        recipes = RecipeRepository(db, foods)
    }

    @After fun tearDown() = db.close()

    private suspend fun food(name: String, kcal: Double, protein: Double, cupGrams: Double? = null): Long =
        foods.createCustom(name, null, Nutrients.of(Nutrient.ENERGY to kcal, Nutrient.PROTEIN to protein, Nutrient.FAT to 1.0, Nutrient.CARBS to 5.0),
            listOfNotNull(cupGrams?.let { UnitDraft("cup", it, 0.5, 0.5, 4.0, true, UnitSource.USER) }))

    @Test fun mealSlotsAreSeededWithAnOptionalExtraSlot() = runBlocking {
        val slots = plan.slots.first()
        assertEquals(listOf("breakfast", "lunch", "dinner", "extra", "snacks"), slots.map { it.id })
        assertFalse(slots.first { it.isExtra }.enabled)
        plan.configureExtraSlot(true, "Pre-workout", MealSlot.LUNCH)
        val after = plan.slots.first()
        assertEquals(listOf("breakfast", "lunch", "extra", "dinner", "snacks"), after.map { it.id })
        assertTrue(after.first { it.isExtra }.enabled)
    }

    @Test fun plannedRecipeIsAnIndependentCopy() = runBlocking {
        val skyr = food("Skyr", 63.0, 11.0)
        val oats = food("Oats", 372.0, 13.5)
        val gSkyr = foods.get(skyr)!!.gramUnit.id
        val gOats = foods.get(oats)!!.gramUnit.id
        val rid = recipes.create("Overnight oats", ingredients = listOf(PortionDraft(skyr, gSkyr, 150.0), PortionDraft(oats, gOats, 60.0)))
        val template = recipes.get(rid)!!
        val entryId = plan.addRecipe(day, MealSlot.BREAKFAST, template, template.ingredients.map { PortionDraft(it.food.id, it.unit.id, it.amount) })

        // Editing the entry does not touch the template …
        val entry = plan.entry(entryId)!!
        assertFalse(entry.edited)
        plan.updatePortion(entry.portions[0].id, entryId, entry.portions[0].unit, 200.0)
        assertEquals(150.0, recipes.get(rid)!!.ingredients[0].amount)
        assertTrue(plan.entry(entryId)!!.edited)

        // … and editing the template does not touch the entry.
        recipes.setIngredients(rid, listOf(PortionDraft(skyr, gSkyr, 100.0)))
        assertEquals(listOf(200.0, 60.0), plan.entry(entryId)!!.portions.map { it.amount })

        // Explicit "save to template" copies the entry's ingredients and clears "edited".
        plan.saveEntryToTemplate(entryId)
        assertEquals(listOf(200.0, 60.0), recipes.get(rid)!!.ingredients.map { it.amount })
        assertFalse(plan.entry(entryId)!!.edited)
    }

    @Test fun copyCreatesPlannedEntriesKeepingTheSlot() = runBlocking {
        val skyr = food("Skyr", 63.0, 11.0)
        val id = plan.addSingle(day, MealSlot.LUNCH, PortionDraft(skyr, foods.get(skyr)!!.gramUnit.id, 150.0), eaten = true)
        val copies = plan.copy(listOf(plan.entry(id)!!), day.plusDays(1))
        val copied = plan.entry(copies.single())!!
        assertEquals(MealSlot.LUNCH, copied.slotId)
        assertFalse(copied.eaten)
        assertEquals(day.plusDays(1), copied.date)
        plan.deleteEntries(copies) // Undo
        assertEquals(1, plan.range(day, day.plusDays(1), foods.catalog.first()).size)
    }

    @Test fun removingTheLastIngredientDeletesTheEntry() = runBlocking {
        val skyr = food("Skyr", 63.0, 11.0)
        val id = plan.addSingle(day, MealSlot.LUNCH, PortionDraft(skyr, foods.get(skyr)!!.gramUnit.id, 150.0))
        plan.removeIngredient(id, plan.entry(id)!!.portions.single().id)
        assertEquals(null, plan.entry(id))
    }

    @Test fun unitsInUseAndTheGramUnitCannotBeRemoved() = runBlocking {
        val skyr = food("Skyr", 63.0, 11.0, cupGrams = 150.0)
        val f = foods.get(skyr)!!
        val cup = f.units.first { it.name == "cup" }
        assertTrue(cup.isDefault)
        assertFalse(foods.removeUnit(skyr, f.gramUnit.id))
        plan.addSingle(day, MealSlot.LUNCH, PortionDraft(skyr, cup.id, 1.0))
        assertFalse(foods.removeUnit(skyr, cup.id))
    }

    @Test fun measurementsAreDeduplicatedByTypeTimeAndOrigin() = runBlocking {
        val m = MeasurementEntity(time = 1_000L, type = "WEIGHT", value = 78.4, origin = "com.fitdays")
        db.bodyDao().insertAll(listOf(m, m.copy(value = 78.5)))
        db.bodyDao().insertAll(listOf(m.copy(origin = "com.google.android.apps.fitness")))
        assertEquals(2, db.bodyDao().all().size)
    }
}

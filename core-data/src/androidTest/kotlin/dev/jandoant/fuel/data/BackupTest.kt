package dev.jandoant.fuel.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.data.backup.BackupRepository
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.db.MeasurementEntity
import dev.jandoant.fuel.data.db.PhaseEntity
import dev.jandoant.fuel.data.off.OpenFoodFactsClient
import dev.jandoant.fuel.data.repo.FoodRepository
import dev.jandoant.fuel.data.repo.PlanRepository
import dev.jandoant.fuel.data.repo.PortionDraft
import dev.jandoant.fuel.data.repo.RecipeRepository
import dev.jandoant.fuel.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals

/** PRD §9: a JSON backup must restore the full database. */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = FuelDatabase.build(ctx, inMemory = true)

    @After fun tearDown() = db.close()

    @Test fun exportThenRestoreGivesTheSameData() = runBlocking {
        db.ensureSeeded()
        val settings = SettingsRepository(ctx)
        settings.update { it.copy(fiberMinG = 35, goalWeightKg = 75.0) }
        val foods = FoodRepository(db, OpenFoodFactsClient())
        val plan = PlanRepository(db, foods)
        val recipes = RecipeRepository(db, foods)
        val id = foods.createCustom("Skyr", "Milbona", Nutrients.of(Nutrient.ENERGY to 63.0, Nutrient.PROTEIN to 11.0), emptyList())
        val g = foods.get(id)!!.gramUnit.id
        recipes.create("Bowl", ingredients = listOf(PortionDraft(id, g, 150.0)))
        plan.addSingle(LocalDate.of(2026, 11, 26), MealSlot.BREAKFAST, PortionDraft(id, g, 150.0), eaten = true)
        db.bodyDao().insertAll(listOf(MeasurementEntity(time = 1L, type = "WEIGHT", value = 78.4, origin = "x")))
        db.modelDao().insertPhase(PhaseEntity(name = "Phase 1", startDate = 20000, startingMaintenance = 2600.0))

        val backup = BackupRepository(ctx, db, settings)
        val text = backup.encode(backup.snapshot())

        // Change everything, then restore.
        settings.update { it.copy(fiberMinG = 20) }
        plan.addSingle(LocalDate.of(2026, 11, 27), MealSlot.LUNCH, PortionDraft(id, g, 50.0))
        backup.restore(backup.decode(text))

        val after = backup.snapshot()
        val before = backup.decode(text)
        assertEquals(before.foods, after.foods)
        assertEquals(before.units, after.units)
        assertEquals(before.recipes, after.recipes)
        assertEquals(before.recipeIngredients, after.recipeIngredients)
        assertEquals(before.entries, after.entries)
        assertEquals(before.entryIngredients, after.entryIngredients)
        assertEquals(before.measurements, after.measurements)
        assertEquals(before.phases, after.phases)
        assertEquals(35, settings.current().fiberMinG)
        assertEquals(75.0, settings.current().goalWeightKg)
    }
}

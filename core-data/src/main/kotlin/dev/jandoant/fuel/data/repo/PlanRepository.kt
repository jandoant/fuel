package dev.jandoant.fuel.data.repo

import dev.jandoant.fuel.core.domain.EntryKind
import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.domain.RecipeTemplate
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.data.db.DbJson
import dev.jandoant.fuel.data.db.EntryIngredientEntity
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.db.PlanEntryEntity
import dev.jandoant.fuel.data.db.RecipeIngredientEntity
import dev.jandoant.fuel.data.db.toDomain
import dev.jandoant.fuel.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Receives every change to eaten entries so Health Connect can mirror them (PRD §4.2). */
interface EatenEntrySink {
    /** The entry is eaten (new, edited, or ticked): write or update its NutritionRecord. */
    fun upsert(entry: PlanEntry)
    /** The entry was un-ticked or deleted: delete its NutritionRecord. */
    fun remove(entryId: Long)

    object None : EatenEntrySink {
        override fun upsert(entry: PlanEntry) = Unit
        override fun remove(entryId: Long) = Unit
    }
}

/** An ingredient to add: food + unit + amount, before it is stored. */
data class PortionDraft(val foodId: Long, val unitId: Long, val amount: Double)

fun Portion.toDraft() = PortionDraft(food.id, unit.id, amount)

class PlanRepository(
    private val db: FuelDatabase,
    private val foods: FoodRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.planDao()

    /** Health Connect mirror; set by the app container. */
    @Volatile var sink: EatenEntrySink = EatenEntrySink.None

    val slots: Flow<List<MealSlot>> = dao.observeSlots().map { list -> list.map { it.toDomain() }.sortedBy { it.order } }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<PlanEntry>> =
        combine(dao.observeEntries(from.toEpochDay(), to.toEpochDay()), dao.observeIngredients(from.toEpochDay(), to.toEpochDay()), foods.catalog) { e, i, cat ->
            assemble(e, i, cat)
        }

    fun observeDay(date: LocalDate): Flow<List<PlanEntry>> = observeRange(date, date)

    suspend fun range(from: LocalDate, to: LocalDate, catalog: Map<Long, Food>): List<PlanEntry> =
        assemble(dao.entries(from.toEpochDay(), to.toEpochDay()), dao.ingredients(from.toEpochDay(), to.toEpochDay()), catalog)

    suspend fun entry(id: Long): PlanEntry? {
        val e = dao.entry(id) ?: return null
        val ing = dao.ingredientsOf(id)
        val cat = ing.mapNotNull { foods.get(it.foodId) }.associateBy { it.id }
        return assemble(listOf(e), ing, cat).firstOrNull()
    }

    // --- Adding ---

    suspend fun addSingle(date: LocalDate, slotId: String, portion: PortionDraft, eaten: Boolean = false): Long {
        val food = foods.get(portion.foodId) ?: error("Unknown food ${portion.foodId}")
        val id = db.tx {
            val entryId = dao.insertEntry(
                PlanEntryEntity(date = date.toEpochDay(), slotId = slotId, kind = EntryKind.SINGLE.name, name = food.name,
                    eaten = eaten, sortOrder = dao.nextSortOrder(date.toEpochDay(), slotId), updatedAt = now()),
            )
            dao.insertIngredients(listOf(EntryIngredientEntity(entryId = entryId, foodId = portion.foodId, unitId = portion.unitId, amount = portion.amount, sortOrder = 0)))
            entryId
        }
        foods.touch(listOf(portion.foodId))
        afterChange(id)
        return id
    }

    /**
     * Plans a recipe: the entry gets its **own copy** of the ingredients ([portions], possibly adjusted in the add
     * sheet). The template snapshot is stored to show "edited" later; the template itself is never touched.
     */
    suspend fun addRecipe(date: LocalDate, slotId: String, template: RecipeTemplate, portions: List<PortionDraft>, eaten: Boolean = false): Long {
        val id = db.tx {
            val entryId = dao.insertEntry(
                PlanEntryEntity(date = date.toEpochDay(), slotId = slotId, kind = EntryKind.RECIPE.name, name = template.name,
                    templateId = template.id, eaten = eaten, sortOrder = dao.nextSortOrder(date.toEpochDay(), slotId),
                    templateSnapshotJson = DbJson.encodeKeys(template.ingredients.map { it.key }), updatedAt = now()),
            )
            dao.insertIngredients(portions.mapIndexed { i, p -> EntryIngredientEntity(entryId = entryId, foodId = p.foodId, unitId = p.unitId, amount = p.amount, sortOrder = i) })
            entryId
        }
        foods.touch(portions.map { it.foodId })
        afterChange(id)
        return id
    }

    // --- Editing ---

    suspend fun setEaten(entryId: Long, eaten: Boolean) {
        val e = dao.entry(entryId) ?: return
        dao.updateEntry(e.copy(eaten = eaten, updatedAt = now()))
        afterChange(entryId)
    }

    /** Changes one ingredient's unit/amount (amount is clamped to the unit's range). */
    suspend fun updatePortion(ingredientId: Long, entryId: Long, unit: ServingUnit, amount: Double) {
        val row = dao.ingredientsOf(entryId).firstOrNull { it.id == ingredientId } ?: return
        db.tx {
            dao.updateIngredient(row.copy(unitId = unit.id, amount = Amounts.clamp(amount, unit)))
            dao.entry(entryId)?.let { dao.updateEntry(it.copy(updatedAt = now())) }
        }
        afterChange(entryId)
    }

    suspend fun addIngredient(entryId: Long, portion: PortionDraft) {
        db.tx {
            val order = (dao.ingredientsOf(entryId).maxOfOrNull { it.sortOrder } ?: -1) + 1
            dao.insertIngredients(listOf(EntryIngredientEntity(entryId = entryId, foodId = portion.foodId, unitId = portion.unitId, amount = portion.amount, sortOrder = order)))
            dao.entry(entryId)?.let { dao.updateEntry(it.copy(updatedAt = now())) }
        }
        foods.touch(listOf(portion.foodId))
        afterChange(entryId)
    }

    /** Removing the last ingredient deletes the entry. */
    suspend fun removeIngredient(entryId: Long, ingredientId: Long) {
        val remaining = db.tx {
            dao.deleteIngredient(ingredientId)
            dao.ingredientsOf(entryId).size
        }
        if (remaining == 0) deleteEntry(entryId) else afterChange(entryId)
    }

    suspend fun deleteEntry(entryId: Long) {
        dao.deleteEntry(entryId)
        sink.remove(entryId)
    }

    suspend fun moveEntry(entryId: Long, date: LocalDate, slotId: String) {
        val e = dao.entry(entryId) ?: return
        dao.updateEntry(e.copy(date = date.toEpochDay(), slotId = slotId, sortOrder = dao.nextSortOrder(date.toEpochDay(), slotId), updatedAt = now()))
        afterChange(entryId)
    }

    /**
     * Copies entries to [toDate] as new, planned (not eaten) entries with their own ingredient copies. They keep
     * their meal slot unless [toSlot] is given. Returns the new ids (for Undo).
     */
    suspend fun copy(entries: List<PlanEntry>, toDate: LocalDate, toSlot: String? = null): List<Long> = db.tx {
        entries.map { e ->
            val slot = toSlot ?: e.slotId
            val id = dao.insertEntry(
                PlanEntryEntity(date = toDate.toEpochDay(), slotId = slot, kind = e.kind.name, name = e.name,
                    templateId = e.templateId, eaten = false, sortOrder = dao.nextSortOrder(toDate.toEpochDay(), slot),
                    templateSnapshotJson = e.templateSnapshot?.let { DbJson.encodeKeys(it) }, updatedAt = now()),
            )
            dao.insertIngredients(e.portions.mapIndexed { i, p -> EntryIngredientEntity(entryId = id, foodId = p.food.id, unitId = p.unit.id, amount = p.amount, sortOrder = i) })
            id
        }
    }

    suspend fun deleteEntries(ids: List<Long>) = ids.forEach { deleteEntry(it) }

    /**
     * "Save changes to template…": the template takes this entry's ingredients. Other planned entries keep their
     * own copies. This entry's snapshot is refreshed so it no longer shows "edited".
     */
    suspend fun saveEntryToTemplate(entryId: Long) {
        val e = dao.entry(entryId) ?: return
        val templateId = e.templateId ?: return
        val ing = dao.ingredientsOf(entryId)
        db.tx {
            db.recipeDao().replaceIngredients(templateId, ing.mapIndexed { i, r -> RecipeIngredientEntity(recipeId = templateId, foodId = r.foodId, unitId = r.unitId, amount = r.amount, sortOrder = i) })
            val keys = ing.map { dev.jandoant.fuel.core.domain.PortionKey(it.foodId, it.unitId, it.amount) }
            dao.updateEntry(e.copy(templateSnapshotJson = DbJson.encodeKeys(keys)))
        }
    }

    // --- Meal slots ---

    /** Optional Extra slot: on/off, name and position (placed after [afterSlotId]; null = before Breakfast). */
    suspend fun configureExtraSlot(enabled: Boolean, name: String, afterSlotId: String?) {
        val current = dao.slots().map { it.toDomain() }
        val extra = current.firstOrNull { it.isExtra } ?: MealSlot.DEFAULTS.first { it.isExtra }
        dao.upsertSlots(listOf(extra.copy(enabled = enabled, name = name.trim().ifBlank { "Extra" }, order = MealSlot.extraOrderAfter(afterSlotId)).toEntity()))
    }

    private suspend fun afterChange(entryId: Long) {
        val e = entry(entryId) ?: return
        if (e.eaten) sink.upsert(e) else sink.remove(entryId)
    }

    companion object {
        fun assemble(entries: List<PlanEntryEntity>, ingredients: List<EntryIngredientEntity>, catalog: Map<Long, Food>): List<PlanEntry> {
            val byEntry = ingredients.groupBy { it.entryId }
            return entries.map { e ->
                val portions = byEntry[e.id].orEmpty().sortedBy { it.sortOrder }.mapNotNull { i ->
                    val food = catalog[i.foodId] ?: return@mapNotNull null
                    Portion(i.id, food, food.unit(i.unitId), i.amount)
                }
                PlanEntry(
                    id = e.id,
                    date = LocalDate.ofEpochDay(e.date),
                    slotId = e.slotId,
                    kind = EntryKind.valueOf(e.kind),
                    name = e.name,
                    templateId = e.templateId,
                    portions = portions,
                    eaten = e.eaten,
                    sortOrder = e.sortOrder,
                    templateSnapshot = e.templateSnapshotJson?.let { DbJson.decodeKeys(it) },
                )
            }
        }
    }
}

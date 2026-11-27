package dev.jandoant.fuel.data.repo

import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.FoodSource
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.core.model.UnitSource
import dev.jandoant.fuel.data.db.DbJson
import dev.jandoant.fuel.data.db.FoodEntity
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.db.ServingUnitEntity
import dev.jandoant.fuel.data.off.OffProduct
import dev.jandoant.fuel.data.off.OffResult
import dev.jandoant.fuel.data.off.OpenFoodFactsClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

fun ServingUnitEntity.toDomain() = ServingUnit(id, name, grams, step, min, max, isDefault, UnitSource.valueOf(source))

fun FoodEntity.toDomain(units: List<ServingUnitEntity>) = Food(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    source = FoodSource.valueOf(source),
    sourcePer100g = DbJson.decode(nutrientsJson),
    overrides = DbJson.decode(overridesJson),
    favourite = favourite,
    units = units.sortedBy { it.sortOrder }.map { it.toDomain() }.ifEmpty { listOf(ServingUnit.grams(isDefault = true)) },
    lastUsed = lastUsedAt?.let { java.time.Instant.ofEpochMilli(it) },
)

/** A unit draft before it has an id (creating foods, adding units). */
data class UnitDraft(val name: String, val grams: Double, val step: Double, val min: Double, val max: Double?, val isDefault: Boolean, val source: UnitSource)

class FoodRepository(
    private val db: FuelDatabase,
    private val off: OpenFoodFactsClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.foodDao()

    /** Every food including hidden ones, keyed by id — plan entries and recipes resolve their foods here. */
    val catalog: Flow<Map<Long, Food>> = combine(dao.observeAllIncludingHidden(), dao.observeAllUnits()) { foods, units ->
        val byFood = units.groupBy { it.foodId }
        foods.associate { it.id to it.toDomain(byFood[it.id].orEmpty()) }
    }

    val visible: Flow<List<Food>> = combine(dao.observeAll(), catalog) { list, cat -> list.mapNotNull { cat[it.id] } }

    val recent: Flow<List<Food>> = combine(dao.observeRecent(), catalog) { list, cat -> list.mapNotNull { cat[it.id] } }
    val favourites: Flow<List<Food>> = combine(dao.observeFavourites(), catalog) { list, cat -> list.mapNotNull { cat[it.id] } }
    val custom: Flow<List<Food>> = combine(dao.observeCustom(), catalog) { list, cat -> list.mapNotNull { cat[it.id] } }

    fun observe(id: Long): Flow<Food?> = catalog.map { it[id] }

    suspend fun get(id: Long): Food? {
        val f = dao.byId(id) ?: return null
        return f.toDomain(dao.unitsFor(listOf(id)))
    }

    suspend fun searchLocal(q: String): List<Food> {
        val list = dao.search(q.trim())
        val units = dao.unitsFor(list.map { it.id }).groupBy { it.foodId }
        return list.map { it.toDomain(units[it.id].orEmpty()) }
    }

    /** Barcode lookup: local cache first (works offline), then Open Food Facts. */
    suspend fun byBarcode(barcode: String, country: String): OffResult<Food> {
        dao.byBarcode(barcode)?.let { return OffResult.Ok(it.toDomain(dao.unitsFor(listOf(it.id)))) }
        return when (val r = off.product(barcode, country)) {
            is OffResult.Ok -> OffResult.Ok(store(r.value))
            OffResult.NotFound -> OffResult.NotFound
            is OffResult.Error -> r
        }
    }

    /**
     * Re-fetches cached Open Food Facts foods that have no energy value (cached from incomplete search results
     * before 2026-11-27). Entries reference the food, so they show the right numbers afterwards. Returns the count.
     */
    suspend fun repairIncomplete(): Int {
        val broken = dao.all().filter { it.source == FoodSource.OPEN_FOOD_FACTS.name && it.barcode != null && !DbJson.decode(it.nutrientsJson).has(dev.jandoant.fuel.core.model.Nutrient.ENERGY) }
        var fixed = 0
        for (f in broken) {
            val r = off.product(f.barcode!!) as? OffResult.Ok ?: continue
            if (r.value.hasNutrition) { store(r.value); fixed++ }
        }
        return fixed
    }

    suspend fun searchOff(q: String, country: String): OffResult<List<OffProduct>> = off.search(q, country)

    /** Caches a product from Open Food Facts (or returns the cached one with the same barcode). */
    suspend fun cache(p: OffProduct): Food {
        val full = if (p.hasNutrition) p else (off.product(p.barcode) as? OffResult.Ok)?.value ?: p
        return store(full)
    }

    private suspend fun store(p: OffProduct): Food = db.tx {
        val existing = dao.byBarcode(p.barcode)
        val id = if (existing != null) {
            // Refresh source values; local overrides and units stay.
            dao.update(existing.copy(name = p.name, brand = p.brand, nutrientsJson = DbJson.encode(p.per100g), hidden = false))
            existing.id
        } else {
            val newId = dao.insert(
                FoodEntity(
                    name = p.name, brand = p.brand, barcode = p.barcode, source = FoodSource.OPEN_FOOD_FACTS.name,
                    nutrientsJson = DbJson.encode(p.per100g), createdAt = now(),
                ),
            )
            dao.insertUnits(defaultUnitsFor(p).mapIndexed { i, u -> u.toEntity(newId, i) })
            newId
        }
        get(id)!!
    }

    suspend fun createCustom(name: String, brand: String?, per100g: Nutrients, units: List<UnitDraft>): Long = db.tx {
        val id = dao.insert(
            FoodEntity(name = name.trim(), brand = brand?.trim()?.ifBlank { null }, source = FoodSource.CUSTOM.name,
                nutrientsJson = DbJson.encode(per100g), createdAt = now()),
        )
        val all = (listOf(gramsDraft(isDefault = units.none { it.isDefault })) + units.filter { it.grams != 1.0 || it.name != "g" })
        dao.insertUnits(all.mapIndexed { i, u -> u.toEntity(id, i) })
        id
    }

    suspend fun updateCustom(id: Long, name: String, brand: String?, per100g: Nutrients) {
        val f = dao.byId(id) ?: return
        dao.update(f.copy(name = name.trim(), brand = brand?.trim()?.ifBlank { null }, nutrientsJson = DbJson.encode(per100g)))
    }

    /** "Fill in": local override for missing or wrong values. */
    suspend fun setOverrides(id: Long, overrides: Nutrients) {
        val f = dao.byId(id) ?: return
        dao.update(f.copy(overridesJson = DbJson.encode(overrides)))
    }

    suspend fun setFavourite(id: Long, fav: Boolean) = dao.setFavourite(id, fav)

    /** Foods stay in the database (entries reference them) but disappear from lists. */
    suspend fun hide(id: Long) = dao.hide(id)

    suspend fun touch(ids: Collection<Long>) = dao.touch(ids, now())

    // --- Serving units (PRD §7.3) ---

    suspend fun addUnit(foodId: Long, name: String, grams: Double) {
        val existing = dao.unitsFor(listOf(foodId))
        val step = Amounts.suggestedStep(grams)
        dao.insertUnit(
            UnitDraft(name.trim().ifBlank { "unit" }, grams, step, step, null, false, UnitSource.USER)
                .toEntity(foodId, (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1),
        )
    }

    /** Saves edits; the gram unit keeps its name and weight (only step and range are editable). */
    suspend fun updateUnit(unit: ServingUnit, foodId: Long) {
        val current = dao.unitsFor(listOf(foodId))
        val row = current.firstOrNull { it.id == unit.id } ?: return
        val locked = row.source == UnitSource.BUILT_IN.name
        val min = unit.min.coerceAtLeast(0.0)
        val max = unit.max?.coerceAtLeast(min)
        dao.updateUnits(
            listOf(
                row.copy(
                    name = if (locked) row.name else unit.name.trim().ifBlank { row.name },
                    grams = if (locked) row.grams else unit.grams.coerceAtLeast(0.01),
                    step = unit.step.coerceAtLeast(0.01),
                    min = min,
                    max = max,
                ),
            ),
        )
    }

    suspend fun setDefaultUnit(foodId: Long, unitId: Long) {
        val units = dao.unitsFor(listOf(foodId))
        dao.updateUnits(units.map { it.copy(isDefault = it.id == unitId) })
    }

    /** Returns false when the unit cannot be removed (the gram unit, or still used by entries/recipes). */
    suspend fun removeUnit(foodId: Long, unitId: Long): Boolean = db.tx {
        val units = dao.unitsFor(listOf(foodId))
        val row = units.firstOrNull { it.id == unitId } ?: return@tx false
        if (row.source == UnitSource.BUILT_IN.name) return@tx false
        if (dao.entryUsesOfUnit(unitId) > 0 || dao.recipeUsesOfUnit(unitId) > 0) return@tx false
        dao.deleteUnit(row)
        if (row.isDefault) units.firstOrNull { it.source == UnitSource.BUILT_IN.name }?.let { setDefaultUnit(foodId, it.id) }
        true
    }

    companion object {
        fun gramsDraft(isDefault: Boolean) = UnitDraft("g", 1.0, 10.0, 1.0, 2000.0, isDefault, UnitSource.BUILT_IN)

        /** Gram unit plus serving and package units from Open Food Facts; the serving is the default. */
        fun defaultUnitsFor(p: OffProduct): List<UnitDraft> {
            val out = mutableListOf(gramsDraft(isDefault = p.servingGrams == null))
            p.servingGrams?.let { g ->
                out += UnitDraft("serving (${g.roundToInt()} g)", g, 0.5, 0.5, 10.0, true, UnitSource.OPEN_FOOD_FACTS)
            }
            p.packageGrams?.let { g ->
                if (g != p.servingGrams && g > 1) {
                    out += UnitDraft("package (${g.roundToInt()} g)", g, 0.25, 0.25, 4.0, false, UnitSource.OPEN_FOOD_FACTS)
                }
            }
            return out
        }

        fun UnitDraft.toEntity(foodId: Long, order: Int) =
            ServingUnitEntity(0, foodId, name, grams, step, min, max, isDefault, source.name, order)
    }
}

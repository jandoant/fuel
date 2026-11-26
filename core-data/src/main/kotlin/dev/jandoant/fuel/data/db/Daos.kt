package dev.jandoant.fuel.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food WHERE hidden = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food")
    fun observeAllIncludingHidden(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food")
    suspend fun all(): List<FoodEntity>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM food WHERE id = :id")
    fun observe(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM food WHERE id IN (:ids)")
    suspend fun byIds(ids: Collection<Long>): List<FoodEntity>

    @Query("SELECT * FROM food WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM food WHERE hidden = 0 AND (name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%') ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 30): List<FoodEntity>

    @Query("SELECT * FROM food WHERE hidden = 0 AND lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE hidden = 0 AND favourite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavourites(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE hidden = 0 AND source = 'CUSTOM' ORDER BY name COLLATE NOCASE")
    fun observeCustom(): Flow<List<FoodEntity>>

    @Insert suspend fun insert(food: FoodEntity): Long
    @Update suspend fun update(food: FoodEntity)

    @Query("UPDATE food SET lastUsedAt = :at WHERE id IN (:ids)")
    suspend fun touch(ids: Collection<Long>, at: Long)

    @Query("UPDATE food SET favourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: Long, fav: Boolean)

    @Query("UPDATE food SET hidden = 1 WHERE id = :id")
    suspend fun hide(id: Long)

    @Query("SELECT * FROM serving_unit WHERE foodId IN (:foodIds) ORDER BY sortOrder")
    suspend fun unitsFor(foodIds: Collection<Long>): List<ServingUnitEntity>

    @Query("SELECT * FROM serving_unit ORDER BY foodId, sortOrder")
    fun observeAllUnits(): Flow<List<ServingUnitEntity>>

    @Query("SELECT * FROM serving_unit WHERE foodId = :foodId ORDER BY sortOrder")
    fun observeUnits(foodId: Long): Flow<List<ServingUnitEntity>>

    @Query("SELECT * FROM serving_unit")
    suspend fun allUnits(): List<ServingUnitEntity>

    @Insert suspend fun insertUnits(units: List<ServingUnitEntity>): List<Long>
    @Insert suspend fun insertUnit(unit: ServingUnitEntity): Long
    @Update suspend fun updateUnits(units: List<ServingUnitEntity>)
    @Delete suspend fun deleteUnit(unit: ServingUnitEntity)

    @Query("SELECT COUNT(*) FROM entry_ingredient WHERE unitId = :unitId")
    suspend fun entryUsesOfUnit(unitId: Long): Int

    @Query("SELECT COUNT(*) FROM recipe_ingredient WHERE unitId = :unitId")
    suspend fun recipeUsesOfUnit(unitId: Long): Int
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe")
    suspend fun all(): List<RecipeEntity>

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun byId(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipe_ingredient ORDER BY recipeId, sortOrder")
    fun observeAllIngredients(): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_ingredient")
    suspend fun allIngredients(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :id ORDER BY sortOrder")
    suspend fun ingredients(id: Long): List<RecipeIngredientEntity>

    @Query("SELECT templateId AS id, COUNT(*) AS n FROM plan_entry WHERE templateId IS NOT NULL GROUP BY templateId")
    fun observeUsage(): Flow<List<IdCount>>

    @Query("SELECT COUNT(*) FROM plan_entry WHERE templateId = :id")
    suspend fun usage(id: Long): Int

    @Insert suspend fun insert(r: RecipeEntity): Long
    @Update suspend fun update(r: RecipeEntity)

    @Query("DELETE FROM recipe WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :id")
    suspend fun clearIngredients(id: Long)

    @Insert suspend fun insertIngredients(list: List<RecipeIngredientEntity>)

    @Transaction
    suspend fun replaceIngredients(id: Long, list: List<RecipeIngredientEntity>) {
        clearIngredients(id)
        insertIngredients(list)
    }
}

data class IdCount(val id: Long, val n: Int)

@Dao
interface PlanDao {
    @Query("SELECT * FROM meal_slot ORDER BY displayOrder")
    fun observeSlots(): Flow<List<MealSlotEntity>>

    @Query("SELECT * FROM meal_slot")
    suspend fun slots(): List<MealSlotEntity>

    @Upsert suspend fun upsertSlots(slots: List<MealSlotEntity>)

    @Query("SELECT * FROM plan_entry WHERE date BETWEEN :from AND :to ORDER BY date, sortOrder, id")
    fun observeEntries(from: Long, to: Long): Flow<List<PlanEntryEntity>>

    @Query("SELECT * FROM plan_entry WHERE date BETWEEN :from AND :to ORDER BY date, sortOrder, id")
    suspend fun entries(from: Long, to: Long): List<PlanEntryEntity>

    @Query("SELECT * FROM plan_entry")
    suspend fun allEntries(): List<PlanEntryEntity>

    @Query("SELECT * FROM plan_entry")
    fun observeAllEntries(): Flow<List<PlanEntryEntity>>

    @Query("SELECT * FROM entry_ingredient")
    fun observeAllIngredients(): Flow<List<EntryIngredientEntity>>

    @Query("SELECT * FROM plan_entry WHERE id = :id")
    suspend fun entry(id: Long): PlanEntryEntity?

    @Query("SELECT * FROM entry_ingredient WHERE entryId IN (SELECT id FROM plan_entry WHERE date BETWEEN :from AND :to) ORDER BY entryId, sortOrder")
    fun observeIngredients(from: Long, to: Long): Flow<List<EntryIngredientEntity>>

    @Query("SELECT * FROM entry_ingredient WHERE entryId IN (SELECT id FROM plan_entry WHERE date BETWEEN :from AND :to) ORDER BY entryId, sortOrder")
    suspend fun ingredients(from: Long, to: Long): List<EntryIngredientEntity>

    @Query("SELECT * FROM entry_ingredient WHERE entryId = :entryId ORDER BY sortOrder")
    suspend fun ingredientsOf(entryId: Long): List<EntryIngredientEntity>

    @Query("SELECT * FROM entry_ingredient")
    suspend fun allIngredients(): List<EntryIngredientEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM plan_entry WHERE date = :date AND slotId = :slotId")
    suspend fun nextSortOrder(date: Long, slotId: String): Int

    @Insert suspend fun insertEntry(e: PlanEntryEntity): Long
    @Update suspend fun updateEntry(e: PlanEntryEntity)

    @Query("DELETE FROM plan_entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Insert suspend fun insertIngredients(list: List<EntryIngredientEntity>)
    @Update suspend fun updateIngredient(i: EntryIngredientEntity)

    @Query("DELETE FROM entry_ingredient WHERE id = :id")
    suspend fun deleteIngredient(id: Long)

    @Query("SELECT DISTINCT date FROM plan_entry WHERE eaten = 1")
    fun observeLoggedDays(): Flow<List<Long>>
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM measurement ORDER BY time")
    fun observeAll(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement ORDER BY time")
    suspend fun all(): List<MeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<MeasurementEntity>): List<Long>

    @Query("DELETE FROM measurement WHERE recordId IN (:ids)")
    suspend fun deleteByRecordIds(ids: List<String>)

    @Query("SELECT * FROM garmin_energy ORDER BY date")
    fun observeGarmin(): Flow<List<GarminEnergyEntity>>

    @Query("SELECT * FROM garmin_energy")
    suspend fun allGarmin(): List<GarminEnergyEntity>

    @Upsert suspend fun upsertGarmin(list: List<GarminEnergyEntity>)

    @Query("SELECT origin, type, COUNT(*) AS n, MIN(time) AS firstTime, MAX(time) AS lastTime FROM measurement GROUP BY origin, type")
    suspend fun originStats(): List<OriginStat>
}

data class OriginStat(val origin: String, val type: String, val n: Int, val firstTime: Long, val lastTime: Long)

@Dao
interface ModelDao {
    @Query("SELECT * FROM phase ORDER BY startDate")
    fun observePhases(): Flow<List<PhaseEntity>>

    @Query("SELECT * FROM phase ORDER BY startDate")
    suspend fun phases(): List<PhaseEntity>

    @Insert suspend fun insertPhase(p: PhaseEntity): Long
    @Update suspend fun updatePhase(p: PhaseEntity)

    @Upsert suspend fun upsertSnapshots(list: List<ModelSnapshotEntity>)

    @Query("SELECT * FROM model_snapshot ORDER BY date")
    fun observeSnapshots(): Flow<List<ModelSnapshotEntity>>

    @Query("SELECT * FROM model_snapshot")
    suspend fun allSnapshots(): List<ModelSnapshotEntity>

    @Query("SELECT * FROM forecast ORDER BY weekEnd")
    fun observeForecasts(): Flow<List<ForecastEntity>>

    @Query("SELECT * FROM forecast")
    suspend fun allForecasts(): List<ForecastEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertForecast(f: ForecastEntity)

    @Query("UPDATE forecast SET actualKg = :actual WHERE weekEnd = :weekEnd")
    suspend fun setForecastOutcome(weekEnd: Long, actual: Double)

    @Upsert suspend fun upsertForecasts(list: List<ForecastEntity>)
}

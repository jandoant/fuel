package dev.jandoant.fuel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.PortionKey
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.Nutrients
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Database(
    entities = [
        FoodEntity::class, ServingUnitEntity::class, RecipeEntity::class, RecipeIngredientEntity::class,
        MealSlotEntity::class, PlanEntryEntity::class, EntryIngredientEntity::class, MeasurementEntity::class,
        GarminEnergyEntity::class, PhaseEntity::class, ModelSnapshotEntity::class, ForecastEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FuelDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun recipeDao(): RecipeDao
    abstract fun planDao(): PlanDao
    abstract fun bodyDao(): BodyDao
    abstract fun modelDao(): ModelDao

    /** Inserts the default meal slots if missing (also after an import of an older backup). */
    suspend fun ensureSeeded() {
        val existing = planDao().slots().map { it.id }.toSet()
        val missing = MealSlot.DEFAULTS.filter { it.id !in existing }
        if (missing.isNotEmpty()) planDao().upsertSlots(missing.map { it.toEntity() })
    }

    suspend fun <T> tx(block: suspend () -> T): T = withTransaction(block)

    companion object {
        fun build(context: Context, inMemory: Boolean = false): FuelDatabase {
            val b = if (inMemory) Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java)
            else Room.databaseBuilder(context, FuelDatabase::class.java, "fuel.db")
            return b.build()
        }
    }
}

fun MealSlot.toEntity() = MealSlotEntity(id, name, order, enabled, isExtra)
fun MealSlotEntity.toDomain() = MealSlot(id, name, displayOrder, enabled, isExtra)

/** JSON helpers for nutrient maps and template snapshots. */
object DbJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mapSer = MapSerializer(String.serializer(), Double.serializer())

    fun encode(n: Nutrients): String = json.encodeToString(mapSer, n.values.mapKeys { it.key.name })
    fun decode(s: String): Nutrients = Nutrients(
        json.decodeFromString(mapSer, s).mapNotNull { (k, v) -> Nutrient.fromKey(k)?.let { it to v } }.toMap(),
    )

    @Serializable
    private data class Key(val f: Long, val u: Long, val a: Double)

    private val keysSer = ListSerializer(serializer<Key>())
    fun encodeKeys(keys: List<PortionKey>): String = json.encodeToString(keysSer, keys.map { Key(it.foodId, it.unitId, it.amount) })
    fun decodeKeys(s: String): List<PortionKey> = json.decodeFromString(keysSer, s).map { PortionKey(it.f, it.u, it.a) }
}

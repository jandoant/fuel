package dev.jandoant.fuel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Room schema (PRD §5.1). Every entity is also @Serializable: the JSON backup is a dump of these rows.
 * Nutrient maps are stored as JSON text keyed by `Nutrient.name` (absent = unknown).
 */

@Serializable
@Entity(tableName = "food", indices = [Index("barcode"), Index("name")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    /** FoodSource name. */
    val source: String,
    val nutrientsJson: String,
    val overridesJson: String = "{}",
    val favourite: Boolean = false,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    /** Hidden foods (deleted by the user but still referenced by entries) do not show in lists. */
    val hidden: Boolean = false,
)

@Serializable
@Entity(
    tableName = "serving_unit",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("foodId")],
)
data class ServingUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val name: String,
    val grams: Double,
    val step: Double,
    val min: Double,
    val max: Double?,
    val isDefault: Boolean,
    /** UnitSource name. */
    val source: String,
    val sortOrder: Int,
)

@Serializable
@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Comma-separated RecipeTag names. */
    val tags: String = "",
    val favourite: Boolean = false,
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId"), Index("foodId")],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val foodId: Long,
    val unitId: Long,
    val amount: Double,
    val sortOrder: Int,
)

@Serializable
@Entity(tableName = "meal_slot")
data class MealSlotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayOrder: Int,
    val enabled: Boolean,
    val isExtra: Boolean,
)

@Serializable
@Entity(tableName = "plan_entry", indices = [Index("date"), Index("templateId")])
data class PlanEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day. */
    val date: Long,
    val slotId: String,
    /** EntryKind name. */
    val kind: String,
    val name: String,
    val templateId: Long? = null,
    val eaten: Boolean = false,
    val sortOrder: Int = 0,
    /** JSON list of PortionKey at planning time (recipes only). */
    val templateSnapshotJson: String? = null,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "entry_ingredient",
    foreignKeys = [ForeignKey(entity = PlanEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId"), Index("foodId")],
)
data class EntryIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val foodId: Long,
    val unitId: Long,
    val amount: Double,
    val sortOrder: Int,
)

@Serializable
@Entity(tableName = "measurement", indices = [Index(value = ["type", "time", "origin"], unique = true), Index("time")])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis. */
    val time: Long,
    /** MeasurementType name. */
    val type: String,
    val value: Double,
    val origin: String,
    /** Health Connect record id, used to apply deletions. */
    val recordId: String? = null,
)

@Serializable
@Entity(tableName = "garmin_energy")
data class GarminEnergyEntity(
    /** Epoch day. */
    @PrimaryKey val date: Long,
    val totalKcal: Double,
    val activeKcal: Double,
    val origin: String,
)

@Serializable
@Entity(tableName = "phase")
data class PhaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: Long,
    val endDate: Long? = null,
    val startingMaintenance: Double,
    val scaleHistoryOnly: Boolean = false,
)

/** Daily model output (PRD §5.1 ModelSnapshot), kept for charts across phases. */
@Serializable
@Entity(tableName = "model_snapshot")
data class ModelSnapshotEntity(
    @PrimaryKey val date: Long,
    val phaseId: Long,
    val trendKg: Double?,
    val maintenance: Double,
    val maintenanceSd: Double,
    val zoneLow: Double?,
    val zoneHigh: Double?,
    val target: Double?,
    val proteinMin: Int?,
    val proteinMax: Int?,
    val fatMin: Int?,
    val fatMax: Int?,
    val fiberMin: Int?,
    val targetHeld: Boolean,
)

/** Weekly forecast, frozen when first stored; the outcome is filled in once the week is over. */
@Serializable
@Entity(tableName = "forecast")
data class ForecastEntity(
    /** Epoch day of the week's last day. */
    @PrimaryKey val weekEnd: Long,
    val expectedKg: Double,
    val actualKg: Double? = null,
)

package dev.jandoant.fuel.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.jandoant.fuel.data.db.EntryIngredientEntity
import dev.jandoant.fuel.data.db.FoodEntity
import dev.jandoant.fuel.data.db.ForecastEntity
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.db.GarminEnergyEntity
import dev.jandoant.fuel.data.db.MealSlotEntity
import dev.jandoant.fuel.data.db.MeasurementEntity
import dev.jandoant.fuel.data.db.ModelSnapshotEntity
import dev.jandoant.fuel.data.db.PhaseEntity
import dev.jandoant.fuel.data.db.PlanEntryEntity
import dev.jandoant.fuel.data.db.RecipeEntity
import dev.jandoant.fuel.data.db.RecipeIngredientEntity
import dev.jandoant.fuel.data.db.ServingUnitEntity
import dev.jandoant.fuel.data.settings.SettingsRepository
import dev.jandoant.fuel.data.settings.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The whole database plus settings, as one JSON document (PRD §9 Backup). */
@Serializable
data class BackupDocument(
    val format: Int = FORMAT,
    val createdAt: Long,
    val settings: UserSettings,
    val foods: List<FoodEntity>,
    val units: List<ServingUnitEntity>,
    val recipes: List<RecipeEntity>,
    val recipeIngredients: List<RecipeIngredientEntity>,
    val slots: List<MealSlotEntity>,
    val entries: List<PlanEntryEntity>,
    val entryIngredients: List<EntryIngredientEntity>,
    val measurements: List<MeasurementEntity>,
    val garmin: List<GarminEnergyEntity>,
    val phases: List<PhaseEntity>,
    val snapshots: List<ModelSnapshotEntity>,
    val forecasts: List<ForecastEntity>,
) {
    companion object {
        const val FORMAT = 1
    }
}

class BackupRepository(
    private val context: Context,
    private val db: FuelDatabase,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun snapshot(): BackupDocument = withContext(Dispatchers.IO) {
        db.tx {
            BackupDocument(
                createdAt = now(),
                settings = settings.current().copy(backupFolderUri = null, lastBackupAt = null, lastSyncAt = null, lastSyncError = null),
                foods = db.foodDao().all(),
                units = db.foodDao().allUnits(),
                recipes = db.recipeDao().all(),
                recipeIngredients = db.recipeDao().allIngredients(),
                slots = db.planDao().slots(),
                entries = db.planDao().allEntries(),
                entryIngredients = db.planDao().allIngredients(),
                measurements = db.bodyDao().all(),
                garmin = db.bodyDao().allGarmin(),
                phases = db.modelDao().phases(),
                snapshots = db.modelDao().allSnapshots(),
                forecasts = db.modelDao().allForecasts(),
            )
        }
    }

    fun encode(doc: BackupDocument): String = json.encodeToString(serializer<BackupDocument>(), doc)
    fun decode(text: String): BackupDocument = json.decodeFromString(serializer<BackupDocument>(), text)

    /** Manual export to a file the user picked (CreateDocument). */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = encode(snapshot())
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }

    /** Restores the full database from a backup file; the device-specific settings (backup folder) are kept. */
    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        restore(decode(text))
    }

    suspend fun restore(doc: BackupDocument) {
        require(doc.format <= BackupDocument.FORMAT) { "Backup was made by a newer version of Fuel" }
        db.tx {
            val sql = db.openHelper.writableDatabase
            listOf(
                "entry_ingredient", "plan_entry", "recipe_ingredient", "recipe", "serving_unit", "food", "meal_slot",
                "measurement", "garmin_energy", "model_snapshot", "forecast", "phase",
            ).forEach { sql.execSQL("DELETE FROM $it") }
            db.foodDao().let { d -> doc.foods.forEach { d.insert(it) }; if (doc.units.isNotEmpty()) d.insertUnits(doc.units) }
            db.recipeDao().let { d -> doc.recipes.forEach { d.insert(it) }; if (doc.recipeIngredients.isNotEmpty()) d.insertIngredients(doc.recipeIngredients) }
            db.planDao().let { d ->
                d.upsertSlots(doc.slots)
                doc.entries.forEach { d.insertEntry(it) }
                if (doc.entryIngredients.isNotEmpty()) d.insertIngredients(doc.entryIngredients)
            }
            db.bodyDao().let { d -> d.insertAll(doc.measurements); d.upsertGarmin(doc.garmin) }
            db.modelDao().let { d -> doc.phases.forEach { d.insertPhase(it) }; d.upsertSnapshots(doc.snapshots); d.upsertForecasts(doc.forecasts) }
        }
        db.ensureSeeded()
        val keep = settings.current()
        settings.replace(doc.settings.copy(backupFolderUri = keep.backupFolderUri, backupKeep = keep.backupKeep, lastBackupAt = keep.lastBackupAt))
    }

    /**
     * Daily automatic backup into the folder chosen via SAF; keeps the newest [UserSettings.backupKeep] files.
     * Returns the file name written.
     */
    suspend fun backupToFolder(): String = withContext(Dispatchers.IO) {
        val s = settings.current()
        val folderUri = s.backupFolderUri ?: error("No backup folder chosen")
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: error("Backup folder not accessible")
        val name = "fuel-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".json"
        val file = folder.createFile("application/json", name) ?: error("Could not create $name")
        context.contentResolver.openOutputStream(file.uri, "wt")!!.use { it.write(encode(snapshot()).toByteArray()) }
        prune(folder, s.backupKeep)
        settings.update { it.copy(lastBackupAt = now()) }
        name
    }

    private fun prune(folder: DocumentFile, keep: Int) {
        val backups = folder.listFiles().filter { it.name?.startsWith("fuel-backup-") == true && it.name!!.endsWith(".json") }
        backups.sortedByDescending { it.name }.drop(keep.coerceAtLeast(1)).forEach { it.delete() }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    }
}

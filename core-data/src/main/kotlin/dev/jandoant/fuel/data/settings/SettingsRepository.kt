package dev.jandoant.fuel.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jandoant.fuel.core.model.GoalRange
import dev.jandoant.fuel.core.model.MacroSettings
import dev.jandoant.fuel.core.model.ModelDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Everything the user can configure (PRD §5.1 Settings). Starting maintenance lives on the phase. */
@Serializable
data class UserSettings(
    val goalLowPct: Double = -0.25,
    val goalHighPct: Double = -0.5,
    val goalWeightKg: Double? = null,
    val referenceWindowDays: Int = 14,
    val proteinMinGPerKg: Double = 1.8,
    val proteinMaxGPerKg: Double = 2.0,
    val fatMinGPerKg: Double = 0.5,
    val fatMaxGPerKg: Double = 0.8,
    val fiberMinG: Int = 30,
    val maxDailyChangeKcal: Double = 50.0,
    val calorieFloorKcal: Double = 1800.0,
    val garminCalibration: Boolean = false,
    val offCountry: String = "de",
    val backupFolderUri: String? = null,
    val backupKeep: Int = 14,
    val lastBackupAt: Long? = null,
    val lastSyncAt: Long? = null,
    val lastSyncError: String? = null,
) {
    val goal: GoalRange get() = GoalRange(goalLowPct, goalHighPct)

    fun modelDefaults(): ModelDefaults = ModelDefaults(
        maxDailyTargetChangeKcal = maxDailyChangeKcal,
        calorieFloorKcal = calorieFloorKcal,
        macros = MacroSettings(
            proteinGPerKg = proteinMinGPerKg..maxOf(proteinMinGPerKg, proteinMaxGPerKg),
            fatGPerKg = fatMinGPerKg..maxOf(fatMinGPerKg, fatMaxGPerKg),
            fiberMinG = fiberMinG,
            referenceWindowDays = referenceWindowDays.takeIf { it in setOf(7, 14, 28) } ?: 14,
        ),
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    private object K {
        val goalLow = doublePreferencesKey("goal_low")
        val goalHigh = doublePreferencesKey("goal_high")
        val goalWeight = doublePreferencesKey("goal_weight")
        val refWindow = intPreferencesKey("ref_window")
        val pMin = doublePreferencesKey("protein_min")
        val pMax = doublePreferencesKey("protein_max")
        val fMin = doublePreferencesKey("fat_min")
        val fMax = doublePreferencesKey("fat_max")
        val fiber = intPreferencesKey("fiber_min")
        val maxChange = doublePreferencesKey("max_change")
        val floor = doublePreferencesKey("calorie_floor")
        val garmin = booleanPreferencesKey("garmin")
        val country = stringPreferencesKey("off_country")
        val backupUri = stringPreferencesKey("backup_uri")
        val backupKeep = intPreferencesKey("backup_keep")
        val lastBackup = longPreferencesKey("last_backup")
        val lastSync = longPreferencesKey("last_sync")
        val lastSyncError = stringPreferencesKey("last_sync_error")
    }

    val settings: Flow<UserSettings> = store.data.map { fromPrefs(it) }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { p ->
            write(p, transform(fromPrefs(p)))
        }
    }

    /** Replaces everything (backup import). */
    suspend fun replace(s: UserSettings) = store.edit { p -> p.clear(); write(p, s) }

    private fun fromPrefs(p: Preferences): UserSettings {
        val d = UserSettings()
        return UserSettings(
            goalLowPct = p[K.goalLow] ?: d.goalLowPct,
            goalHighPct = p[K.goalHigh] ?: d.goalHighPct,
            goalWeightKg = p[K.goalWeight],
            referenceWindowDays = p[K.refWindow] ?: d.referenceWindowDays,
            proteinMinGPerKg = p[K.pMin] ?: d.proteinMinGPerKg,
            proteinMaxGPerKg = p[K.pMax] ?: d.proteinMaxGPerKg,
            fatMinGPerKg = p[K.fMin] ?: d.fatMinGPerKg,
            fatMaxGPerKg = p[K.fMax] ?: d.fatMaxGPerKg,
            fiberMinG = p[K.fiber] ?: d.fiberMinG,
            maxDailyChangeKcal = p[K.maxChange] ?: d.maxDailyChangeKcal,
            calorieFloorKcal = p[K.floor] ?: d.calorieFloorKcal,
            garminCalibration = p[K.garmin] ?: d.garminCalibration,
            offCountry = p[K.country] ?: d.offCountry,
            backupFolderUri = p[K.backupUri],
            backupKeep = p[K.backupKeep] ?: d.backupKeep,
            lastBackupAt = p[K.lastBackup],
            lastSyncAt = p[K.lastSync],
            lastSyncError = p[K.lastSyncError],
        )
    }

    private fun write(p: MutablePreferences, s: UserSettings) {
        p[K.goalLow] = s.goalLowPct
        p[K.goalHigh] = s.goalHighPct
        if (s.goalWeightKg != null) p[K.goalWeight] = s.goalWeightKg else p.remove(K.goalWeight)
        p[K.refWindow] = s.referenceWindowDays
        p[K.pMin] = s.proteinMinGPerKg
        p[K.pMax] = s.proteinMaxGPerKg
        p[K.fMin] = s.fatMinGPerKg
        p[K.fMax] = s.fatMaxGPerKg
        p[K.fiber] = s.fiberMinG
        p[K.maxChange] = s.maxDailyChangeKcal
        p[K.floor] = s.calorieFloorKcal
        p[K.garmin] = s.garminCalibration
        p[K.country] = s.offCountry
        if (s.backupFolderUri != null) p[K.backupUri] = s.backupFolderUri else p.remove(K.backupUri)
        p[K.backupKeep] = s.backupKeep
        if (s.lastBackupAt != null) p[K.lastBackup] = s.lastBackupAt else p.remove(K.lastBackup)
        if (s.lastSyncAt != null) p[K.lastSync] = s.lastSyncAt else p.remove(K.lastSync)
        if (s.lastSyncError != null) p[K.lastSyncError] = s.lastSyncError else p.remove(K.lastSyncError)
    }
}

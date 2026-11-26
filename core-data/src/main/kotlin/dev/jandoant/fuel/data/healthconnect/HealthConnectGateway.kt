package dev.jandoant.fuel.data.healthconnect

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.MeasurementType
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.db.GarminEnergyEntity
import dev.jandoant.fuel.data.db.MeasurementEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

enum class HcAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

data class SyncResult(val imported: Int, val deleted: Int, val garminDays: Int, val fullImport: Boolean)

/** What Milestone 0 asks for: which record types and origins exist and how far back they go. */
data class OriginReport(val recordType: String, val origin: String, val count: Int, val first: Instant?, val last: Instant?)

private val Context.hcStore: DataStore<Preferences> by preferencesDataStore(name = "health_connect")

/**
 * Health Connect access (PRD §4.1, §4.2). Reads weigh-ins and body composition (all history with the history
 * permission), Garmin energy filtered by origin, and mirrors eaten entries as NutritionRecords.
 */
class HealthConnectGateway(
    private val context: Context,
    private val db: FuelDatabase,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val store = context.hcStore
    private val tokenKey = stringPreferencesKey("changes_token")

    fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
        else -> HcAvailability.NOT_INSTALLED
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        if (availability() != HcAvailability.AVAILABLE) emptySet() else client.permissionController.getGrantedPermissions()

    suspend fun hasReadPermissions(): Boolean = grantedPermissions().containsAll(READ_PERMISSIONS)

    suspend fun historyAvailable(): Boolean =
        availability() == HcAvailability.AVAILABLE &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    // --- Reading ---

    /**
     * Imports new weigh-ins and body composition. First run (or an expired token) reads everything Health
     * Connect returns — all history when the history permission is granted, otherwise ~30 days.
     */
    suspend fun sync(garminDays: Long = 21): SyncResult {
        check(availability() == HcAvailability.AVAILABLE) { "Health Connect is not available" }
        val granted = grantedPermissions()
        require(granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) { "Weight permission not granted" }

        var imported = 0
        var deleted = 0
        var full = false
        val token = store.data.map { it[tokenKey] }.first()
        val types = readableTypes(granted)
        var nextToken: String? = null
        if (token == null) {
            full = true
            imported += fullImport(types)
        } else {
            var t: String = token
            while (true) {
                val resp = client.getChanges(t)
                if (resp.changesTokenExpired) {
                    full = true
                    imported += fullImport(types)
                    break
                }
                val upserts = resp.changes.filterIsInstance<UpsertionChange>().mapNotNull { toEntity(it.record) }
                imported += db.bodyDao().insertAll(upserts).count { it != -1L }
                val deletions = resp.changes.filterIsInstance<DeletionChange>().map { it.recordId }
                if (deletions.isNotEmpty()) {
                    db.bodyDao().deleteByRecordIds(deletions)
                    deleted += deletions.size
                }
                t = resp.nextChangesToken
                nextToken = t
                if (!resp.hasMore) break
            }
        }
        if (full || nextToken == null) nextToken = client.getChangesToken(ChangesTokenRequest(types))
        store.edit { it[tokenKey] = nextToken!! }

        val gDays = if (granted.containsAll(GARMIN_PERMISSIONS)) syncGarmin(if (full) 400 else garminDays) else 0
        return SyncResult(imported, deleted, gDays, full)
    }

    suspend fun resetSync() = store.edit { it.remove(tokenKey) }

    private fun readableTypes(granted: Set<String>): Set<KClass<out Record>> = buildSet {
        add(WeightRecord::class)
        if (granted.contains(HealthPermission.getReadPermission(BodyFatRecord::class))) add(BodyFatRecord::class)
        if (granted.contains(HealthPermission.getReadPermission(LeanBodyMassRecord::class))) add(LeanBodyMassRecord::class)
    }

    private suspend fun fullImport(types: Set<KClass<out Record>>): Int {
        var n = 0
        for (type in types) n += db.bodyDao().insertAll(readAll(type).mapNotNull { toEntity(it) }).count { it != -1L }
        return n
    }

    private suspend fun <T : Record> readAll(type: KClass<T>): List<T> {
        val out = mutableListOf<T>()
        var page: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(type, TimeRangeFilter.after(Instant.EPOCH), pageSize = 5000, pageToken = page),
            )
            out += resp.records
            page = resp.pageToken
        } while (page != null)
        return out
    }

    private fun toEntity(r: Record): MeasurementEntity? = when (r) {
        is WeightRecord -> MeasurementEntity(time = r.time.toEpochMilli(), type = MeasurementType.WEIGHT.name, value = r.weight.inKilograms, origin = r.metadata.dataOrigin.packageName, recordId = r.metadata.id)
        is BodyFatRecord -> MeasurementEntity(time = r.time.toEpochMilli(), type = MeasurementType.BODY_FAT.name, value = r.percentage.value, origin = r.metadata.dataOrigin.packageName, recordId = r.metadata.id)
        is LeanBodyMassRecord -> MeasurementEntity(time = r.time.toEpochMilli(), type = MeasurementType.LEAN_MASS.name, value = r.mass.inKilograms, origin = r.metadata.dataOrigin.packageName, recordId = r.metadata.id)
        else -> null
    }

    /** Daily Garmin totals, only from the Garmin Connect app (other sources are not mixed in). */
    private suspend fun syncGarmin(days: Long): Int {
        val z = zone()
        val end = LocalDate.now(z).atStartOfDay()
        val start = end.minusDays(days)
        val buckets = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1),
                dataOriginFilter = setOf(DataOrigin(GARMIN_PACKAGE)),
            ),
        )
        val rows = buckets.mapNotNull { b ->
            val total = b.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: return@mapNotNull null
            val active = b.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            GarminEnergyEntity(b.startTime.toLocalDate().toEpochDay(), total, active, GARMIN_PACKAGE)
        }
        db.bodyDao().upsertGarmin(rows)
        return rows.size
    }

    /** Milestone 0 report: record types, origins, counts and date ranges as Health Connect has them. */
    suspend fun originReport(): List<OriginReport> {
        val granted = grantedPermissions()
        val out = mutableListOf<OriginReport>()
        suspend fun <T : Record> report(type: KClass<T>, name: String, time: (T) -> Instant) {
            if (!granted.contains(HealthPermission.getReadPermission(type))) return
            val records = readAll(type)
            records.groupBy { it.metadata.dataOrigin.packageName }.forEach { (origin, rs) ->
                out += OriginReport(name, origin, rs.size, rs.minOfOrNull(time), rs.maxOfOrNull(time))
            }
            if (records.isEmpty()) out += OriginReport(name, "—", 0, null, null)
        }
        report(WeightRecord::class, "Weight") { it.time }
        report(BodyFatRecord::class, "Body fat") { it.time }
        report(LeanBodyMassRecord::class, "Lean mass") { it.time }
        report(TotalCaloriesBurnedRecord::class, "Total calories") { it.startTime }
        report(ActiveCaloriesBurnedRecord::class, "Active calories") { it.startTime }
        out.forEach { Log.i(TAG, "HC origin: ${it.recordType} · ${it.origin} · ${it.count} records · ${it.first} → ${it.last}") }
        return out
    }

    // --- Writing (PRD §4.2) ---

    /** Writes or updates the NutritionRecord of an eaten entry; clientRecordId = entry id. */
    suspend fun writeEaten(entry: PlanEntry, slotName: String?) {
        if (!grantedPermissions().contains(WRITE_NUTRITION)) return
        val z = zone()
        val start = LocalDateTime.of(entry.date, mealTime(entry.slotId)).atZone(z)
        val end = start.plusMinutes(15)
        val n = entry.nutrients
        fun mass(nutrient: Nutrient, factorToGrams: Double): Mass? =
            if (entry.portions.any { it.food.per100g.has(nutrient) }) Mass.grams(n[nutrient] * factorToGrams) else null
        val record = NutritionRecord(
            startTime = start.toInstant(),
            startZoneOffset = start.offset,
            endTime = end.toInstant(),
            endZoneOffset = end.offset,
            metadata = Metadata.manualEntry(clientRecordId = clientId(entry.id), clientRecordVersion = System.currentTimeMillis()),
            energy = Energy.kilocalories(n.kcal),
            protein = Mass.grams(n.protein),
            totalFat = Mass.grams(n.fat),
            totalCarbohydrate = Mass.grams(n.carbs),
            dietaryFiber = Mass.grams(n.fiber),
            sugar = mass(Nutrient.SUGAR, 1.0),
            saturatedFat = mass(Nutrient.SAT_FAT, 1.0),
            // Salt → sodium: salt / 2.5.
            sodium = if (entry.portions.any { it.food.per100g.has(Nutrient.SALT) }) Mass.grams(n[Nutrient.SALT] / 2.5) else null,
            potassium = mass(Nutrient.POTASSIUM, 1e-3),
            calcium = mass(Nutrient.CALCIUM, 1e-3),
            magnesium = mass(Nutrient.MAGNESIUM, 1e-3),
            iron = mass(Nutrient.IRON, 1e-3),
            zinc = mass(Nutrient.ZINC, 1e-3),
            vitaminC = mass(Nutrient.VITAMIN_C, 1e-3),
            vitaminD = mass(Nutrient.VITAMIN_D, 1e-6),
            vitaminB12 = mass(Nutrient.VITAMIN_B12, 1e-6),
            folate = mass(Nutrient.FOLATE, 1e-6),
            name = entry.title,
            mealType = mealType(entry.slotId),
        )
        client.insertRecords(listOf(record))
    }

    suspend fun deleteEaten(entryId: Long) {
        if (!grantedPermissions().contains(WRITE_NUTRITION)) return
        client.deleteRecords(NutritionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(clientId(entryId)))
    }

    /** Milestone 0: one test record, deleted again right away. Returns true when both calls worked. */
    suspend fun writeTestRecord(): Boolean {
        if (!grantedPermissions().contains(WRITE_NUTRITION)) return false
        val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val offset = zone().rules.getOffset(now)
        client.insertRecords(
            listOf(
                NutritionRecord(
                    startTime = now.minusSeconds(600), startZoneOffset = offset, endTime = now, endZoneOffset = offset,
                    metadata = Metadata.manualEntry(clientRecordId = "fuel-test", clientRecordVersion = 1),
                    energy = Energy.kilocalories(1.0), name = "Fuel test record",
                ),
            ),
        )
        client.deleteRecords(NutritionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf("fuel-test"))
        return true
    }

    companion object {
        const val TAG = "FuelHealthConnect"
        const val GARMIN_PACKAGE = "com.garmin.android.apps.connectmobile"
        fun clientId(entryId: Long) = "fuel-entry-$entryId"

        val WRITE_NUTRITION = HealthPermission.getWritePermission(NutritionRecord::class)

        val READ_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        )
        val GARMIN_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
        val ALL_PERMISSIONS: Set<String> = READ_PERMISSIONS + GARMIN_PERMISSIONS + WRITE_NUTRITION +
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

        fun mealType(slotId: String): Int = when (slotId) {
            MealSlot.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
            MealSlot.LUNCH -> MealType.MEAL_TYPE_LUNCH
            MealSlot.DINNER -> MealType.MEAL_TYPE_DINNER
            MealSlot.SNACKS, MealSlot.EXTRA -> MealType.MEAL_TYPE_SNACK
            else -> MealType.MEAL_TYPE_UNKNOWN
        }

        /** Nominal time of a meal (Fuel does not record when exactly food was eaten). */
        fun mealTime(slotId: String): LocalTime = when (slotId) {
            MealSlot.BREAKFAST -> LocalTime.of(8, 0)
            MealSlot.LUNCH -> LocalTime.of(12, 30)
            MealSlot.DINNER -> LocalTime.of(19, 0)
            MealSlot.EXTRA -> LocalTime.of(17, 0)
            else -> LocalTime.of(15, 30)
        }
    }
}

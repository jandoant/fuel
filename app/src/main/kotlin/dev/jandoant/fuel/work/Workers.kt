package dev.jandoant.fuel.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.jandoant.fuel.FuelApp
import dev.jandoant.fuel.data.healthconnect.HcAvailability
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Health Connect sync (PRD §4.1): periodic and on app open. Also nudges the model to re-read "today". */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FuelApp).container
        c.model.refreshDay()
        if (c.healthConnect.availability() != HcAvailability.AVAILABLE || !c.healthConnect.hasReadPermissions()) return Result.success()
        return runCatching {
            val r = c.healthConnect.sync()
            Log.i(TAG, "Sync: +${r.imported} −${r.deleted}, garmin ${r.garminDays} days, full=${r.fullImport}")
            c.settings.update { it.copy(lastSyncAt = System.currentTimeMillis(), lastSyncError = null) }
            Result.success()
        }.getOrElse { e ->
            Log.w(TAG, "Sync failed", e)
            c.settings.update { it.copy(lastSyncError = e.message ?: e::class.simpleName) }
            Result.retry()
        }
    }

    companion object { const val TAG = "FuelSync" }
}

/** Daily JSON backup into the SAF folder (PRD §9). Skips quietly when no folder is set. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FuelApp).container
        c.model.refreshDay()
        if (c.settings.current().backupFolderUri == null) return Result.success()
        return runCatching {
            val name = c.backup.backupToFolder()
            Log.i("FuelBackup", "Wrote $name")
            Result.success()
        }.getOrElse { e ->
            Log.w("FuelBackup", "Backup failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object Jobs {
    private const val SYNC_PERIODIC = "hc-sync-periodic"
    private const val SYNC_NOW = "hc-sync-now"
    private const val BACKUP_DAILY = "backup-daily"
    private const val BACKUP_NOW = "backup-now"

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(4)).build(),
        )
        // Daily backup, first run around 03:00.
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(LocalTime.of(3, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        wm.enqueueUniquePeriodicWork(
            BACKUP_DAILY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(Duration.ofDays(1))
                .setInitialDelay(Duration.between(now, next))
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncWorker>().build())
    }

    fun backupNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            BACKUP_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()).build(),
        )
    }
}

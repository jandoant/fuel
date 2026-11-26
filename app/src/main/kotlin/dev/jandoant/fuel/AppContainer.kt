package dev.jandoant.fuel

import android.content.Context
import android.util.Log
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.data.backup.BackupRepository
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.healthconnect.HcAvailability
import dev.jandoant.fuel.data.healthconnect.HealthConnectGateway
import dev.jandoant.fuel.data.off.OpenFoodFactsClient
import dev.jandoant.fuel.data.repo.EatenEntrySink
import dev.jandoant.fuel.data.repo.FoodRepository
import dev.jandoant.fuel.data.repo.ModelRepository
import dev.jandoant.fuel.data.repo.PlanRepository
import dev.jandoant.fuel.data.repo.RecipeRepository
import dev.jandoant.fuel.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Manual dependency injection (PRD §3 allows it; decision #29): one container per process, created by [FuelApp].
 * Everything is constructed here, so tests can build the same graph with an in-memory database.
 */
class AppContainer(context: Context, inMemoryDb: Boolean = false) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: FuelDatabase = FuelDatabase.build(context, inMemoryDb)
    val settings = SettingsRepository(context)
    val off = OpenFoodFactsClient()
    val foods = FoodRepository(db, off)
    val plan = PlanRepository(db, foods)
    val recipes = RecipeRepository(db, foods)
    val model = ModelRepository(db, settings, foods, appScope)
    val healthConnect = HealthConnectGateway(context, db)
    val backup = BackupRepository(context, db, settings)
    val messages = UiMessages()

    init {
        plan.sink = HealthConnectMirror(healthConnect, plan, appScope)
        appScope.launch { db.ensureSeeded() }
    }
}

/** Short user-facing messages (snackbar), optionally with an Undo action. */
class UiMessages {
    data class Message(val text: String, val actionLabel: String? = null, val action: (suspend () -> Unit)? = null)

    private val channel = Channel<Message>(Channel.BUFFERED)
    val flow = channel.receiveAsFlow()
    fun show(text: String, actionLabel: String? = null, action: (suspend () -> Unit)? = null) {
        channel.trySend(Message(text, actionLabel, action))
    }
}

/** Mirrors eaten entries into Health Connect (PRD §4.2). Failures never block the app. */
class HealthConnectMirror(
    private val hc: HealthConnectGateway,
    private val plan: PlanRepository,
    private val scope: CoroutineScope,
) : EatenEntrySink {
    override fun upsert(entry: PlanEntry) {
        scope.launch {
            runCatching { if (hc.availability() == HcAvailability.AVAILABLE) hc.writeEaten(entry, null) }
                .onFailure { Log.w(HealthConnectGateway.TAG, "NutritionRecord write failed", it) }
        }
    }

    override fun remove(entryId: Long) {
        scope.launch {
            runCatching { if (hc.availability() == HcAvailability.AVAILABLE) hc.deleteEaten(entryId) }
                .onFailure { Log.w(HealthConnectGateway.TAG, "NutritionRecord delete failed", it) }
        }
    }
}

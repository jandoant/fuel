package dev.jandoant.fuel.ui.plan

import dev.jandoant.fuel.UiMessages
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.data.repo.PlanRepository
import dev.jandoant.fuel.data.repo.toDraft
import dev.jandoant.fuel.ui.components.EntryActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Shared implementation of [EntryActions] for every screen that shows plan entries. Navigation-type actions
 * (adding an ingredient) are forwarded to [onAddIngredient]; "Move" opens a dialog via [moveRequest].
 */
class EntryActionsHandler(
    private val plan: PlanRepository,
    private val messages: UiMessages,
    private val scope: CoroutineScope,
    private val onAddIngredient: (PlanEntry) -> Unit,
) : EntryActions {

    val moveRequest = MutableStateFlow<PlanEntry?>(null)

    override fun toggleEaten(entry: PlanEntry) {
        scope.launch { plan.setEaten(entry.id, !entry.eaten) }
    }

    override fun changePortion(entry: PlanEntry, portion: Portion, unit: ServingUnit, amount: Double) {
        scope.launch { plan.updatePortion(portion.id, entry.id, unit, amount) }
    }

    override fun addIngredient(entry: PlanEntry) = onAddIngredient(entry)

    override fun removeIngredient(entry: PlanEntry, portion: Portion) {
        scope.launch {
            plan.removeIngredient(entry.id, portion.id)
            messages.show("Removed ${portion.food.name}", "Undo") { plan.addIngredient(entry.id, portion.toDraft()) }
        }
    }

    override fun saveToTemplate(entry: PlanEntry) {
        scope.launch {
            plan.saveEntryToTemplate(entry.id)
            messages.show("Template “${entry.name}” updated. Other planned entries keep their own amounts.")
        }
    }

    override fun delete(entry: PlanEntry) {
        scope.launch {
            plan.deleteEntry(entry.id)
            messages.show("Removed ${entry.title}", "Undo") {
                val ids = plan.copy(listOf(entry), entry.date)
                if (entry.eaten) ids.forEach { plan.setEaten(it, true) }
            }
        }
    }

    override fun move(entry: PlanEntry) { moveRequest.value = entry }

    fun confirmMove(entry: PlanEntry, date: LocalDate, slotId: String) {
        moveRequest.value = null
        scope.launch { plan.moveEntry(entry.id, date, slotId) }
    }
}

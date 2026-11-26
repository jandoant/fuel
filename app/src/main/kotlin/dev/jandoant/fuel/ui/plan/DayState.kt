package dev.jandoant.fuel.ui.plan

import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.model.CalorieZone
import dev.jandoant.fuel.core.model.DaySummaries
import dev.jandoant.fuel.core.model.DaySummary
import dev.jandoant.fuel.core.model.MacroTargets
import dev.jandoant.fuel.core.model.MicroReferences
import dev.jandoant.fuel.core.model.MicroReport
import dev.jandoant.fuel.core.model.MicroScope
import dev.jandoant.fuel.core.model.Nutrients
import java.time.LocalDate

data class SlotSection(val slot: MealSlot, val entries: List<PlanEntry>)

/** Everything a day view (Today, Plan, Calendar details) shows. Pure function of the inputs → unit-testable. */
data class DayState(
    val date: LocalDate,
    val sections: List<SlotSection>,
    val summary: DaySummary,
    val micros: MicroReport,
    val entries: List<PlanEntry>,
)

object DayStates {

    /**
     * Visible slots: the enabled ones in display order, plus a disabled slot (the Extra slot when switched off)
     * on days that still have entries in it (PRD §5.1).
     */
    fun sections(entries: List<PlanEntry>, slots: List<MealSlot>): List<SlotSection> {
        val bySlot = entries.groupBy { it.slotId }
        val known = slots.map { it.id }.toSet()
        val visible = slots.filter { it.enabled || bySlot.containsKey(it.id) }.sortedBy { it.order }
        val orphans = bySlot.keys.filter { it !in known }.map { MealSlot(it, it.replaceFirstChar { c -> c.uppercase() }, 99, true, false) }
        return (visible + orphans).map { s -> SlotSection(s, bySlot[s.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id }))) }
    }

    fun build(
        date: LocalDate,
        today: LocalDate,
        entries: List<PlanEntry>,
        slots: List<MealSlot>,
        zone: CalorieZone?,
        targets: MacroTargets?,
    ): DayState {
        val summary = DaySummaries.summarize(entries.map { it.plannedItem }, zone, targets)
        val scope = if (date == today) MicroScope.TODAY else MicroScope.DAY
        // Today shows "so far" (eaten); other days the whole plan.
        val counted = if (scope == MicroScope.TODAY) entries.filter { it.eaten } else entries
        val total = Nutrients.sum(counted.map { it.nutrients })
        val portions = counted.flatMap { it.portions }
        val micros = MicroReferences.report(
            total = total,
            energyKcal = zone?.target ?: total.kcal,
            scope = scope,
            itemsWithFullData = portions.count { MicroReferences.hasFullData(it.food.per100g) },
            itemCount = portions.size,
        )
        return DayState(date, sections(entries, slots), summary, micros, entries)
    }
}

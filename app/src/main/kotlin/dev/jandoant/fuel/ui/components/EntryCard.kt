package dev.jandoant.fuel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.model.MacroNumbers
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType

/** What can be done with an entry; implemented by the screen's ViewModel. */
interface EntryActions {
    fun toggleEaten(entry: PlanEntry)
    fun changePortion(entry: PlanEntry, portion: Portion, unit: ServingUnit, amount: Double)
    fun addIngredient(entry: PlanEntry)
    fun removeIngredient(entry: PlanEntry, portion: Portion)
    fun saveToTemplate(entry: PlanEntry)
    fun delete(entry: PlanEntry)
    fun move(entry: PlanEntry)
}

@Composable
fun EatenCheckbox(checked: Boolean, label: String, onToggle: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .semantics {
                contentDescription = "$label eaten"
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(if (checked) FuelColors.Accent else FuelColors.Surface, CircleShape)
                .border(2.dp, if (checked) FuelColors.Accent else FuelColors.CheckOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (checked) FuelIcon(FuelIcons.CHECK, null, Color.White, size = 14.dp, strokeWidth = 3f) }
    }
}

/**
 * One plan entry (PRD §7.1): eaten = white card, planned = dashed outline. Tap expands inline editing; the
 * checkbox toggles planned ↔ eaten. [showCheckbox] is false for days in the future.
 */
@Composable
fun EntryCard(
    entry: PlanEntry,
    expanded: Boolean,
    onExpand: () -> Unit,
    actions: EntryActions,
    modifier: Modifier = Modifier,
    showCheckbox: Boolean = true,
) {
    val eaten = entry.eaten
    val shape = FuelShapes.Entry
    val base = if (eaten) modifier.background(FuelColors.Surface, shape) else modifier.dashedBorder(FuelColors.Dashed, 18.dp)
    val n = entry.numbers
    Column(base.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 2.dp, end = 10.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showCheckbox) EatenCheckbox(eaten, entry.title, { actions.toggleEaten(entry) }) else Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onExpand)
                    .semantics { stateDescription = if (expanded) "expanded" else "collapsed" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.title, style = FuelType.BodyStrong, color = if (eaten) FuelColors.Ink else FuelColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (entry.isRecipe) RecipeBadge()
                        if (entry.edited) EditedChip()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.amountText, style = FuelType.TinyStrong, color = FuelColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        MacroInline(n)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("${n.kcal}", style = FuelType.BodyStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = FuelColors.Ink)
                Spacer(Modifier.width(6.dp))
                FuelIcon(FuelIcons.CHEVRON_DOWN, null, FuelColors.Faint, size = 14.dp, strokeWidth = 2.5f, modifier = Modifier.rotate(if (expanded) 180f else 0f))
            }
        }
        AnimatedVisibility(expanded) { EntryPanel(entry, actions) }
    }
}

@Composable
private fun EntryPanel(entry: PlanEntry, actions: EntryActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (entry.isRecipe) FuelColors.RecipePanel else Color.Transparent, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
            .padding(start = 14.dp, end = 12.dp, bottom = 8.dp),
    ) {
        Divider()
        if (entry.isRecipe) {
            Text(
                "Your copy of the recipe. Changes apply only to this entry, not to the template.",
                style = FuelType.Tiny, color = FuelColors.Recipe, modifier = Modifier.padding(top = 8.dp),
            )
        }
        entry.portions.forEachIndexed { i, p ->
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.isRecipe) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.food.name, style = FuelType.Small, color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        MacroInline(p.numbers, showKcal = true)
                        IconButton44(FuelIcons.CLOSE, "Remove ${p.food.name}", { actions.removeIngredient(entry, p) }, tint = FuelColors.Faint, iconSize = 14.dp)
                    }
                }
                PortionInputRow(p.amount, p.unit, p.food.units, p.food.name, { u, a -> actions.changePortion(entry, p, u, a) })
            }
            if (entry.isRecipe && i < entry.portions.lastIndex) Divider(color = FuelColors.RecipeRule)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (entry.isRecipe) {
                TextLink("+ Add ingredient", { actions.addIngredient(entry) }, color = FuelColors.Recipe, style = FuelType.Meta)
                Spacer(Modifier.width(12.dp))
                if (entry.edited && entry.templateId != null) TextLink("Save changes to template…", { actions.saveToTemplate(entry) }, color = FuelColors.Muted, style = FuelType.Meta)
            }
            Spacer(Modifier.weight(1f))
            TextLink("Move", { actions.move(entry) }, color = FuelColors.Muted, style = FuelType.Meta)
            Spacer(Modifier.width(10.dp))
            TextLink("Remove", { actions.delete(entry) }, color = FuelColors.WarningText, style = FuelType.Meta)
        }
    }
}

@Composable
fun RecipeBadge() = Text(
    "RECIPE", style = FuelType.Micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = FuelColors.Recipe,
    modifier = Modifier.background(FuelColors.RecipeBg, FuelShapes.Chip).padding(horizontal = 7.dp, vertical = 2.dp),
)

@Composable
fun EditedChip() = Text(
    "edited", style = FuelType.Micro, color = FuelColors.Recipe,
    modifier = Modifier.border(1.dp, FuelColors.RecipeOutline, FuelShapes.Chip).padding(horizontal = 6.dp),
)

/** A meal section of the timeline (Today, Plan): node tile + connector, header, entries, dashed add row. */
@Composable
fun MealTimelineSection(
    slot: MealSlot,
    entries: List<PlanEntry>,
    expandedId: Long?,
    onExpand: (Long) -> Unit,
    actions: EntryActions,
    onAdd: () -> Unit,
    showCheckboxes: Boolean,
    isLast: Boolean,
) {
    val allDone = entries.isNotEmpty() && entries.all { it.eaten }
    val kcal = MacroNumbers.sum(entries.map { it.numbers }).kcal
    val lineColor = if (allDone) FuelColors.AccentSoft else Color(0xFFE6DFD6)
    Row(
        Modifier.fillMaxWidth().drawBehind {
            if (!isLast) {
                val x = 16.dp.toPx()
                drawLine(lineColor, Offset(x, 36.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        },
    ) {
        Column(Modifier.width(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).background(if (allDone) FuelColors.Accent else FuelColors.Surface, FuelShapes.Tile),
                contentAlignment = Alignment.Center,
            ) { FuelIcon(FuelIcons.forSlot(slot.id), if (allDone) "${slot.name} complete" else null, if (allDone) Color.White else FuelColors.Muted, size = 17.dp) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(slot.name, style = FuelType.SectionHeading, color = FuelColors.Ink, modifier = Modifier.weight(1f))
                Text("${fmt(kcal)} kcal", style = FuelType.MetaStrong, color = FuelColors.Muted)
            }
            entries.forEach { e ->
                EntryCard(e, expandedId == e.id, { onExpand(e.id) }, actions, showCheckbox = showCheckboxes)
            }
            AddRow("Add to ${slot.name.lowercase()}", onAdd)
        }
    }
}

@Composable
fun AddRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(FuelShapes.Entry)
            .dashedBorder(FuelColors.Dashed, 18.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).background(FuelColors.Surface, CircleShape), contentAlignment = Alignment.Center) {
            FuelIcon(FuelIcons.PLUS, null, FuelColors.Ink, size = 14.dp, strokeWidth = 2.5f)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = FuelType.Small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = FuelColors.Muted)
    }
}

/** Neutral round icon / purple recipe tile used in food lists. */
@Composable
fun FoodKindTile(isRecipe: Boolean) {
    Box(
        Modifier.size(30.dp).background(if (isRecipe) FuelColors.RecipeBg else FuelColors.Line2, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecipe) FuelIcon(FuelIcons.RECIPE, "Recipe", FuelColors.Recipe, size = 20.dp)
        else FuelIcon(FuelIcons.SINGLE, "Single food", FuelColors.Subtle, size = 18.dp)
    }
}

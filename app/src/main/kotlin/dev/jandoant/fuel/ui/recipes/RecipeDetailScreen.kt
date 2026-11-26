package dev.jandoant.fuel.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.domain.RecipeTag
import dev.jandoant.fuel.core.domain.RecipeTemplate
import dev.jandoant.fuel.core.model.MicroReferences
import dev.jandoant.fuel.core.model.MicroScope
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.data.repo.PortionDraft
import dev.jandoant.fuel.data.repo.toDraft
import dev.jandoant.fuel.ui.components.Divider
import dev.jandoant.fuel.ui.components.FilterChip
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.IconButton44
import dev.jandoant.fuel.ui.components.MacroInline
import dev.jandoant.fuel.ui.components.MicrosSection
import dev.jandoant.fuel.ui.components.MiniBar
import dev.jandoant.fuel.ui.components.PortionInputRow
import dev.jandoant.fuel.ui.components.PrimaryButton
import dev.jandoant.fuel.ui.components.SecondaryButton
import dev.jandoant.fuel.ui.components.SubScreenHeader
import dev.jandoant.fuel.ui.components.TextInput
import dev.jandoant.fuel.ui.components.TextLink
import dev.jandoant.fuel.ui.nav.AddFoodRoute
import dev.jandoant.fuel.ui.nav.AddTarget
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.plan.dayLabel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import dev.jandoant.fuel.ui.theme.Macros
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecipeDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val recipe = c.recipes.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val slots = c.plan.slots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealSlot.DEFAULTS)

    private fun drafts(r: RecipeTemplate) = r.ingredients.map { it.toDraft() }

    fun rename(name: String) = viewModelScope.launch { c.recipes.update(id, name = name) }
    fun toggleTag(t: RecipeTag) = viewModelScope.launch { recipe.value?.let { r -> c.recipes.update(id, tags = if (t in r.tags) r.tags - t else r.tags + t) } }
    fun toggleFavourite() = viewModelScope.launch { recipe.value?.let { c.recipes.update(id, favourite = !it.favourite) } }

    fun changePortion(index: Int, unit: ServingUnit, amount: Double) = viewModelScope.launch {
        val r = recipe.value ?: return@launch
        c.recipes.setIngredients(id, drafts(r).mapIndexed { i, d -> if (i == index) PortionDraft(d.foodId, unit.id, amount) else d })
    }

    fun remove(index: Int) = viewModelScope.launch {
        val r = recipe.value ?: return@launch
        c.recipes.setIngredients(id, drafts(r).filterIndexed { i, _ -> i != index })
    }

    fun plan(date: LocalDate, slotId: String) = viewModelScope.launch {
        val r = recipe.value ?: return@launch
        c.plan.addRecipe(date, slotId, r, drafts(r))
        c.messages.show("Planned ${r.name} · ${dayLabel(date, LocalDate.now())}")
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        c.recipes.delete(id)
        c.messages.show("Recipe deleted. Planned entries keep their own copies.")
        onDone()
    }
}

/** Recipe details (PRD §7.10, RecipeDetail.dc.html). Edits change the template only. */
@Composable
fun RecipeDetailScreen(nav: Navigator, recipeId: Long) {
    val vm = fuelViewModel(key = "recipe$recipeId") { c -> RecipeDetailViewModel(c, recipeId) }
    val recipe by vm.recipe.collectAsStateWithLifecycle()
    val slots by vm.slots.collectAsStateWithLifecycle()
    var rename by remember { mutableStateOf(false) }
    var planDialog by remember { mutableStateOf<Boolean?>(null) } // true = Plan…, false = Add to today
    var confirmDelete by remember { mutableStateOf(false) }
    val r = recipe ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubScreenHeader(r.name, nav::back, subtitle = (r.tags.map { it.label } + "${r.ingredients.size} ingredients").joinToString(" · ")) {
            IconButton44(FuelIcons.EDIT, "Rename recipe", { rename = true })
            IconButton44(FuelIcons.STAR, if (r.favourite) "Remove from favorites" else "Add to favorites", vm::toggleFavourite, tint = if (r.favourite) FuelColors.Fiber else FuelColors.Ink)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RecipeTag.entries.forEach { t -> FilterChip(t.label, t in r.tags, { vm.toggleTag(t) }) }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("This is a template.") }
                append(" Planning it creates an independent copy. Changes here only apply to future plans; the ${r.usageCount} entries already planned keep their own amounts.")
            },
            style = FuelType.Meta, color = FuelColors.Recipe,
            modifier = Modifier.fillMaxWidth().background(FuelColors.RecipeBg, FuelShapes.Item).padding(12.dp),
        )
        NutritionCard(r)
        FuelCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Ingredients ", style = FuelType.SectionHeading, color = FuelColors.Ink)
                Text("default amounts", style = FuelType.Meta, color = FuelColors.Muted)
            }
            r.ingredients.forEachIndexed { i, p -> IngredientRow(p, { u, a -> vm.changePortion(i, u, a) }, { vm.remove(i) }) }
            TextLink("+ Add ingredient", { nav.go(AddFoodRoute(AddTarget.RECIPE, targetId = r.id)) }, color = FuelColors.Recipe)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Plan…", { planDialog = true }, Modifier.weight(1f), enabled = r.ingredients.isNotEmpty())
            PrimaryButton("Add to today", { planDialog = false }, Modifier.weight(1f), enabled = r.ingredients.isNotEmpty())
        }
        TextLink("Delete recipe", { confirmDelete = true }, color = FuelColors.WarningText, style = FuelType.Meta)
    }

    if (rename) {
        var name by remember { mutableStateOf(r.name) }
        AlertDialog(
            onDismissRequest = { rename = false }, containerColor = FuelColors.Surface,
            title = { Text("Rename recipe", style = FuelType.SectionHeading) },
            text = { TextInput(name, { name = it }, "Name") },
            confirmButton = { TextButton({ vm.rename(name); rename = false }) { Text("Save", color = FuelColors.AccentText, style = FuelType.BodyStrong) } },
            dismissButton = { TextButton({ rename = false }) { Text("Cancel", color = FuelColors.Muted) } },
        )
    }
    planDialog?.let { pickDate ->
        PlanRecipeDialog(pickDate, slots, { d, s -> vm.plan(d, s); planDialog = null }, { planDialog = null })
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = FuelColors.Surface,
        title = { Text("Delete ${r.name}?", style = FuelType.SectionHeading) },
        text = { Text("The template is removed. Entries already planned keep their own ingredients.", style = FuelType.Body) },
        confirmButton = { TextButton({ confirmDelete = false; vm.delete(nav::back) }) { Text("Delete", color = FuelColors.Danger, style = FuelType.BodyStrong) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = FuelColors.Muted) } },
    )
}

@Composable
private fun NutritionCard(r: RecipeTemplate) {
    val n = r.nutrients
    val m = r.numbers
    FuelCard {
        Text("With default amounts", style = FuelType.Meta, color = FuelColors.Muted)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontSize = FuelType.BigNumber.fontSize, fontWeight = FontWeight.ExtraBold)) { append("${m.kcal}") }
            append(" kcal")
        }, style = FuelType.Small, color = FuelColors.Ink)
        Spacer(Modifier.width(4.dp))
        val kcal = maxOf(n.kcal, 1.0)
        val shares = listOf(n.protein * 4 / kcal, n.fat * 9 / kcal, n.carbs * 4 / kcal, n.fiber / 30.0)
        val values = listOf(m.protein, m.fat, m.carbs, m.fiber)
        Macros.All.forEachIndexed { i, pal ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pal.name, style = FuelType.MetaStrong, color = pal.color, modifier = Modifier.width(60.dp))
                Box(Modifier.weight(1f)) { MiniBar(shares[i].toFloat(), 1f, pal.color, height = 6.dp) }
                Text("${values[i]} g", style = FuelType.Meta, color = FuelColors.Ink, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
            }
        }
        Text("Bars: share of calories · fiber: share of 30 g goal", style = FuelType.Micro.copy(fontWeight = FontWeight.Medium), color = FuelColors.Subtle)
        val portions = r.ingredients
        MicrosSection(
            MicroReferences.report(n, MicroReferences.REFERENCE_ENERGY_KCAL, MicroScope.DAY, portions.count { MicroReferences.hasFullData(it.food.per100g) }, portions.size),
            MicroScope.DAY,
        )
    }
}

@Composable
private fun IngredientRow(p: Portion, onChange: (ServingUnit, Double) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.food.name, style = FuelType.Small, color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            MacroInline(p.numbers, showKcal = true)
            IconButton44(FuelIcons.CLOSE, "Remove ${p.food.name}", onRemove, tint = FuelColors.Faint, iconSize = 14.dp)
        }
        PortionInputRow(p.amount, p.unit, p.food.units, p.food.name, onChange)
    }
}

@Composable
private fun PlanRecipeDialog(pickDate: Boolean, slots: List<MealSlot>, onPick: (LocalDate, String) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    var date by remember { mutableStateOf(if (pickDate) today.plusDays(1) else today) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = FuelColors.Surface,
        title = { Text(if (pickDate) "Plan recipe" else "Add to today", style = FuelType.SectionHeading) },
        text = {
            Column {
                if (pickDate) Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton44(FuelIcons.BACK, "Previous day", { date = date.minusDays(1) })
                    Text(dayLabel(date, today), style = FuelType.BodyStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton44(FuelIcons.FORWARD, "Next day", { date = date.plusDays(1) })
                }
                slots.filter { it.enabled }.forEach { s ->
                    Text(s.name, style = FuelType.Body, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(FuelShapes.Item).clickable { onPick(date, s.id) }.padding(12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = FuelColors.Muted) } },
    )
}

package dev.jandoant.fuel.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.FoodSource
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.NutrientUnit
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.core.model.UnitSource
import dev.jandoant.fuel.ui.components.Divider
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.IconButton44
import dev.jandoant.fuel.ui.components.NumberField
import dev.jandoant.fuel.ui.components.SubScreenHeader
import dev.jandoant.fuel.ui.components.TextInput
import dev.jandoant.fuel.ui.components.fmt1
import dev.jandoant.fuel.ui.nav.FoodEditorRoute
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class FoodDetailViewModel(private val c: AppContainer, private val foodId: Long) : ViewModel() {
    val food = c.foods.observe(foodId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavourite() = viewModelScope.launch { food.value?.let { c.foods.setFavourite(it.id, !it.favourite) } }
    fun setOverrides(values: Map<Nutrient, Double>) = viewModelScope.launch {
        val f = food.value ?: return@launch
        c.foods.setOverrides(f.id, Nutrients(f.overrides.values + values))
    }
    fun updateUnit(u: ServingUnit) = viewModelScope.launch { c.foods.updateUnit(u, foodId) }
    fun setDefault(u: ServingUnit) = viewModelScope.launch { c.foods.setDefaultUnit(foodId, u.id) }
    fun addUnit(name: String, grams: Double) = viewModelScope.launch { c.foods.addUnit(foodId, name, grams) }
    fun removeUnit(u: ServingUnit) = viewModelScope.launch {
        if (!c.foods.removeUnit(foodId, u.id)) c.messages.show("“${u.name}” is still used by planned entries or recipes")
    }
    fun hide(onDone: () -> Unit) = viewModelScope.launch { c.foods.hide(foodId); c.messages.show("Food removed from your lists"); onDone() }
}

/** Food details (PRD §7.3, Portion.dc.html): read-only for adding, plus the serving units editor. */
@Composable
fun FoodDetailScreen(nav: Navigator, foodId: Long) {
    val vm = fuelViewModel(key = "food$foodId") { c -> FoodDetailViewModel(c, foodId) }
    val food by vm.food.collectAsStateWithLifecycle()
    val f = food ?: return
    var fillIn by remember { mutableStateOf(false) }
    var addUnit by remember { mutableStateOf(false) }
    val serving = f.defaultUnit.takeIf { !it.isGrams }
    val servingGrams = serving?.grams ?: 100.0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubScreenHeader(
            f.name, nav::back,
            subtitle = listOfNotNull(f.brand, f.barcode?.let { "EAN $it" }).joinToString(" · ").ifBlank { null },
        ) {
            if (f.source == FoodSource.CUSTOM) IconButton44(FuelIcons.EDIT, "Edit food", { nav.go(FoodEditorRoute(foodId = f.id)) })
            IconButton44(FuelIcons.STAR, if (f.favourite) "Remove from favorites" else "Add to favorites", vm::toggleFavourite, tint = if (f.favourite) FuelColors.Fiber else FuelColors.Ink)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceChip("Source: ${f.source.label}")
            if (f.source == FoodSource.OPEN_FOOD_FACTS) SourceChip("Cached")
            if (f.overrides.values.isNotEmpty()) SourceChip("Edited locally")
            if (f.favourite) SourceChip("Favorite")
        }
        Text(
            if (serving != null) "Per serving (${Amounts.formatNumber(servingGrams)} g) · ${serving.name}" else "Per 100 g",
            style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(horizontal = 2.dp),
        )
        val portion = Nutrients.forGrams(f.per100g, servingGrams)
        FuelCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Summary("kcal", "${portion.kcal.toInt()}", FuelColors.Ink)
                Summary("Protein", "${fmt1(portion.protein)} g", FuelColors.Protein)
                Summary("Fat", "${fmt1(portion.fat)} g", FuelColors.Fat)
                Summary("Carbs", "${fmt1(portion.carbs)} g", FuelColors.Carbs)
            }
        }
        NutritionTable(f, servingGrams, onFillIn = { fillIn = true })
        UnitsEditor(f, vm, onAdd = { addUnit = true })
        if (f.source == FoodSource.CUSTOM) {
            Text("Remove from my foods", style = FuelType.Meta, color = FuelColors.WarningText, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.hide(nav::back) }.padding(8.dp))
        }
    }
    if (fillIn) FillInDialog(f, onSave = { vm.setOverrides(it); fillIn = false }, onDismiss = { fillIn = false })
    if (addUnit) AddUnitDialog(onAdd = { n, g -> vm.addUnit(n, g); addUnit = false }, onDismiss = { addUnit = false })
}

@Composable
private fun SourceChip(t: String) = Text(t, style = FuelType.Meta, color = FuelColors.Ink2, modifier = Modifier.background(FuelColors.Line, FuelShapes.Chip).padding(horizontal = 10.dp, vertical = 4.dp))

@Composable
private fun androidx.compose.foundation.layout.RowScope.Summary(label: String, value: String, color: Color) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = FuelType.MetaStrong, color = color)
        Text(value, style = FuelType.Number.copy(fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit(19f, androidx.compose.ui.unit.TextUnitType.Sp)), color = FuelColors.Ink)
        Box(Modifier.size(24.dp, 3.dp).background(color, RoundedCornerShape(2.dp)))
    }
}

fun formatNutrient(v: Double, unit: NutrientUnit): String = when (unit) {
    NutrientUnit.KCAL -> "${v.toInt()}"
    NutrientUnit.G -> String.format(Locale.US, "%.1f", v)
    NutrientUnit.MG -> if (v < 10) String.format(Locale.US, "%.1f", v) else "${v.toInt()}"
    NutrientUnit.UG -> String.format(Locale.US, "%.1f", v)
}

@Composable
private fun NutritionTable(f: Food, servingGrams: Double, onFillIn: () -> Unit) {
    val per = f.per100g
    FuelCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.padding(top = 4.dp, bottom = 6.dp)) {
            Text("Nutrient", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.weight(1f))
            Text("100 g", style = FuelType.Meta, color = FuelColors.Muted, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
            Text("Serving", style = FuelType.Meta, color = FuelColors.Muted, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
        }
        Divider(color = FuelColors.Line)
        val rows = Nutrient.LABEL + Nutrient.MICROS.filter { per.has(it) }
        rows.forEach { n ->
            val color = when (n) {
                Nutrient.PROTEIN -> FuelColors.Protein
                Nutrient.FAT -> FuelColors.Fat
                Nutrient.CARBS -> FuelColors.Carbs
                Nutrient.FIBER -> FuelColors.Fiber
                Nutrient.SAT_FAT, Nutrient.SUGAR -> FuelColors.Muted
                else -> FuelColors.Ink
            }
            val indent = n == Nutrient.SAT_FAT || n == Nutrient.SUGAR
            val unit = if (n == Nutrient.ENERGY) " (kcal)" else if (n.unit == NutrientUnit.G) "" else " (${n.unit.symbol})"
            Row(Modifier.padding(vertical = 5.dp)) {
                Text((if (indent) "   " else "") + n.label + unit, style = FuelType.Small.copy(fontWeight = FontWeight.Medium), color = color, modifier = Modifier.weight(1f))
                val has = per.has(n)
                Text(if (has) formatNutrient(per[n], n.unit) else "—", style = FuelType.Small.copy(fontWeight = FontWeight.Medium), color = FuelColors.Muted, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
                Text(if (has) formatNutrient(per[n] * servingGrams / 100, n.unit) else "—", style = FuelType.Small.copy(fontWeight = FontWeight.Medium), color = FuelColors.Ink, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
            }
            Divider()
        }
        val missing = f.missing
        if (missing.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${missing.joinToString { it.label.removePrefix("of which ").replaceFirstChar { c -> c.uppercase() } }} missing in ${f.source.label} · ", style = FuelType.Meta, color = FuelColors.WarningText, modifier = Modifier.weight(1f, fill = false))
                Text("fill in", style = FuelType.Meta.copy(textDecoration = TextDecoration.Underline), color = FuelColors.WarningText, modifier = Modifier.clickable(role = Role.Button, onClick = onFillIn).padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun UnitsEditor(f: Food, vm: FoodDetailViewModel, onAdd: () -> Unit) {
    FuelCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Serving units", style = FuelType.SectionHeading.copy(fontWeight = FontWeight.Bold), color = FuelColors.Ink, modifier = Modifier.weight(1f))
            Text("step and range for − / +", style = FuelType.Tiny, color = FuelColors.Muted)
        }
        f.units.forEach { u -> UnitRow(u, vm) }
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, FuelColors.CheckOutline, RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onAdd).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FuelIcon(FuelIcons.PLUS, null, FuelColors.AccentText, size = 14.dp, strokeWidth = 2.5f)
            Spacer(Modifier.width(8.dp))
            Text("Add serving unit", style = FuelType.Small.copy(fontWeight = FontWeight.Bold), color = FuelColors.AccentText)
        }
    }
}

@Composable
private fun UnitRow(u: ServingUnit, vm: FoodDetailViewModel) {
    val locked = u.source == UnitSource.BUILT_IN
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            var name by remember(u.id) { mutableStateOf(u.name) }
            Box(Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicTextField(
                    name, { name = it; if (it.isNotBlank()) vm.updateUnit(u.copy(name = it)) }, singleLine = true, readOnly = locked,
                    textStyle = FuelType.Small.copy(fontWeight = FontWeight.Bold, color = FuelColors.Ink),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().height(30.dp).background(if (locked) FuelColors.Background else FuelColors.Surface, RoundedCornerShape(7.dp))
                            .border(1.dp, if (locked) Color.Transparent else FuelColors.InputBorder, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) { inner() }
                    },
                )
            }
            Text("=", style = FuelType.Meta, color = FuelColors.Muted)
            NumberField(u.grams, { it?.let { g -> if (g > 0) vm.updateUnit(u.copy(grams = g)) } }, "Grams per ${u.name}", readOnly = locked, align = TextAlign.End)
            Text("g", style = FuelType.Meta, color = FuelColors.Muted)
            if (!locked) IconButton44(FuelIcons.CLOSE, "Remove ${u.name}", { vm.removeUnit(u) }, tint = FuelColors.Faint, iconSize = 14.dp)
            else Spacer(Modifier.width(44.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Step", style = FuelType.Tiny, color = FuelColors.Muted)
            NumberField(u.step, { it?.let { s -> if (s > 0) vm.updateUnit(u.copy(step = s)) } }, "Step for ${u.name}", width = 48.dp)
            Text("Range", style = FuelType.Tiny, color = FuelColors.Muted, modifier = Modifier.padding(start = 4.dp))
            NumberField(u.min, { it?.let { m -> vm.updateUnit(u.copy(min = m)) } }, "Minimum for ${u.name}", width = 48.dp)
            Text("–", style = FuelType.Tiny, color = FuelColors.Muted)
            NumberField(u.max, { vm.updateUnit(u.copy(max = it)) }, "Maximum for ${u.name}", width = 52.dp, allowEmpty = true)
            Spacer(Modifier.weight(1f))
            val def = u.isDefault
            Text(
                if (def) "Default" else "Set default", style = FuelType.TinyStrong, color = if (def) FuelColors.AccentDeep else FuelColors.Muted,
                modifier = Modifier.clip(FuelShapes.Chip).background(if (def) FuelColors.AccentBg else FuelColors.Surface)
                    .border(1.dp, if (def) FuelColors.Accent else FuelColors.InputBorder, FuelShapes.Chip)
                    .clickable(role = Role.Button) { if (!def) vm.setDefault(u) }.padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        Text(
            when (u.source) {
                UnitSource.BUILT_IN -> "Built in · grams are always available"
                UnitSource.OPEN_FOOD_FACTS -> "From Open Food Facts serving size"
                UnitSource.USER -> "Added by you"
            },
            style = FuelType.Micro.copy(fontWeight = FontWeight.Medium), color = FuelColors.Subtle,
        )
    }
}

@Composable
private fun FillInDialog(f: Food, onSave: (Map<Nutrient, Double>) -> Unit, onDismiss: () -> Unit) {
    val values = remember { mutableStateMapOf<Nutrient, Double>() }
    val fields = (f.missing + Nutrient.LABEL.filter { it !in f.missing }).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FuelColors.Surface,
        title = { Text("Fill in values (per 100 g)", style = FuelType.SectionHeading) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved on this phone only; Open Food Facts is not changed.", style = FuelType.Meta, color = FuelColors.Muted)
                fields.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.label + if (n in f.missing) " (missing)" else "", style = FuelType.Small, color = if (n in f.missing) FuelColors.WarningText else FuelColors.Ink, modifier = Modifier.weight(1f))
                        NumberField(values[n] ?: f.per100g.values[n], { v -> if (v != null) values[n] = v }, n.label, width = 72.dp, allowEmpty = true)
                        Text(" ${n.unit.symbol}", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.width(36.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSave(values.toMap()) }) { Text("Save", color = FuelColors.AccentText, style = FuelType.BodyStrong) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = FuelColors.Muted) } },
    )
}

@Composable
fun AddUnitDialog(onAdd: (String, Double) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf<Double?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FuelColors.Surface,
        title = { Text("Add serving unit", style = FuelType.SectionHeading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(name, { name = it }, "Name", placeholder = "e.g. slice, cup, tub")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Weight", style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.weight(1f))
                    NumberField(grams, { grams = it }, "Grams per unit", width = 80.dp, allowEmpty = true)
                    Text(" g", style = FuelType.Small, color = FuelColors.Muted)
                }
            }
        },
        confirmButton = { TextButton({ grams?.let { if (it > 0) onAdd(name, it) } }, enabled = (grams ?: 0.0) > 0 && name.isNotBlank()) { Text("Add", color = FuelColors.AccentText, style = FuelType.BodyStrong) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = FuelColors.Muted) } },
    )
}

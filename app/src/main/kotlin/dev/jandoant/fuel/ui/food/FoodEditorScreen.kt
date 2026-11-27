package dev.jandoant.fuel.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.UnitSource
import dev.jandoant.fuel.data.repo.UnitDraft
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.NumberField
import dev.jandoant.fuel.ui.components.PrimaryButton
import dev.jandoant.fuel.ui.components.SectionTitle
import dev.jandoant.fuel.ui.components.SubScreenHeader
import dev.jandoant.fuel.ui.components.TextInput
import dev.jandoant.fuel.ui.nav.FoodDetailRoute
import dev.jandoant.fuel.ui.nav.FoodEditorRoute
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import kotlinx.coroutines.launch

class FoodEditorViewModel(private val c: AppContainer, private val route: FoodEditorRoute) : ViewModel() {
    suspend fun load() = if (route.foodId != 0L) c.foods.get(route.foodId) else null

    fun save(name: String, brand: String, values: Map<Nutrient, Double>, unitName: String, unitGrams: Double?, onDone: (Long) -> Unit) =
        viewModelScope.launch {
            val n = Nutrients(values)
            val id = if (route.foodId != 0L) {
                c.foods.updateCustom(route.foodId, name, brand, n); route.foodId
            } else {
                val units = if (unitGrams != null && unitGrams > 0 && unitName.isNotBlank()) {
                    val step = Amounts.suggestedStep(unitGrams)
                    listOf(UnitDraft("${unitName.trim()} (${Amounts.formatNumber(unitGrams)} g)", unitGrams, step, step, null, true, UnitSource.USER))
                } else emptyList()
                val newId = c.foods.createCustom(name, brand, n, units)
                route.barcode?.let { setBarcode(newId, it) }
                newId
            }
            onDone(id)
        }

    private suspend fun setBarcode(id: Long, code: String) {
        val dao = c.db.foodDao()
        dao.byId(id)?.let { dao.update(it.copy(barcode = code)) }
    }
}

/** Create or edit your own product ("Not found? Create a product"). Values per 100 g. */
@Composable
fun FoodEditorScreen(nav: Navigator, route: FoodEditorRoute) {
    val vm = fuelViewModel(key = route.toString()) { c -> FoodEditorViewModel(c, route) }
    var name by remember { mutableStateOf(route.name ?: "") }
    var brand by remember { mutableStateOf("") }
    val values = remember { mutableStateMapOf<Nutrient, Double>() }
    var unitName by remember { mutableStateOf("") }
    var unitGrams by remember { mutableStateOf<Double?>(null) }
    val editing = route.foodId != 0L
    LaunchedEffect(Unit) {
        vm.load()?.let { f -> name = f.name; brand = f.brand.orEmpty(); values.putAll(f.sourcePer100g.values) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubScreenHeader(if (editing) "Edit food" else "New product", nav::back, subtitle = route.barcode?.let { "EAN $it" })
        FuelCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(name, { name = it }, "Name", placeholder = "e.g. Bäcker Brötchen")
                TextInput(brand, { brand = it }, "Brand (optional)")
            }
        }
        FuelCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            SectionTitle("Per 100 g")
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Energy in kJ (fills kcal)", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.weight(1f))
                NumberField(values[Nutrient.ENERGY]?.let { it * 4.184 }, { kj -> if (kj == null) values.remove(Nutrient.ENERGY) else values[Nutrient.ENERGY] = kj / 4.184 }, "Energy in kilojoules", width = 80.dp, allowEmpty = true)
                Text(" kJ", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.width(36.dp))
            }
            (Nutrient.LABEL + Nutrient.MICROS).forEach { n ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.label.replaceFirstChar { it.uppercase() }, style = FuelType.Small, color = if (n in Nutrient.MICROS) FuelColors.Muted else FuelColors.Ink, modifier = Modifier.weight(1f))
                    NumberField(values[n], { v -> if (v == null) values.remove(n) else values[n] = v }, n.label, width = 80.dp, allowEmpty = true)
                    Text(" ${n.unit.symbol}", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.width(36.dp))
                }
            }
            Text("Leave unknown values empty — they are flagged, not counted as 0.", style = FuelType.Tiny, color = FuelColors.Subtle, modifier = Modifier.padding(top = 6.dp))
        }
        if (!editing) FuelCard {
            SectionTitle("Serving unit (optional)")
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(unitName, { unitName = it }, "", placeholder = "e.g. piece", modifier = Modifier.weight(1f))
                Text("=", style = FuelType.Small, color = FuelColors.Muted)
                NumberField(unitGrams, { unitGrams = it }, "Grams per unit", width = 72.dp, allowEmpty = true)
                Text("g", style = FuelType.Small, color = FuelColors.Muted)
            }
        }
        PrimaryButton(
            if (editing) "Save" else "Create product",
            { vm.save(name, brand, values.toMap(), unitName, unitGrams) { id -> if (editing) nav.back() else nav.replace(FoodDetailRoute(id)) } },
            Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && values.containsKey(Nutrient.ENERGY),
        )
    }
}

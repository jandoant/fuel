package dev.jandoant.fuel.ui.myfoods

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.FoodSource
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.ui.components.FoodKindTile
import dev.jandoant.fuel.ui.components.MacroInline
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.SecondaryButton
import dev.jandoant.fuel.ui.components.SegmentedTabs
import dev.jandoant.fuel.ui.components.TextInput
import dev.jandoant.fuel.ui.nav.FoodDetailRoute
import dev.jandoant.fuel.ui.nav.FoodEditorRoute
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MyFoodsViewModel(c: AppContainer) : ViewModel() {
    val foods = c.foods.visible.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** My foods: every cached or self-made food, offline (PRD §4.3). */
@Composable
fun MyFoodsScreen(nav: Navigator) {
    val vm = fuelViewModel { c -> MyFoodsViewModel(c) }
    val all by vm.foods.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val list = all.filter { f ->
        (query.isBlank() || f.displayName.contains(query.trim(), true)) && when (tab) {
            1 -> f.favourite
            2 -> f.source == FoodSource.CUSTOM
            else -> true
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { MainScreenHeader("My foods", nav.openDrawer) { SecondaryButton("New product", { nav.go(FoodEditorRoute()) }, emphasized = true) } }
        item { TextInput(query, { query = it }, "", placeholder = "Search your foods") }
        item { SegmentedTabs(listOf("All", "Favorites", "Created by you"), tab, { tab = it }) }
        if (list.isEmpty()) item { Text("Foods you scan, search or create are kept here and work offline.", style = FuelType.Small, color = FuelColors.Muted) }
        items(list, key = { it.id }) { f -> FoodListRow(f) { nav.go(FoodDetailRoute(f.id)) } }
    }
}

@Composable
fun FoodListRow(f: Food, onClick: () -> Unit) {
    val u = f.defaultUnit
    val amount = Amounts.defaultAmount(u)
    val numbers = Nutrients.forGrams(f.per100g, Amounts.grams(amount, u)).macros()
    Row(
        Modifier.fillMaxWidth().clip(FuelShapes.Item).background(FuelColors.Surface).border(1.dp, FuelColors.CardBorder, FuelShapes.Item)
            .clickable(role = Role.Button, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodKindTile(false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.displayName, style = FuelType.Body, color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Amounts.label(amount, u), style = FuelType.Meta, color = FuelColors.Muted)
                Spacer(Modifier.width(6.dp))
                MacroInline(numbers, style = FuelType.Meta, showFiber = false)
                if (f.missing.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Text("· values missing", style = FuelType.Meta, color = FuelColors.WarningText) }
            }
        }
        Text("${numbers.kcal} kcal", style = FuelType.Small, color = FuelColors.Ink, modifier = Modifier.padding(start = 6.dp))
    }
}

package dev.jandoant.fuel.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import dev.jandoant.fuel.core.domain.RecipeTag
import dev.jandoant.fuel.core.domain.RecipeTemplate
import dev.jandoant.fuel.ui.components.FilterChip
import dev.jandoant.fuel.ui.components.FoodKindTile
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.MacroInline
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.components.SecondaryButton
import dev.jandoant.fuel.ui.components.TextInput
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.RecipeDetailRoute
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipesViewModel(private val c: AppContainer) : ViewModel() {
    val recipes = c.recipes.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun create(onCreated: (Long) -> Unit) = viewModelScope.launch { onCreated(c.recipes.create("New recipe")) }
}

private val FILTERS = listOf("All", "Favorites") + RecipeTag.entries.map { it.label }

/** Filter logic, pure for tests. */
fun filterRecipes(all: List<RecipeTemplate>, query: String, filter: Int): List<RecipeTemplate> = all.filter { r ->
    (query.isBlank() || r.name.contains(query.trim(), ignoreCase = true)) && when (filter) {
        0 -> true
        1 -> r.favourite
        else -> RecipeTag.entries[filter - 2] in r.tags
    }
}

/** Recipes (PRD §7.10, Recipes.dc.html). */
@Composable
fun RecipesScreen(nav: Navigator) {
    val vm = fuelViewModel { c -> RecipesViewModel(c) }
    val all by vm.recipes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val list = filterRecipes(all, query, filter)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            MainScreenHeader("Recipes", nav.openDrawer) {
                SecondaryButton("New recipe", { vm.create { nav.go(RecipeDetailRoute(it)) } }, emphasized = true)
            }
        }
        item { TextInput(query, { query = it }, "", placeholder = "Search recipes") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FILTERS.forEachIndexed { i, f -> FilterChip(f, i == filter, { filter = i }) }
            }
        }
        if (list.isEmpty()) item { Text(if (all.isEmpty()) "No recipes yet. A recipe is a template of ingredients at their usual amounts." else "No recipe matches.", style = FuelType.Small, color = FuelColors.Muted) }
        items(list, key = { it.id }) { r -> RecipeCard(r) { nav.go(RecipeDetailRoute(r.id)) } }
    }
}

@Composable
private fun RecipeCard(r: RecipeTemplate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(FuelShapes.Item).background(FuelColors.Surface).border(1.dp, FuelColors.CardBorder, FuelShapes.Item)
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodKindTile(true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = FuelType.BodyStrong, color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.favourite) { Spacer(Modifier.width(4.dp)); FuelIcon(FuelIcons.STAR, "Favorite", FuelColors.Fiber, size = 14.dp) }
            }
            val meta = (r.tags.map { it.label } + "${r.ingredients.size} ingredients" + if (r.usageCount > 0) "planned ${r.usageCount}×" else null).filterNotNull().joinToString(" · ")
            Text(meta, style = FuelType.Meta, color = FuelColors.Muted, maxLines = 1)
            MacroInline(r.numbers)
        }
        Text("${r.numbers.kcal} kcal", style = FuelType.Small, color = FuelColors.Ink)
    }
}

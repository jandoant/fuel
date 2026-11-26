package dev.jandoant.fuel.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.ui.components.MainScreenHeader
import dev.jandoant.fuel.ui.nav.*

@Composable
fun RecipesScreen(nav: Navigator) {
    Column(Modifier.padding(18.dp)) { MainScreenHeader("Recipes", nav.openDrawer) }
}

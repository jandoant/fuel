package dev.jandoant.fuel.ui.addfood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jandoant.fuel.core.model.DaySummary
import dev.jandoant.fuel.core.model.MacroNumbers
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.ui.components.FoodKindTile
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.MacroInline
import dev.jandoant.fuel.ui.components.PortionInputRow
import dev.jandoant.fuel.ui.components.PrimaryButton
import dev.jandoant.fuel.ui.components.SegmentedTabs
import dev.jandoant.fuel.ui.components.StatusText
import dev.jandoant.fuel.ui.components.SubScreenHeader
import dev.jandoant.fuel.ui.components.TextLink
import dev.jandoant.fuel.ui.components.hatched
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.components.scaleMax
import dev.jandoant.fuel.ui.nav.AddFoodRoute
import dev.jandoant.fuel.ui.nav.FoodDetailRoute
import dev.jandoant.fuel.ui.nav.FoodEditorRoute
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.RecipeDetailRoute
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import dev.jandoant.fuel.ui.theme.Macros
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Add food (PRD §7.2, AddFood.dc.html). The destination comes from the entry point — no meal dropdown. */
@Composable
fun AddFoodScreen(nav: Navigator, route: AddFoodRoute) {
    val vm = fuelViewModel(key = route.toString()) { c -> AddFoodViewModel(c, route) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val notFound by vm.notFoundBarcode.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) nav.back() }
    val s = state

    Column(Modifier.fillMaxSize().testTag("addFood")) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubScreenHeader("Add food", nav::back, subtitle = s?.destinationText?.let { "Adding to $it" })
            BarcodeScannerBox(vm::onBarcode)
            SearchField(query, { vm.query.value = it })
            if (query.isBlank()) SegmentedTabs(ListTab.entries.map { it.label }, s?.tab?.ordinal ?: 0, { vm.tab.value = ListTab.entries[it] })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s != null) {
                if (s.items.isEmpty() && query.isBlank()) item {
                    Text(
                        when (s.tab) {
                            ListTab.RECENT -> "Foods you add show up here. Scan a barcode or search to start."
                            ListTab.FAVORITES -> "Star foods or recipes to find them here."
                            ListTab.MINE -> "Your own products and recipes show up here."
                        },
                        style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(s.items, key = { it.key }) { item -> FoodRow(item, onOpen = { openDetail(nav, item) }, onAdd = { vm.open(item) }) }
                if (query.isNotBlank()) {
                    item { Text("Open Food Facts", style = FuelType.MetaStrong, color = FuelColors.Muted, modifier = Modifier.padding(top = 8.dp)) }
                    items(s.remote, key = { it.key }) { item -> FoodRow(item, onOpen = { vm.open(item) }, onAdd = { vm.open(item) }) }
                    if (s.remoteStatus != null) item { Text(s.remoteStatus, style = FuelType.Meta, color = FuelColors.Muted) }
                    else if (s.remote.isEmpty() && query.trim().length >= 3) item { Text("Searching…", style = FuelType.Meta, color = FuelColors.Muted) }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextLink("Not found? Create a product", { nav.go(FoodEditorRoute(name = query.takeIf { it.isNotBlank() })) })
                        if (s.allowRecipes) {
                            Text(" or ", style = FuelType.Small, color = FuelColors.Muted)
                            TextLink("recipe", { nav.go(dev.jandoant.fuel.ui.nav.RecipesRoute) })
                        }
                    }
                }
            }
        }
    }

    if (sheet != null && s != null) AddSheetView(vm, s)
    notFound?.let { code ->
        AlertDialog(
            onDismissRequest = vm::resetScan,
            containerColor = FuelColors.Surface,
            title = { Text("Product not found", style = FuelType.SectionHeading) },
            text = { Text("Barcode $code is not in Open Food Facts. Create it as your own product?", style = FuelType.Body) },
            confirmButton = { TextButton({ vm.resetScan(); nav.go(FoodEditorRoute(barcode = code)) }) { Text("Create product", color = FuelColors.AccentText, style = FuelType.BodyStrong) } },
            dismissButton = { TextButton(vm::resetScan) { Text("Cancel", color = FuelColors.Muted) } },
        )
    }
}

private fun openDetail(nav: Navigator, item: FoodItem) = when (item) {
    is FoodItem.Single -> nav.go(FoodDetailRoute(item.food.id))
    is FoodItem.Recipe -> nav.go(RecipeDetailRoute(item.recipe.id))
    is FoodItem.Remote -> Unit
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(FuelColors.Surface, FuelShapes.Item).border(1.dp, FuelColors.InputBorder, FuelShapes.Item).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuelIcon(FuelIcons.SEARCH, null, FuelColors.Muted, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search Open Food Facts or your foods", style = FuelType.Body.copy(fontWeight = FontWeight.Medium, fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), color = FuelColors.Subtle, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = FuelType.Body.copy(color = FuelColors.Ink, fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search food" }.testTag("search"),
            )
        }
        if (value.isNotEmpty()) dev.jandoant.fuel.ui.components.IconButton44(FuelIcons.CLOSE, "Clear search", { onChange("") }, tint = FuelColors.Muted, iconSize = 16.dp)
    }
}

@Composable
private fun FoodRow(item: FoodItem, onOpen: () -> Unit, onAdd: () -> Unit) {
    val (numbers, meta, recipeInfo) = when (item) {
        is FoodItem.Single -> Triple(item.defaultPortion.numbers, item.defaultPortion.label, null)
        is FoodItem.Recipe -> Triple(item.recipe.numbers, "1 portion", "Recipe · ${item.recipe.ingredients.size} ingr.")
        is FoodItem.Remote -> {
            val g = item.product.servingGrams ?: 100.0
            Triple(Nutrients.forGrams(item.product.per100g, g).macros(), "${g.roundToInt()} g", null)
        }
    }
    Row(
        Modifier.fillMaxWidth().background(FuelColors.Surface, FuelShapes.Item).border(1.dp, FuelColors.CardBorder, FuelShapes.Item),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(FuelShapes.Item).clickable(role = Role.Button, onClick = onOpen).padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodKindTile(item is FoodItem.Recipe)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = FuelType.Body, color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (recipeInfo != null) { Spacer(Modifier.width(6.dp)); Text(recipeInfo, style = FuelType.TinyStrong, color = FuelColors.Recipe, maxLines = 1) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meta, style = FuelType.Meta, color = FuelColors.Muted, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    MacroInline(numbers, style = FuelType.Meta, showFiber = false)
                }
            }
        }
        Text("${numbers.kcal} kcal", style = FuelType.Small, color = FuelColors.Ink)
        Box(
            Modifier.padding(horizontal = 4.dp).size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "Add ${item.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(28.dp).background(FuelColors.AccentBg, CircleShape), contentAlignment = Alignment.Center) {
                FuelIcon(FuelIcons.PLUS, null, FuelColors.AccentText, size = 16.dp, strokeWidth = 2.5f)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheetView(vm: AddFoodViewModel, s: AddFoodUiState) {
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val sh = sheet ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = vm::close,
        sheetState = sheetState,
        containerColor = FuelColors.Surface,
        shape = FuelShapes.Sheet,
        scrimColor = FuelColors.Scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).background(FuelColors.InputBorder, RoundedCornerShape(2.dp))) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 18.dp).navigationBarsPadding().testTag("addSheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(sh.title, style = FuelType.Number.copy(fontWeight = FontWeight.Bold), color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildAnnotatedString { append("Adding to "); withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FuelColors.Ink)) { append(s.destinationText) } },
                        style = FuelType.Meta, color = FuelColors.Muted,
                    )
                }
                MacroInline(sh.numbers, showKcal = true, modifier = Modifier.padding(top = 4.dp))
            }
            if (!sh.isRecipe) {
                val p = sh.portions.first()
                PortionInputRow(p.amount, p.unit, p.food.units, p.food.name, { u, a -> vm.changePortion(0, u, a) })
            } else {
                Column(Modifier.fillMaxWidth().background(FuelColors.RecipePanel, FuelShapes.Item).border(1.dp, FuelColors.RecipeRule, FuelShapes.Item).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Adjust for this entry. The recipe template stays unchanged.", style = FuelType.Tiny, color = FuelColors.Recipe, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                    sh.portions.forEachIndexed { i, p ->
                        dev.jandoant.fuel.ui.components.Divider(color = FuelColors.RecipeRule)
                        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.food.name, style = FuelType.Small, color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                MacroInline(p.numbers, showKcal = true)
                            }
                            PortionInputRow(p.amount, p.unit, p.food.units, p.food.name, { u, a -> vm.changePortion(i, u, a) })
                        }
                    }
                }
            }
            s.effect?.let { (before, after) -> EffectPanel(before, after) }
            s.recipeEffect?.let { (before, after) -> RecipeEffect(before, after) }
            PrimaryButton(sh.addLabel, { scope.launch { vm.add() } }, Modifier.fillMaxWidth().testTag("addButton"), enabled = !busy)
        }
    }
}

/** "Effect on today's plan": planned + this food + hatched goal zone, before → after and status. */
@Composable
private fun EffectPanel(before: DaySummary, after: DaySummary) {
    Column(Modifier.fillMaxWidth().background(FuelColors.Background, FuelShapes.Item).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Effect on the day's plan", style = FuelType.MetaStrong, color = FuelColors.Ink, modifier = Modifier.weight(1f))
            dev.jandoant.fuel.ui.components.LegendDot(FuelColors.CheckOutline, "planned")
            Spacer(Modifier.width(8.dp))
            dev.jandoant.fuel.ui.components.LegendDot(FuelColors.Ink, "this food")
        }
        val calZone = after.calories.zone
        EffectRow("kcal", FuelColors.Ink, FuelColors.CheckOutline, before.calories.planned, after.calories.planned, calZone?.low, calZone?.high, after.calories.status.let { StatusText.effect(it, "") },
            StatusText.tone(after.calories.status), maxOf((calZone?.high ?: after.calories.planned) * 1.3f, after.calories.planned * 1.05f))
        val lines = listOf(before.protein to after.protein, before.fat to after.fat, before.carbs to after.carbs, before.fiber to after.fiber)
        lines.zip(Macros.All).forEach { (pair, pal) ->
            val (b, a) = pair
            EffectRow(pal.name, pal.color, pal.soft, b.planned, a.planned, a.range?.min, a.range?.max, StatusText.effect(a.status, " g"), StatusText.tone(a.status), scaleMax(a.range, a.planned))
        }
    }
}

@Composable
private fun EffectRow(name: String, color: Color, band: Color, before: Int, after: Int, lo: Int?, hi: Int?, status: String, tone: dev.jandoant.fuel.ui.components.Tone, max: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = FuelType.TinyStrong, color = color, modifier = Modifier.width(50.dp))
        Box(Modifier.weight(1f)) {
            // planned (soft) then this food (solid) on top: draw as a ZoneBar where "eaten" = after, "planned" = before swapped colours.
            EffectBar(before.toFloat(), after.toFloat(), lo?.toFloat(), hi?.toFloat(), max, color, band)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = FuelColors.Muted)) { append("${fmt(before)}→") }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FuelColors.Ink)) { append(fmt(after)) }
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = when (tone) { dev.jandoant.fuel.ui.components.Tone.OK -> FuelColors.AccentDeep; dev.jandoant.fuel.ui.components.Tone.NEUTRAL -> FuelColors.Muted; else -> FuelColors.WarningText })) { append(status) }
            },
            style = FuelType.Tiny, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(132.dp),
        )
    }
}

@Composable
private fun EffectBar(before: Float, after: Float, lo: Float?, hi: Float?, max: Float, color: Color, band: Color) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(FuelColors.Line)) {
        val w = maxWidth
        fun pct(v: Float) = (v / max).coerceIn(0f, 1f)
        Box(Modifier.width(w * pct(before)).height(8.dp).background(band))
        Box(Modifier.padding(start = w * pct(before)).width(w * (pct(after) - pct(before)).coerceAtLeast(0f)).height(8.dp).background(color))
        if (lo != null) {
            val zh = hi?.let { pct(it) } ?: 1f
            Box(Modifier.padding(start = w * pct(lo)).width(w * (zh - pct(lo))).height(8.dp).background(Color(0x1A17191B)).hatched())
        }
    }
}

@Composable
private fun RecipeEffect(before: MacroNumbers, after: MacroNumbers) {
    Column(Modifier.fillMaxWidth().background(FuelColors.Background, FuelShapes.Item).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Effect on the recipe (default amounts)", style = FuelType.MetaStrong, color = FuelColors.Ink)
        Text("${fmt(before.kcal)} → ${fmt(after.kcal)} kcal", style = FuelType.Small, color = FuelColors.Ink)
        MacroInline(after)
    }
}


package dev.jandoant.fuel.ui.nav

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.FuelApp
import dev.jandoant.fuel.ui.addfood.AddFoodScreen
import dev.jandoant.fuel.ui.body.BodyScreen
import dev.jandoant.fuel.ui.calendar.CalendarScreen
import dev.jandoant.fuel.ui.checkin.CheckInScreen
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.fmt
import dev.jandoant.fuel.ui.components.fmt1
import dev.jandoant.fuel.ui.energy.EnergyScreen
import dev.jandoant.fuel.ui.food.FoodDetailScreen
import dev.jandoant.fuel.ui.food.FoodEditorScreen
import dev.jandoant.fuel.ui.myfoods.MyFoodsScreen
import dev.jandoant.fuel.ui.plan.PlanScreen
import dev.jandoant.fuel.ui.recipes.RecipeDetailScreen
import dev.jandoant.fuel.ui.recipes.RecipesScreen
import dev.jandoant.fuel.ui.settings.SettingsScreen
import dev.jandoant.fuel.ui.targets.TargetsScreen
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType
import dev.jandoant.fuel.ui.today.TodayScreen
import dev.jandoant.fuel.ui.trends.TrendsScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- Routes (type-safe navigation) ---
@Serializable object TodayRoute
@Serializable data class PlanRoute(val date: Long? = null)
@Serializable object CalendarRoute
@Serializable object BodyRoute
@Serializable object EnergyRoute
@Serializable object TrendsRoute
@Serializable data class CheckInRoute(val index: Int = 0)
@Serializable object RecipesRoute
@Serializable object MyFoodsRoute
@Serializable object TargetsRoute
@Serializable object SettingsRoute

/** Where "Add food" puts the food: a plan slot, a planned entry's own copy, or a recipe template. */
enum class AddTarget { PLAN, ENTRY, RECIPE }

@Serializable data class AddFoodRoute(val target: AddTarget = AddTarget.PLAN, val date: Long = 0, val slotId: String = "", val targetId: Long = 0)
@Serializable data class FoodDetailRoute(val foodId: Long)
@Serializable data class FoodEditorRoute(val foodId: Long = 0, val barcode: String? = null, val name: String? = null)
@Serializable data class RecipeDetailRoute(val recipeId: Long)

/** Thin wrapper the screens use to move around. */
class Navigator(private val nav: NavHostController, val openDrawer: () -> Unit) {
    fun back() { if (!nav.popBackStack()) nav.navigate(TodayRoute) }
    fun go(route: Any) = nav.navigate(route)
    /** Drawer destinations replace the stack above Today. */
    fun top(route: Any) = nav.navigate(route) {
        popUpTo(TodayRoute) { inclusive = route == TodayRoute }
        launchSingleTop = true
    }
    fun replace(route: Any) = nav.navigate(route) { nav.currentDestination?.id?.let { popUpTo(it) { inclusive = true } } }
}

/** ViewModel built from the app container, scoped to the current destination. */
@Composable
inline fun <reified VM : ViewModel> fuelViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = (LocalContext.current.applicationContext as FuelApp).container
    return viewModel(key = key) { create(c) }
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as FuelApp).container

private data class DrawerItem(val label: String, val icon: String, val route: Any)

private val MAIN = listOf(
    DrawerItem("Today", FuelIcons.NAV_TODAY, TodayRoute),
    DrawerItem("Meal plan", FuelIcons.NAV_PLAN, PlanRoute()),
    DrawerItem("Calendar", FuelIcons.NAV_CALENDAR, CalendarRoute),
    DrawerItem("Body", FuelIcons.NAV_BODY, BodyRoute),
    DrawerItem("Energy", FuelIcons.NAV_ENERGY, EnergyRoute),
    DrawerItem("Trends", FuelIcons.NAV_TRENDS, TrendsRoute),
    DrawerItem("Weekly check-in", FuelIcons.NAV_CHECKIN, CheckInRoute()),
)
private val SECONDARY = listOf(
    DrawerItem("Recipes", FuelIcons.NAV_RECIPES, RecipesRoute),
    DrawerItem("My foods", FuelIcons.NAV_FOODS, MyFoodsRoute),
    DrawerItem("Macro targets", FuelIcons.NAV_TARGETS, TargetsRoute),
    DrawerItem("Settings", FuelIcons.NAV_SETTINGS, SettingsRoute),
)

@Composable
fun FuelRoot() {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navigator = remember(nav) { Navigator(nav) { scope.launch { drawer.open() } } }
    val container = appContainer()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.messages.flow.collect { m ->
            val r = snackbar.showSnackbar(m.text, actionLabel = m.actionLabel, withDismissAction = m.actionLabel == null)
            if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val activeLabel = (MAIN + SECONDARY).firstOrNull { item -> dest?.route?.substringBefore('?')?.substringBefore('/')?.endsWith(item.route::class.simpleName ?: "-") == true }?.label

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        scrimColor = FuelColors.Scrim,
        drawerContent = {
            DrawerContent(activeLabel) { item ->
                scope.launch { drawer.close() }
                navigator.top(item.route)
            }
        },
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight().background(FuelColors.Background)) {
            NavHost(nav, startDestination = TodayRoute, modifier = Modifier.safeDrawingPadding()) {
                composable<TodayRoute> { TodayScreen(navigator) }
                composable<PlanRoute> { PlanScreen(navigator, it.toRoute<PlanRoute>().date) }
                composable<CalendarRoute> { CalendarScreen(navigator) }
                composable<BodyRoute> { BodyScreen(navigator) }
                composable<EnergyRoute> { EnergyScreen(navigator) }
                composable<TrendsRoute> { TrendsScreen(navigator) }
                composable<CheckInRoute> { CheckInScreen(navigator, it.toRoute<CheckInRoute>().index) }
                composable<RecipesRoute> { RecipesScreen(navigator) }
                composable<MyFoodsRoute> { MyFoodsScreen(navigator) }
                composable<TargetsRoute> { TargetsScreen(navigator) }
                composable<SettingsRoute> { SettingsScreen(navigator) }
                composable<AddFoodRoute> { AddFoodScreen(navigator, it.toRoute()) }
                composable<FoodDetailRoute> { FoodDetailScreen(navigator, it.toRoute<FoodDetailRoute>().foodId) }
                composable<FoodEditorRoute> { FoodEditorScreen(navigator, it.toRoute()) }
                composable<RecipeDetailRoute> { RecipeDetailScreen(navigator, it.toRoute<RecipeDetailRoute>().recipeId) }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp))
        }
    }
}

@Composable
private fun DrawerContent(active: String?, onPick: (DrawerItem) -> Unit) {
    val c = appContainer()
    val model by c.model.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    ModalDrawerSheet(
        drawerContainerColor = FuelColors.Surface,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.width(300.dp),
    ) {
        Column(Modifier.fillMaxHeight().safeDrawingPadding().padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
                Text("Fuel", style = FuelType.PageTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)), color = FuelColors.Ink)
                val trend = model?.trendToday?.let { "Trend ${fmt1(it)} kg" } ?: "No weigh-in yet"
                val target = model?.zoneToday?.display()?.target?.let { " · target ${fmt(it)} kcal" } ?: ""
                Text(trend + target, style = FuelType.Meta, color = FuelColors.Muted)
            }
            MAIN.forEach { DrawerRow(it, it.label == active, onPick, primary = true) }
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth().background(FuelColors.Line).size(1.dp))
            SECONDARY.forEach { DrawerRow(it, it.label == active, onPick, primary = false) }
            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
            val s = settings
            val (dot, text) = when {
                s?.lastSyncError != null -> FuelColors.Warning to "Health Connect · sync failed"
                s?.lastSyncAt != null -> FuelColors.Accent to "Health Connect · synced " + Instant.ofEpochMilli(s.lastSyncAt!!).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                else -> FuelColors.Faint to "Health Connect · not synced yet"
            }
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(dot, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(text, style = FuelType.Meta, color = FuelColors.Muted)
            }
        }
    }
}

@Composable
private fun DrawerRow(item: DrawerItem, on: Boolean, onPick: (DrawerItem) -> Unit, primary: Boolean) {
    val fg = if (on) FuelColors.AccentDeep else if (primary) FuelColors.Ink else FuelColors.Ink2
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (on) FuelColors.AccentBg else Color.Transparent)
            .clickable(role = Role.Tab) { onPick(item) }
            .semantics { selected = on }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuelIcon(item.icon, null, fg)
        Spacer(Modifier.width(14.dp))
        Text(item.label, style = FuelType.Body.copy(fontWeight = FontWeight(if (on) 600 else if (primary) 500 else 400)), color = fg)
    }
}

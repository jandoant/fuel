package dev.jandoant.fuel.ui.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.domain.RecipeTemplate
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.DaySummaries
import dev.jandoant.fuel.core.model.DaySummary
import dev.jandoant.fuel.core.model.MacroNumbers
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.data.off.OffProduct
import dev.jandoant.fuel.data.off.OffResult
import dev.jandoant.fuel.data.repo.PortionDraft
import dev.jandoant.fuel.ui.nav.AddFoodRoute
import dev.jandoant.fuel.ui.nav.AddTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A row in the food list: a cached food, a recipe template, or an Open Food Facts hit not cached yet. */
sealed interface FoodItem {
    val key: String
    val name: String

    data class Single(val food: Food) : FoodItem {
        override val key get() = "f${food.id}"
        override val name get() = food.displayName
        val defaultPortion get() = Portion(0, food, food.defaultUnit, Amounts.defaultAmount(food.defaultUnit))
    }

    data class Recipe(val recipe: RecipeTemplate) : FoodItem {
        override val key get() = "r${recipe.id}"
        override val name get() = recipe.name
    }

    data class Remote(val product: OffProduct) : FoodItem {
        override val key get() = "o${product.barcode}"
        override val name get() = if (product.brand != null) "${product.name} (${product.brand})" else product.name
    }
}

/** The "+" sheet: the portions being added, editable before adding. */
data class AddSheet(
    val title: String,
    val recipe: RecipeTemplate?,
    val portions: List<Portion>,
) {
    val isRecipe: Boolean get() = recipe != null
    val numbers: MacroNumbers get() = MacroNumbers.sum(portions.map { it.numbers })
    val addLabel: String get() = if (isRecipe) "Add recipe · ${numbers.kcal} kcal" else portions.first().let {
        if (it.unit.isGrams) "Add ${Amounts.label(it.amount, it.unit)}" else "Add ${Amounts.formatNumber(it.amount)} × ${it.unit.shortName}"
    }
}

enum class ListTab(val label: String) { RECENT("Recent"), FAVORITES("Favorites"), MINE("My foods") }

data class AddFoodUiState(
    val destinationText: String,
    val tab: ListTab,
    val query: String,
    val items: List<FoodItem>,
    val remote: List<FoodItem.Remote>,
    val remoteStatus: String?,
    val allowRecipes: Boolean,
    val effect: Pair<DaySummary, DaySummary>?,
    /** For recipe targets: recipe totals before → after. */
    val recipeEffect: Pair<MacroNumbers, MacroNumbers>?,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AddFoodViewModel(private val c: AppContainer, private val route: AddFoodRoute) : ViewModel() {

    val tab = MutableStateFlow(ListTab.RECENT)
    val query = MutableStateFlow("")
    val sheet = MutableStateFlow<AddSheet?>(null)
    val busy = MutableStateFlow(false)
    /** Set when an add finished; the screen navigates back. */
    val done = MutableStateFlow(false)
    /** A barcode that was not found; the screen offers "Create product". */
    val notFoundBarcode = MutableStateFlow<String?>(null)

    private var targetEntry: PlanEntry? = null
    private var targetRecipe: RecipeTemplate? = null
    private val targetDate = MutableStateFlow<LocalDate?>(if (route.target == AddTarget.PLAN) LocalDate.ofEpochDay(route.date) else null)
    private val destination = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            when (route.target) {
                AddTarget.PLAN -> {
                    val slot = c.plan.slots.first().firstOrNull { it.id == route.slotId }
                    destination.value = "${slot?.name ?: "Plan"} · ${dayWord(LocalDate.ofEpochDay(route.date), today)}"
                }
                AddTarget.ENTRY -> {
                    val e = c.plan.entry(route.targetId)
                    targetEntry = e
                    targetDate.value = e?.date
                    destination.value = "${e?.title ?: "entry"} · your copy"
                }
                AddTarget.RECIPE -> {
                    val r = c.recipes.get(route.targetId)
                    targetRecipe = r
                    destination.value = "recipe ${r?.name ?: ""} (template)"
                }
            }
        }
    }

    private val recipes = c.recipes.all
    private val dayEntries = targetDate.flatMapLatest { d -> if (d == null) flowOf(emptyList()) else c.plan.observeDay(d) }

    private val listItems = combine(tab, c.foods.recent, c.foods.favourites, c.foods.custom, recipes) { t, recent, fav, custom, rec ->
        val allowRecipes = route.target == AddTarget.PLAN
        when (t) {
            ListTab.RECENT -> recent.map { FoodItem.Single(it) }
            ListTab.FAVORITES -> (if (allowRecipes) rec.filter { it.favourite }.map { FoodItem.Recipe(it) } else emptyList()) + fav.map { FoodItem.Single(it) }
            ListTab.MINE -> (if (allowRecipes) rec.map { FoodItem.Recipe(it) } else emptyList()) + custom.map { FoodItem.Single(it) }
        }
    }

    private val localSearch = combine(query.debounce(150), recipes) { q, rec -> q to rec }.mapLatest { (q, rec) ->
        if (q.isBlank()) null
        else {
            val foods = c.foods.searchLocal(q).map { FoodItem.Single(it) }
            val r = if (route.target == AddTarget.PLAN) rec.filter { it.name.contains(q.trim(), ignoreCase = true) }.map { FoodItem.Recipe(it) } else emptyList()
            r + foods
        }
    }

    private val remoteSearch = query.debounce(600).map { it.trim() }.distinctUntilChanged().mapLatest { q ->
        if (q.length < 3) emptyList<FoodItem.Remote>() to null
        else {
            val country = c.settings.current().offCountry
            when (val r = c.foods.searchOff(q, country)) {
                is OffResult.Ok -> r.value.map { FoodItem.Remote(it) } to (if (r.value.isEmpty()) "No results on Open Food Facts" else null)
                OffResult.NotFound -> emptyList<FoodItem.Remote>() to "No results on Open Food Facts"
                is OffResult.Error -> emptyList<FoodItem.Remote>() to r.message
            }
        }
    }

    val state: StateFlow<AddFoodUiState?> = combine(
        combine(tab, query, listItems, localSearch, remoteSearch) { t, q, list, local, remote -> Quint(t, q, list, local, remote) },
        dayEntries, sheet, c.model.state, destination,
    ) { base, entries, sh, model, dest ->
        val known = base.local.orEmpty().filterIsInstance<FoodItem.Single>().mapNotNull { it.food.barcode }.toSet()
        val effect = if (sh != null && targetDate.value != null) {
            val date = targetDate.value!!
            val items = entries.map { it.plannedItem }
            val added = sh.numbers
            // Adding an ingredient to an entry: the entry already counts, so only the new portion is added.
            DaySummaries.effect(items, added, model?.zoneFor(date), model?.targetsFor(date))
        } else null
        val recipeEffect = if (sh != null && targetRecipe != null) targetRecipe!!.numbers.let { it to (it + sh.numbers) } else null
        AddFoodUiState(
            destinationText = dest,
            tab = base.tab,
            query = base.query,
            // Products without nutrition data are useless for logging: hidden from lists and search
            // (a barcode scan still finds them and offers "Fill in").
            items = (base.local ?: base.list).filter { it !is FoodItem.Single || it.food.hasEnergy },
            remote = base.remote.first.filter { it.product.barcode !in known && it.product.hasNutrition },
            remoteStatus = base.remote.second,
            allowRecipes = route.target == AddTarget.PLAN,
            effect = effect,
            recipeEffect = recipeEffect,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class Quint(val tab: ListTab, val query: String, val list: List<FoodItem>, val local: List<FoodItem>?, val remote: Pair<List<FoodItem.Remote>, String?>)

    // --- Sheet ---

    fun open(item: FoodItem) {
        when (item) {
            is FoodItem.Single -> sheet.value = AddSheet(item.food.name, null, listOf(item.defaultPortion))
            is FoodItem.Recipe -> sheet.value = AddSheet(item.recipe.name, item.recipe, item.recipe.ingredients)
            is FoodItem.Remote -> viewModelScope.launch {
                busy.value = true
                val food = c.foods.cache(item.product)
                busy.value = false
                open(FoodItem.Single(food))
            }
        }
    }

    fun close() { sheet.value = null }

    fun changePortion(index: Int, unit: ServingUnit, amount: Double) {
        val s = sheet.value ?: return
        sheet.value = s.copy(portions = s.portions.mapIndexed { i, p -> if (i == index) p.copy(unit = unit, amount = Amounts.clamp(amount, unit)) else p })
    }

    fun add() {
        val s = sheet.value ?: return
        viewModelScope.launch {
            busy.value = true
            val drafts = s.portions.map { PortionDraft(it.food.id, it.unit.id, it.amount) }
            when (route.target) {
                AddTarget.PLAN -> {
                    val date = LocalDate.ofEpochDay(route.date)
                    if (s.recipe != null) c.plan.addRecipe(date, route.slotId, s.recipe, drafts)
                    else c.plan.addSingle(date, route.slotId, drafts.first())
                }
                AddTarget.ENTRY -> drafts.forEach { c.plan.addIngredient(route.targetId, it) }
                AddTarget.RECIPE -> drafts.forEach { c.recipes.addIngredient(route.targetId, it) }
            }
            c.messages.show("Added ${s.title} to ${destination.value}")
            busy.value = false
            sheet.value = null
            done.value = true
        }
    }

    // --- Voice ---

    val voice = MutableStateFlow<VoiceReview?>(null)
    private val matcher = VoiceMatcher(c.foods)

    /** Dictated text → items → matched foods, shown for confirmation before anything is added. */
    fun onVoice(text: String) {
        val items = dev.jandoant.fuel.core.model.VoiceParser.parse(text)
        if (items.isEmpty()) { c.messages.show("Nothing recognised in “$text”"); return }
        voice.value = VoiceReview(text, items.map { VoiceLine(it) })
        items.forEachIndexed { i, item ->
            viewModelScope.launch {
                val country = c.settings.current().offCountry
                val cands = runCatching { matcher.candidates(item.query, country) }.getOrDefault(emptyList())
                val resolved = cands.firstOrNull()?.let { runCatching { matcher.portion(item, it) }.getOrNull() }
                updateLine(i) { it.copy(candidates = cands, chosen = 0, portion = resolved?.first, note = resolved?.second, loading = false, include = resolved != null) }
            }
        }
    }

    fun chooseCandidate(index: Int, candidate: Int) {
        val line = voice.value?.lines?.getOrNull(index) ?: return
        val cand = line.candidates.getOrNull(candidate) ?: return
        updateLine(index) { it.copy(chosen = candidate, loading = true) }
        viewModelScope.launch {
            val (portion, note) = matcher.portion(line.item, cand)
            updateLine(index) { it.copy(portion = portion, note = note, loading = false, include = true) }
        }
    }

    fun changeVoicePortion(index: Int, unit: ServingUnit, amount: Double) = updateLine(index) { it.withPortion(unit, amount).copy(note = null) }
    fun toggleVoiceLine(index: Int) = updateLine(index) { it.copy(include = !it.include && it.portion != null) }
    fun closeVoice() { voice.value = null }

    private fun updateLine(index: Int, f: (VoiceLine) -> VoiceLine) {
        val v = voice.value ?: return
        voice.value = v.copy(lines = v.lines.mapIndexed { i, l -> if (i == index) f(l) else l })
    }

    fun addVoice() {
        val portions = voice.value?.toAdd.orEmpty()
        if (portions.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            val drafts = portions.map { PortionDraft(it.food.id, it.unit.id, it.amount) }
            when (route.target) {
                AddTarget.PLAN -> drafts.forEach { c.plan.addSingle(LocalDate.ofEpochDay(route.date), route.slotId, it) }
                AddTarget.ENTRY -> drafts.forEach { c.plan.addIngredient(route.targetId, it) }
                AddTarget.RECIPE -> drafts.forEach { c.recipes.addIngredient(route.targetId, it) }
            }
            c.messages.show("Added ${portions.size} ${if (portions.size == 1) "food" else "foods"} to ${destination.value}")
            busy.value = false
            voice.value = null
            done.value = true
        }
    }

    // --- Barcode ---

    private var lastScan: String? = null

    fun onBarcode(code: String) {
        if (code == lastScan || busy.value || sheet.value != null) return
        lastScan = code
        viewModelScope.launch {
            busy.value = true
            val country = c.settings.current().offCountry
            when (val r = c.foods.byBarcode(code, country)) {
                is OffResult.Ok -> open(FoodItem.Single(r.value))
                OffResult.NotFound -> notFoundBarcode.value = code
                is OffResult.Error -> c.messages.show(r.message)
            }
            busy.value = false
        }
    }

    fun resetScan() { lastScan = null; notFoundBarcode.value = null }

    companion object {
        fun dayWord(d: LocalDate, today: LocalDate) = when (d) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            today.minusDays(1) -> "yesterday"
            else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH))
        }
    }
}

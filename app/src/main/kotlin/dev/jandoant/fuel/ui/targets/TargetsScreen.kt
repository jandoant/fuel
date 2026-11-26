package dev.jandoant.fuel.ui.targets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jandoant.fuel.AppContainer
import dev.jandoant.fuel.core.model.MacroTargetModel
import dev.jandoant.fuel.core.model.TrendWeight
import dev.jandoant.fuel.data.settings.UserSettings
import dev.jandoant.fuel.ui.components.FilterChip
import dev.jandoant.fuel.ui.components.FuelCard
import dev.jandoant.fuel.ui.components.NumberField
import dev.jandoant.fuel.ui.components.SectionTitle
import dev.jandoant.fuel.ui.components.SubScreenHeader
import dev.jandoant.fuel.ui.components.fmt1
import dev.jandoant.fuel.ui.nav.Navigator
import dev.jandoant.fuel.ui.nav.fuelViewModel
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TargetsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val model = c.model.state
    fun update(f: (UserSettings) -> UserSettings) = viewModelScope.launch { c.settings.update(f) }
}

/** Macro & fiber targets (PRD §7.11, Targets.dc.html). */
@Composable
fun TargetsScreen(nav: Navigator) {
    val vm = fuelViewModel { c -> TargetsViewModel(c) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val st = s ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubScreenHeader("Macro & fiber targets", nav::back)
        val m = model
        FuelCard {
            SectionTitle("Reference weight")
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(7, 14, 28).forEach { w ->
                    val ref = m?.let { TrendWeight.referenceWeight(it.run.trend, it.today, w) }
                    FilterChip("$w days" + (ref?.let { " · ${fmt1(it)} kg" } ?: ""), st.referenceWindowDays == w, { vm.update { it.copy(referenceWindowDays = w) } })
                }
            }
            Text("Average trend weight over the window, recomputed once a day.", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 8.dp))
        }
        val ref = m?.snapshotToday?.referenceKg
        RangeCard("Protein", FuelColors.Protein, st.proteinMinGPerKg, st.proteinMaxGPerKg, ref,
            { v -> vm.update { it.copy(proteinMinGPerKg = v) } }, { v -> vm.update { it.copy(proteinMaxGPerKg = v) } })
        RangeCard("Fat", FuelColors.Fat, st.fatMinGPerKg, st.fatMaxGPerKg, ref,
            { v -> vm.update { it.copy(fatMinGPerKg = v) } }, { v -> vm.update { it.copy(fatMaxGPerKg = v) } })
        FuelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fiber minimum", style = FuelType.CardTitle, color = FuelColors.Fiber, modifier = Modifier.weight(1f))
                NumberField(st.fiberMinG.toDouble(), { v -> v?.let { n -> vm.update { it.copy(fiberMinG = n.toInt().coerceIn(5, 100)) } } }, "Fiber minimum grams per day", width = 64.dp)
                Text(" g/day", style = FuelType.Small, color = FuelColors.Muted)
            }
            Text("Default 30 g (DGE).", style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(top = 6.dp))
        }
        val targets = m?.targetsToday
        val zone = m?.zoneToday
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Carbs have no target of their own") }
                append(" — they fill the rest of the calorie zone.")
                if (targets != null && zone != null) {
                    val r = MacroTargetModel.indicativeCarbRange(zone.target, targets)
                    append(" Today that is roughly ${r.min}–${r.max} g, depending on the protein and fat you plan.")
                }
            },
            style = FuelType.Meta, color = FuelColors.Ink2,
            modifier = Modifier.fillMaxWidth().background(FuelColors.CarbsTint, FuelShapes.Item).padding(12.dp),
        )
    }
}

@Composable
private fun RangeCard(name: String, color: androidx.compose.ui.graphics.Color, min: Double, max: Double, refKg: Double?, onMin: (Double) -> Unit, onMax: (Double) -> Unit) {
    FuelCard {
        Text(name, style = FuelType.CardTitle, color = color)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Min", style = FuelType.Small, color = FuelColors.Muted)
            NumberField(min, { it?.let { v -> if (v in 0.1..5.0) onMin(v) } }, "$name minimum g per kg", width = 56.dp)
            Text("Max", style = FuelType.Small, color = FuelColors.Muted)
            NumberField(max, { it?.let { v -> if (v in 0.1..5.0) onMax(v) } }, "$name maximum g per kg", width = 56.dp)
            Text("g/kg", style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.width(40.dp))
        }
        Text(
            if (refKg != null) "= ${Math.round(min * refKg)}–${Math.round(max * refKg)} g/day at ${fmt1(refKg)} kg" else "g/day appear after the first weigh-in",
            style = FuelType.Meta, color = FuelColors.Ink2, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

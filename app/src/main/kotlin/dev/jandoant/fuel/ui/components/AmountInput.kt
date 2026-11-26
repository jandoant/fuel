package dev.jandoant.fuel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType

/**
 * Compact stepper (PRD §7.13): − / typeable field / +, 32 dp high. Accepts comma or point, keeps the raw text while
 * typing (only in-range values are applied), normalises on blur, and respects the unit's step and range.
 */
@Composable
fun AmountStepper(
    amount: Double,
    unit: ServingUnit,
    onAmount: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(Amounts.format(amount, unit)) }
    LaunchedEffect(amount, unit, focused) { if (!focused) text = Amounts.format(amount, unit) }
    val focus = LocalFocusManager.current

    Row(
        modifier.height(32.dp).border(1.dp, FuelColors.InputBorder, FuelShapes.Input).background(FuelColors.Surface, FuelShapes.Input),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", "Less $label", Amounts.canDecrement(amount, unit)) { focus.clearFocus(); onAmount(Amounts.decrement(amount, unit)) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(FuelColors.Line))
        BasicTextField(
            value = text,
            onValueChange = { t ->
                text = t
                Amounts.acceptTyped(t, unit)?.let(onAmount)
            },
            singleLine = true,
            textStyle = FuelType.Small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FuelColors.Ink, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .width(48.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "Amount of $label" },
            decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } },
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(FuelColors.Line))
        StepButton("+", "More $label", Amounts.canIncrement(amount, unit)) { focus.clearFocus(); onAmount(Amounts.increment(amount, unit)) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp, 32.dp)
            .background(FuelColors.Background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = FuelType.NumberSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = if (enabled) FuelColors.Ink else FuelColors.Faint) }
}

/** Compact unit dropdown (34 dp). */
@Composable
fun UnitDropdown(units: List<ServingUnit>, selected: ServingUnit, onSelect: (ServingUnit) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .height(34.dp)
                .widthIn(min = 64.dp)
                .border(1.dp, FuelColors.InputBorder, FuelShapes.Input)
                .background(FuelColors.Surface, FuelShapes.Input)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = "Serving size for $label: ${selected.name}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.name, style = FuelType.Small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            FuelIcon(FuelIcons.CHEVRON_DOWN, null, FuelColors.Muted, size = 14.dp)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = FuelColors.Surface) {
            units.forEach { u ->
                DropdownMenuItem(text = { Text(u.name, style = FuelType.Small) }, onClick = { open = false; if (u.id != selected.id) onSelect(u) })
            }
        }
    }
}

/** Stepper + unit dropdown + "= 150 g", the row used everywhere an amount is edited. */
@Composable
fun PortionInputRow(
    amount: Double,
    unit: ServingUnit,
    units: List<ServingUnit>,
    label: String,
    onChange: (ServingUnit, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AmountStepper(amount, unit, { onChange(unit, it) }, label)
        Spacer(Modifier.width(8.dp))
        UnitDropdown(units, unit, { nu -> onChange(nu, Amounts.convert(amount, unit, nu)) }, label, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(Amounts.gramsHint(amount, unit), style = FuelType.Tiny, color = FuelColors.Muted, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 46.dp))
    }
}

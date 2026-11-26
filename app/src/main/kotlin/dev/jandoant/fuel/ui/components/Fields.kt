package dev.jandoant.fuel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType

/**
 * Small bordered number field. Keeps the raw text while focused; [onValue] fires for every parsable value; on
 * blur the field shows the last applied value again.
 */
@Composable
fun NumberField(
    value: Double?,
    onValue: (Double?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    width: Dp? = 56.dp,
    readOnly: Boolean = false,
    allowEmpty: Boolean = false,
    align: TextAlign = TextAlign.Center,
) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(value?.let { Amounts.formatNumber(it) } ?: "") }
    LaunchedEffect(value, focused) { if (!focused) text = value?.let { Amounts.formatNumber(it) } ?: "" }
    val m = if (width != null) modifier.width(width) else modifier
    BasicTextField(
        value = text,
        onValueChange = { t ->
            if (readOnly) return@BasicTextField
            text = t
            if (t.isBlank() && allowEmpty) onValue(null) else Amounts.parse(t)?.let { onValue(it) }
        },
        readOnly = readOnly,
        singleLine = true,
        textStyle = FuelType.Meta.copy(color = FuelColors.Ink, textAlign = align),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = m.height(30.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(30.dp)
                    .background(if (readOnly) FuelColors.Background else FuelColors.Surface, RoundedCornerShape(7.dp))
                    .border(1.dp, if (readOnly) Color.Transparent else FuelColors.InputBorder, RoundedCornerShape(7.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

@Composable
fun TextInput(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier) {
        if (label.isNotEmpty()) Text(label, style = FuelType.Meta, color = FuelColors.Muted, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = { if (!readOnly) onValue(it) },
            singleLine = true,
            readOnly = readOnly,
            textStyle = FuelType.Body.copy(color = FuelColors.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label.ifEmpty { placeholder } },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(44.dp)
                        .background(if (readOnly) FuelColors.Background else FuelColors.Surface, RoundedCornerShape(10.dp))
                        .border(1.dp, FuelColors.InputBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = FuelType.Body, color = FuelColors.Faint)
                    inner()
                }
            },
        )
    }
}

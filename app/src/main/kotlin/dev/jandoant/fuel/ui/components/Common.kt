package dev.jandoant.fuel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.model.MacroNumbers
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import java.text.NumberFormat
import java.util.Locale

/** "2,450" — the prototype formats numbers en-US. */
fun fmt(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
fun fmt(n: Double): String = fmt(Math.round(n).toInt())
fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)
fun signed1(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "±") + fmt1(kotlin.math.abs(v))
fun signedInt(v: Int): String = (if (v > 0) "+" else if (v < 0) "−" else "±") + fmt(kotlin.math.abs(v))

@Composable
fun FuelCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    shape: Shape = FuelShapes.Card,
    border: Color = FuelColors.CardBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(1.dp, shape, ambientColor = Color(0x14281E14), spotColor = Color(0x14281E14))
            .background(FuelColors.Surface, shape)
            .border(1.dp, border, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun RoundIconButton(path: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, iconSize: Dp = 20.dp) {
    Box(
        modifier
            .size(size)
            .shadow(1.dp, CircleShape, ambientColor = Color(0x10281E14), spotColor = Color(0x10281E14))
            .background(FuelColors.Surface, CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { FuelIcon(path, null, FuelColors.Ink, size = iconSize, strokeWidth = 2.2f) }
}

@Composable
fun IconButton44(path: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = FuelColors.Ink, iconSize: Dp = 22.dp, enabled: Boolean = true) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { FuelIcon(path, null, if (enabled) tint else FuelColors.Faint, size = iconSize) }
}

/** Header of sub-screens: back arrow, title and optional subtitle and actions. */
@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton44(FuelIcons.BACK, "Back", onBack, Modifier.offset(x = (-12).dp))
        Column(Modifier.weight(1f).offset(x = (-12).dp)) {
            Text(title, style = FuelType.PageTitle, color = FuelColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = FuelType.Meta, color = FuelColors.Muted, maxLines = 2)
        }
        actions()
    }
}

/** Header of main screens (drawer destinations): menu button + large title. */
@Composable
fun MainScreenHeader(title: String, onMenu: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton44("M4 7h16M4 12h16M4 17h16", "Open menu", onMenu, Modifier.offset(x = (-10).dp))
        Text(title, style = FuelType.LargeTitle, color = FuelColors.Ink, modifier = Modifier.weight(1f).offset(x = (-6).dp).semantics { heading() })
        actions()
    }
}

enum class Tone { OK, WARNING, DANGER, NEUTRAL }

@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier, small: Boolean = false) {
    val (fg, bg) = when (tone) {
        Tone.OK -> FuelColors.AccentDeep to FuelColors.AccentBg
        Tone.WARNING -> FuelColors.WarningText to FuelColors.WarningBg
        Tone.DANGER -> FuelColors.Danger to FuelColors.DangerBg
        Tone.NEUTRAL -> FuelColors.Ink2 to FuelColors.Line
    }
    Row(
        modifier.background(bg, FuelShapes.Chip).padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone == Tone.DANGER) {
            FuelIcon(FuelIcons.WARNING, "Warning", fg, size = 11.dp, strokeWidth = 2.5f)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = if (small) FuelType.Micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) else FuelType.MetaStrong, color = fg, maxLines = 1)
    }
}

/** "P 17  F 0  C 6  Fib 0" in the macro colours. */
@Composable
fun MacroInline(m: MacroNumbers, modifier: Modifier = Modifier, style: TextStyle = FuelType.TinyStrong, showFiber: Boolean = true, showKcal: Boolean = false) {
    Text(
        buildAnnotatedString {
            if (showKcal) { withStyle(SpanStyle(color = FuelColors.Ink)) { append("${m.kcal}") }; append("  ") }
            withStyle(SpanStyle(color = FuelColors.Protein)) { append("P ${m.protein}") }
            append("  ")
            withStyle(SpanStyle(color = FuelColors.Fat)) { append("F ${m.fat}") }
            append("  ")
            withStyle(SpanStyle(color = FuelColors.Carbs)) { append("C ${m.carbs}") }
            if (showFiber) {
                append("  ")
                withStyle(SpanStyle(color = FuelColors.Fiber)) { append("Fib ${m.fiber}") }
            }
        },
        modifier = modifier,
        style = style,
        maxLines = 1,
        softWrap = false,
    )
}

/** Segmented control used for tabs (Recent / Favorites / My foods, ranges, week/month). */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(FuelColors.Line, FuelShapes.Item).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) FuelColors.Surface else Color.Transparent)
                    .clickable(role = Role.Tab, onClick = { onSelect(i) })
                    .semantics { contentDescription = label + if (on) ", selected" else "" },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = FuelType.Small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(if (on) 600 else 500)), color = FuelColors.Ink) }
        }
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(FuelShapes.Chip)
            .background(if (selected) FuelColors.AccentBg else FuelColors.Surface)
            .border(1.dp, if (selected) FuelColors.Accent else FuelColors.InputBorder, FuelShapes.Chip)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, style = FuelType.Meta, color = if (selected) FuelColors.AccentDeep else FuelColors.Ink) }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 50.dp)
            .clip(FuelShapes.Entry)
            .background(if (enabled) FuelColors.Accent else FuelColors.AccentSoft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = FuelType.Body.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, emphasized: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(FuelShapes.Item)
            .background(if (emphasized) FuelColors.AccentBg else FuelColors.Surface)
            .border(1.dp, if (emphasized) FuelColors.Accent else FuelColors.InputBorder, FuelShapes.Item)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = FuelType.Small, color = if (!enabled) FuelColors.Faint else if (emphasized) FuelColors.AccentDeep else FuelColors.Ink, maxLines = 1) }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = FuelColors.AccentText, style: TextStyle = FuelType.Small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
    Text(
        text, style = style, color = color,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 36.dp).padding(vertical = 8.dp, horizontal = 2.dp),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = FuelType.SectionHeading, color = FuelColors.Ink, modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}

@Composable
fun LegendDot(color: Color, label: String, wide: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (wide) 12.dp else 8.dp, if (wide) 3.dp else 8.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = FuelType.Micro, color = FuelColors.Muted)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier, color: Color = FuelColors.Line2) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Dashed rounded outline (planned entries, add rows). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp, dash: Dp = 6.dp, gap: Dp = 4.dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
    )
}

/** White diagonal hatching over a goal zone (design-system "goal zone" rule). */
fun Modifier.hatched(color: Color = Color.White.copy(alpha = 0.95f), back: Color = Color.Transparent, spacing: Dp = 5.dp, line: Dp = 1.5.dp) = drawWithContent {
    drawContent()
    if (back != Color.Transparent) drawRect(back)
    val s = spacing.toPx()
    val lw = line.toPx()
    clipRect {
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = lw)
            x += s
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = FuelType.Small, color = FuelColors.Muted, modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = FuelColors.Ink) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FuelType.Small, color = FuelColors.Muted, modifier = Modifier.weight(1f))
        Text(value, style = FuelType.Small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = valueColor)
    }
}

val NoBorder = BorderStroke(0.dp, Color.Transparent)

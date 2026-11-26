package dev.jandoant.fuel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Tokens from docs/design-system.md (style D). */
object FuelColors {
    val Background = Color(0xFFF6F3EE)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1E1B18)
    val Muted = Color(0xFF776E65)
    val Subtle = Color(0xFF857C73)
    val Line = Color(0xFFEFE9E1)
    val TrackStrong = Color(0xFFE9E2D8)
    val Dashed = Color(0xFFDDD5CB)
    val InputBorder = Color(0xFFDED6CB)
    val Accent = Color(0xFF22A06B)
    val AccentSoft = Color(0xFFBFE6D2)
    val AccentText = Color(0xFF177A50)
    val AccentBg = Color(0xFFE6F5EC)
    val Protein = Color(0xFF4F46E5)
    val ProteinSoft = Color(0xFFC7C9F7)
    val ProteinTint = Color(0xFFEEF0FF)
    val Fat = Color(0xFFEA6A2A)
    val FatSoft = Color(0xFFF8CDB4)
    val FatTint = Color(0xFFFFF1E8)
    val Carbs = Color(0xFF1F9D63)
    val CarbsSoft = Color(0xFFB9E5CD)
    val CarbsTint = Color(0xFFE8F6EE)
    val Fiber = Color(0xFFA27B2C)
    val FiberSoft = Color(0xFFE9D7AE)
    val FiberTint = Color(0xFFFBF3E2)
    val Recipe = Color(0xFF7048C7)
    val RecipeBg = Color(0xFFF1EBFC)
    val Warning = Color(0xFFC2410C)
    val WarningBg = Color(0xFFFFEDE3)
    val Danger = Color(0xFFB42318)
    val DangerBg = Color(0xFFFDE8E6)
}

/**
 * TODO: bundle Manrope (500/600/700/800) in res/font and replace FontFamily.Default.
 * The font files could not be downloaded from the build container.
 */
private val Manrope = FontFamily.Default

object FuelType {
    val ScreenTitle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    val SectionHeading = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    val Body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val Meta = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val BigNumber = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
}

@Composable
fun FuelTheme(content: @Composable () -> Unit) {
    // Material3 is only used as a base for components; colours come from FuelColors, not the stock look.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FuelColors.Accent,
            background = FuelColors.Background,
            surface = FuelColors.Surface,
            onBackground = FuelColors.Ink,
            onSurface = FuelColors.Ink,
            error = FuelColors.Danger,
        ),
        content = content,
    )
}

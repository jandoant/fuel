package dev.jandoant.fuel.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jandoant.fuel.R

/** Tokens from docs/design-system.md (style D). */
object FuelColors {
    val Background = Color(0xFFF6F3EE)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1E1B18)
    val Ink2 = Color(0xFF4A433C)
    val Muted = Color(0xFF776E65)
    val Subtle = Color(0xFF857C73)
    val Faint = Color(0xFFB5ABA0)
    val Line = Color(0xFFEFE9E1)
    val Line2 = Color(0xFFF2EDE6)
    val CardBorder = Color(0xFFF1ECE5)
    val TrackStrong = Color(0xFFE9E2D8)
    val Dashed = Color(0xFFDDD5CB)
    val InputBorder = Color(0xFFDED6CB)
    val CheckOutline = Color(0xFFD6CEC4)
    val Accent = Color(0xFF22A06B)
    val AccentSoft = Color(0xFFBFE6D2)
    val AccentText = Color(0xFF177A50)
    val AccentDeep = Color(0xFF136A45)
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
    val RecipePanel = Color(0xFFFAF7FE)
    val RecipeRule = Color(0xFFECE4FA)
    val RecipeOutline = Color(0xFFD9CBF4)
    val Warning = Color(0xFFC2410C)
    val WarningText = Color(0xFF9A3412)
    val WarningBg = Color(0xFFFFEDE3)
    val Danger = Color(0xFFB42318)
    val DangerBg = Color(0xFFFDE8E6)
    val Scrim = Color(0x6B17191B)
    val Camera = Color(0xFF2A2622)
}

/** Colours of one macro in the fixed order P · F · C · Fib. */
data class MacroPalette(val name: String, val color: Color, val soft: Color, val tint: Color)

object Macros {
    val Protein = MacroPalette("Protein", FuelColors.Protein, FuelColors.ProteinSoft, FuelColors.ProteinTint)
    val Fat = MacroPalette("Fat", FuelColors.Fat, FuelColors.FatSoft, FuelColors.FatTint)
    val Carbs = MacroPalette("Carbs", FuelColors.Carbs, FuelColors.CarbsSoft, FuelColors.CarbsTint)
    val Fiber = MacroPalette("Fiber", FuelColors.Fiber, FuelColors.FiberSoft, FuelColors.FiberTint)
    val All = listOf(Protein, Fat, Carbs, Fiber)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: TextUnit, weight: Int, color: Color = Color.Unspecified) =
    TextStyle(fontFamily = Manrope, fontWeight = FontWeight(weight), fontSize = size, color = color, fontFeatureSettings = "tnum")

object FuelType {
    val ScreenTitle = style(17.sp, 800)
    val LargeTitle = style(26.sp, 700)
    val PageTitle = style(20.sp, 700)
    val SectionHeading = style(15.sp, 800)
    val CardTitle = style(14.sp, 700)
    val Body = style(14.sp, 600)
    val BodyStrong = style(14.sp, 700)
    val Small = style(13.sp, 600)
    val Meta = style(12.sp, 600)
    val MetaStrong = style(12.sp, 700)
    val Tiny = style(11.sp, 600)
    val TinyStrong = style(11.sp, 700)
    val Micro = style(10.sp, 700)
    val BigNumber = style(28.sp, 800)
    val Number = style(17.sp, 800)
    val NumberSmall = style(16.sp, 800)
}

object FuelShapes {
    val Card = RoundedCornerShape(22.dp)
    val Entry = RoundedCornerShape(18.dp)
    val Item = RoundedCornerShape(16.dp)
    val Chip = RoundedCornerShape(999.dp)
    val Input = RoundedCornerShape(8.dp)
    val Tile = RoundedCornerShape(12.dp)
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

@Composable
fun FuelTheme(content: @Composable () -> Unit) {
    // Material3 only provides base components; every visible colour and shape comes from the tokens above.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FuelColors.Accent,
            onPrimary = Color.White,
            primaryContainer = FuelColors.AccentBg,
            onPrimaryContainer = FuelColors.AccentDeep,
            secondary = FuelColors.AccentText,
            background = FuelColors.Background,
            surface = FuelColors.Surface,
            surfaceContainer = FuelColors.Surface,
            surfaceContainerHigh = FuelColors.Surface,
            surfaceContainerHighest = FuelColors.Line,
            surfaceContainerLow = FuelColors.Background,
            onBackground = FuelColors.Ink,
            onSurface = FuelColors.Ink,
            onSurfaceVariant = FuelColors.Muted,
            outline = FuelColors.InputBorder,
            outlineVariant = FuelColors.Line,
            error = FuelColors.Danger,
        ),
        typography = Typography(
            bodyLarge = FuelType.Body,
            bodyMedium = FuelType.Small,
            bodySmall = FuelType.Meta,
            labelLarge = FuelType.BodyStrong,
            labelMedium = FuelType.MetaStrong,
            labelSmall = FuelType.TinyStrong,
            titleLarge = FuelType.PageTitle,
            titleMedium = FuelType.SectionHeading,
            titleSmall = FuelType.CardTitle,
            headlineSmall = FuelType.PageTitle,
        ),
        content = content,
    )
}

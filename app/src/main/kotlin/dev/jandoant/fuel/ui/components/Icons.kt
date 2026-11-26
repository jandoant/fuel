package dev.jandoant.fuel.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.domain.MealSlot

/** Stroke icons taken from the prototype SVGs (24×24 viewBox, round caps). */
object FuelIcons {
    private val cache = HashMap<String, ImageVector>()

    fun stroke(path: String, width: Float = 2f): ImageVector = cache.getOrPut("$path|$width") {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
    }

    const val MENU = "M4 8h16M4 16h10"
    const val BACK = "M15 18l-6-6 6-6"
    const val FORWARD = "M9 18l6-6-6-6"
    const val CHEVRON_DOWN = "M6 9l6 6 6-6"
    const val CHECK = "M5 12l5 5 9-10"
    const val PLUS = "M12 5v14M5 12h14"
    const val CLOSE = "M6 6l12 12M18 6L6 18"
    const val WARNING = "M12 3l10 18H2zM12 10v5M12 18v.5"
    const val SEARCH = "M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0zM20 20l-4-4"
    const val FLASH = "M7 3h10l-2 6v12H9V9z"
    const val STAR = "M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z"
    const val RECIPE = "M4 10h16v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM2 10h20M9 3v3M15 3v3M12 2v4"
    const val SINGLE = "M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0z"
    const val EDIT = "M4 20h4L19 9l-4-4L4 16z"
    const val TARGET = "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0zM16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0z"
    const val TRASH = "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"
    const val BARCODE = "M4 6v12M7 6v12M11 6v12M14 6v12M17 6v12M20 6v12"
    const val COPY = "M8 8h12v12H8zM4 16V4h12"
    const val MOVE = "M5 12h14M13 6l6 6-6 6"
    const val SYNC = "M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5"
    const val CAMERA = "M4 8h3l2-3h6l2 3h3v11H4zM15.5 13a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0z"

    // Meal slots (Today timeline)
    const val BREAKFAST = "M12 3v2M5 12H3M21 12h-2M6 6l1.5 1.5M18 6l-1.5 1.5M8 16a4 4 0 0 1 8 0M3 20h18"
    const val LUNCH = "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M4 12H2M22 12h-2M5 5l1.4 1.4M17.6 17.6L19 19M5 19l1.4-1.4M17.6 6.4L19 5"
    const val DINNER = "M20 14A8 8 0 1 1 10 4a6 6 0 0 0 10 10z"
    const val SNACKS = "M4 12a8 8 0 1 0 16 0 8 8 0 0 0-16 0zM9 9h.01M14 13h.01M10 15h.01"
    const val EXTRA = "M13 2L4 14h7l-1 8 9-12h-7z"

    // Navigation drawer
    const val NAV_TODAY = "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"
    const val NAV_PLAN = "M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM3 10h18M8 3v4M16 3v4"
    const val NAV_CALENDAR = "M3 5h18v16H3zM3 10h18M9 10v11M15 10v11M3 15.5h18"
    const val NAV_BODY = "M3 17l5-5 4 3 8-8"
    const val NAV_ENERGY = "M13 2L4 14h7l-1 8 9-12h-7z"
    const val NAV_TRENDS = "M4 20V10M10 20V4M16 20v-7M22 20H2"
    const val NAV_CHECKIN = "M9 11l3 3 8-8M20 12v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9"
    const val NAV_RECIPES = RECIPE
    const val NAV_FOODS = "M4 6h16M4 12h16M4 18h10"
    const val NAV_TARGETS = "M4 6h9m4 0h3M4 12h3m4 0h9M4 18h11m4 0h1M13 4v4M7 10v4M15 16v4"
    const val NAV_SETTINGS = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19 12l2-1-1-3-2 .5-1.5-1.5.5-2-3-1-1 2h-2l-1-2-3 1 .5 2L6 9.5 4 9l-1 3 2 1v2l-2 1 1 3 2-.5L7.5 18 7 20l3 1 1-2h2l1 2 3-1-.5-2 1.5-1.5 2 .5 1-3-2-1z"

    fun forSlot(slotId: String): String = when (slotId) {
        MealSlot.BREAKFAST -> BREAKFAST
        MealSlot.LUNCH -> LUNCH
        MealSlot.DINNER -> DINNER
        MealSlot.SNACKS -> SNACKS
        else -> EXTRA
    }
}

@Composable
fun FuelIcon(
    path: String,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Float = 2f,
) = Icon(FuelIcons.stroke(path, strokeWidth), contentDescription, modifier.size(size), tint = tint)

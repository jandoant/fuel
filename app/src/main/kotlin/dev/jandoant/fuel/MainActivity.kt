package dev.jandoant.fuel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.model.CalorieZoneModel
import dev.jandoant.fuel.core.model.GoalRange
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelTheme
import dev.jandoant.fuel.ui.theme.FuelType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FuelTheme { PlaceholderToday() }
        }
    }
}

/** Placeholder until Milestone 2 builds the real Today screen. Proves :core-model is wired in. */
@Composable
private fun PlaceholderToday() {
    val zone = CalorieZoneModel.rawZone(
        maintenanceKcal = 2770.0,
        trendWeightKg = 78.4,
        goal = GoalRange.LOSE_0_25_TO_0_5,
    ).display()
    Column(
        Modifier
            .fillMaxSize()
            .background(FuelColors.Background)
            .safeDrawingPadding()
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.today), style = FuelType.ScreenTitle, color = FuelColors.Ink)
        Text(
            stringResource(R.string.placeholder_target, zone.target, zone.low, zone.high),
            style = FuelType.Body,
            color = FuelColors.Muted,
        )
    }
}

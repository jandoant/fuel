package dev.jandoant.fuel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.jandoant.fuel.ui.nav.FuelRoot
import dev.jandoant.fuel.ui.theme.FuelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FuelTheme { FuelRoot() } }
    }
}

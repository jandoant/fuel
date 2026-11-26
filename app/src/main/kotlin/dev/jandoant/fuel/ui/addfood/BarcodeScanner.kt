package dev.jandoant.fuel.ui.addfood

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import dev.jandoant.fuel.ui.components.FuelIcon
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelType

/** Barcode scanner box (Add food): CameraX preview + on-device ML Kit, flashlight toggle. */
@Composable
fun BarcodeScannerBox(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var active by remember { mutableStateOf(granted) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; active = ok }
    var torch by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)

    Box(modifier.fillMaxWidth().height(236.dp).clip(shape).background(FuelColors.Camera), contentAlignment = Alignment.Center) {
        if (granted && active) {
            CameraPreview(onCode, torch)
            Box(Modifier.size(240.dp, 120.dp).border(2.dp, Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF7FD8A9)))
            }
            Text("Point the camera at a barcode", style = FuelType.Small, color = Color(0xFFE8E2DA), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp).clip(CircleShape).background(if (torch) FuelColors.Accent else Color(0xFF4A443E))
                    .clickable(role = Role.Switch) { torch = !torch }.semantics { contentDescription = "Toggle flashlight" },
                contentAlignment = Alignment.Center,
            ) { FuelIcon(FuelIcons.FLASH, null, Color.White) }
        } else {
            Column(
                Modifier.fillMaxSize().clickable(role = Role.Button) { if (granted) active = true else launcher.launch(Manifest.permission.CAMERA) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                FuelIcon(FuelIcons.BARCODE, null, Color.White, size = 40.dp)
                Text(if (granted) "Tap to scan a barcode" else "Tap to allow the camera and scan a barcode", style = FuelType.Small, color = Color(0xFFE8E2DA), modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit, torch: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onCode)
    val controller = remember { LifecycleCameraController(context) }
    DisposableEffect(owner) {
        val executor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E).build(),
        )
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                result.getValue(scanner)?.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let { callback(it) }
            },
        )
        controller.bindToLifecycle(owner)
        onDispose {
            controller.unbind()
            scanner.close()
        }
    }
    LaunchedEffect(torch) { runCatching { controller.enableTorch(torch) } }
    AndroidView(factory = { ctx -> PreviewView(ctx).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } }, modifier = Modifier.fillMaxSize())
}

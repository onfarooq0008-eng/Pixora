package com.pixorapdf

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@Composable
fun ScanScreen(state: AppState) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var flash by remember { mutableStateOf(false) }
    var documentMode by remember { mutableStateOf(false) }
    var shots by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    LaunchedEffect(flash) { imageCapture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }

    val providerRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    // If the user leaves the screen before the camera provider is ready, the late callback must not bind the camera
    val disposed = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) {
        onDispose { disposed[0] = true; try { providerRef[0]?.unbindAll() } catch (_: Throwable) {} }
    }

    fun capture() {
        if (capturing) return
        capturing = true
        val file = File(ctx.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        // Targeting API 36 lifts the portrait lock on tablets/foldables, so follow the real display rotation
        imageCapture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                state.addUris(listOf(Uri.fromFile(file)), enhance = documentMode)
                shots++
                capturing = false
            }
            override fun onError(exception: ImageCaptureException) {
                capturing = false
                Toast.makeText(ctx, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    val pv = PreviewView(c)
                    val future = ProcessCameraProvider.getInstance(c)
                    future.addListener({
                        try {
                            if (disposed[0]) return@addListener
                            val provider = future.get()
                            providerRef[0] = provider
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        } catch (e: Throwable) {
                            Toast.makeText(c, "Camera unavailable", Toast.LENGTH_LONG).show()
                        }
                    }, ContextCompat.getMainExecutor(c))
                    pv
                }
            )
            // Guide frame
            Canvas(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 110.dp)) {
                val len = 34.dp.toPx()
                val sw = 4.dp.toPx()
                val w = size.width
                val h = size.height
                val c = Blue
                drawRect(Color.White.copy(alpha = .35f), Offset.Zero, Size(w, h), style = Stroke(1.dp.toPx()))
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(c, Offset(x, y), Offset(x + dx * len, y), sw, StrokeCap.Round)
                    drawLine(c, Offset(x, y), Offset(x, y + dy * len), sw, StrokeCap.Round)
                }
                corner(0f, 0f, 1f, 1f); corner(w, 0f, -1f, 1f); corner(0f, h, 1f, -1f); corner(w, h, -1f, -1f)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is needed to scan documents.", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }

        // Top bar
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { flash = !flash }) { Icon(if (flash) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash", tint = Color.White) }
            Text("Scan Document", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { state.go(Screen.Select) }) { Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White) }
        }
        IconButton(onClick = { state.back() }, modifier = Modifier.align(Alignment.TopStart).padding(top = 56.dp, start = 8.dp)) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }

        // Bottom controls
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = .18f)).padding(4.dp)) {
                listOf("Photo" to false, "Document" to true).forEach { (label, doc) ->
                    Text(
                        label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (documentMode == doc) Blue else Color.Transparent)
                            .clickable { documentMode = doc }.padding(horizontal = 22.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (shots > 0) Text("$shots scanned", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(6.dp)
                        .clip(CircleShape).background(if (capturing) Color.Gray else Color.White).clickable(enabled = granted) { capture() }
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (shots > 0) Button(onClick = { state.go(Screen.Arrange) }, shape = RoundedCornerShape(14.dp)) { Text("Done  →", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

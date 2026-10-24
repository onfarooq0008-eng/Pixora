package com.pixorapdf

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = applicationContext
        Thread {
            try { PdfOps.init(app) } catch (_: Throwable) {}
            try {
                val cutoff = System.currentTimeMillis() - 24L * 3600 * 1000
                app.cacheDir.listFiles()?.filter { it.name.startsWith("scan_") && it.lastModified() < cutoff }?.forEach { it.delete() }
            } catch (_: Throwable) {}
        }.start()
        setContent { PixoraRoot(this) }
    }
}

private fun Screen.isTab() = this is Screen.Home || this is Screen.Files || this is Screen.Tools || this is Screen.Settings

@Composable
fun PixoraRoot(activity: ComponentActivity) {
    val state = remember { AppState(activity.applicationContext) }
    val dark = when (state.prefs.theme) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    SideEffect {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    BackHandler(enabled = state.stack.size > 1) { state.back() }

    PixoraTheme(dark) {
        val cs = MaterialTheme.colorScheme
        Scaffold(
            containerColor = cs.background,
            contentColor = cs.onBackground,
            bottomBar = { if (state.current.isTab()) BottomNav(state) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val s = state.current
                key(s) {
                    when (s) {
                        Screen.Home -> HomeScreen(state)
                        Screen.Files -> FilesScreen(state)
                        Screen.Tools -> ToolsScreen(state)
                        Screen.Settings -> SettingsScreen(state)
                        Screen.Select -> SelectScreen(state)
                        Screen.Arrange -> ArrangeScreen(state)
                        Screen.PdfSettings -> PdfSettingsScreen(state)
                        Screen.Scan -> ScanScreen(state)
                        is Screen.Created -> CreatedScreen(state, s)
                        is Screen.Crop -> CropScreen(state, s.pageId)
                        is Screen.Viewer -> ViewerScreen(state, s)
                        is Screen.Tool -> PdfToolScreen(state, s.kind)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNav(state: AppState) {
    val tabs = listOf<Triple<Screen, String, ImageVector>>(
        Triple(Screen.Home, "Home", Icons.Default.Home),
        Triple(Screen.Files, "Files", Icons.Default.Folder),
        Triple(Screen.Tools, "Tools", Icons.Default.GridView),
        Triple(Screen.Settings, "Settings", Icons.Default.Settings)
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { (screen, label, icon) ->
            NavigationBarItem(
                selected = state.current == screen,
                onClick = { if (state.current != screen) state.root(screen) },
                icon = { Icon(icon, label) },
                label = { Text(label, fontSize = 11.sp) }
            )
        }
    }
}

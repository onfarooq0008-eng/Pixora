package com.pixorapdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun sharePdf(ctx: Context, uri: Uri) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share PDF"))
}

private const val SITE = "https://onfarooq0008-eng.github.io/Pixoraweb/"

private fun openPage(ctx: Context, page: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE + page)))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No browser app available to open this page", Toast.LENGTH_LONG).show()
    }
}

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

// ---------------------------------------------------------------- HOME

@Composable
fun HomeScreen(state: AppState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var recent by remember { mutableStateOf<List<PdfFile>>(emptyList()) }
    LaunchedEffect(state.filesVersion) {
        recent = withContext(Dispatchers.IO) { try { PdfOps.listPdfs(ctx).take(3) } catch (e: Throwable) { emptyList() } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(Sky, Blue, Color(0xFF6228F5)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Collections, null, tint = Color.White, modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("Pixora PDF", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { state.root(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
        }

        // Banner
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Sky, Blue, DeepBlue)))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Convert\nImages to PDF", color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Turn your photos into high quality PDF files in seconds.", color = Color.White.copy(alpha = .9f), fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { state.openImageToPdf() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Blue),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Create PDF  →", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(116.dp)) {
                Box(
                    Modifier.align(Alignment.TopStart).offset(0.dp, 8.dp).size(66.dp, 82.dp).rotate(-12f)
                        .clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .92f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Landscape, null, tint = Blue, modifier = Modifier.size(34.dp)) }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(70.dp, 88.dp).rotate(8f)
                        .clip(RoundedCornerShape(10.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Image, null, tint = Sky, modifier = Modifier.size(34.dp))
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Red).padding(horizontal = 5.dp, vertical = 2.dp)
                    ) { Text("PDF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val tiles = listOf<Triple<String, ImageVector, Color>>(
            Triple("Image\nto PDF", Icons.Default.Image, Blue),
            Triple("Scan\nDocument", Icons.Default.DocumentScanner, Green),
            Triple("Compress\nPDF", Icons.Default.Compress, Indigo),
            Triple("Merge PDF", Icons.Default.MergeType, Red),
            Triple("Split PDF", Icons.Default.CallSplit, Purple),
            Triple("My PDFs", Icons.Default.Folder, Blue)
        )
        val actions = listOf<() -> Unit>(
            { state.openImageToPdf() },
            { state.openScan() },
            { state.go(Screen.Tool(ToolKind.COMPRESS)) },
            { state.go(Screen.Tool(ToolKind.MERGE)) },
            { state.go(Screen.Tool(ToolKind.SPLIT)) },
            { state.root(Screen.Files) }
        )
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until 3) {
                    val i = row * 3 + col
                    ToolTile(tiles[i].first, tiles[i].second, tiles[i].third, Modifier.weight(1f), onClick = actions[i])
                }
            }
        }

        if (recent.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent files", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { state.root(Screen.Files) }) { Text("See all") }
            }
            recent.forEach { f ->
                FileRow(f, state, onChanged = { state.filesVersion++ })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- TOOLS

@Composable
fun ToolsScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        Header("Tools", actions = { IconButton(onClick = { state.root(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") } })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            val tools = listOf<Triple<String, ImageVector, Color>>(
                Triple("Image\nto PDF", Icons.Default.Image, Blue),
                Triple("Scan\nDocument", Icons.Default.DocumentScanner, Green),
                Triple("Compress\nPDF", Icons.Default.Compress, Indigo),
                Triple("Merge PDF", Icons.Default.MergeType, Red),
                Triple("Split PDF", Icons.Default.CallSplit, Purple),
                Triple("JPG to PDF", Icons.Default.PhotoLibrary, Sky),
                Triple("PDF to\nImages", Icons.Default.Photo, Orange),
                Triple("Rotate PDF", Icons.Default.RotateRight, Red),
                Triple("Add\nPassword", Icons.Default.Lock, Purple)
            )
            val actions = listOf<() -> Unit>(
                { state.openImageToPdf() },
                { state.openScan() },
                { state.go(Screen.Tool(ToolKind.COMPRESS)) },
                { state.go(Screen.Tool(ToolKind.MERGE)) },
                { state.go(Screen.Tool(ToolKind.SPLIT)) },
                { state.openImageToPdf() },
                { state.go(Screen.Tool(ToolKind.TO_IMAGES)) },
                { state.go(Screen.Tool(ToolKind.ROTATE)) },
                { state.go(Screen.Tool(ToolKind.PASSWORD)) }
            )
            for (row in 0 until 3) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (col in 0 until 3) {
                        val i = row * 3 + col
                        ToolTile(tools[i].first, tools[i].second, tools[i].third, Modifier.weight(1f), onClick = actions[i])
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- FILES

@Composable
fun FileRow(f: PdfFile, state: AppState, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val fav = f.uri.toString() in state.prefs.favorites

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { state.go(Screen.Viewer(f.uri, f.name)) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PdfBadge(46)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            val pages = if (f.pages > 0) "${f.pages} page${if (f.pages == 1) "" else "s"} • " else "Protected • "
            Text(pages + formatSize(f.size), fontSize = 12.sp, color = cs.onSurfaceVariant)
            Text(formatDate(f.modified), fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        if (fav) Icon(Icons.Default.Star, "Favorite", tint = Orange, modifier = Modifier.size(18.dp))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                    onClick = { menu = false; state.go(Screen.Viewer(f.uri, f.name)) })
                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = { menu = false; sharePdf(ctx, f.uri) })
                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menu = false; renaming = true })
                DropdownMenuItem(
                    text = { Text(if (fav) "Remove from favorites" else "Add to favorites") },
                    leadingIcon = { Icon(if (fav) Icons.Default.StarBorder else Icons.Default.Star, null) },
                    onClick = { menu = false; state.prefs.toggleFavorite(f.uri) })
                DropdownMenuItem(text = { Text("Delete", color = Red) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Red) },
                    onClick = { menu = false; deleting = true })
            }
        }
    }
    if (renaming) TextInputDialog("Rename PDF", f.name.removeSuffix(".pdf"), ".pdf", onConfirm = {
        renaming = false
        if (PdfOps.rename(ctx, f.uri, it)) onChanged() else toast(ctx, "Couldn't rename this file")
    }, onDismiss = { renaming = false })
    if (deleting) ConfirmDialog("Delete PDF?", "“${f.name}” will be permanently deleted.", "Delete", onConfirm = {
        if (PdfOps.delete(ctx, f.uri)) onChanged() else toast(ctx, "Couldn't delete this file")
    }, onDismiss = { deleting = false })
}

@Composable
fun FilesScreen(state: AppState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var files by remember { mutableStateOf<List<PdfFile>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(state.filesVersion) {
        files = withContext(Dispatchers.IO) { try { PdfOps.listPdfs(ctx) } catch (e: Throwable) { emptyList() } }
        loaded = true
    }
    val weekAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
    val shown = files
        .filter { if (tab == 1) it.modified >= weekAgo else if (tab == 2) it.uri.toString() in state.prefs.favorites else true }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .let { when (sort) { 1 -> it.sortedBy { f -> f.name.lowercase() }; 2 -> it.sortedByDescending { f -> f.size }; else -> it } }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search PDFs") },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Default.Close, "Close search") }
            } else {
                Text("My PDFs", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Sort by date") }, onClick = { sort = 0; menu = false })
                        DropdownMenuItem(text = { Text("Sort by name") }, onClick = { sort = 1; menu = false })
                        DropdownMenuItem(text = { Text("Sort by size") }, onClick = { sort = 2; menu = false })
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; state.filesVersion++ })
                    }
                }
            }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Recent", "Favorites").forEachIndexed { i, l -> ChoiceChip(l, tab == i, Modifier.weight(1f)) { tab = i } }
        }
        if (shown.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(bottom = 60.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                PdfBadge(72)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (!loaded) "Loading…" else if (files.isEmpty()) "No PDFs yet" else "Nothing here",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Text("PDFs you create appear here.", color = cs.onSurfaceVariant, fontSize = 13.sp)
                if (loaded && files.isEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { state.openImageToPdf() }, shape = RoundedCornerShape(14.dp)) { Text("Create PDF") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.uri.toString() }) { f ->
                    FileRow(f, state, onChanged = { state.filesVersion++ })
                    HorizontalDivider(color = cs.outlineVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- SETTINGS

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = cs.onSurface.copy(alpha = .75f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 13.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(state: AppState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val prefs = state.prefs
    var dialog by remember { mutableStateOf("") }
    val cardShape = RoundedCornerShape(18.dp)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Settings")
        Column(Modifier.padding(horizontal = 18.dp)) {
            Column(Modifier.fillMaxWidth().clip(cardShape).background(cs.surface).border(1.dp, cs.outlineVariant, cardShape)) {
                SettingRow(Icons.Default.DarkMode, "Dark Mode", prefs.theme) { dialog = "theme" }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Description, "Default Page Size", prefs.pageSize.label) { dialog = "size" }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Image, "Default Image Quality", prefs.quality.label) { dialog = "quality" }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Folder, "Save Location", prefs.saveLocation) { dialog = "location" }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Translate, "Language", "English") { dialog = "language" }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(cardShape).background(cs.surface).border(1.dp, cs.outlineVariant, cardShape)) {
                SettingRow(Icons.Default.StarRate, "Rate Us") {
                    val pkg = ctx.packageName
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
                    catch (e: Exception) {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) }
                        catch (e2: Exception) { toast(ctx, "No app available to open the store") }
                    }
                }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Share, "Share App") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Try Pixora PDF – turn photos into PDFs: https://play.google.com/store/apps/details?id=${ctx.packageName}")
                    }
                    ctx.startActivity(Intent.createChooser(send, "Share Pixora PDF"))
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(cardShape).background(cs.surface).border(1.dp, cs.outlineVariant, cardShape)) {
                SettingRow(Icons.Default.PrivacyTip, "Privacy Policy") { openPage(ctx, "privacy-policy.html") }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Description, "Terms of Service") { openPage(ctx, "terms-of-service.html") }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Lock, "Permissions") { openPage(ctx, "permissions.html") }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Delete, "Data Deletion") { openPage(ctx, "data-deletion.html") }
                HorizontalDivider(color = cs.outlineVariant)
                SettingRow(Icons.Default.Info, "Help & Support") { openPage(ctx, "support.html") }
            }
            Spacer(Modifier.height(20.dp))
            Text("Pixora PDF 1.0", color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
        }
    }

    when (dialog) {
        "theme" -> ChoiceDialog("Dark Mode", listOf("System", "Light", "Dark"), prefs.theme, { it }, { prefs.updateTheme(it) }, { dialog = "" })
        "size" -> ChoiceDialog("Default Page Size", PageSize.values().toList(), prefs.pageSize, { it.label }, { prefs.updatePageSize(it) }, { dialog = "" })
        "quality" -> ChoiceDialog("Default Image Quality", Quality.values().toList(), prefs.quality, { it.label }, { prefs.updateQuality(it) }, { dialog = "" })
        "location" -> ChoiceDialog("Save Location", listOf("Downloads", "Documents"), prefs.saveLocation, { "$it/Pixora PDF" }, { prefs.updateSaveLocation(it); state.filesVersion++ }, { dialog = "" })
        "language" -> ChoiceDialog("Language", listOf("English"), "English", { it }, {}, { dialog = "" })
    }
}

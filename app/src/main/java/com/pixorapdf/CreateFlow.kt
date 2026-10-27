package com.pixorapdf

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

// ---------------------------------------------------------------- 2. SELECT IMAGES
// Uses the system photo picker (Photos / Albums / Recent). No storage permission is needed.

@Composable
fun SelectScreen(state: AppState) {
    val cs = MaterialTheme.colorScheme
    val pages = state.pages
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { state.addUris(it) }
    fun openPicker() { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoOpened && pages.isEmpty()) { autoOpened = true; openPicker() }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Select Images", onBack = { state.back() }) {
            if (pages.isNotEmpty()) TextButton(onClick = { openPicker() }) { Text("Add more", fontWeight = FontWeight.SemiBold) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (pages.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = Blue, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Choose your photos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Pick from Photos, Albums or Recent. Pixora only sees the images you choose.",
                        color = cs.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { openPicker() }, shape = RoundedCornerShape(14.dp)) { Text("Choose photos") }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
                        val shape = RoundedCornerShape(12.dp)
                        Box(Modifier.aspectRatio(1f).clip(shape).border(2.dp, Blue, shape)) {
                            AsyncThumb(p, 256, Modifier.fillMaxSize())
                            Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = .45f)).clickable { pages.removeAll { it.id == p.id } },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${pages.size} image${if (pages.size == 1) "" else "s"} selected", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(
                enabled = pages.isNotEmpty(), shape = RoundedCornerShape(14.dp),
                onClick = { if (state.stack.contains(Screen.Arrange)) state.back() else state.go(Screen.Arrange) }
            ) { Text("Continue  →", fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------------------------------------------------------------- 3. ARRANGE PAGES

@Composable
private fun ToolbarButton(icon: ImageVector, label: String, active: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (active) Blue else cs.onSurface.copy(alpha = .8f), modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, color = if (active) Blue else cs.onSurfaceVariant)
    }
}

@Composable
fun ArrangeScreen(state: AppState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val pages = state.pages
    val sel = remember { mutableStateListOf<Long>() }
    var reorder by remember { mutableStateOf(false) }
    val dashColor = cs.onSurfaceVariant

    fun rotate() {
        val targets = if (sel.isEmpty()) pages.map { it.id } else sel.toList()
        for (i in pages.indices) if (pages[i].id in targets) pages[i] = pages[i].rotated(true)
    }
    fun crop() {
        val id = when {
            sel.size == 1 -> sel[0]
            sel.isEmpty() && pages.size == 1 -> pages[0].id
            else -> null
        }
        if (id == null) toast(ctx, "Select one page to crop") else state.go(Screen.Crop(id))
    }
    fun delete() {
        if (sel.isEmpty()) toast(ctx, "Select the pages you want to delete") else { pages.removeAll { it.id in sel }; sel.clear() }
    }
    fun move(i: Int, d: Int) {
        val j = i + d
        if (j in pages.indices) { val t = pages[i]; pages[i] = pages[j]; pages[j] = t }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Arrange Pages", onBack = { state.back() }) {
            Button(onClick = { state.go(Screen.PdfSettings) }, enabled = pages.isNotEmpty(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
        Text(
            if (reorder) "Use the arrows to move pages" else "Tap pages to select, then use the tools below",
            fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), modifier = Modifier.weight(1f), contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
                val selected = p.id in sel
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.clip(shape).background(cs.surface).border(if (selected) 2.dp else 1.dp, if (selected) Blue else cs.outlineVariant, shape).padding(6.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(10.dp))
                            .clickable { if (selected) sel.remove(p.id) else sel.add(p.id) }
                    ) {
                        AsyncThumb(p, 400, Modifier.fillMaxSize())
                        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = .25f)).border(1.5.dp, Color.White, CircleShape))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (reorder) {
                            Icon(Icons.Default.ChevronLeft, "Move left", tint = if (i > 0) Blue else cs.outlineVariant, modifier = Modifier.size(26.dp).clickable { move(i, -1) })
                            Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, "Move right", tint = if (i < pages.lastIndex) Blue else cs.outlineVariant, modifier = Modifier.size(26.dp).clickable { move(i, 1) })
                        } else {
                            Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            Icon(Icons.Default.Delete, "Delete page", tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp).clickable { sel.remove(p.id); pages.removeAll { it.id == p.id } })
                        }
                    }
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(14.dp)).clickable { state.go(Screen.Select) }
                        .drawBehind {
                            drawRoundRect(
                                Blue.copy(alpha = .8f), cornerRadius = CornerRadius(14.dp.toPx()),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, null, tint = Blue, modifier = Modifier.size(30.dp))
                        Text("Add Images", color = dashColor, fontSize = 12.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 12.dp, vertical = 6.dp)) {
            ToolbarButton(Icons.Default.RotateRight, "Rotate", modifier = Modifier.weight(1f)) { rotate() }
            ToolbarButton(Icons.Default.Crop, "Crop", modifier = Modifier.weight(1f)) { crop() }
            ToolbarButton(Icons.Default.Delete, "Delete", modifier = Modifier.weight(1f)) { delete() }
            ToolbarButton(Icons.Default.SwapHoriz, "Reorder", active = reorder, modifier = Modifier.weight(1f)) { reorder = !reorder }
        }
    }
}

// ---------------------------------------------------------------- 4. PDF SETTINGS

@Composable
fun PdfSettingsScreen(state: AppState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val o = state.opts
    var progress by remember { mutableStateOf<Int?>(null) }
    val total = state.pages.size

    fun create() {
        if (progress != null) return
        val snapshot = state.pages.toList()
        val opts = state.opts
        scope.launch {
            progress = 0
            val result = try {
                Result.success(withContext(Dispatchers.IO) {
                    PdfOps.savePdf(ctx, state.prefs, opts.name) { out ->
                        PdfOps.imagesToPdf(ctx, snapshot, opts, out) { n -> progress = n }
                    }
                })
            } catch (e: Throwable) { Result.failure(e) }
            progress = null
            result.onSuccess { f ->
                state.filesVersion++
                state.pages.clear()
                state.root(Screen.Home)
                state.go(Screen.Created(f, "PDF Created Successfully!", "Saved to: ${PdfOps.folderLabel(state.prefs)}"))
            }.onFailure { toast(ctx, "Couldn't create the PDF: ${it.message}") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header("PDF Settings", onBack = { state.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("File name", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = o.name, onValueChange = { state.opts = o.copy(name = it) }, singleLine = true,
                suffix = { Text(".pdf", color = cs.onSurfaceVariant) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
            )
            SectionLabel("Page size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageSize.values().forEach { s -> ChoiceChip(s.label, o.size == s, Modifier.weight(1f), sub = s.sub) { state.opts = o.copy(size = s) } }
            }
            SectionLabel("Orientation")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Portrait", !o.landscape, Modifier.weight(1f), icon = Icons.Default.CropPortrait) { state.opts = o.copy(landscape = false) }
                ChoiceChip("Landscape", o.landscape, Modifier.weight(1f), icon = Icons.Default.CropLandscape) { state.opts = o.copy(landscape = true) }
            }
            if (o.size == PageSize.ORIGINAL) Text("Original size follows each image, so orientation is ignored.", fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            SectionLabel("Image quality")
            Text(o.quality.label + if (o.quality == Quality.HIGH) " (Recommended)" else "", fontSize = 13.sp, color = cs.onSurfaceVariant)
            Slider(
                value = o.quality.ordinal.toFloat(), valueRange = 0f..2f, steps = 1,
                onValueChange = { state.opts = o.copy(quality = Quality.values()[it.roundToInt().coerceIn(0, 2)]) }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Low", "Medium", "High").forEach { Text(it, fontSize = 11.sp, color = cs.onSurfaceVariant) }
            }
            SectionLabel("Page margin")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Margin.values().forEach { m -> ChoiceChip(m.label, o.margin == m, Modifier.weight(1f)) { state.opts = o.copy(margin = m) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton("Create PDF  →", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), enabled = total > 0 && o.name.isNotBlank()) { create() }
    }
    progress?.let { BusyDialog("Creating page ${minOf(it + 1, total)} of $total", if (total > 0) it / total.toFloat() else null) }
}

// ---------------------------------------------------------------- 5. PDF CREATED

@Composable
fun CreatedScreen(state: AppState, s: Screen.Created) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val cs = MaterialTheme.colorScheme
    var file by remember { mutableStateOf(s.file) }
    var renaming by remember { mutableStateOf(false) }
    val confetti = remember { listOf(Blue, Green, Orange, Red, Purple, Sky) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val pts = listOf(0.1f to 0.3f, 0.2f to 0.1f, 0.5f to 0.02f, 0.82f to 0.12f, 0.95f to 0.35f, 0.9f to 0.75f, 0.12f to 0.7f, 0.3f to 0.95f, 0.72f to 0.93f)
                pts.forEachIndexed { i, (x, y) -> drawCircle(confetti[i % confetti.size], 4.dp.toPx(), Offset(x * size.width, y * size.height)) }
            }
            Box(Modifier.size(92.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(s.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        val f = file
        if (f != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).border(1.dp, cs.outlineVariant, RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PdfBadge(46)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(f.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text("${f.pages} page${if (f.pages == 1) "" else "s"} • ${formatSize(f.size)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Text(s.detail, fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Open PDF", icon = Icons.Default.OpenInNew) { state.go(Screen.Viewer(f.uri, f.name)) }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { sharePdf(ctx, f.uri) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { renaming = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Rename")
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { Ads.afterTask(activity) { state.root(Screen.Files) } }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("View in Files")
            }
        } else {
            Text(s.detail, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Back to Home") { Ads.afterTask(activity) { state.root(Screen.Home) } }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { Ads.afterTask(activity) { state.root(Screen.Home); state.openImageToPdf() } },
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
        ) { Text("Create Another PDF", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(16.dp))
        NativeAdCard(Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
    }

    val cur = file
    if (renaming && cur != null) TextInputDialog("Rename PDF", cur.name.removeSuffix(".pdf"), ".pdf", onConfirm = { n ->
        renaming = false
        if (PdfOps.rename(ctx, cur.uri, n)) { file = PdfOps.fileInfo(ctx, cur.uri); state.filesVersion++ } else toast(ctx, "Couldn't rename this file")
    }, onDismiss = { renaming = false })
}

// ---------------------------------------------------------------- 8. CROP

private fun fitRatio(imgAspect: Float, r: Float): CropRect {
    val w: Float
    val h: Float
    if (r > imgAspect) { w = 1f; h = imgAspect / r } else { h = 1f; w = r / imgAspect }
    val ww = w * 0.9f
    val hh = h * 0.9f
    val l = (1f - ww) / 2f
    val t = (1f - hh) / 2f
    return CropRect(l, t, l + ww, t + hh)
}

@Composable
fun CropScreen(state: AppState, pageId: Long) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val idx = state.pages.indexOfFirst { it.id == pageId }
    if (idx < 0) { LaunchedEffect(Unit) { state.back() }; return }
    val page = state.pages[idx]

    var rot by remember { mutableIntStateOf(page.rotation) }
    var crop by remember { mutableStateOf(page.crop ?: CropRect.FULL) }
    var ratio by remember { mutableStateOf<Float?>(null) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page.uri, rot) {
        bmp = withContext(Dispatchers.IO) { Img.render(ctx, page.copy(rotation = rot, crop = null, enhance = false), 1600) }
    }
    val ratios = listOf<Pair<String, Float?>>("Free" to null, "1:1" to 1f, "3:4" to 0.75f, "4:3" to 4f / 3f, "16:9" to 16f / 9f)

    fun apply() {
        state.pages[idx] = page.copy(rotation = rot, crop = if (crop.isFull) null else crop)
        state.back()
    }

    Column(Modifier.fillMaxSize()) {
        Header("Crop Image", onBack = { state.back() }) {
            IconButton(onClick = { apply() }) { Icon(Icons.Default.Check, "Apply", tint = Blue) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B1226)), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b == null) CircularProgressIndicator(color = Color.White)
            else CropBox(b, crop, ratio, onChange = { crop = it }, modifier = Modifier.fillMaxSize().padding(14.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ratios.forEach { (label, r) ->
                ChoiceChip(label, ratio == r, Modifier.weight(1f)) {
                    ratio = r
                    val b = bmp
                    if (r != null && b != null) crop = fitRatio(b.width.toFloat() / b.height, r)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            ToolbarButton(Icons.Default.RotateLeft, "Rotate Left", modifier = Modifier.weight(1f)) { rot = (rot + 270) % 360; crop = CropRect.FULL; ratio = null }
            ToolbarButton(Icons.Default.RotateRight, "Rotate Right", modifier = Modifier.weight(1f)) { rot = (rot + 90) % 360; crop = CropRect.FULL; ratio = null }
            ToolbarButton(Icons.Default.Refresh, "Reset", modifier = Modifier.weight(1f)) { rot = 0; crop = CropRect.FULL; ratio = null }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun CropBox(bmp: Bitmap, crop: CropRect, ratio: Float?, onChange: (CropRect) -> Unit, modifier: Modifier = Modifier) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val cur = rememberUpdatedState(crop)
    val img = remember(bmp) { bmp.asImageBitmap() }
    val aspect = bmp.width.toFloat() / bmp.height
    val bw = box.width.toFloat()
    val bh = box.height.toFloat()
    val dw = if (bh > 0f && bw / bh > aspect) bh * aspect else bw
    val dh = if (aspect > 0f) dw / aspect else 0f
    val ox = (bw - dw) / 2f
    val oy = (bh - dh) / 2f
    val minSize = 0.1f

    Canvas(
        modifier.onSizeChanged { box = it }.pointerInput(box, ratio, bmp) {
            if (dw <= 0f || dh <= 0f) return@pointerInput
            var handle = -1
            val slop = 44.dp.toPx()
            detectDragGestures(
                onDragStart = { p ->
                    val c = cur.value
                    val corners = listOf(
                        Offset(ox + c.l * dw, oy + c.t * dh), Offset(ox + c.r * dw, oy + c.t * dh),
                        Offset(ox + c.l * dw, oy + c.b * dh), Offset(ox + c.r * dw, oy + c.b * dh)
                    )
                    var best = -1
                    var bestD = Float.MAX_VALUE
                    corners.forEachIndexed { i, o -> val d = (o - p).getDistance(); if (d < bestD) { bestD = d; best = i } }
                    handle = when {
                        bestD < slop -> best
                        p.x >= corners[0].x && p.x <= corners[1].x && p.y >= corners[0].y && p.y <= corners[2].y -> 4
                        else -> -1
                    }
                },
                onDragEnd = { handle = -1 },
                onDragCancel = { handle = -1 },
                onDrag = { change, drag ->
                    change.consume()
                    val c = cur.value
                    val nx = ((change.position.x - ox) / dw).coerceIn(0f, 1f)
                    val ny = ((change.position.y - oy) / dh).coerceIn(0f, 1f)
                    if (handle == 4) {
                        val w = c.r - c.l
                        val h = c.b - c.t
                        val l = (c.l + drag.x / dw).coerceIn(0f, 1f - w)
                        val t = (c.t + drag.y / dh).coerceIn(0f, 1f - h)
                        onChange(CropRect(l, t, l + w, t + h))
                    } else if (handle in 0..3) {
                        val left = handle == 0 || handle == 2
                        val top = handle == 0 || handle == 1
                        val ax = if (left) c.r else c.l
                        val ay = if (top) c.b else c.t
                        if (ratio == null) {
                            val l = if (left) minOf(nx, ax - minSize) else ax
                            val r = if (left) ax else maxOf(nx, ax + minSize)
                            val t = if (top) minOf(ny, ay - minSize) else ay
                            val b = if (top) ay else maxOf(ny, ay + minSize)
                            onChange(CropRect(l.coerceAtLeast(0f), t.coerceAtLeast(0f), r.coerceAtMost(1f), b.coerceAtMost(1f)))
                        } else {
                            val maxW = if (left) ax else 1f - ax
                            val maxH = if (top) ay else 1f - ay
                            var wn = abs(nx - ax).coerceAtLeast(minSize)
                            var hn = wn * dw / (dh * ratio)
                            if (wn > maxW) { wn = maxW; hn = wn * dw / (dh * ratio) }
                            if (hn > maxH) { hn = maxH; wn = hn * dh * ratio / dw }
                            val l = if (left) ax - wn else ax
                            val t = if (top) ay - hn else ay
                            onChange(CropRect(l, t, l + wn, t + hn))
                        }
                    }
                }
            )
        }
    ) {
        if (dw <= 0f || dh <= 0f) return@Canvas
        drawImage(img, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()))
        val l = ox + crop.l * dw
        val t = oy + crop.t * dh
        val r = ox + crop.r * dw
        val b = oy + crop.b * dh
        val scrim = Color.Black.copy(alpha = .55f)
        drawRect(scrim, Offset(ox, oy), Size(dw, t - oy))
        drawRect(scrim, Offset(ox, b), Size(dw, oy + dh - b))
        drawRect(scrim, Offset(ox, t), Size(l - ox, b - t))
        drawRect(scrim, Offset(r, t), Size(ox + dw - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
        for (k in 1..2) {
            val x = l + (r - l) * k / 3f
            val y = t + (b - t) * k / 3f
            drawLine(Color.White.copy(alpha = .5f), Offset(x, t), Offset(x, b), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = .5f), Offset(l, y), Offset(r, y), 1.dp.toPx())
        }
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
            drawCircle(Color.White, 11.dp.toPx(), it)
            drawCircle(Blue, 8.dp.toPx(), it)
        }
    }
}

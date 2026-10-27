package com.pixorapdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SrcPdf(val uri: Uri, val name: String, val pages: Int)

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

private fun titleOf(k: ToolKind) = when (k) {
    ToolKind.MERGE -> "Merge PDF"
    ToolKind.SPLIT -> "Split PDF"
    ToolKind.ROTATE -> "Rotate PDF"
    ToolKind.PASSWORD -> "Add Password"
    ToolKind.COMPRESS -> "Compress PDF"
    ToolKind.TO_IMAGES -> "PDF to Images"
}

private fun descOf(k: ToolKind) = when (k) {
    ToolKind.MERGE -> "Combine several PDFs into one. Use the arrows to set the order."
    ToolKind.SPLIT -> "Enter page ranges such as 1-3, 5, 7-9. Every range is saved as its own PDF."
    ToolKind.ROTATE -> "Rotate every page of a PDF. Text stays selectable."
    ToolKind.PASSWORD -> "Encrypt a PDF (AES-128). Remember the password: it can't be recovered."
    ToolKind.COMPRESS -> "Shrink a PDF by re-encoding its pages as JPEG images. Text in the compressed copy is no longer selectable."
    ToolKind.TO_IMAGES -> "Save every page of a PDF as a JPEG in Pictures/Pixora PDF."
}

@Composable
fun PdfToolScreen(state: AppState, kind: ToolKind) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val srcs = remember { mutableStateListOf<SrcPdf>() }
    var ranges by remember { mutableStateOf("") }
    var rotate by remember { mutableIntStateOf(90) }
    var quality by remember { mutableStateOf(Quality.MEDIUM) }
    var password by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun addUris(list: List<Uri>) {
        scope.launch {
            val items = withContext(Dispatchers.IO) { list.map { SrcPdf(it, PdfOps.displayName(ctx, it), PdfOps.pageCount(ctx, it)) } }
            val ok = items.filter { it.pages > 0 }
            if (ok.size < items.size) toast(ctx, "Some files can't be read (damaged or password protected) and were skipped")
            if (kind == ToolKind.MERGE) srcs.addAll(ok) else if (ok.isNotEmpty()) { srcs.clear(); srcs.add(ok.first()) }
        }
    }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addUris(it) }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) addUris(listOf(it)) }
    fun pick() { if (kind == ToolKind.MERGE) multi.launch(arrayOf("application/pdf")) else single.launch(arrayOf("application/pdf")) }

    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    val canRun = when (kind) {
        ToolKind.MERGE -> srcs.size >= 2
        ToolKind.SPLIT -> srcs.isNotEmpty() && ranges.isNotBlank()
        ToolKind.PASSWORD -> srcs.isNotEmpty() && password.length >= 4
        else -> srcs.isNotEmpty()
    }

    fun execute(): Screen.Created {
        val prefs = state.prefs
        val folder = PdfOps.folderLabel(prefs)
        val first = srcs.firstOrNull()
        val base = PdfOps.sanitize(first?.name ?: "Document")
        return when (kind) {
            ToolKind.MERGE -> {
                val f = PdfOps.savePdf(ctx, prefs, "Merged_$stamp") { PdfOps.assemble(ctx, srcs.map { it.uri to null }, 0, it) }
                Screen.Created(f, "PDFs Merged!", "${srcs.size} files combined • Saved to: $folder")
            }
            ToolKind.SPLIT -> {
                val src = first!!
                val parts = PdfOps.parseRanges(ranges, src.pages) ?: throw IllegalArgumentException("Invalid page range. This PDF has ${src.pages} pages.")
                val files = parts.mapIndexed { i, p ->
                    PdfOps.savePdf(ctx, prefs, "${base}_part${i + 1}") { PdfOps.assemble(ctx, listOf(src.uri to p), 0, it) }
                }
                Screen.Created(files.first(), "PDF Split!", "${files.size} file${if (files.size == 1) "" else "s"} saved to: $folder")
            }
            ToolKind.ROTATE -> {
                val f = PdfOps.savePdf(ctx, prefs, "${base}_rotated") { PdfOps.assemble(ctx, listOf(first!!.uri to null), rotate, it) }
                Screen.Created(f, "PDF Rotated!", "Saved to: $folder")
            }
            ToolKind.PASSWORD -> {
                val f = PdfOps.savePdf(ctx, prefs, "${base}_protected") { PdfOps.protect(ctx, first!!.uri, password, it) }
                Screen.Created(f, "PDF Protected!", "Saved to: $folder")
            }
            ToolKind.COMPRESS -> {
                val original = PdfOps.sizeOf(ctx, first!!.uri)
                val f = PdfOps.savePdf(ctx, prefs, "${base}_compressed") { PdfOps.compress(ctx, first.uri, quality, it) }
                if (original > 0 && f.size >= original) {
                    PdfOps.delete(ctx, f.uri)
                    throw IllegalStateException("This PDF is already well optimised, so a smaller copy wasn't possible.")
                }
                val pct = if (original > 0) 100 - (f.size * 100 / original) else 0
                Screen.Created(f, "PDF Compressed!", "${formatSize(original)} → ${formatSize(f.size)} (−$pct%)")
            }
            ToolKind.TO_IMAGES -> {
                val n = PdfOps.pdfToImages(ctx, first!!.uri, base)
                Screen.Created(null, "Images Saved!", "$n image${if (n == 1) "" else "s"} saved to Pictures/Pixora PDF")
            }
        }
    }

    fun run() {
        scope.launch {
            busy = true
            val result = try { Result.success(withContext(Dispatchers.IO) { PdfOps.init(ctx); execute() }) } catch (e: Throwable) { Result.failure(e) }
            busy = false
            result.onSuccess { s ->
                state.filesVersion++
                state.root(Screen.Home)
                state.go(s)
            }.onFailure { toast(ctx, it.message ?: "Something went wrong") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(titleOf(kind), onBack = { state.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(descOf(kind), fontSize = 13.sp, color = cs.onSurfaceVariant, lineHeight = 18.sp)
            Spacer(Modifier.height(16.dp))

            if (srcs.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)).clickable { pick() }
                        .drawBehind {
                            drawRoundRect(Blue.copy(alpha = .8f), cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = Blue, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(if (kind == ToolKind.MERGE) "Choose PDFs" else "Choose a PDF", color = Blue, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                srcs.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(cs.surface)
                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PdfBadge(40)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                            Text("${s.pages} page${if (s.pages == 1) "" else "s"}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        if (kind == ToolKind.MERGE) {
                            Icon(Icons.Default.ArrowUpward, "Move up", tint = if (i > 0) Blue else cs.outlineVariant,
                                modifier = Modifier.size(32.dp).clickable(enabled = i > 0) { val t = srcs[i]; srcs[i] = srcs[i - 1]; srcs[i - 1] = t }.padding(5.dp))
                            Icon(Icons.Default.ArrowDownward, "Move down", tint = if (i < srcs.lastIndex) Blue else cs.outlineVariant,
                                modifier = Modifier.size(32.dp).clickable(enabled = i < srcs.lastIndex) { val t = srcs[i]; srcs[i] = srcs[i + 1]; srcs[i + 1] = t }.padding(5.dp))
                            Icon(Icons.Default.Close, "Remove", tint = cs.onSurfaceVariant, modifier = Modifier.size(32.dp).clickable { srcs.removeAt(i) }.padding(5.dp))
                        } else TextButton(onClick = { pick() }) { Text("Change") }
                    }
                }
                if (kind == ToolKind.MERGE) OutlinedButton(onClick = { pick() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add more PDFs")
                }
            }

            if (srcs.isNotEmpty()) when (kind) {
                ToolKind.SPLIT -> {
                    SectionLabel("Page ranges")
                    OutlinedTextField(
                        value = ranges, onValueChange = { ranges = it }, singleLine = true, placeholder = { Text("e.g. 1-3, 5, 7-9") },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { ranges = (1..srcs[0].pages).joinToString(", ") }) { Text("Every page as its own PDF") }
                }
                ToolKind.ROTATE -> {
                    SectionLabel("Rotate by")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("90° right" to 90, "180°" to 180, "90° left" to 270).forEach { (l, v) ->
                            ChoiceChip(l, rotate == v, Modifier.weight(1f)) { rotate = v }
                        }
                    }
                }
                ToolKind.COMPRESS -> {
                    SectionLabel("Compression level")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Quality.values().forEach { q ->
                            val sub = when (q) { Quality.LOW -> "Smallest"; Quality.MEDIUM -> "Balanced"; Quality.HIGH -> "Sharpest" }
                            ChoiceChip(q.label, quality == q, Modifier.weight(1f), sub = sub) { quality = q }
                        }
                    }
                }
                ToolKind.PASSWORD -> {
                    SectionLabel("Password")
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, singleLine = true, placeholder = { Text("At least 4 characters") },
                        visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showPwd = !showPwd }) { Icon(if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle") } },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {}
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(titleOf(kind), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), enabled = canRun && !busy) { run() }
    }
    if (busy) BusyDialog("Working…")
}

// ---------------------------------------------------------------- PDF VIEWER (in-app, PdfRenderer)

private class PdfSession(ctx: Context, uri: Uri) {
    private val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Cannot open file")
    private val renderer: PdfRenderer = try { PdfRenderer(pfd) } catch (e: Throwable) { pfd.close(); throw e }
    private val lock = Mutex()
    val count: Int = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) { PdfOps.renderPdfPage(renderer, index, widthPx).bmp }
    }

    fun close() { try { renderer.close() } catch (_: Throwable) {}; try { pfd.close() } catch (_: Throwable) {} }
}

@Composable
fun ViewerScreen(state: AppState, v: Screen.Viewer) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val session = remember(v.uri) { try { PdfSession(ctx, v.uri) } catch (e: Throwable) { null } }
    DisposableEffect(session) { onDispose { session?.close() } }

    Column(Modifier.fillMaxSize()) {
        Header(v.name, onBack = { state.back() }) {
            IconButton(onClick = { sharePdf(ctx, v.uri) }) { Icon(Icons.Default.Share, "Share") }
        }
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("This PDF can't be previewed here (it may be password protected). Use Share to open it in another app.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().background(cs.surfaceVariant), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(session.count) { i ->
                    var bmp by remember(i) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(i) { bmp = try { session.render(i, 1080) } catch (e: Throwable) { null } }
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.White)) {
                        val b = bmp
                        if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        else Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

package com.pixorapdf

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Normalised (0..1) crop rectangle, relative to the page AFTER rotation. */
data class CropRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val isFull: Boolean get() = l <= 0.002f && t <= 0.002f && r >= 0.998f && b >= 0.998f

    companion object { val FULL = CropRect(0f, 0f, 1f, 1f) }
}

data class PageItem(
    val id: Long,
    val uri: Uri,
    val rotation: Int = 0,
    val crop: CropRect? = null,
    val enhance: Boolean = false
) {
    fun rotated(clockwise: Boolean): PageItem {
        val c = crop
        val nc = when {
            c == null -> null
            clockwise -> CropRect(1 - c.b, c.l, 1 - c.t, c.r)
            else -> CropRect(c.t, 1 - c.r, c.b, 1 - c.l)
        }
        return copy(rotation = (rotation + if (clockwise) 90 else 270) % 360, crop = nc)
    }
}

enum class PageSize(val label: String, val sub: String) {
    A4("A4", "210 × 297 mm"), LETTER("Letter", "216 × 279 mm"), ORIGINAL("Original", "Image size")
}

enum class Quality(val label: String, val maxDim: Int, val jpeg: Float, val pdfWidth: Int) {
    LOW("Low", 1000, 0.5f, 800),
    MEDIUM("Medium", 1600, 0.75f, 1200),
    HIGH("High", 2400, 0.9f, 1700)
}

enum class Margin(val label: String, val pts: Float) {
    NONE("None", 0f), SMALL("Small", 18f), MEDIUM("Medium", 36f), LARGE("Large", 54f)
}

data class PdfOptions(
    val name: String = "",
    val size: PageSize = PageSize.A4,
    val landscape: Boolean = false,
    val quality: Quality = Quality.HIGH,
    val margin: Margin = Margin.NONE
)

data class PdfFile(val uri: Uri, val name: String, val size: Long, val modified: Long, val pages: Int)

enum class ToolKind { MERGE, SPLIT, ROTATE, PASSWORD, COMPRESS, TO_IMAGES }

sealed interface Screen {
    data object Home : Screen
    data object Files : Screen
    data object Tools : Screen
    data object Settings : Screen
    data object Select : Screen
    data object Arrange : Screen
    data object PdfSettings : Screen
    data object Scan : Screen
    data class Crop(val pageId: Long) : Screen
    data class Viewer(val uri: Uri, val name: String) : Screen
    data class Tool(val kind: ToolKind) : Screen
    data class Created(val file: PdfFile?, val title: String, val detail: String) : Screen
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pixora", Context.MODE_PRIVATE)

    var theme by mutableStateOf(sp.getString("theme", "System") ?: "System")
        private set
    var pageSize by mutableStateOf(runCatching { PageSize.valueOf(sp.getString("size", "A4")!!) }.getOrDefault(PageSize.A4))
        private set
    var quality by mutableStateOf(runCatching { Quality.valueOf(sp.getString("quality", "HIGH")!!) }.getOrDefault(Quality.HIGH))
        private set
    var saveLocation by mutableStateOf(sp.getString("location", "Downloads") ?: "Downloads")
        private set
    var favorites by mutableStateOf(sp.getStringSet("fav", emptySet())?.toSet() ?: emptySet())
        private set

    fun updateTheme(v: String) { theme = v; sp.edit().putString("theme", v).apply() }
    fun updatePageSize(v: PageSize) { pageSize = v; sp.edit().putString("size", v.name).apply() }
    fun updateQuality(v: Quality) { quality = v; sp.edit().putString("quality", v.name).apply() }
    fun updateSaveLocation(v: String) { saveLocation = v; sp.edit().putString("location", v).apply() }
    fun toggleFavorite(uri: Uri) {
        val k = uri.toString()
        favorites = if (k in favorites) favorites - k else favorites + k
        sp.edit().putStringSet("fav", favorites).apply()
    }
}

class AppState(context: Context) {
    val prefs = Prefs(context)
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val pages = mutableStateListOf<PageItem>()
    var opts by mutableStateOf(PdfOptions())
    var filesVersion by mutableIntStateOf(0)
    private var nextId = 1L

    val current: Screen get() = stack.last()

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun root(s: Screen) { stack.clear(); stack.add(s) }
    fun newId(): Long = nextId++

    fun startNew() {
        pages.clear()
        opts = PdfOptions(
            name = "PDF_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()),
            size = prefs.pageSize,
            quality = prefs.quality
        )
    }

    fun openImageToPdf() { startNew(); go(Screen.Select) }
    fun openScan() { startNew(); go(Screen.Scan) }

    fun addUris(uris: List<Uri>, enhance: Boolean = false) {
        for (u in uris) if (pages.none { it.uri == u }) pages.add(PageItem(newId(), u, enhance = enhance))
    }


}

fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
}

fun formatDate(seconds: Long): String {
    val ms = seconds * 1000
    return if (android.text.format.DateUtils.isToday(ms))
        "Today, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
    else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))
}

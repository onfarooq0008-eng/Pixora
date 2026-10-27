package com.pixorapdf

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.OutputStream
import kotlin.math.min

class Rendered(val bmp: Bitmap, val wPt: Int, val hPt: Int)

object PdfOps {
    val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val imagesUri: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val countCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun init(ctx: Context) { PDFBoxResourceLoader.init(ctx.applicationContext) }

    fun folder(prefs: Prefs) = if (prefs.saveLocation == "Documents") "Documents/Pixora PDF" else "Download/Pixora PDF"
    fun folderLabel(prefs: Prefs) = folder(prefs).replace("Download/", "Downloads/")

    fun sanitize(name: String): String {
        val s = name.trim().removeSuffix(".pdf").replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return s.ifEmpty { "Document" }
    }

    // ---------- storage ----------

    fun saveMedia(ctx: Context, collection: Uri, relPath: String, displayName: String, mime: String, write: (OutputStream) -> Unit): Uri {
        val cr = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(collection, values) ?: throw IllegalStateException("Cannot create file")
        try {
            val out = cr.openOutputStream(uri) ?: throw IllegalStateException("Cannot open file")
            out.use { write(it) }
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            try { cr.delete(uri, null, null) } catch (_: Throwable) {}
            throw e
        }
        return uri
    }

    fun savePdf(ctx: Context, prefs: Prefs, name: String, write: (OutputStream) -> Unit): PdfFile {
        val fileName = sanitize(name) + ".pdf"
        val uri = saveMedia(ctx, filesUri, folder(prefs), fileName, "application/pdf", write)
        return fileInfo(ctx, uri)
    }

    fun fileInfo(ctx: Context, uri: Uri): PdfFile {
        var name = "document.pdf"
        var size = 0L
        var mod = System.currentTimeMillis() / 1000
        val proj = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED)
        ctx.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) { name = c.getString(0) ?: name; size = c.getLong(1); mod = c.getLong(2) }
        }
        return PdfFile(uri, name, size, mod, pageCount(ctx, uri))
    }

    fun listPdfs(ctx: Context): List<PdfFile> {
        val out = ArrayList<PdfFile>()
        val proj = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED
        )
        val mime = MediaStore.MediaColumns.MIME_TYPE
        val rel = MediaStore.MediaColumns.RELATIVE_PATH
        val sel = "$mime=? AND ($rel LIKE ? OR $rel LIKE ?)"
        val args = arrayOf("application/pdf", "Download/Pixora PDF%", "Documents/Pixora PDF%")
        ctx.contentResolver.query(filesUri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(filesUri, c.getLong(0))
                val mod = c.getLong(3)
                val pages = countCache.getOrPut("$uri#$mod") { pageCount(ctx, uri) }
                out.add(PdfFile(uri, c.getString(1) ?: "document.pdf", c.getLong(2), mod, pages))
            }
        }
        return out
    }

    fun rename(ctx: Context, uri: Uri, newName: String): Boolean = try {
        val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, sanitize(newName) + ".pdf") }
        ctx.contentResolver.update(uri, v, null, null) > 0
    } catch (e: Throwable) { false }

    fun delete(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.delete(uri, null, null) > 0
    } catch (e: Throwable) { false }

    // ---------- picked documents ----------

    fun displayName(ctx: Context, uri: Uri): String {
        var n = "document.pdf"
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) n = it.getString(0) ?: n
            }
        } catch (_: Throwable) {}
        return n
    }

    fun sizeOf(ctx: Context, uri: Uri): Long {
        var s = 0L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) s = it.getLong(0)
            }
        } catch (_: Throwable) {}
        return s
    }

    /** Returns -1 when the file cannot be opened (damaged or password protected). */
    fun pageCount(ctx: Context, uri: Uri): Int = try {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
        if (pfd == null) -1 else try {
            val r = PdfRenderer(pfd)
            try { r.pageCount } finally { r.close() }
        } finally { pfd.close() }
    } catch (e: Throwable) { -1 }

    fun <T> withRenderer(ctx: Context, uri: Uri, block: (PdfRenderer) -> T): T {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Cannot open file")
        try {
            val r = PdfRenderer(pfd)
            try { return block(r) } finally { r.close() }
        } finally { pfd.close() }
    }

    fun renderPdfPage(r: PdfRenderer, index: Int, widthPx: Int): Rendered {
        val page = r.openPage(index)
        try {
            val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp.setHasAlpha(false)
            return Rendered(bmp, page.width, page.height)
        } finally { page.close() }
    }

    // ---------- PDF building ----------

    private fun pageDims(o: PdfOptions, bmpW: Int, bmpH: Int): Pair<Float, Float> {
        val m = o.margin.pts
        return when (o.size) {
            PageSize.ORIGINAL -> {
                val cw = 595f - 2 * m
                595f to (cw * bmpH / bmpW + 2 * m)
            }
            else -> {
                val base = if (o.size == PageSize.A4) PDRectangle.A4 else PDRectangle.LETTER
                if (o.landscape) base.height to base.width else base.width to base.height
            }
        }
    }

    fun imagesToPdf(ctx: Context, pages: List<PageItem>, o: PdfOptions, out: OutputStream, onProgress: (Int) -> Unit) {
        init(ctx)
        val doc = PDDocument()
        try {
            pages.forEachIndexed { i, p ->
                val bmp = Img.render(ctx, p, o.quality.maxDim)
                if (bmp != null) {
                    val (pw, ph) = pageDims(o, bmp.width, bmp.height)
                    val page = PDPage(PDRectangle(pw, ph))
                    doc.addPage(page)
                    val m = o.margin.pts
                    val s = min((pw - 2 * m) / bmp.width, (ph - 2 * m) / bmp.height)
                    val w = bmp.width * s
                    val h = bmp.height * s
                    val img = JPEGFactory.createFromImage(doc, bmp, o.quality.jpeg)
                    PDPageContentStream(doc, page).use { it.drawImage(img, (pw - w) / 2f, (ph - h) / 2f, w, h) }
                    bmp.recycle()
                }
                onProgress(i + 1)
            }
            doc.save(out)
        } finally { doc.close() }
    }

    /** Merge / split / rotate: copies real PDF pages (text stays selectable). */
    fun assemble(ctx: Context, parts: List<Pair<Uri, List<Int>?>>, rotateBy: Int, out: OutputStream) {
        init(ctx)
        val sources = ArrayList<PDDocument>()
        val dest = PDDocument()
        try {
            for ((uri, idx) in parts) {
                val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open file")
                val d = input.use { PDDocument.load(it) }
                sources.add(d)
                val pageIdx = idx ?: (0 until d.numberOfPages).toList()
                for (i in pageIdx) {
                    val np = dest.importPage(d.getPage(i))
                    if (rotateBy != 0) np.rotation = ((np.rotation + rotateBy) % 360 + 360) % 360
                }
            }
            dest.save(out)
        } finally {
            dest.close()
            sources.forEach { try { it.close() } catch (_: Throwable) {} }
        }
    }

    fun protect(ctx: Context, src: Uri, password: String, out: OutputStream) {
        init(ctx)
        val input = ctx.contentResolver.openInputStream(src) ?: throw IllegalStateException("Cannot open file")
        val doc = input.use { PDDocument.load(it) }
        try {
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(out)
        } finally { doc.close() }
    }

    /** Re-encodes every page as a JPEG at the chosen quality. */
    fun compress(ctx: Context, src: Uri, q: Quality, out: OutputStream) {
        init(ctx)
        val doc = PDDocument()
        try {
            withRenderer(ctx, src) { r ->
                for (i in 0 until r.pageCount) {
                    val rd = renderPdfPage(r, i, q.pdfWidth)
                    val page = PDPage(PDRectangle(rd.wPt.toFloat(), rd.hPt.toFloat()))
                    doc.addPage(page)
                    val img = JPEGFactory.createFromImage(doc, rd.bmp, q.jpeg)
                    PDPageContentStream(doc, page).use { it.drawImage(img, 0f, 0f, rd.wPt.toFloat(), rd.hPt.toFloat()) }
                    rd.bmp.recycle()
                }
            }
            doc.save(out)
        } finally { doc.close() }
    }

    fun pdfToImages(ctx: Context, src: Uri, base: String): Int = withRenderer(ctx, src) { r ->
        val n = r.pageCount
        for (i in 0 until n) {
            val rd = renderPdfPage(r, i, 1600)
            saveMedia(ctx, imagesUri, "Pictures/Pixora PDF", "${base}_page_${i + 1}.jpg", "image/jpeg") { o ->
                rd.bmp.compress(Bitmap.CompressFormat.JPEG, 92, o)
            }
            rd.bmp.recycle()
        }
        n
    }

    /** "1-3, 5" -> [[0,1,2],[4]] (zero based) or null when invalid. */
    fun parseRanges(text: String, max: Int): List<List<Int>>? {
        val out = ArrayList<List<Int>>()
        for (tok in text.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val p = tok.split("-").map { it.trim() }
            if (p.size > 2) return null
            val a = p[0].toIntOrNull() ?: return null
            val b = if (p.size == 1) a else (p[1].toIntOrNull() ?: return null)
            if (a < 1 || b > max || a > b) return null
            out.add((a - 1 until b).toList())
        }
        return if (out.isEmpty()) null else out
    }
}

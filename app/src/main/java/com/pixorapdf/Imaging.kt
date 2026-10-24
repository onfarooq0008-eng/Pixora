package com.pixorapdf

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Img {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Scanner-style look: grayscale + strong contrast so paper turns white. */
    private val docFilter: ColorMatrixColorFilter by lazy {
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val c = 1.5f
        val t = (-0.5f * c + 0.5f + 0.08f) * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )))
        ColorMatrixColorFilter(cm)
    }

    /** Decodes with EXIF orientation applied, longest side <= maxDim. */
    fun decode(ctx: Context, uri: Uri, maxDim: Int): Bitmap? = try {
        val src = if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path!!))
        else ImageDecoder.createSource(ctx.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > maxDim) {
                val f = maxDim.toFloat() / longest
                dec.setTargetSize(maxOf(1, (w * f).toInt()), maxOf(1, (h * f).toInt()))
            }
        }
    } catch (e: Throwable) { null }

    /** Applies rotation, crop and the optional document filter. Result is opaque (white background). */
    fun render(ctx: Context, p: PageItem, maxDim: Int): Bitmap? {
        val c = p.crop
        val frac = if (c == null) 1f else maxOf(c.r - c.l, c.b - c.t).coerceAtLeast(0.1f)
        var bmp = decode(ctx, p.uri, minOf((maxDim / frac).toInt(), 4000)) ?: return null
        if (p.rotation != 0) {
            val m = Matrix().apply { postRotate(p.rotation.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        if (c != null && !c.isFull) {
            val x = (c.l * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val y = (c.t * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            val w = ((c.r - c.l) * bmp.width).toInt().coerceIn(1, bmp.width - x)
            val h = ((c.b - c.t) * bmp.height).toInt().coerceIn(1, bmp.height - y)
            bmp = Bitmap.createBitmap(bmp, x, y, w, h)
        }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (p.enhance) paint.colorFilter = docFilter
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        out.setHasAlpha(false)
        return out
    }

    suspend fun thumb(ctx: Context, p: PageItem, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${p.uri}|${p.rotation}|${p.crop}|${p.enhance}|$size"
        cache.get(key) ?: render(ctx, p, size)?.also { cache.put(key, it) }
    }
}

@Composable
fun AsyncThumb(page: PageItem, size: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, page.uri, page.rotation, page.crop, page.enhance, size) {
        value = Img.thumb(ctx, page, size)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = scale) }
    }
}

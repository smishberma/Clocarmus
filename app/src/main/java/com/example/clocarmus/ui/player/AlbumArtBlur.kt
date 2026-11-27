package com.example.clocarmus.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** API 23 CPU blur: crop first, then three clamped box passes at a bounded resolution.
 * Radius scales with the display's long edge (2.5%), independent of cover resolution.
 * Only this background copy is changed; the decoded foreground artwork stays untouched.
 */
internal suspend fun blurAlbumArtwork(image: ImageBitmap, width: Int, height: Int): ImageBitmap =
    withContext(Dispatchers.Default) {
        val scale = minOf(1f, 256f / maxOf(width, height))
        val w = (width * scale).roundToInt().coerceAtLeast(1)
        val h = (height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            val fill = maxOf(w.toFloat() / image.width, h.toFloat() / image.height)
            val left = (w - image.width * fill) / 2f
            val top = (h - image.height * fill) / 2f
            canvas.drawBitmap(image.asAndroidBitmap(), null,
                RectF(left, top, w - left, h - top), Paint(Paint.FILTER_BITMAP_FLAG))
            val pixels = IntArray(w * h)
            val scratch = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val radius = (maxOf(w, h) * .025f).roundToInt().coerceAtLeast(1)
            repeat(3) {
                boxBlur(pixels, scratch, w, h, radius)
                boxBlur(scratch, pixels, h, w, radius)
            }
            currentCoroutineContext().ensureActive()
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.asImageBitmap()
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

/** Reads rows and writes transposed rows, making both passes linear in pixel count. */
private suspend fun boxBlur(
    source: IntArray, target: IntArray, width: Int, height: Int, radius: Int
) {
    val count = radius * 2 + 1
    for (y in 0 until height) {
        currentCoroutineContext().ensureActive()
        var red = 0
        var green = 0
        var blue = 0
        fun add(x: Int, sign: Int) {
            val pixel = source[y * width + x.coerceIn(0, width - 1)]
            red += ((pixel ushr 16) and 255) * sign
            green += ((pixel ushr 8) and 255) * sign
            blue += (pixel and 255) * sign
        }
        for (x in -radius..radius) add(x, 1)
        for (x in 0 until width) {
            target[x * height + y] = Color.rgb(red / count, green / count, blue / count)
            add(x - radius, -1)
            add(x + radius + 1, 1)
        }
    }
}

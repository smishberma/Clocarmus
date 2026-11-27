package com.example.clocarmus.ui.player

import android.graphics.Bitmap
import android.graphics.Color
import android.test.InstrumentationTestCase
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.runBlocking
import java.io.File

@Suppress("DEPRECATION")
class EmbeddedBackgroundLoaderTest : InstrumentationTestCase() {
    fun testBlurIsBoundedPreservesSourceAndSoftensDetailedAndSmallCovers() = runBlocking {
        for (size in listOf(16, 512)) {
            val source = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (y in 0 until size) for (x in 0 until size) {
                source.setPixel(x, y, if (x < size / 2) Color.BLACK else Color.WHITE)
            }
            for ((width, height) in listOf(800 to 480, 1920 to 1080)) {
                val blurred = blurAlbumArtwork(source.asImageBitmap(), width, height).asAndroidBitmap()
                try {
                    assertTrue(blurred.width <= 256 && blurred.height <= 256)
                    assertEquals(width.toFloat() / height, blurred.width.toFloat() / blurred.height, .02f)
                    val middle = Color.red(blurred.getPixel(blurred.width / 2, blurred.height / 2))
                    assertTrue("Sharp edge must become a moderate transition", middle in 80..180)
                    assertEquals(Color.BLACK, blurred.getPixel(0, blurred.height / 2))
                    assertEquals(Color.WHITE, blurred.getPixel(blurred.width - 1, blurred.height / 2))
                    assertEquals(Color.BLACK, source.getPixel(0, 0))
                    assertEquals(Color.WHITE, source.getPixel(size - 1, size - 1))
                } finally { blurred.recycle() }
            }
            source.recycle()
        }
    }

    fun testSampledArtworkContentIdentityAndCorruptFallback() = runBlocking {
        val directory = File(instrumentation.targetContext.cacheDir, "background-loader-test").apply { mkdirs() }
        try {
            val source = File(directory, "first.png")
            val copy = File(directory, "second.png")
            val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.MAGENTA)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            source.copyTo(copy, overwrite = true)
            val loader = EmbeddedBackgroundLoader()
            val first = loader.load(source.path, 320, 180)!!
            assertTrue(first.image.width <= 640)
            assertTrue(first.image.height <= 360)
            assertSame(first, loader.load(source.path, 320, 180))
            assertEquals(first.identity, loader.load(copy.path, 320, 180)!!.identity)
            val corrupt = File(directory, "corrupt").apply { writeText("invalid image") }
            assertNull(loader.load(corrupt.path, 320, 180))
            assertNull(loader.load(null, 320, 180))
        } finally { directory.deleteRecursively() }
    }
}

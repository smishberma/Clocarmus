package com.example.clocarmus.ui.player

import android.graphics.Bitmap
import android.graphics.Color
import android.test.InstrumentationTestCase
import kotlinx.coroutines.runBlocking
import java.io.File

@Suppress("DEPRECATION")
class EmbeddedBackgroundLoaderTest : InstrumentationTestCase() {
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

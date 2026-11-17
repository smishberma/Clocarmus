@file:Suppress("DEPRECATION")
package com.example.clocarmus.library

import android.net.Uri
import android.test.InstrumentationTestCase
import java.io.File
import kotlinx.coroutines.runBlocking

class TrackFilePathTest : InstrumentationTestCase() {
    fun testResolvesAnOpenedFileAndHandlesMissingFiles() = runBlocking {
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "file path 日本語.mp3")
        try {
            file.writeText("test")
            assertEquals(file.canonicalPath, trackFilePath(context.contentResolver, Uri.fromFile(file).toString()))
            file.delete()
            assertNull(trackFilePath(context.contentResolver, Uri.fromFile(file).toString()))
            assertNull(trackFilePath(context.contentResolver, "content://missing.provider/opaque-document"))
        } finally {
            file.delete()
        }
    }
}

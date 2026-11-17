package com.example.clocarmus.library

import android.content.ContentResolver
import android.net.Uri
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolve the actual opened file, without interpreting provider-specific document IDs. */
internal suspend fun trackFilePath(resolver: ContentResolver, uri: String): String? = withContext(Dispatchers.IO) {
    try {
        resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
            absoluteTrackFilePath(Os.readlink("/proc/self/fd/${descriptor.fd}"))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

/** Pipes and anonymous descriptors have no filesystem path. */
internal fun absoluteTrackFilePath(target: String): String? =
    target.takeIf { it.startsWith("/") && !it.endsWith(" (deleted)") }

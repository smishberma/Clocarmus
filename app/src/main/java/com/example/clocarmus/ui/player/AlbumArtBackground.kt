package com.example.clocarmus.ui.player

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** A single decoded cache entry, plus the two transition layers, bounds bitmap retention. */
internal class EmbeddedBackgroundLoader {
    private val mutex = Mutex()
    private var cachedKey: Triple<String, Int, Int>? = null
    private var cached: BackgroundArtwork<ImageBitmap>? = null

    suspend fun load(path: String?, width: Int, height: Int): BackgroundArtwork<ImageBitmap>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (path == null) return@withContext null
            val key = Triple(path, width, height)
            if (key == cachedKey) return@withContext cached
            val file = File(path)
            val identity = file.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxOf(width, height) * 2) sample *= 2
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            val result = bitmap?.let { BackgroundArtwork(identity, it.asImageBitmap()) }
            cached = result
            cachedKey = key
            result
        }
    }
}

@Composable
internal fun rememberAlbumArtBackground(
    enabled: Boolean,
    trackId: String?,
    embeddedPath: String?,
    timing: ArtworkSelectionTiming = ArtworkSelectionTiming()
): AlbumArtBackgroundState<ImageBitmap> {
    val scope = rememberCoroutineScope()
    val state = remember { AlbumArtBackgroundState<ImageBitmap>(scope) }
    val loader = remember { EmbeddedBackgroundLoader() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val width = (configuration.screenWidthDp * density).toInt().coerceAtLeast(1)
    val height = (configuration.screenHeightDp * density).toInt().coerceAtLeast(1)
    LaunchedEffect(enabled, trackId, embeddedPath, width, height, timing) {
        val remainingDelay = timing.eligibleAtMs - android.os.SystemClock.elapsedRealtime()
        state.select(enabled, trackId, timing.revision, remainingDelay) {
            loader.load(embeddedPath, width, height)
        }
    }
    DisposableEffect(state) { onDispose { state.close() } }
    return state
}

@Composable
internal fun AlbumArtBackground(state: AlbumArtBackgroundState<ImageBitmap>, visible: Boolean) {
    val frame by state.frame.collectAsState()
    val alpha = remember(frame.revision) { Animatable(1f) }
    LaunchedEffect(frame.revision, visible) {
        if (!visible) {
            state.finishFade(frame.revision)
            return@LaunchedEffect
        }
        alpha.snapTo(1f)
        alpha.animateTo(0f, tween(ALBUM_ART_FADE_MS, easing = LinearEasing))
        state.finishFade(frame.revision)
    }
    if (!visible) return
    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        frame.current?.let { Image(it.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (frame.transitioning) {
            Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color.Black)) {
                frame.outgoing?.let { Image(it.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)))
    }
}

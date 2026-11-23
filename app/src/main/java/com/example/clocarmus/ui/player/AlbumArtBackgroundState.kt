package com.example.clocarmus.ui.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val ALBUM_ART_STABILITY_MS = 3_000L
internal const val ALBUM_ART_FADE_MS = 500

internal data class BackgroundArtwork<T>(val identity: String, val image: T)
internal data class AlbumArtFrame<T>(
    val current: BackgroundArtwork<T>? = null,
    val outgoing: BackgroundArtwork<T>? = null,
    val revision: Long = 0,
    val transitioning: Boolean = false
)

/** Owned outside navigation: selection and mode changes alone invalidate preparation. */
internal class AlbumArtBackgroundState<T>(private val scope: CoroutineScope) {
    private val mutableFrame = MutableStateFlow(AlbumArtFrame<T>())
    val frame = mutableFrame.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L
    private var enabled = false
    private var selectedTrackId: String? = null
    private var stability: Deferred<Unit>? = null

    fun select(enabled: Boolean, trackId: String?, load: suspend () -> BackgroundArtwork<T>?) {
        pending?.cancel()
        val request = ++generation
        if (!enabled) {
            this.enabled = false
            stability?.cancel()
            stability = null
            mutableFrame.value = AlbumArtFrame(revision = request)
            return
        }
        if (!this.enabled || selectedTrackId != trackId) {
            stability?.cancel()
            stability = scope.async { delay(ALBUM_ART_STABILITY_MS) }
        }
        this.enabled = true
        selectedTrackId = trackId
        val stable = checkNotNull(stability)
        pending = scope.launch {
            val decoded = async {
                try { load() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
            stable.await()
            val next = decoded.await()
            if (generation != request) return@launch
            val previous = mutableFrame.value.current
            if (previous?.identity != next?.identity) {
                mutableFrame.value = AlbumArtFrame(next, previous, request, transitioning = true)
            }
        }
    }

    fun finishFade(revision: Long) {
        if (mutableFrame.value.revision == revision) {
            mutableFrame.value = mutableFrame.value.copy(outgoing = null, transitioning = false)
        }
    }

    fun close() {
        pending?.cancel()
        stability?.cancel()
        generation++
    }
}

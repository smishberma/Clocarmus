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

/** Process-only selection deadline, measured on the elapsed realtime clock. */
internal data class ArtworkSelectionTiming(val revision: Long = 0, val eligibleAtMs: Long = 0)

internal data class BackgroundArtwork<T>(val identity: String, val image: T)
internal data class AlbumArtFrame<T>(
    val current: BackgroundArtwork<T>? = null,
    val outgoing: BackgroundArtwork<T>? = null,
    val revision: Long = 0,
    val transitioning: Boolean = false
)

/** Presentation work never changes selection eligibility or restarts an in-flight fade. */
internal class AlbumArtBackgroundPresentation<T>(private val scope: CoroutineScope) {
    private val mutableFrame = MutableStateFlow(AlbumArtFrame<T>())
    val frame = mutableFrame.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L
    private var cache = emptyMap<BackgroundArtwork<T>, BackgroundArtwork<T>>()

    fun update(frame: AlbumArtFrame<T>, blur: Boolean, transform: suspend (T) -> T) {
        pending?.cancel()
        val request = ++generation
        if (!blur || (frame.current == null && frame.outgoing == null)) {
            cache = emptyMap()
            mutableFrame.value = frame
            return
        }
        pending = scope.launch {
            val prepared = mutableMapOf<BackgroundArtwork<T>, BackgroundArtwork<T>>()
            suspend fun prepare(art: BackgroundArtwork<T>?): BackgroundArtwork<T>? = art?.let {
                prepared[it] ?: (cache[it] ?: it.copy(image = transform(it.image))).also { result -> prepared[it] = result }
            }
            val next = frame.copy(current = prepare(frame.current), outgoing = prepare(frame.outgoing))
            if (request == generation) {
                // Retain at most the two visible layers, and reject even uncooperative old work.
                cache = prepared
                mutableFrame.value = next
            }
        }
    }

    fun close() {
        pending?.cancel()
        cache = emptyMap()
        generation++
    }
}

/** Owned outside navigation: selection and mode changes alone invalidate preparation. */
internal class AlbumArtBackgroundState<T>(private val scope: CoroutineScope) {
    private val mutableFrame = MutableStateFlow(AlbumArtFrame<T>())
    val frame = mutableFrame.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L
    private var enabled = false
    private var selectedTrackId: String? = null
    private var stability: Deferred<Unit>? = null
    private var selectionRevision = 0L

    fun select(
        enabled: Boolean,
        trackId: String?,
        selectionRevision: Long = 0,
        delayMillis: Long = 0,
        load: suspend () -> BackgroundArtwork<T>?
    ) {
        pending?.cancel()
        val request = ++generation
        if (!enabled) {
            this.enabled = false
            stability?.cancel()
            stability = null
            mutableFrame.value = AlbumArtFrame(revision = request)
            return
        }
        if (!this.enabled || selectedTrackId != trackId || this.selectionRevision != selectionRevision) {
            stability?.cancel()
            val wait = if (this.enabled) delayMillis.coerceAtLeast(0) else 0
            stability = scope.async { delay(wait) }
        }
        this.enabled = true
        selectedTrackId = trackId
        this.selectionRevision = selectionRevision
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

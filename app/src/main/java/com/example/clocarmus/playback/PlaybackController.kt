package com.example.clocarmus.playback

import android.content.*
import android.media.*
import android.net.Uri
import android.os.*
import androidx.compose.runtime.*
import com.example.clocarmus.ClocarmusApplication
import com.example.clocarmus.library.Availability
import com.example.clocarmus.ui.player.*
import com.example.clocarmus.playback.shuffle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** One process session. Activities only edit these command states; the service hosts audio. */
internal interface PlaybackHost { fun publish() }

internal class PlaybackController(
    private val app: Context,
    private val library: com.example.clocarmus.library.LocalLibrary = (app.applicationContext as ClocarmusApplication).library,
    private val shuffleGenerator: suspend (ShufflePreparationInput) -> List<QueueOccurrence> = ::prepareShuffle,
    private val openAudio: (Uri) -> android.content.res.AssetFileDescriptor? = { app.contentResolver.openAssetFileDescriptor(it, "r") }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(AudioManager::class.java)
    private val store = PlaybackStore(app)
    private var loaded = false
    private var playAfterLoad = false
    private var shuffleRemainderRequested = false
    private var remainderPrevious: List<String>? = null
    private var shuffleSuspended = false
    private var shuffleBoundary = false
    private var lastAudible: String? = null
    private var generatedOrder = emptyList<String>()
    private val shufflePreparation = ShufflePreparation(scope, shuffleGenerator) { schedule() }
    var preparingShuffle by mutableStateOf(false)
        private set
    internal val ready get() = loaded
    private var revision = 0L
    private var allowCheckpoint = true
    private var player: MediaPlayer? = null
    private var prepared = false
    private var engineOccurrence: String? = null
    private var pendingSeek = false
    private var seeking = false
    private var saveWarningShown = false
    private var newStart = true
    private var ended = false
    private var focus = false
    private var ducked = false
    private var focusGeneration = 0
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var focusRequest: AudioFocusRequest? = null
    private val failed = mutableSetOf<String>()
    var host: PlaybackHost? = null
    var warning by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    fun dismissNotice() { notice = null }
    fun dismissWarning() { warning = null }

    private fun <T> command(initial: T, changed: (T, T) -> Unit = { _, _ -> }): MutableState<T> {
        val backing = mutableStateOf(initial)
        return object : MutableState<T> {
            override var value: T
                get() = backing.value
                set(next) {
                    val old = backing.value
                    if (old == next) return
                    backing.value = next
                    if (reporting) return
                    changed(old, next)
                    allowCheckpoint = true
                    revision++
                    schedule()
                }
            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
    val queueState = command(TrackQueue()) { old, next ->
        invalidateShuffle()
        // A manual ordering/replacement supersedes pending ordering; cursor advances do not.
        if (old.entries != next.entries && next.scheduledIds.isEmpty()) shuffleRemainderRequested = false
        if (old.currentId != next.currentId) shuffleBoundary = false
        if (old.currentId != next.currentId) { ended = false; failed.clear() }
    }
    val playingState = command(false) { _, next ->
        if (!next) { shuffleSuspended = true; invalidateShuffle(); pauseEngine() }
        else shuffleSuspended = false
    }
    val positionState = command(0f) { _, _ ->
        pendingSeek = true; ended = false
        if (shuffleBoundary) { shuffleBoundary = false; invalidateShuffle() }
    }
    val shuffleState = command(false) { _, enabled ->
        invalidateShuffle()
        if (loaded) {
            shuffleRemainderRequested = enabled
            remainderPrevious = null
            shuffleSuspended = false
        }
    }
    val repeatState = command(0) { _, _ -> invalidateShuffle() }
    private var queue by queueState
    private var playing by playingState
    private var position by positionState
    private val available: (String) -> Boolean = { id -> library.state.value.records.any { it.id == id && it.availability == Availability.AVAILABLE } }
    private data class PendingCheckpoint(val generation: Long, val checkpoint: PlaybackCheckpoint)
    private val writeGeneration = AtomicLong()
    private val writeMutex = Mutex()
    private val writes = Channel<PendingCheckpoint>(Channel.CONFLATED)
    private val reconcile = Runnable { if (loaded) { reconcileShuffle(); reconcileEngine(); checkpoint(); host?.publish() } }
    private fun schedule() { handler.removeCallbacks(reconcile); handler.post(reconcile) }

    init {
        scope.launch {
            val initialRevision = revision
            val saved = withContext(Dispatchers.IO) { runCatching {
                store.load() ?: loadDemoSession(app.getSharedPreferences("demo-session", 0))
                    ?.takeIf { it.queue.entries.all { entry -> entry.trackId.startsWith("local:") } }
                    ?.let { PlaybackCheckpoint(it) }
            } }
            if (revision == initialRevision) {
                saved.getOrNull()?.let {
                    queue = it.session.queue; position = it.session.position
                    shuffleState.value = it.session.shuffle; repeatState.value = it.session.repeat
                    ended = it.ended
                    shuffleBoundary = it.waitingForShuffle
                    shuffleSuspended = true // Restoration neither prepares nor activates a new pass.
                    generatedOrder = it.session.queue.entries.map { entry -> entry.trackId }
                }
                if (saved.isFailure) warning = "Saved playback could not be read. The player was restored empty and paused."
            }
            allowCheckpoint = saved.isSuccess || revision != initialRevision
            loaded = true
            // A corrupt snapshot must remain available for diagnosis until a real command changes state.
            if (saved.isSuccess) schedule()
            if (playAfterLoad) { playAfterLoad = false; play() }
            for (snapshot in writes) {
                var candidate = snapshot
                var delayMs = 1000L
                while (true) {
                    val result = withContext(Dispatchers.IO) { runCatching {
                        writeMutex.withLock {
                            if (candidate.generation == writeGeneration.get()) store.save(candidate.checkpoint)
                        }
                    } }
                    if (result.isSuccess) break
                    if (!saveWarningShown) {
                        saveWarningShown = true
                        warning = "Playback progress could not be saved. Free storage; saving will retry."
                    }
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(30_000)
                    writes.tryReceive().getOrNull()?.let { candidate = it }
                }
            }
        }
        scope.launch {
            var previousAvailability = emptySet<String>()
            library.state.collect { snapshot ->
                val scopeIds = queue.entries.mapTo(HashSet()) { it.trackId }
                val availability = snapshot.records.filter { it.id in scopeIds && it.availability == Availability.AVAILABLE }.mapTo(HashSet()) { it.id }
                if (availability != previousAvailability) {
                    previousAvailability = availability
                    invalidateShuffle()
                    schedule()
                }
                if (snapshot.ready && queue.current != null && !available(queue.current!!.trackId)) {
                    playing = false // Drive/access recovery never resumes audio.
                    if (snapshot.records.none { it.id == queue.current?.trackId }) {
                        queue = queue.copy(currentId = null, stopAfterId = null, resumeAfterStop = false)
                        position = 0f
                    }
                }
                host?.publish()
            }
        }
        scope.launch {
            var ticks = 0
            while (true) {
                delay(250)
                val engine = player
                if (prepared && engine != null && !pendingSeek && !seeking) {
                    val seconds = runCatching { engine.currentPosition / 1000f }.getOrNull()
                    if (seconds != null) {
                        // Updating the cursor must not turn a progress tick into a seek command.
                        positionStateInternal(seconds)
                    }
                }
                if (++ticks % 20 == 0 && playing) { checkpoint(); host?.publish() }
            }
        }
    }

    // Suppress command side effects when publishing decoder progress.
    private var reporting = false
    private fun positionStateInternal(seconds: Float) {
        reporting = true
        position = seconds
        reporting = false
    }
    private fun checkpoint() {
        if (loaded && allowCheckpoint && !reporting) writes.trySend(PendingCheckpoint(writeGeneration.get(), PlaybackCheckpoint(DemoSession(queue, position, shuffleState.value, repeatState.value), ended, shuffleBoundary)))
    }

    /** Browser selection captures an unshuffled snapshot; ordering work belongs to this owner. */
    fun selectCollection(ids: List<String>, selected: String) {
        if (selected !in ids || !available(selected)) { notice = "Track unavailable"; return }
        generatedOrder = emptyList()
        shuffleBoundary = false
        queue = TrackQueue.fromTracks(ids, selected)
        if (shuffleState.value) {
            // A browser-selected opening pins the chosen occurrence at the start of the session.
            val current = queue.current!!
            queue = queue.copy(entries = listOf(current) + queue.entries.filterNot { it.id == current.id })
        }
        shuffleRemainderRequested = shuffleState.value
        remainderPrevious = emptyList() // A chosen opening starts an unconstrained first remainder.
        shuffleSuspended = false
        position = 0f
        playing = true
        schedule()
    }

    fun toggleShuffle() { shuffleState.value = !shuffleState.value }

    private fun invalidateShuffle() {
        shufflePreparation.invalidate()
        preparingShuffle = false
    }

    private fun reconcileShuffle() {
        if (!shuffleState.value) {
            shuffleRemainderRequested = false
            // Turning shuffle off while waiting at a boundary resumes the ordinary repeat path.
            if (shuffleBoundary && !shuffleSuspended) {
                shuffleBoundary = false
                val first = queue.entries.firstOrNull { available(it.trackId) }
                if (first == null) playing = false
                else { queue = queue.copy(currentId = first.id, resumeAfterStop = false, scheduledIds = emptyList()); position = 0f }
            }
            return
        }
        if (shuffleSuspended || queue.current == null) return
        if (!shuffleRemainderRequested && (repeatState.value != 2 || (!playing && !shuffleBoundary))) {
            if (shuffleBoundary) { shuffleBoundary = false; playing = false; ended = true }
            return
        }
        val kind = if (shuffleRemainderRequested) ShufflePreparationKind.REMAINDER else ShufflePreparationKind.PASS
        val tail = queue.entries.drop(queue.currentIndex + 1).filterNot { it.id in queue.scheduledIds }
        val scopeIds = queue.entries.mapTo(HashSet()) { it.trackId }
        val input = ShufflePreparationInput(queue, kind,
            library.state.value.records.filter { it.id in scopeIds && it.availability == Availability.AVAILABLE }.mapTo(HashSet()) { it.id },
            if (kind == ShufflePreparationKind.REMAINDER) remainderPrevious ?: tail.map { it.trackId }
                else generatedOrder.ifEmpty { queue.entries.map { it.trackId } },
            lastAudible)
        shufflePreparation.prepare(input)
        preparingShuffle = shufflePreparation.result == null && shufflePreparation.failure == null
        if (shufflePreparation.failure != null) {
            preparingShuffle = false
            if (kind == ShufflePreparationKind.REMAINDER) {
                shuffleRemainderRequested = false
                shuffleState.value = false
                notice = "Shuffle could not be prepared. The current queue was kept."
            } else if (shuffleBoundary) {
                shuffleSuspended = true
                playing = false
                notice = "The next shuffled pass could not be prepared. Press Play to retry."
            }
            return
        }
        val entries = shufflePreparation.result ?: return
        if (kind == ShufflePreparationKind.REMAINDER) {
            shuffleRemainderRequested = false
            if (generatedOrder.isEmpty()) generatedOrder = entries.map { it.trackId }
            queue = queue.copy(entries = entries)
            invalidateShuffle()
            schedule()
        } else if (shuffleBoundary && !shuffleSuspended) {
            shuffleBoundary = false
            if (entries.isEmpty()) {
                playing = false
                ended = true
                notice = "No available tracks remain in this collection."
            } else {
                generatedOrder = entries.map { it.trackId }
                queue = queue.copy(entries = entries, currentId = entries.first().id,
                    scheduledIds = emptyList(), resumeAfterStop = false,
                    stopAfterId = queue.stopAfterId?.takeIf { marker -> entries.any { it.id == marker } })
                position = 0f
                ended = false
                releaseEngine()
                invalidateShuffle()
                schedule()
            }
        }
    }

    private fun waitForShufflePass() {
        shuffleSuspended = false
        shuffleBoundary = true
        releaseEngine()
        schedule()
    }

    fun play() {
        if (!loaded) { playAfterLoad = true; return }
        if (queue.current == null) { notice = "Select a track to start playback."; return }
        if (!available(queue.current!!.trackId)) { notice = "Track unavailable"; return }
        failed.clear()
        shuffleSuspended = false
        if (shuffleBoundary) {
            invalidateShuffle()
            playing = true
            schedule()
            return
        }
        if (queue.resumeAfterStop) {
            val next = queue.step(1, available)
            if (next.currentId == queue.currentId) {
                if (repeatState.value != 2) return
                if (shuffleState.value) { playing = true; waitForShufflePass(); return }
                val first = queue.entries.firstOrNull { available(it.trackId) } ?: return
                queue = queue.copy(currentId = first.id, resumeAfterStop = false, scheduledIds = emptyList())
            } else queue = next
            position = 0f
        } else if (ended) { position = 0f; ended = false; releaseEngine() }
        else if (durationMs() > 0 && position * 1000 >= durationMs()) { playing = true; complete(); return }
        playing = true
        schedule()
    }
    fun pause() { playAfterLoad = false; shuffleSuspended = true; invalidateShuffle(); playing = false; checkpoint(); host?.publish() }
    fun selectOccurrence(id: String) {
        val occurrence = queue.entries.firstOrNull { it.id == id } ?: return
        if (!available(occurrence.trackId)) { notice = "Track unavailable"; return }
        shuffleRemainderRequested = false
        invalidateShuffle()
        if (id == queue.currentId) { play(); return }
        queue = queue.select(id).copy(stopAfterId = null)
        position = 0f
        playing = true
    }
    fun step(direction: Int) {
        if (queue.current == null) return
        if (direction < 0) { shuffleRemainderRequested = false; invalidateShuffle() }
        if (direction < 0 && position > 3f) {
            position = 0f; releaseEngine(); schedule(); return
        }
        val old = queue
        var next = old.step(direction, available)
        if (direction > 0 && old.stopAfterId == old.currentId) next = next.copy(stopAfterId = null)
        if (next.currentId == old.currentId) {
            if (repeatState.value == 2) {
                if (direction > 0 && shuffleState.value) { queue = next; waitForShufflePass(); return }
                val entry = if (direction > 0) next.entries.firstOrNull { available(it.trackId) }
                    else next.entries.lastOrNull { available(it.trackId) }
                if (entry != null) next = next.copy(currentId = entry.id, resumeAfterStop = false, scheduledIds = emptyList())
            } else if (direction > 0) {
                queue = next
                positionStateInternal(durationMs() / 1000f)
                ended = true; playing = false; releaseEngine(); checkpoint(); return
            }
        }
        queue = next
        position = 0f
        releaseEngine()
        schedule()
    }
    private fun durationMs(): Int = if (prepared) runCatching { player?.duration ?: 0 }.getOrDefault(0)
        else (library.state.value.tracks.firstOrNull { it.id == queue.current?.trackId }?.durationSeconds ?: 0) * 1000
    private fun reconcileEngine() {
        if (shuffleBoundary) return
        if (queue.currentId != engineOccurrence) releaseEngine()
        if (queue.current == null) { if (playing) playing = false; releaseEngine(); return }
        if (playing && !available(queue.current!!.trackId)) {
            notice = "Track unavailable"; playing = false; return
        }
        if (playing && host == null) {
            try {
                val intent = Intent(app, PlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
            } catch (_: RuntimeException) { notice = "Playback could not start. Open the app and press Play again."; playing = false }
            return
        }
        if (!playing) {
            if (prepared && pendingSeek) {
                pendingSeek = false
                player?.let { seekEngine(it, (position * 1000).toInt().coerceIn(0, durationMs().coerceAtLeast(0))) }
            }
            pauseEngine(); return
        }
        host?.publish()
        if (!focus && !requestFocus()) { notice = "Audio is busy. Press Play after the interruption."; playing = false; return }
        if (player == null) prepare()
        else if (prepared) {
            val engine = player ?: return
            if (pendingSeek) {
                pendingSeek = false
                val target = (position * 1000).toInt().coerceIn(0, durationMs().coerceAtLeast(0))
                if (target >= durationMs() && durationMs() > 0) { complete(); return }
                seekEngine(engine, target)
            }
            if (!engine.isPlaying) startEngine(engine)
        }
    }
    private fun prepare() {
        val record = library.state.value.records.firstOrNull { it.id == queue.current?.trackId } ?: return
        val engine = MediaPlayer()
        player = engine; engineOccurrence = queue.currentId; prepared = false; newStart = true
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        engine.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
        engine.setOnPreparedListener {
            if (player === it) {
                prepared = true
                if (position > 0f) seekEngine(it, (position * 1000).toInt().coerceIn(0, it.duration.coerceAtLeast(0)))
                pendingSeek = false
                if (playing && focus) startEngine(it)
                host?.publish()
            }
        }
        engine.setOnSeekCompleteListener { if (player === it) seeking = false }
        engine.setOnCompletionListener { if (player === it && playing) complete() }
        engine.setOnErrorListener { source, _, _ -> if (player === source) recover(); true }
        // Providers may block when opening a descriptor; keep that work off the main thread.
        scope.launch {
            val descriptor = withContext(Dispatchers.IO) { runCatching { openAudio(Uri.parse(record.uri)) } }
            val asset = descriptor.getOrNull()
            if (player !== engine) { asset?.close(); return@launch }
            try {
                checkNotNull(asset)
                asset.use {
                    if (it.declaredLength < 0) engine.setDataSource(it.fileDescriptor)
                    else engine.setDataSource(it.fileDescriptor, it.startOffset, it.declaredLength)
                }
                engine.prepareAsync()
            } catch (_: Exception) { recover() }
        }
    }
    private fun seekEngine(engine: MediaPlayer, millis: Int) { seeking = true; engine.seekTo(millis) }
    private fun startEngine(engine: MediaPlayer) {
        try {
            engine.setVolume(if (ducked) .2f else 1f, if (ducked) .2f else 1f)
            engine.start()
            val audible = queue.current?.trackId
            if (lastAudible != audible) { lastAudible = audible; invalidateShuffle(); schedule() }
            if (newStart) {
                newStart = false
                val track = library.state.value.tracks.firstOrNull { it.id == queue.current?.trackId }
                if (track != null) scope.launch {
                    runCatching { library.editPlaylists { it.playbackStarted(track, System.currentTimeMillis()) } }
                        .onFailure { warning = "Recently played could not be saved." }
                }
            }
        } catch (_: RuntimeException) { recover() }
    }
    private fun complete() {
        if (prepared) positionStateInternal(durationMs() / 1000f)
        if (shuffleState.value && repeatState.value == 2 && queue.currentId != queue.stopAfterId &&
            queue.entries.drop(queue.currentIndex + 1).none { available(it.trackId) }) {
            waitForShufflePass()
            return
        }
        val result = queue.complete(repeatState.value, available)
        queue = result.queue
        releaseEngine()
        if (result.playing) { position = 0f; playing = true; schedule() }
        else { ended = !queue.resumeAfterStop; playing = false; checkpoint() }
    }
    private fun recover() {
        if (!playing) { releaseEngine(); return }
        val rejected = queue.currentId
        val attempted = failed + listOfNotNull(rejected)
        releaseEngine()
        notice = "Track could not play. Trying the next available track."
        val next = queue.entries.drop(queue.currentIndex + 1).firstOrNull { it.id !in attempted && available(it.trackId) }
        if (next == null) { playing = false; notice = "No remaining track could play." }
        else {
            queue = queue.copy(currentId = next.id, stopAfterId = queue.stopAfterId?.takeUnless { it == rejected }, scheduledIds = queue.scheduledIds - attempted)
            failed.addAll(attempted); position = 0f; schedule()
        }
    }
    private fun pauseEngine() {
        if (prepared) runCatching {
            if (!pendingSeek && player?.isPlaying == true) positionStateInternal(player!!.currentPosition / 1000f)
            player?.pause()
        }
        abandonFocus()
        host?.publish()
    }
    private fun releaseEngine() {
        player?.release(); player = null; prepared = false; seeking = false; engineOccurrence = null
    }
    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        val generation = ++focusGeneration
        val listener = AudioManager.OnAudioFocusChangeListener { event -> handler.post {
            if (generation == focusGeneration) when (event) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { ducked = true; player?.setVolume(.2f, .2f) }
                AudioManager.AUDIOFOCUS_GAIN -> { ducked = false; player?.setVolume(1f, 1f) }
                else -> pause()
            }
        } }
        focusListener = listener
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(false).setOnAudioFocusChangeListener(listener, handler).build()
            focusRequest = request; audio.requestAudioFocus(request)
        } else audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        focus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focus) abandonFocus()
        return focus
    }
    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        focusGeneration++
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        else focusListener?.let { audio.abandonAudioFocus(it) }
        ducked = false
        focus = false; focusRequest = null; focusListener = null
        if (prepared) player?.setVolume(1f, 1f)
    }
    /** Invalidates queued/retrying saves before the reset journal is cleared. */
    suspend fun resetDurably() {
        val empty = withContext(Dispatchers.Main.immediate) {
            pause()
            writeGeneration.incrementAndGet()
            queue = TrackQueue(); position = 0f; ended = false
            releaseEngine()
            PlaybackCheckpoint(DemoSession(queue, position, shuffleState.value, repeatState.value))
        }
        withContext(Dispatchers.IO) { writeMutex.withLock { store.save(empty) } }
    }
    fun hostReady(service: PlaybackHost) { host = service; schedule() }
    internal fun close() { pause(); releaseEngine(); scope.cancel(); handler.removeCallbacks(reconcile) }
    fun hostDestroyed() { host = null; playing = false; releaseEngine(); checkpoint() }
}

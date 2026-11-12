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
    private var orderingRequested = false
    private var shuffleSuspended = false
    private var shuffleBoundary = false
    private var remainderAdvancePending = false
    private var remainderAdvanceAutomatic = true
    private var allowPausedAdvance = false
    private var lastAudible: String? = null
    private var generatedOrder = emptyList<String>()
    private var collection = ShuffleCollection(kind = "snapshot")
    private var retained = emptyList<String>()
    private var historicalOrder = emptyList<String>()
    private var sourceEntries = emptyList<QueueOccurrence>()
    private var collectionEpoch = 0L
    private var membershipJob: Job? = null
    private var historyJob: Job? = null
    private var membershipReady = false
    private var historyReady = false
    private var historyFailure: Throwable? = null
    private val pendingHistories = mutableMapOf<String, ShuffleHistory>()
    private var generatedRevision = 0L
    private var historicalRevision = 0L
    private var freshSelection = false
    private var settingCollection = false
    private var keepOrderOnDisable = false
    private val historyWrites = Channel<Unit>(Channel.CONFLATED)
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
        if (old.entries != next.entries && next.scheduledIds.isEmpty()) orderingRequested = false
        if (old.currentId != next.currentId) { shuffleBoundary = false; remainderAdvancePending = false; allowPausedAdvance = false }
        if (old.currentId != next.currentId) { ended = false; failed.clear() }
        if (loaded && !settingCollection) {
            val previousIds = old.entries.mapTo(HashSet()) { it.id }
            if (next.entries.isNotEmpty() && next.entries.none { it.id in previousIds }) {
                bindCollection(ShuffleCollection(), next.originalEntries.map { it.trackId })
            } else if (collection.kind == "manual" && old.originalEntries != next.originalEntries) {
                retained = next.originalEntries.map { it.trackId }
                refreshCollection(library.state.value)
            }
        }
    }
    val playingState = command(false) { _, next ->
        if (!next) { shuffleSuspended = true; allowPausedAdvance = false; invalidateShuffle(); pauseEngine() }
        else shuffleSuspended = false
    }
    val positionState = command(0f) { _, _ ->
        pendingSeek = true; ended = false
        if (shuffleBoundary || remainderAdvancePending) {
            shuffleBoundary = false; remainderAdvancePending = false; invalidateShuffle()
        }
    }
    val shuffleState = command(false) { _, _ ->
        invalidateShuffle()
        if (loaded) {
            orderingRequested = !keepOrderOnDisable
            freshSelection = false
            shuffleSuspended = false
        }
    }
    val repeatState = command(0) { _, _ -> invalidateShuffle() }
    private var queue by queueState
    private var playing by playingState
    private var position by positionState
    private val available: (String) -> Boolean = { id -> library.state.value.byId[id]?.availability == Availability.AVAILABLE }
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
                    remainderAdvancePending = it.waitingForRemainder
                    remainderAdvanceAutomatic = it.automaticRemainderAdvance
                    shuffleSuspended = true // Restoration neither prepares nor activates a new pass.
                    val metadata = it.shuffleSession
                    lastAudible = metadata?.lastAudible
                    bindCollection(metadata?.collection ?: ShuffleCollection(kind = "snapshot"),
                        metadata?.retained ?: it.session.queue.originalEntries.map { entry -> entry.trackId })
                    generatedOrder = metadata?.generated ?: emptyList()
                    generatedRevision = metadata?.historyRevision ?: 0
                    orderingRequested = metadata?.pendingRemainder ?: false
                    freshSelection = metadata?.freshSelection ?: false
                }
                if (store.recovered) warning = "Playback was restored paused from the last valid recovery checkpoint."
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
                if (snapshot.ready) refreshCollection(snapshot)
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
            for (signal in historyWrites) {
                while (pendingHistories.isNotEmpty()) {
                    val key = pendingHistories.keys.first()
                    var record = checkNotNull(pendingHistories[key])
                    val generation = writeGeneration.get()
                    var retry = 1000L
                    while (generation == writeGeneration.get()) {
                        val saving = record
                        val saved = withContext(Dispatchers.IO) { runCatching {
                            writeMutex.withLock { if (generation == writeGeneration.get()) store.saveHistory(key, saving.order, saving.revision) }
                        } }
                        if (saved.isSuccess) {
                            if (pendingHistories[key] === saving) pendingHistories.remove(key)
                            break
                        }
                        if (!saveWarningShown) { saveWarningShown = true; warning = "Shuffle history could not be saved. Saving will retry." }
                        delay(retry); retry = (retry * 2).coerceAtMost(30_000)
                        record = pendingHistories[key] ?: break
                    }
                }
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
        if (loaded && allowCheckpoint && !reporting) writes.trySend(PendingCheckpoint(writeGeneration.get(), PlaybackCheckpoint(DemoSession(queue, position, shuffleState.value, repeatState.value), ended, shuffleBoundary,
            ShuffleSession(collection, retained, generatedOrder, lastAudible, orderingRequested, freshSelection, generatedRevision), remainderAdvancePending, remainderAdvanceAutomatic)))
    }

    /** Browser selection captures an unshuffled snapshot; ordering work belongs to this owner. */
    fun selectCollection(ids: List<String>, selected: String, source: ShuffleCollection = ShuffleCollection()) {
        if (selected !in ids || !available(selected)) { notice = "Track unavailable"; return }
        settingCollection = true
        bindCollection(source, ids.toList())
        generatedOrder = emptyList()
        shuffleBoundary = false
        remainderAdvancePending = false
        queue = TrackQueue.fromTracks(ids, selected)
        if (shuffleState.value) {
            // A browser-selected opening pins the chosen occurrence at the start of the session.
            val current = queue.current!!
            val availableIds = library.state.value.records.filter { it.availability == Availability.AVAILABLE }.mapTo(HashSet()) { it.id }
            queue = queue.copy(entries = listOf(current) + queue.entries.filter { it.id != current.id && it.trackId in availableIds },
                consumedIds = emptySet())
        }
        settingCollection = false
        orderingRequested = shuffleState.value
        freshSelection = true
        shuffleSuspended = false
        position = 0f
        playing = true
        schedule()
    }

    private fun bindCollection(source: ShuffleCollection, snapshot: List<String>) {
        collectionEpoch++
        collection = source
        retained = snapshot
        sourceEntries = emptyList()
        historicalOrder = emptyList()
        historyReady = false
        historyFailure = null
        membershipReady = false
        generatedOrder = emptyList()
        generatedRevision = 0
        historicalRevision = 0
        invalidateShuffle()
        val epoch = collectionEpoch
        historyJob?.cancel()
        val pending = pendingHistories[source.key]
        historyJob = scope.launch {
            val result = if (pending != null) Result.success(pending) else
                withContext(Dispatchers.IO) { runCatching { writeMutex.withLock { store.loadHistoryRecord(source.key) } } }
            if (epoch != collectionEpoch) return@launch
            val recoveredHistory = result.isFailure && generatedOrder.isNotEmpty() && generatedRevision > 0
            val usable = if (recoveredHistory) {
                warning = "Shuffle history was recovered from the saved active session."
                enqueueHistory()
                Result.success(ShuffleHistory(generatedOrder, generatedRevision))
            } else result
            usable.onSuccess {
                historicalOrder = it.order
                historicalRevision = it.revision
                // A committed active checkpoint can outlive an interrupted history write.
                if (generatedOrder.isNotEmpty() && generatedRevision > historicalRevision) {
                    historicalOrder = generatedOrder
                    historicalRevision = generatedRevision
                    enqueueHistory()
                }
            }
                .onFailure { warning = "Saved shuffle history could not be read. Shuffle is unavailable for this collection." }
            historyReady = usable.isSuccess
            historyFailure = usable.exceptionOrNull()
            schedule()
        }
        refreshCollection(library.state.value)
    }

    private fun refreshCollection(snapshot: com.example.clocarmus.library.LibrarySnapshot) {
        if (!snapshot.ready) return
        membershipJob?.cancel()
        membershipReady = false
        val epoch = collectionEpoch
        val source = collection
        val fallback = retained
        membershipJob = scope.launch {
            val ids = withContext(Dispatchers.Default) { source.resolve(snapshot, fallback, eligibleOnly = false).also { currentCoroutineContext().ensureActive() } }
            if (epoch != collectionEpoch || library.state.value !== snapshot) return@launch
            if (ids != sourceEntries.map { it.trackId }) {
                val preparedEntries = withContext(Dispatchers.Default) { TrackQueue.fromTracks(ids, null).entries }
                if (epoch != collectionEpoch || library.state.value !== snapshot) return@launch
                sourceEntries = preparedEntries
                if (!orderingRequested) invalidateShuffle()
            }
            membershipReady = true
            schedule()
        }
    }

    private fun commitGenerated(entries: List<QueueOccurrence>) {
        generatedOrder = entries.map { it.trackId }
        generatedRevision = maxOf(System.currentTimeMillis(), historicalRevision + 1, generatedRevision + 1)
        historicalOrder = generatedOrder
        historicalRevision = generatedRevision
        freshSelection = false
        enqueueHistory()
    }

    private fun enqueueHistory() {
        pendingHistories[collection.key] = ShuffleHistory(generatedOrder, generatedRevision)
        historyWrites.trySend(Unit)
    }

    fun toggleShuffle() { shuffleState.value = !shuffleState.value }

    private fun invalidateShuffle() {
        shufflePreparation.invalidate()
        preparingShuffle = false
    }

    private fun disableFailedShuffle() {
        orderingRequested = false
        invalidateShuffle()
        keepOrderOnDisable = true
        try { shuffleState.value = false } finally { keepOrderOnDisable = false }
        if (remainderAdvancePending) pause()
    }

    private fun reconcileShuffle() {
        if (remainderAdvancePending && !shuffleSuspended && !orderingRequested && (playing || allowPausedAdvance)) {
            finishRemainderAdvance()
        }
        if (!shuffleState.value && !orderingRequested) {
            // Turning shuffle off while waiting at a boundary resumes the ordinary repeat path.
            if (shuffleBoundary && !shuffleSuspended && (playing || allowPausedAdvance)) {
                shuffleBoundary = false
                val first = queue.entries.firstOrNull { available(it.trackId) }
                if (first == null) playing = false
                else { queue = queue.copy(currentId = first.id, resumeAfterStop = false, scheduledIds = emptyList(),
                    consumedIds = emptySet(), replayIds = emptySet()); position = 0f }
            }
            return
        }
        if (shuffleSuspended || queue.current == null) return
        if (!orderingRequested && (repeatState.value != 2 || (!playing && !shuffleBoundary))) {
            preparingShuffle = false
            if (shuffleBoundary) { shuffleBoundary = false; playing = false; ended = true }
            return
        }
        val kind = if (orderingRequested) {
            if (shuffleState.value) ShufflePreparationKind.REMAINDER else ShufflePreparationKind.ORIGINAL
        } else ShufflePreparationKind.PASS
        if (kind != ShufflePreparationKind.ORIGINAL && !historyReady && historyFailure != null) {
            if (orderingRequested) {
                disableFailedShuffle()
                notice = "Shuffle history could not be read. The current queue was kept."
            } else if (shuffleBoundary) {
                pause()
                notice = "Shuffle history could not be read. Playback remains paused."
            }
            return
        }
        if ((kind == ShufflePreparationKind.PASS && !membershipReady) ||
            (kind != ShufflePreparationKind.ORIGINAL && !historyReady)) { preparingShuffle = true; return }
        val original = kind == ShufflePreparationKind.ORIGINAL
        val scopeIds = if (original) emptySet() else
            (if (kind == ShufflePreparationKind.PASS) queue.entries + sourceEntries else queue.entries).mapTo(HashSet()) { it.trackId }
        val records = library.state.value.byId
        val input = ShufflePreparationInput(queue, kind,
            scopeIds.filterTo(HashSet()) { records[it]?.availability == Availability.AVAILABLE },
            if (original || (kind == ShufflePreparationKind.REMAINDER && !freshSelection)) emptyList() else historicalOrder,
            if (kind == ShufflePreparationKind.PASS) lastAudible else null,
            if (kind == ShufflePreparationKind.PASS) sourceEntries else queue.originalEntries,
            kind == ShufflePreparationKind.REMAINDER && freshSelection)
        shufflePreparation.prepare(input)
        preparingShuffle = shufflePreparation.result == null && shufflePreparation.failure == null
        if (shufflePreparation.failure != null) {
            preparingShuffle = false
            if (kind != ShufflePreparationKind.PASS) {
                orderingRequested = false
                disableFailedShuffle()
                notice = "Shuffle could not be prepared. The current queue was kept."
            } else if (shuffleBoundary) {
                shuffleSuspended = true
                playing = false
                notice = "The next shuffled pass could not be prepared. Press Play to retry."
            }
            return
        }
        val entries = shufflePreparation.result ?: return
        if (kind != ShufflePreparationKind.ORIGINAL && (kind == ShufflePreparationKind.REMAINDER || shuffleBoundary) &&
            (kind == ShufflePreparationKind.PASS || generatedOrder.isEmpty()) && pendingHistories.size >= 8 && collection.key !in pendingHistories) {
            if (kind == ShufflePreparationKind.REMAINDER) disableFailedShuffle() else pause()
            notice = "Shuffle history storage is unavailable. Free storage and try again."
            return
        }
        if (kind != ShufflePreparationKind.PASS) {
            orderingRequested = false
            if (kind == ShufflePreparationKind.REMAINDER && generatedOrder.isEmpty()) commitGenerated(entries)
            queue = queue.copy(entries = entries)
            invalidateShuffle()
            if (remainderAdvancePending && (playing || allowPausedAdvance)) {
                finishRemainderAdvance()
            }
            schedule()
        } else if (shuffleBoundary && !shuffleSuspended && (playing || allowPausedAdvance)) {
            shuffleBoundary = false
            if (entries.isEmpty()) {
                settingCollection = true
                queue = TrackQueue(originalEntries = sourceEntries)
                settingCollection = false
                playing = false
                ended = true
                notice = "No available tracks remain in this collection."
            } else {
                commitGenerated(entries)
                if (collection.kind !in setOf("manual", "snapshot")) retained = sourceEntries.map { it.trackId }
                settingCollection = true
                queue = TrackQueue(entries = entries, currentId = entries.first().id, originalEntries = sourceEntries)
                settingCollection = false
                position = 0f
                ended = false
                releaseEngine()
                invalidateShuffle()
                schedule()
            }
        }
    }

    private fun finishRemainderAdvance() {
        remainderAdvancePending = false
        if (queue.currentId == queue.stopAfterId ||
            (remainderAdvanceAutomatic && repeatState.value == 1 && queue.scheduledIds.isEmpty())) {
            val result = queue.complete(repeatState.value, available)
            queue = result.queue
            if (!result.playing) { playing = false; ended = !queue.resumeAfterStop }
            else { position = 0f; releaseEngine() }
            schedule()
        } else step(1)
    }

    private fun waitForShufflePass() {
        queue = queue.step(1, available) // Consume the final occurrence and unavailable tail once.
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
        allowPausedAdvance = false
        shuffleSuspended = false
        if (shuffleBoundary || remainderAdvancePending) {
            invalidateShuffle()
            playing = true
            schedule()
            return
        }
        if (queue.resumeAfterStop && orderingRequested) {
            playing = true
            remainderAdvancePending = true
            remainderAdvanceAutomatic = false
            schedule()
            return
        }
        if (queue.resumeAfterStop) {
            val next = queue.step(1, available)
            if (next.currentId == queue.currentId) {
                if (repeatState.value != 2) return
                if (shuffleState.value) { playing = true; waitForShufflePass(); return }
                val first = queue.entries.firstOrNull { available(it.trackId) } ?: return
                queue = queue.copy(currentId = first.id, resumeAfterStop = false, scheduledIds = emptyList(),
                    consumedIds = emptySet(), replayIds = emptySet())
            } else queue = next
            position = 0f
        } else if (ended) { position = 0f; ended = false; releaseEngine() }
        else if (durationMs() > 0 && position * 1000 >= durationMs()) { playing = true; complete(); return }
        playing = true
        schedule()
    }
    fun pause() { playAfterLoad = false; allowPausedAdvance = false; shuffleSuspended = true; invalidateShuffle(); playing = false; checkpoint(); host?.publish() }
    fun selectOccurrence(id: String) {
        val occurrence = queue.entries.firstOrNull { it.id == id } ?: return
        if (!available(occurrence.trackId)) { notice = "Track unavailable"; return }
        orderingRequested = false
        invalidateShuffle()
        if (id == queue.currentId) { play(); return }
        queue = queue.select(id).copy(stopAfterId = null)
        position = 0f
        playing = true
    }
    fun step(direction: Int) {
        if (queue.current == null) return
        if (direction < 0) { orderingRequested = false; remainderAdvancePending = false; invalidateShuffle() }
        if (direction > 0 && orderingRequested) {
            if (queue.stopAfterId == queue.currentId) queue = queue.copy(stopAfterId = null)
            remainderAdvancePending = true
            remainderAdvanceAutomatic = false
            allowPausedAdvance = !playing
            releaseEngine()
            schedule()
            return
        }
        if (direction < 0 && position > 3f) {
            position = 0f; releaseEngine(); schedule(); return
        }
        val old = queue
        var next = old.step(direction, available)
        if (direction > 0 && old.stopAfterId == old.currentId) next = next.copy(stopAfterId = null)
        if (next.currentId == old.currentId) {
            if (repeatState.value == 2) {
                if (direction > 0 && shuffleState.value) { queue = next; allowPausedAdvance = !playing; waitForShufflePass(); return }
                val entry = if (direction > 0) next.entries.firstOrNull { available(it.trackId) }
                    else next.entries.lastOrNull { available(it.trackId) }
                if (entry != null) next = next.copy(currentId = entry.id, resumeAfterStop = false, scheduledIds = emptyList(),
                    consumedIds = if (direction > 0) emptySet() else next.consumedIds,
                    replayIds = if (direction > 0) emptySet() else next.replayIds)
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
        else (library.state.value.tracksById[queue.current?.trackId]?.durationSeconds ?: 0) * 1000
    private fun reconcileEngine() {
        if (shuffleBoundary || remainderAdvancePending) return
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
        val record = library.state.value.byId[queue.current?.trackId] ?: return
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
            if (lastAudible != audible) { lastAudible = audible; if (!orderingRequested) invalidateShuffle(); schedule() }
            if (newStart) {
                newStart = false
                val track = library.state.value.tracksById[queue.current?.trackId]
                if (track != null) scope.launch {
                    runCatching { library.editPlaylists { it.playbackStarted(track, System.currentTimeMillis()) } }
                        .onFailure { warning = "Recently played could not be saved." }
                }
            }
        } catch (_: RuntimeException) { recover() }
    }
    private fun complete() {
        allowPausedAdvance = false
        if (prepared) positionStateInternal(durationMs() / 1000f)
        if (orderingRequested && queue.currentId != queue.stopAfterId &&
            (repeatState.value != 1 || queue.scheduledIds.isNotEmpty())) {
            remainderAdvancePending = true
            remainderAdvanceAutomatic = true
            releaseEngine()
            schedule()
            return
        }
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
        if (next == null) { queue = queue.step(1) { false }; playing = false; notice = "No remaining track could play." }
        else {
            queue = queue.select(next.id).copy(stopAfterId = queue.stopAfterId?.takeUnless { it == rejected }, scheduledIds = queue.scheduledIds - attempted)
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
            settingCollection = true
            bindCollection(ShuffleCollection(), emptyList())
            remainderAdvancePending = false
            queue = TrackQueue(); position = 0f; ended = false
            lastAudible = null
            pendingHistories.clear()
            settingCollection = false
            releaseEngine()
            PlaybackCheckpoint(DemoSession(queue, position, shuffleState.value, repeatState.value))
        }
        withContext(Dispatchers.IO) { writeMutex.withLock { store.save(empty); store.clearHistory(); store.save(empty) } }
    }
    fun hostReady(service: PlaybackHost) { host = service; schedule() }
    internal val isClosed get() = scope.coroutineContext[Job]?.isCancelled == true
    internal suspend fun awaitClosed() { scope.coroutineContext[Job]?.join() }
    internal fun close() { pause(); releaseEngine(); scope.cancel(); handler.removeCallbacks(reconcile) }
    fun hostDestroyed() { host = null; playing = false; releaseEngine(); checkpoint() }
}

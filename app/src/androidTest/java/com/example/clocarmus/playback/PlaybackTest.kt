@file:Suppress("DEPRECATION")
package com.example.clocarmus.playback

import android.content.*
import android.content.res.AssetFileDescriptor
import android.database.MatrixCursor
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.test.InstrumentationTestCase
import com.example.clocarmus.library.*
import com.example.clocarmus.playback.shuffle.*
import com.example.clocarmus.ui.player.*
import com.example.clocarmus.ui.startup.*
import kotlinx.coroutines.*
import java.io.File
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real decoder, SQLite, audio focus and atomic files, isolated from the user's library/session. */
class PlaybackTest : InstrumentationTestCase() {
    private lateinit var context: Context
    private lateinit var library: LocalLibrary
    private lateinit var controller: PlaybackController
    private lateinit var directory: File
    private lateinit var wav: File
    @Volatile private var sourceFailure: Exception? = null
    @Volatile private var scanGate: java.util.concurrent.CountDownLatch? = null
    @Volatile private var scanEntered: java.util.concurrent.CountDownLatch? = null
    @Volatile private var emptySource = false
    private var publications = 0
    private var rejectAudio = false
    private var openedAudio = 0
    private var shuffleCalls = 0
    private var shuffleGate: CompletableDeferred<Unit>? = null
    private var failShuffle = false
    override fun setUp() {
        super.setUp()
        val target = instrumentation.targetContext
        directory = File(target.filesDir, "playback-test").apply { deleteRecursively(); mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getFilesDir() = directory
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?) =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences("playback-test-$name", mode)
        }
        context.getSharedPreferences("onboarding", 0).edit().clear().commit()
        saveOnboarding(context.getSharedPreferences("onboarding", 0), OnboardingState(completed = true,
            folders = listOf(OnboardingFolder("content://test.documents/tree/music", "Music", "Test"))))
        wav = File(directory, "tone.wav")
        val samples = 8000 * 8
        val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { data.putShort((kotlin.math.sin(it * 2 * Math.PI * 440 / 8000) * 1200).toInt().toShort()) }
        wav.writeBytes(data.array())
        library = LocalLibrary(context, object : DocumentAccess {
            override fun children(tree: Uri, id: String) = MatrixCursor(arrayOf("id", "name", "mime", "size", "modified")).apply {
                scanEntered?.countDown()
                scanGate?.await(15, java.util.concurrent.TimeUnit.SECONDS)
                sourceFailure?.let { throw it }
                if (emptySource) return@apply
                // Deliberately differ from title order to exercise initial queue sorting.
                addRow(arrayOf<Any>("second", "Second.wav", "audio/wav", wav.length(), 1))
                addRow(arrayOf<Any>("first", "First.wav", "audio/wav", wav.length(), 1))
            }
            override suspend fun audio(document: AudioDocument, id: String) =
                TrackMenuEntry(document.name, "Test", "Test", id = id, durationSeconds = 8) to document.name
        })
        await { library.state.value.records.size == 2 && !library.state.value.summary.running }
        createController()
    }
    private fun main(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        if (::controller.isInitialized && controller.isClosed) runBlocking { controller.awaitClosed() }
        failure?.let { throw it }
    }
    private fun createController() {
        main {
            controller = PlaybackController(context, library, shuffleGenerator = { input ->
                shuffleCalls++
                val gate = shuffleGate
                gate?.await()
                if (failShuffle) error("Shuffle fixture failure")
                prepareShuffle(input)
            }) {
                openedAudio++
                if (rejectAudio) error("Decoder fixture failure")
                AssetFileDescriptor(ParcelFileDescriptor.open(wav, ParcelFileDescriptor.MODE_READ_ONLY), 0, wav.length())
            }
            controller.hostReady(object : PlaybackHost { override fun publish() { publications++ } })
        }
        await { controller.ready }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < deadline) {
            var done = false
            main { done = condition() }
            if (done) return
            Thread.sleep(25)
        }
        fail("Timed out waiting for playback state")
    }
    private fun select() = main {
        val ids = library.state.value.tracks.map { it.id }
        controller.queueState.value = TrackQueue.fromTracks(ids, ids.first())
        controller.play()
    }
    override fun tearDown() {
        scanGate?.countDown()
        main { controller.close() }
        runBlocking { library.close() }
        directory.deleteRecursively()
        context.getSharedPreferences("onboarding", 0).edit().clear().commit()
        super.tearDown()
    }
    private fun refreshAvailability(expected: Availability) {
        val version = library.state.value.version
        library.refresh()
        await {
            val snapshot = library.state.value
            snapshot.version > version && !snapshot.summary.running &&
                snapshot.records.size == 2 && snapshot.records.all { it.availability == expected }
        }
    }

    private fun setupConfiguration() = com.example.clocarmus.ui.startup.loadOnboarding(context.getSharedPreferences("onboarding", 0))

    private fun holdInitialScan(): OnboardingState {
        scanGate = java.util.concurrent.CountDownLatch(1)
        scanEntered = java.util.concurrent.CountDownLatch(1)
        val configuration = setupConfiguration().copy(exclusions = setOf("unused-exclusion"))
        saveOnboarding(context.getSharedPreferences("onboarding", 0), configuration)
        assertTrue(scanEntered!!.await(10, java.util.concurrent.TimeUnit.SECONDS))
        return configuration
    }

    fun testOnboardingCompletedScanSelectsAlphabeticalQueuePausedOnlyOnceAndRestores() {
        val expected = library.state.value.tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }).map { it.id }
        main { controller.initializeAfterOnboarding(setupConfiguration()) }
        await { controller.queueState.value.entries.size == 2 }
        val selected = controller.queueState.value
        assertEquals(expected, selected.entries.map { it.trackId })
        assertEquals(expected.first(), selected.current!!.trackId)
        assertEquals(0f, controller.positionState.value)
        assertFalse(controller.playingState.value)
        assertEquals(0, openedAudio)
        assertTrue(library.state.value.playlists.played.isEmpty())
        main { controller.initializeAfterOnboarding(setupConfiguration()) }
        assertEquals(selected, controller.queueState.value)
        await { PlaybackStore(context).load()?.session?.queue == selected }
        main { controller.close() }
        createController()
        main { controller.initializeAfterOnboarding(setupConfiguration()) }
        assertEquals(selected, controller.queueState.value)
        assertFalse(controller.playingState.value)
    }

    fun testOnboardingWaitsForScanThenSelectsPaused() {
        val configuration = holdInitialScan()
        main { controller.initializeAfterOnboarding(configuration) }
        assertTrue(controller.queueState.value.entries.isEmpty())
        scanGate!!.countDown()
        await { controller.queueState.value.entries.size == 2 }
        assertFalse(controller.playingState.value)
        assertEquals(0f, controller.positionState.value)
        assertEquals(0, openedAudio)
    }

    fun testOnboardingPendingSelectionCancelledByEvenAnAlreadyEmptyQueueClear() {
        val configuration = holdInitialScan()
        main {
            controller.initializeAfterOnboarding(configuration)
            controller.queueState.value = TrackQueue()
        }
        scanGate!!.countDown()
        await { library.completedScan.value?.configuration?.exclusions == configuration.exclusions }
        main { controller.initializeAfterOnboarding(configuration) }
        assertTrue(controller.queueState.value.entries.isEmpty())
    }

    fun testOnboardingPendingSelectionPreservesUserSelection() {
        val configuration = holdInitialScan()
        val id = library.state.value.tracks.last().id
        main {
            controller.initializeAfterOnboarding(configuration)
            controller.queueState.value = TrackQueue.fromTracks(listOf(id), id)
        }
        val expected = controller.queueState.value
        scanGate!!.countDown()
        await { library.completedScan.value?.configuration?.exclusions == configuration.exclusions }
        assertEquals(expected, controller.queueState.value)
    }

    fun testOnboardingPendingSelectionCancelledByPlayOnEmptyPlayer() {
        val configuration = holdInitialScan()
        main {
            controller.initializeAfterOnboarding(configuration)
            controller.play()
        }
        scanGate!!.countDown()
        await { library.completedScan.value?.configuration?.exclusions == configuration.exclusions }
        assertTrue(controller.queueState.value.entries.isEmpty())
        assertFalse(controller.playingState.value)
    }

    fun testOnboardingExistingQueueIsPreserved() {
        val id = library.state.value.tracks.last().id
        main {
            controller.queueState.value = TrackQueue.fromTracks(listOf(id), id)
            controller.initializeAfterOnboarding(setupConfiguration())
        }
        assertEquals(listOf(id), controller.queueState.value.entries.map { it.trackId })
    }

    fun testOnboardingEmptyScanNeverSelectsFromLaterImport() {
        emptySource = true
        val configuration = holdInitialScan()
        main { controller.initializeAfterOnboarding(configuration) }
        scanGate!!.countDown()
        await { library.completedScan.value?.configuration?.exclusions == configuration.exclusions }
        // Let the controller consume the terminal empty result before importing again.
        instrumentation.waitForIdleSync()
        emptySource = false
        library.refresh()
        await { library.state.value.tracks.all { it.available } && !library.state.value.summary.running }
        main { controller.initializeAfterOnboarding(configuration) }
        assertTrue(controller.queueState.value.entries.isEmpty())
        assertTrue(library.completedScan.value!!.records.none { it.availability == com.example.clocarmus.library.Availability.AVAILABLE })
    }

    fun testOnboardingSourceChangeCancelsPendingSelection() {
        val configuration = holdInitialScan()
        main { controller.initializeAfterOnboarding(configuration) }
        saveOnboarding(context.getSharedPreferences("onboarding", 0), configuration.copy(folders = emptyList()))
        await { library.sourceConfiguration.value?.folders?.isEmpty() == true }
        scanGate!!.countDown()
        await { library.completedScan.value?.configuration?.folders?.isEmpty() == true }
        saveOnboarding(context.getSharedPreferences("onboarding", 0), configuration)
        await { library.completedScan.value?.configuration?.folders == configuration.folders }
        assertTrue(controller.queueState.value.entries.isEmpty())
    }

    fun testOnboardingSkipDecisionSurvivesInterruptedLoading() {
        val configuration = setupConfiguration().copy(
            step = com.example.clocarmus.ui.startup.WelcomePage.Loading, initialSelectionSkipped = true)
        saveOnboarding(context.getSharedPreferences("onboarding", 0), configuration)
        assertTrue(setupConfiguration().initialSelectionSkipped)
        main { controller.initializeAfterOnboarding(setupConfiguration()) }
        assertTrue(controller.queueState.value.entries.isEmpty())
    }

    fun testOnboardingNoFoldersCannotBeArmedByLaterCompletion() {
        main {
            controller.initializeAfterOnboarding(setupConfiguration().copy(folders = emptyList()))
            controller.initializeAfterOnboarding(setupConfiguration())
        }
        assertTrue(controller.queueState.value.entries.isEmpty())
    }

    fun testSimulatedDriveDisconnectionRestoresShufflePausedAndReconnectNeedsPlay() =
        verifySourceLossRecovery(IOException("Simulated removable drive disconnected"), Availability.DISCONNECTED)

    fun testSimulatedStoragePermissionLossRestoresPausedAndRegrantNeedsPlay() =
        verifySourceLossRecovery(SecurityException("Simulated persisted grant loss"), Availability.ACCESS_LOST)

    private fun verifySourceLossRecovery(failure: Exception, expected: Availability) {
        select()
        await { controller.positionState.value > .25f }
        main { controller.toggleShuffle() }
        await { !controller.preparingShuffle && controller.shuffleState.value }
        val queue = controller.queueState.value
        sourceFailure = failure
        refreshAvailability(expected)
        await { !controller.playingState.value }
        val position = controller.positionState.value
        val opens = openedAudio
        main {
            assertEquals(queue.entries, controller.queueState.value.entries)
            assertEquals(queue.currentId, controller.queueState.value.currentId)
            assertEquals(queue.consumedIds, controller.queueState.value.consumedIds)
            assertTrue(controller.shuffleState.value)
            controller.play()
            assertFalse(controller.playingState.value)
            assertEquals("Track unavailable", controller.notice)
        }
        assertEquals(opens, openedAudio)
        await { PlaybackStore(context).load()?.session?.let { it.queue.currentId == queue.currentId && kotlin.math.abs(it.position - position) < .2f } == true }
        main { controller.close() }
        createController()
        main {
            assertFalse(controller.playingState.value)
            assertTrue(controller.shuffleState.value)
            assertEquals(queue.entries, controller.queueState.value.entries)
            assertEquals(queue.currentId, controller.queueState.value.currentId)
            assertEquals(queue.consumedIds, controller.queueState.value.consumedIds)
            assertEquals(position, controller.positionState.value, .2f)
        }
        sourceFailure = null
        refreshAvailability(Availability.AVAILABLE)
        Thread.sleep(300)
        main {
            assertFalse(controller.playingState.value)
            assertEquals(queue.currentId, controller.queueState.value.currentId)
            assertEquals(position, controller.positionState.value, .2f)
            controller.play()
        }
        await { controller.playingState.value && controller.positionState.value > position + .15f }
    }

    fun testNextWithDisconnectedDriveTerminatesWithoutOpeningUnavailableAudio() {
        select()
        await { controller.positionState.value > .25f }
        val entries = controller.queueState.value.entries
        sourceFailure = IOException("Simulated removable drive disconnected")
        refreshAvailability(Availability.DISCONNECTED)
        await { !controller.playingState.value }
        val opens = openedAudio
        main { controller.step(1) }
        Thread.sleep(300)
        main {
            assertFalse(controller.playingState.value)
            assertEquals(entries, controller.queueState.value.entries)
            assertTrue(controller.queueState.value.consumedIds.containsAll(entries.map { it.id }))
        }
        assertEquals(opens, openedAudio)
        sourceFailure = null
        refreshAvailability(Availability.AVAILABLE)
        Thread.sleep(300)
        main { assertFalse(controller.playingState.value) }
    }

    fun testDecoderProgressPauseSeekResumeAndQueueReorder() {
        select()
        await { controller.positionState.value > .5f && library.state.value.playlists.played.isNotEmpty() }
        val starts = library.state.value.playlists.played
        main {
            controller.pause()
            controller.positionState.value = 3f
            val queue = controller.queueState.value
            controller.queueState.value = queue.reorder(queue.entries.map { it.id }, queue.currentId!!, 1)
        }
        Thread.sleep(500)
        main { assertFalse(controller.playingState.value); assertEquals(3f, controller.positionState.value, .2f); controller.play() }
        await { controller.positionState.value > 3.5f }
        assertTrue(publications > 0)
        assertEquals(starts, library.state.value.playlists.played)
    }
    fun testScheduledCompletionStopsAndPlayContinuesWithoutRepeating() {
        select()
        await { controller.positionState.value > .25f }
        var current: String? = null
        main {
            current = controller.queueState.value.currentId
            controller.queueState.value = controller.queueState.value.copy(stopAfterId = current)
            controller.repeatState.value = 1
            controller.positionState.value = 8f
        }
        await { !controller.playingState.value && controller.queueState.value.resumeAfterStop }
        main { assertNull(controller.queueState.value.stopAfterId); controller.play() }
        await { controller.playingState.value && controller.queueState.value.currentId != current && controller.positionState.value > .25f }
    }
    fun testPausedSeekToEndAndFreshControllerRestoreStayPaused() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.pause(); controller.positionState.value = 8f }
        await { runCatching { PlaybackStore(context).load()?.session?.position == 8f }.getOrDefault(false) }
        main { assertFalse(controller.playingState.value); controller.close() }
        createController()
        main { assertEquals(8f, controller.positionState.value); assertFalse(controller.playingState.value); controller.play() }
        await { controller.queueState.value.currentIndex == 1 && controller.positionState.value > .25f }
    }
    fun testCompetingFocusPausesAndFocusGainDoesNotResume() {
        select()
        await { controller.positionState.value > .25f }
        val audio = context.getSystemService(AudioManager::class.java)
        val listener = AudioManager.OnAudioFocusChangeListener { }
        main { audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) }
        await { !controller.playingState.value }
        main { audio.abandonAudioFocus(listener) }
        Thread.sleep(400)
        main { assertFalse(controller.playingState.value) }
    }
    fun testArtworkTimingTracksTransportAndExplicitSelectionWhilePaused() {
        select()
        await { controller.positionState.value > .25f }
        main {
            assertEquals(0L, controller.artworkSelection.eligibleAtMs)
            controller.pause()
            controller.step(1)
            val timing = controller.artworkSelection
            assertTrue(timing.eligibleAtMs > android.os.SystemClock.elapsedRealtime())
            controller.positionState.value = 4f
            controller.step(-1) // Restarts this track without a new selection.
            assertEquals(timing, controller.artworkSelection)
            controller.play()
            controller.pause()
            assertEquals(timing, controller.artworkSelection)
            controller.selectOccurrence(controller.queueState.value.currentId!!)
            assertEquals(0L, controller.artworkSelection.eligibleAtMs)
            controller.pause()
            controller.step(-1)
            assertTrue(controller.artworkSelection.eligibleAtMs > android.os.SystemClock.elapsedRealtime())
            val ids = library.state.value.tracks.map { it.id }
            controller.selectCollection(ids, ids.last())
            assertEquals(0L, controller.artworkSelection.eligibleAtMs)
        }
    }

    fun testArtworkTimingPreservesCauseAcrossPendingShuffleOrdering() {
        val gate = CompletableDeferred<Unit>()
        shuffleGate = gate
        main {
            val ids = library.state.value.tracks.map { it.id }
            controller.shuffleState.value = true
            controller.selectCollection(ids, ids.first())
        }
        await { controller.preparingShuffle && controller.positionState.value > .25f }
        var first: String? = null
        main {
            first = controller.queueState.value.currentId
            controller.pause()
            controller.step(1)
            gate.complete(Unit)
        }
        await { controller.queueState.value.currentId != first }
        main {
            assertFalse(controller.playingState.value)
            assertTrue(controller.artworkSelection.eligibleAtMs > android.os.SystemClock.elapsedRealtime())
            controller.repeatState.value = 2
            controller.play()
        }
        await { controller.positionState.value > .25f }
        var last: String? = null
        main { last = controller.queueState.value.currentId; controller.positionState.value = 8f }
        await { controller.queueState.value.currentId != last && controller.queueState.value.currentIndex == 0 }
        main { assertEquals(0L, controller.artworkSelection.eligibleAtMs) }
    }

    fun testNaturalCompletionCancelsTransportArtworkWaitIncludingRepeatAllWrap() {
        select()
        await { controller.positionState.value > .25f }
        main {
            controller.repeatState.value = 2
            controller.step(1)
            assertTrue(controller.artworkSelection.eligibleAtMs > android.os.SystemClock.elapsedRealtime())
        }
        await { controller.queueState.value.currentIndex == 1 && controller.positionState.value > .25f }
        main { controller.positionState.value = 6.5f }
        // Let the decoder finish within the pending three-second artwork wait.
        await { controller.queueState.value.currentIndex == 0 }
        main { assertEquals(0L, controller.artworkSelection.eligibleAtMs) }
    }

    fun testPreviousThresholdAndRepeatAllTransportBoundary() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.pause(); controller.step(1); controller.positionState.value = 4f; controller.step(-1) }
        main { assertEquals(1, controller.queueState.value.currentIndex); assertEquals(0f, controller.positionState.value) }
        main { controller.step(-1); assertEquals(0, controller.queueState.value.currentIndex) }
        main {
            controller.repeatState.value = 2
            val consumed = controller.queueState.value.consumedIds
            controller.step(-1)
            assertEquals(1, controller.queueState.value.currentIndex)
            assertEquals(consumed, controller.queueState.value.consumedIds)
        }
        main { controller.step(1); assertEquals(0, controller.queueState.value.currentIndex); assertFalse(controller.playingState.value) }
    }
    fun testAllDecoderFailuresStopEvenWithRepeatOne() {
        rejectAudio = true
        select()
        main { controller.repeatState.value = 1 }
        await { !controller.playingState.value && controller.queueState.value.currentIndex == 1 }
        assertTrue(library.state.value.playlists.played.isEmpty())
        Thread.sleep(250)
        assertEquals(2, openedAudio)
    }
    fun testNaturalFinalCompletionRetainsSelectionAndExplicitPlayRestarts() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.step(1); controller.positionState.value = 7.5f }
        await { !controller.playingState.value && controller.queueState.value.currentIndex == 1 }
        main { controller.selectOccurrence(controller.queueState.value.currentId!!) }
        await { controller.playingState.value && controller.positionState.value > .25f && controller.positionState.value < 4f }
    }
    fun testCorruptCommittedSnapshotRestoresLastValidRecoveryPaused() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.pause() }
        var expected = TrackQueue()
        main { expected = controller.queueState.value }
        await { PlaybackStore(context).load()?.session?.queue == expected }
        main { controller.close() }
        File(directory, "playback-v1.json").writeText("corrupt checkpoint")
        createController()
        main {
            assertFalse(controller.playingState.value)
            assertEquals(expected.entries, controller.queueState.value.entries)
            assertNotNull(controller.warning)
        }
    }

    fun testNoValidRecoveryRestoresExplainedEmptyPausedPlayer() {
        await { runCatching { PlaybackStore(context).load() != null }.getOrDefault(false) }
        main { controller.close() }
        File(directory, "playback-v1.json").writeText("corrupt checkpoint")
        File(directory, "playback-recovery.json").writeText("corrupt recovery")
        createController()
        main { assertFalse(controller.playingState.value); assertTrue(controller.queueState.value.entries.isEmpty()); assertNotNull(controller.warning) }
        Thread.sleep(200)
        assertEquals("corrupt checkpoint", File(directory, "playback-v1.json").readText())
    }

    fun testResetInvalidatesQueuedSavesBeforeDurableClear() {
        select()
        await { controller.positionState.value > .25f }
        runBlocking { controller.resetDurably() }
        Thread.sleep(200)
        val snapshot = PlaybackStore(context).load()!!
        assertTrue(snapshot.session.queue.entries.isEmpty())
        assertEquals(0f, snapshot.session.position)
        main { assertFalse(controller.playingState.value) }
    }
    fun testAtomicSnapshotRoundTripAndInterruptedWriteRecovery() {
        val queue = TrackQueue.fromTracks(listOf("a", "a", "b"), "a").schedule("b", true)
        val original = PlaybackCheckpoint(DemoSession(queue, 2.5f, true, 2))
        val store = PlaybackStore(context)
        // Wait for startup's initial checkpoint before testing the file independently.
        await { File(directory, "playback-v1.json").exists() }
        main { controller.close() }
        store.save(original)
        assertEquals(original, store.load())
        val atomic = android.util.AtomicFile(File(directory, "playback-v1.json"))
        val stream = atomic.startWrite()
        stream.write("incomplete".toByteArray())
        stream.close() // Simulates loss before finishWrite; load must recover the previous snapshot.
        assertEquals(original, store.load())
    }
    fun testShuffleTogglePreservesPausedOccurrencePositionSchedulingAndMarker() {
        select()
        await { controller.positionState.value > .25f }
        var before = TrackQueue()
        main {
            controller.pause()
            controller.positionState.value = 2f
            val q = controller.queueState.value
            controller.queueState.value = q.schedule(q.entries.last().trackId, true)
            before = controller.queueState.value
            controller.toggleShuffle()
        }
        await { shuffleCalls > 0 && !controller.preparingShuffle }
        main {
            assertTrue(controller.shuffleState.value)
            assertFalse(controller.playingState.value)
            assertEquals(before.currentId, controller.queueState.value.currentId)
            assertEquals(before.scheduledIds, controller.queueState.value.scheduledIds)
            assertEquals(before.stopAfterId, controller.queueState.value.stopAfterId)
            assertEquals(before.entries, controller.queueState.value.entries)
            assertEquals(2f, controller.positionState.value, .2f)
        }
        assertEquals(1, openedAudio)
    }

    fun testBrowserSelectionStartsAudioWhileShufflePreparationIsPending() {
        val gate = CompletableDeferred<Unit>()
        shuffleGate = gate
        main {
            val ids = library.state.value.tracks.map { it.id }
            controller.shuffleState.value = true
            controller.selectCollection(ids, ids.last())
        }
        await { controller.preparingShuffle && controller.positionState.value > .5f }
        var current: String? = null
        main {
            current = controller.queueState.value.currentId
            assertEquals(library.state.value.tracks.last().id, controller.queueState.value.entries.first().trackId)
            gate.complete(Unit)
        }
        await { !controller.preparingShuffle }
        main {
            assertEquals(current, controller.queueState.value.currentId)
            assertTrue(controller.positionState.value >= .5f)
            assertTrue(controller.playingState.value)
        }
        assertEquals(1, openedAudio)
    }

    fun testShuffleBoundaryWaitPauseAndExplicitRetry() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.toggleShuffle() }
        await { shuffleCalls > 0 && !controller.preparingShuffle }
        val gate = CompletableDeferred<Unit>()
        var finalId: String? = null
        main {
            shuffleGate = gate
            controller.selectOccurrence(controller.queueState.value.entries.last().id)
            finalId = controller.queueState.value.currentId
            controller.repeatState.value = 2
        }
        await { controller.preparingShuffle && controller.positionState.value > .25f }
        val openedBeforeBoundary = openedAudio
        main { controller.positionState.value = 8f }
        Thread.sleep(350)
        main {
            assertTrue(controller.playingState.value)
            assertEquals(finalId, controller.queueState.value.currentId)
            controller.pause()
            gate.complete(Unit)
        }
        Thread.sleep(350)
        main {
            assertFalse(controller.playingState.value)
            assertEquals(finalId, controller.queueState.value.currentId)
        }
        assertEquals(openedBeforeBoundary, openedAudio)
        await { PlaybackStore(context).load()?.waitingForShuffle == true }
        main { controller.close() }
        val calls = shuffleCalls
        createController()
        Thread.sleep(250)
        main {
            assertFalse(controller.playingState.value)
            assertEquals(finalId, controller.queueState.value.currentId)
        }
        assertEquals(calls, shuffleCalls)
        main { controller.play() }
        await { controller.queueState.value.currentId != finalId && controller.positionState.value > .25f }
    }

    fun testReplacingQueueInvalidatesPreparedPassAndPreservesNewScope() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.toggleShuffle() }
        await { shuffleCalls > 0 && !controller.preparingShuffle }
        val gate = CompletableDeferred<Unit>()
        main { shuffleGate = gate; controller.repeatState.value = 2 }
        await { controller.preparingShuffle }
        var selected = ""
        main {
            shuffleGate = null
            selected = library.state.value.tracks.last().id
            controller.selectCollection(listOf(selected), selected)
            gate.complete(Unit)
        }
        await { !controller.preparingShuffle && controller.positionState.value > .25f }
        main {
            assertEquals(listOf(selected), controller.queueState.value.entries.map { it.trackId })
            assertTrue(controller.playingState.value)
        }
    }

    fun testScheduledStopOverridesPreparedShufflePassAndRestoreDoesNotReshuffle() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.toggleShuffle(); controller.repeatState.value = 2 }
        await { shuffleCalls > 0 && !controller.preparingShuffle }
        main {
            val id = controller.queueState.value.currentId
            controller.queueState.value = controller.queueState.value.copy(stopAfterId = id)
            controller.positionState.value = 8f
        }
        await { !controller.playingState.value && controller.queueState.value.resumeAfterStop }
        var saved = TrackQueue()
        main { saved = controller.queueState.value }
        await { PlaybackStore(context).load()?.session?.queue == saved }
        main { controller.close() }
        val calls = shuffleCalls
        createController()
        Thread.sleep(250)
        main {
            assertEquals(saved, controller.queueState.value)
            assertFalse(controller.playingState.value)
            assertTrue(controller.shuffleState.value)
        }
        assertEquals(calls, shuffleCalls)
    }

    fun testFailedPassStopsAtBoundaryAndExplicitPlayCanRetry() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.toggleShuffle() }
        await { shuffleCalls > 0 && !controller.preparingShuffle }
        var finalId: String? = null
        main {
            failShuffle = true
            controller.selectOccurrence(controller.queueState.value.entries.last().id)
            finalId = controller.queueState.value.currentId
            controller.repeatState.value = 2
        }
        await { controller.positionState.value > .25f }
        main { controller.positionState.value = 8f }
        await { !controller.playingState.value && controller.notice?.contains("could not be prepared") == true }
        main {
            assertTrue(controller.shuffleState.value)
            assertEquals(finalId, controller.queueState.value.currentId)
            failShuffle = false
            controller.play()
        }
        await { controller.queueState.value.currentId != finalId && controller.positionState.value > .25f }
    }

    fun testPausedNextAcrossShuffleBoundaryChangesSelectionWithoutStartingAudio() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.toggleShuffle() }
        await { shuffleCalls > 0 && !controller.preparingShuffle }
        val opens = openedAudio
        main {
            controller.pause()
            controller.repeatState.value = 2
            controller.step(1)
            assertEquals(1, controller.queueState.value.currentIndex)
            controller.step(1)
        }
        await { controller.queueState.value.currentIndex == 0 && !controller.preparingShuffle }
        main {
            assertFalse(controller.playingState.value)
            assertEquals(0f, controller.positionState.value)
            assertTrue(controller.artworkSelection.eligibleAtMs > android.os.SystemClock.elapsedRealtime())
        }
        assertEquals(opens, openedAudio)
    }

    fun testExplicitQueueSelectionCancelsPendingRemainderOrdering() {
        select()
        await { controller.positionState.value > .25f }
        val gate = CompletableDeferred<Unit>()
        var original = TrackQueue()
        main {
            val ids = library.state.value.tracks.map { it.id }
            controller.pause()
            controller.queueState.value = TrackQueue.fromTracks(ids + ids, ids.first())
            original = controller.queueState.value
            shuffleGate = gate
            controller.toggleShuffle()
        }
        await { controller.preparingShuffle }
        main {
            controller.selectOccurrence(original.entries[1].id)
            gate.complete(Unit)
        }
        await { controller.positionState.value > .25f }
        main {
            assertEquals(original.entries, controller.queueState.value.entries)
            assertEquals(original.entries[1].id, controller.queueState.value.currentId)
            assertTrue(controller.playingState.value)
        }
    }

    fun testRepeatAllRefreshesPlaylistAndDropsTemporaryQueueAdditions() {
        val tracks = library.state.value.tracks
        val a = tracks.first().id
        val b = tracks.last().id
        runBlocking { library.editPlaylists { it.copy(custom = listOf(SavedPlaylist("scope", "Scope", listOf(a)))) } }
        main {
            controller.shuffleState.value = true
            controller.selectCollection(listOf(a), a, ShuffleCollection(kind = "playlist", token = "scope"))
            controller.repeatState.value = 2
        }
        await { shuffleCalls > 0 && !controller.preparingShuffle && controller.positionState.value > .25f }
        main { controller.queueState.value = controller.queueState.value.append(a) }
        runBlocking { library.editPlaylists { it.copy(custom = listOf(SavedPlaylist("scope", "Scope", listOf(b)))) } }
        main { controller.step(1) }
        await { controller.positionState.value > .25f }
        main { controller.positionState.value = 8f }
        await { controller.queueState.value.entries.map { it.trackId } == listOf(b) && controller.positionState.value > .25f }
        main { assertTrue(controller.playingState.value) }
        val opened = openedAudio
        runBlocking { library.editPlaylists { it.copy(custom = emptyList()) } }
        main { controller.positionState.value = 8f }
        await { openedAudio > opened && controller.positionState.value > .25f && controller.positionState.value < 4f }
        main { assertEquals(listOf(b), controller.queueState.value.entries.map { it.trackId }) }
    }

    fun testEmptyLivePlaylistStopsAtBoundaryWithoutReusingOldSnapshot() {
        val id = library.state.value.tracks.first().id
        runBlocking { library.editPlaylists { it.copy(custom = listOf(SavedPlaylist("scope", "Scope", listOf(id)))) } }
        main {
            controller.shuffleState.value = true
            controller.selectCollection(listOf(id), id, ShuffleCollection(kind = "playlist", token = "scope"))
            controller.repeatState.value = 2
        }
        await { shuffleCalls > 0 && !controller.preparingShuffle && controller.positionState.value > .25f }
        runBlocking { library.editPlaylists { it.copy(custom = listOf(SavedPlaylist("scope", "Scope"))) } }
        main { controller.positionState.value = 8f }
        await { !controller.playingState.value && controller.notice?.contains("No available tracks") == true }
    }

    fun testConsumptionOriginalOrderAndGeneratedHistorySurviveRestoration() {
        val ids = library.state.value.tracks.map { it.id }
        val source = ShuffleCollection(kind = "all")
        main {
            controller.shuffleState.value = true
            controller.selectCollection(ids, ids.first(), source)
        }
        await { shuffleCalls > 0 && !controller.preparingShuffle && controller.positionState.value > .25f }
        main { controller.pause(); controller.step(1); controller.step(-1) }
        var expected = TrackQueue()
        main { expected = controller.queueState.value }
        assertFalse(expected.consumedIds.isEmpty())
        await { PlaybackStore(context).load()?.session?.queue == expected }
        val saved = PlaybackStore(context).load()!!
        assertEquals(source.key, saved.shuffleSession!!.collection.key)
        assertTrue(saved.shuffleSession!!.generated.isNotEmpty())
        main { controller.close() }
        createController()
        main {
            assertEquals(expected, controller.queueState.value)
            assertFalse(controller.playingState.value)
            controller.toggleShuffle()
            assertEquals(ids, controller.queueState.value.entries.map { it.trackId })
        }
        assertEquals(saved.shuffleSession!!.generated, PlaybackStore(context).loadHistory(source.key))
    }

    fun testHistoryIsLoadedPerCollectionAfterSwitchingAndIsNotReplacedByToggle() {
        val ids = library.state.value.tracks.map { it.id }
        val first = ShuffleCollection(kind = "all")
        main { controller.shuffleState.value = true; controller.selectCollection(ids, ids.first(), first) }
        await { shuffleCalls > 0 && !controller.preparingShuffle && controller.positionState.value > .25f }
        await { PlaybackStore(context).loadHistory(first.key).isNotEmpty() }
        val history = PlaybackStore(context).loadHistory(first.key)
        main { controller.toggleShuffle(); controller.toggleShuffle() }
        await { !controller.preparingShuffle }
        main { controller.selectCollection(ids, ids.last(), ShuffleCollection(kind = "snapshot")) }
        await { controller.queueState.value.current?.trackId == ids.last() && !controller.preparingShuffle }
        assertEquals(history, PlaybackStore(context).loadHistory(first.key))
        main { controller.selectCollection(ids, ids.first(), first) }
        await { controller.queueState.value.current?.trackId == ids.first() && !controller.preparingShuffle }
        assertEquals(history.toSet(), PlaybackStore(context).loadHistory(first.key).toSet())
    }

    fun testVersionLinkedCursorWritesKeepHundredThousandEntryStructureUnchanged() {
        main { controller.close() }
        val entries = (0 until 100_000).map { QueueOccurrence("track:$it", "occ:$it") }
        val queue = TrackQueue(entries, entries.first().id)
        val metadata = ShuffleSession(ShuffleCollection(token = "benchmark"), entries.map { it.trackId }, entries.map { it.trackId })
        val original = PlaybackCheckpoint(DemoSession(queue, 1f, true, 2), shuffleSession = metadata)
        var structureWrites = 0
        val store = PlaybackStore(context) { if (it.name.startsWith("playback-structure-")) structureWrites++ }
        val start = android.os.SystemClock.elapsedRealtime()
        store.save(original)
        val structuralMillis = android.os.SystemClock.elapsedRealtime() - start
        val structure = org.json.JSONObject(File(directory, "playback-v1.json").readText()).getString("structure")
        val latest = File(directory, "playback-structure-$structure.json")
        val writes = structureWrites
        val modified = latest.lastModified()
        val next = original.copy(session = original.session.copy(queue = queue.copy(consumedIds = entries.take(50_000).mapTo(HashSet()) { it.id }), position = 3f))
        val cursorStart = android.os.SystemClock.elapsedRealtime()
        store.save(next)
        store.save(next.copy(session = next.session.copy(position = 4f)))
        val cursorMillis = android.os.SystemClock.elapsedRealtime() - cursorStart
        assertEquals(writes, structureWrites)
        assertEquals(modified, latest.lastModified())
        assertTrue(File(directory, "playback-v1.json").length() < 100_000)
        val restored = store.load()!!
        assertEquals(4f, restored.session.position)
        assertEquals(50_000, restored.session.queue.consumedIds.size)
        assertEquals(entries, restored.session.queue.entries)
        val stats = "100k structure=${structuralMillis}ms; two cursors=${cursorMillis}ms; cursor=${File(directory, "playback-v1.json").length()} bytes"
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$stats\n") })
        for ((name, ids) in listOf("unique" to LongArray(100_000) { it.toLong() },
            "identical" to LongArray(100_000) { 1 }, "minority" to LongArray(100_000) { if (it == 99_999) 2 else 1 })) {
            val sampleStart = android.os.SystemClock.elapsedRealtime()
            val order = DataInputStream(FileInputStream("/dev/urandom").buffered()).use {
                runBlocking { ShuffleSampler(StreamShuffleRandom(it)).sample(ids, ids, ids.last()) }
            }
            val seen = BooleanArray(ids.size)
            order.forEach { assertTrue(it in ids.indices && !seen[it]); seen[it] = true }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n100k $name sampling=${android.os.SystemClock.elapsedRealtime() - sampleStart}ms\n") })
        }
    }

    fun testDamagedNewStructureFallsBackToPreviousLinkedGeneration() {
        main { controller.close() }
        val first = PlaybackCheckpoint(DemoSession(TrackQueue.fromTracks(listOf("a", "b"), "a"), 2f, true, 2))
        val second = PlaybackCheckpoint(DemoSession(TrackQueue.fromTracks(listOf("c", "d"), "c"), 3f, true, 2))
        val store = PlaybackStore(context)
        store.save(first)
        store.save(second)
        val hash = org.json.JSONObject(File(directory, "playback-v1.json").readText()).getString("structure")
        File(directory, "playback-structure-$hash.json").writeText("damaged")
        File(directory, "playback-structure-$hash.recovery").writeText("damaged")
        assertEquals(first, store.load())
        assertTrue(store.recovered)
    }

    fun testInjectedWriteFailureRetainsLastValidGenerationAndRetryCommits() {
        main { controller.close() }
        var reject = false
        val store = PlaybackStore(context) { if (reject && it.name == "playback-v1.json") throw IOException("storage full fixture") }
        val first = PlaybackCheckpoint(DemoSession(TrackQueue.fromTracks(listOf("a"), "a"), 2f, false, 0))
        val second = PlaybackCheckpoint(DemoSession(TrackQueue.fromTracks(listOf("b"), "b"), 3f, true, 2))
        store.save(first)
        reject = true
        try { store.save(second); fail("Expected write failure") } catch (_: IOException) { }
        assertEquals(first, store.load())
        reject = false
        store.save(second)
        assertEquals(second, store.load())
    }

    fun testManualCollectionRetainsEditedDuplicateCountsAcrossRepeatAll() {
        val ids = library.state.value.tracks.map { it.id }
        main {
            controller.shuffleState.value = true
            controller.selectCollection(ids + ids, ids.first(), ShuffleCollection(token = "manual-test"))
        }
        await { shuffleCalls > 0 && !controller.preparingShuffle && controller.positionState.value > .25f }
        main {
            val q = controller.queueState.value
            val remove = q.entries.drop(1).first { it.trackId == ids.first() }
            controller.queueState.value = q.remove(remove.id) { true }
            controller.selectOccurrence(controller.queueState.value.entries.last().id)
            controller.repeatState.value = 2
        }
        await { controller.positionState.value > .25f }
        val opened = openedAudio
        main { controller.positionState.value = 8f }
        await { openedAudio > opened && controller.positionState.value > .25f && controller.positionState.value < 4f }
        main {
            assertEquals(1, controller.queueState.value.entries.count { it.trackId == ids.first() })
            assertEquals(2, controller.queueState.value.entries.count { it.trackId == ids.last() })
        }
    }

    fun testInitialOrderWaitsAtCompletionAndRestoresPendingAdvancePaused() {
        val gate = CompletableDeferred<Unit>()
        shuffleGate = gate
        main {
            val ids = library.state.value.tracks.map { it.id }
            controller.shuffleState.value = true
            controller.selectCollection(ids, ids.first())
        }
        await { controller.preparingShuffle && controller.positionState.value > .25f }
        var first: String? = null
        main { first = controller.queueState.value.currentId; controller.positionState.value = 8f }
        Thread.sleep(250)
        main {
            assertEquals(first, controller.queueState.value.currentId)
            assertTrue(controller.playingState.value)
            assertEquals(1, openedAudio)
            controller.pause()
        }
        await { PlaybackStore(context).load()?.waitingForRemainder == true }
        main { controller.close(); gate.complete(Unit) }
        val calls = shuffleCalls
        createController()
        Thread.sleep(200)
        main { assertFalse(controller.playingState.value); assertEquals(first, controller.queueState.value.currentId) }
        assertEquals(calls, shuffleCalls)
        main { controller.play() }
        await { controller.queueState.value.currentId != first && controller.positionState.value > .25f }
    }

    fun testRepeatOneOverridesAnInitialOrderingWait() {
        val gate = CompletableDeferred<Unit>()
        shuffleGate = gate
        main {
            val ids = library.state.value.tracks.map { it.id }
            controller.shuffleState.value = true
            controller.selectCollection(ids, ids.first())
        }
        await { controller.preparingShuffle && controller.positionState.value > .25f }
        var first: String? = null
        main { first = controller.queueState.value.currentId; controller.positionState.value = 8f }
        Thread.sleep(150)
        main { controller.repeatState.value = 1; gate.complete(Unit) }
        await { openedAudio >= 2 && controller.positionState.value > .25f && controller.positionState.value < 4f }
        main { assertEquals(first, controller.queueState.value.currentId); controller.step(1) }
        await { controller.queueState.value.currentId != first && controller.positionState.value > .25f }
    }

    fun testScheduledStopBeforeInitialOrderIsReadyKeepsPlayContinuationWaiting() {
        val gate = CompletableDeferred<Unit>()
        shuffleGate = gate
        main {
            val ids = library.state.value.tracks.map { it.id }
            controller.shuffleState.value = true
            controller.selectCollection(ids, ids.first())
        }
        await { controller.preparingShuffle && controller.positionState.value > .25f }
        var first: String? = null
        main {
            first = controller.queueState.value.currentId
            controller.queueState.value = controller.queueState.value.copy(stopAfterId = first)
            controller.positionState.value = 8f
        }
        await { !controller.playingState.value && controller.queueState.value.resumeAfterStop }
        main { controller.play() }
        Thread.sleep(150)
        main {
            assertEquals(first, controller.queueState.value.currentId)
            assertEquals(1, openedAudio)
            gate.complete(Unit)
        }
        await { controller.queueState.value.currentId != first && controller.positionState.value > .25f }
        main { assertEquals(0L, controller.artworkSelection.eligibleAtMs) }
    }

    fun testCollectionHistoryChecksumsRecoveryAndRevisionOrdering() {
        main { controller.close() }
        val store = PlaybackStore(context)
        store.saveHistory("one", listOf("a", "b"), 10)
        store.saveHistory("two", listOf("c", "d"), 11)
        store.saveHistory("one", listOf("b", "a"), 20)
        store.saveHistory("one", listOf("obsolete"), 15)
        assertEquals(ShuffleHistory(listOf("b", "a"), 20), store.loadHistoryRecord("one"))
        val file = File(directory, "shuffle-history").listFiles()!!.first {
            it.extension == "json" && org.json.JSONObject(it.readText()).getString("key") == "one"
        }
        val damaged = org.json.JSONObject(file.readText()).put("order", org.json.JSONArray(listOf("q", "a")))
        file.writeText(damaged.toString())
        assertEquals(ShuffleHistory(listOf("a", "b"), 10), store.loadHistoryRecord("one"))
        assertEquals(ShuffleHistory(listOf("c", "d"), 11), store.loadHistoryRecord("two"))
        store.saveHistory("one", listOf("b", "a"), 21)
        assertEquals(ShuffleHistory(listOf("b", "a"), 21), store.loadHistoryRecord("one"))
    }

    fun testValidJsonCursorDamageIsDetectedByChecksum() {
        main { controller.close() }
        val q = TrackQueue.fromTracks(listOf("a", "b"), "a")
        val first = PlaybackCheckpoint(DemoSession(q, 2f, false, 0))
        val store = PlaybackStore(context)
        store.save(first)
        store.save(first.copy(session = first.session.copy(position = 3f)))
        val file = File(directory, "playback-v1.json")
        val envelope = org.json.JSONObject(file.readText())
        val changed = org.json.JSONObject(envelope.getString("payload")).put("position", 123)
        envelope.put("payload", changed.toString())
        file.writeText(envelope.toString())
        assertEquals(first, store.load())
        assertTrue(store.recovered)
    }

    fun testModeChangeWhilePausedAtInitialWaitPreservesCurrentUntilPlay() {
        val gate = CompletableDeferred<Unit>()
        shuffleGate = gate
        main {
            val ids = library.state.value.tracks.map { it.id }
            controller.shuffleState.value = true
            controller.selectCollection(ids, ids.first())
        }
        await { controller.preparingShuffle && controller.positionState.value > .25f }
        var first: String? = null
        main { first = controller.queueState.value.currentId; controller.positionState.value = 8f }
        Thread.sleep(150)
        main { controller.pause(); controller.toggleShuffle(); gate.complete(Unit) }
        await { !controller.preparingShuffle }
        main { assertEquals(first, controller.queueState.value.currentId); assertFalse(controller.playingState.value); controller.play() }
        await { controller.queueState.value.currentId != first && controller.positionState.value > .25f }
    }

    fun testLegacyInlineMigrationPreservesOccurrencesModesAndScopedSnapshot() {
        main { controller.close() }
        val entries = listOf(QueueOccurrence("a", "one"), QueueOccurrence("a", "two"), QueueOccurrence("b", "three"))
        val json = org.json.JSONObject().put("version", 1)
            .put("entries", org.json.JSONArray().apply { entries.forEach { put(org.json.JSONObject().put("track", it.trackId).put("id", it.id)) } })
            .put("current", "two").put("stop", "three").put("scheduled", org.json.JSONArray(listOf("three")))
            .put("continuation", true).put("position", 2.5).put("shuffle", true).put("repeat", 2)
        File(directory, "playback-v1.json").writeText(json.toString())
        val store = PlaybackStore(context)
        val saved = store.load()!!
        assertEquals(entries, saved.session.queue.entries)
        assertEquals(setOf("one"), saved.session.queue.consumedIds)
        assertEquals("two", saved.session.queue.currentId)
        assertEquals("three", saved.session.queue.stopAfterId)
        assertTrue(saved.session.queue.resumeAfterStop)
        assertNull(saved.shuffleSession)
        store.save(saved)
        assertEquals(3, org.json.JSONObject(File(directory, "playback-v1.json").readText()).getInt("version"))
        assertEquals(saved, store.load())
    }

    fun testNextPassMembershipChangesDoNotRestartActiveRemainderPreparation() {
        val ids = library.state.value.tracks.map { it.id }
        runBlocking { library.editPlaylists { it.copy(custom = listOf(SavedPlaylist("scope", "Scope", ids))) } }
        val gate = CompletableDeferred<Unit>()
        shuffleGate = gate
        main {
            controller.shuffleState.value = true
            controller.selectCollection(ids, ids.first(), ShuffleCollection(kind = "playlist", token = "scope"))
        }
        await { controller.preparingShuffle && controller.positionState.value > .25f }
        val calls = shuffleCalls
        runBlocking { library.editPlaylists { it.copy(custom = listOf(SavedPlaylist("scope", "Scope", ids.take(1)))) } }
        Thread.sleep(250)
        main {
            assertEquals(calls, shuffleCalls)
            assertEquals(ids, controller.queueState.value.entries.map { it.trackId })
            gate.complete(Unit)
        }
        await { !controller.preparingShuffle }
        main { assertEquals(ids, controller.queueState.value.entries.map { it.trackId }) }
    }

}

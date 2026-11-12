@file:Suppress("DEPRECATION")
package com.example.clocarmus.playback

import android.content.*
import android.content.res.AssetFileDescriptor
import android.database.MatrixCursor
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.test.InstrumentationTestCase
import com.example.clocarmus.library.*
import com.example.clocarmus.ui.player.*
import com.example.clocarmus.ui.startup.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real decoder, SQLite, audio focus and atomic files, isolated from the user's library/session. */
class PlaybackTest : InstrumentationTestCase() {
    private lateinit var context: Context
    private lateinit var library: LocalLibrary
    private lateinit var controller: PlaybackController
    private lateinit var directory: File
    private lateinit var wav: File
    private var publications = 0
    private var rejectAudio = false
    private var openedAudio = 0
    override fun setUp() {
        super.setUp()
        val target = instrumentation.targetContext
        directory = File(target.cacheDir, "playback-test").apply { deleteRecursively(); mkdirs() }
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
                addRow(arrayOf<Any>("first", "First.wav", "audio/wav", wav.length(), 1))
                addRow(arrayOf<Any>("second", "Second.wav", "audio/wav", wav.length(), 1))
            }
            override suspend fun audio(document: AudioDocument, id: String) =
                TrackMenuEntry(document.name, "Test", "Test", id = id, durationSeconds = 8) to document.name
        })
        await { library.state.value.records.size == 2 && !library.state.value.summary.running }
        createController()
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun createController() {
        main {
            controller = PlaybackController(context, library) {
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
        main { controller.close() }
        runBlocking { library.close() }
        directory.deleteRecursively()
        context.getSharedPreferences("onboarding", 0).edit().clear().commit()
        super.tearDown()
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
    fun testPreviousThresholdAndRepeatAllTransportBoundary() {
        select()
        await { controller.positionState.value > .25f }
        main { controller.pause(); controller.step(1); controller.positionState.value = 4f; controller.step(-1) }
        main { assertEquals(1, controller.queueState.value.currentIndex); assertEquals(0f, controller.positionState.value) }
        main { controller.step(-1); assertEquals(0, controller.queueState.value.currentIndex) }
        main { controller.repeatState.value = 2; controller.step(-1); assertEquals(1, controller.queueState.value.currentIndex) }
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
    fun testCorruptCommittedSnapshotRestoresExplainedEmptyPausedPlayer() {
        await { runCatching { PlaybackStore(context).load() != null }.getOrDefault(false) }
        main { controller.close() }
        val file = File(directory, "playback-v1.json")
        file.writeText("corrupt checkpoint")
        createController()
        main {
            assertFalse(controller.playingState.value)
            assertTrue(controller.queueState.value.entries.isEmpty())
            assertNotNull(controller.warning)
        }
        Thread.sleep(200)
        assertEquals("corrupt checkpoint", file.readText())
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
}

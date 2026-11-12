@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.example.clocarmus.library

import android.content.Context
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.test.InstrumentationTestCase
import com.example.clocarmus.ui.player.*
import com.example.clocarmus.ui.startup.*
import kotlinx.coroutines.*
import java.io.File

/** Uses real SQLite and an injected document provider boundary. No changes to personal app state. */
class LocalLibraryTest : InstrumentationTestCase() {
    private lateinit var context: Context
    private lateinit var repository: LocalLibrary
    private lateinit var access: FakeDocuments
    private val source = "content://test.documents/tree/music"
    override fun setUp() {
        super.setUp()
        val target = instrumentation.targetContext
        context = object : ContextWrapper(target) {
            override fun getDatabasePath(name: String) = File(target.cacheDir, "test-$name")
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?) =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences("library-test-$name", mode)
        }
        context.getDatabasePath("local-library.db").delete()
        context.getSharedPreferences("onboarding", 0).edit().clear().commit()
        access = FakeDocuments()
        saveOnboarding(context.getSharedPreferences("onboarding", 0), OnboardingState(completed = true,
            folders = listOf(OnboardingFolder(source, "Music", "Test music"))))
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.size == 1 }
    }
    override fun tearDown() {
        runBlocking { repository.close() }
        context.getDatabasePath("local-library.db").delete()
        context.getSharedPreferences("onboarding", 0).edit().clear().commit()
        super.tearDown()
    }
    private fun awaitIdle(condition: (LibrarySnapshot) -> Boolean = { true }) = runBlocking {
        withTimeout(15000) {
            while (true) {
                val snapshot = repository.state.value
                if (snapshot.ready && !snapshot.summary.running && condition(snapshot)) break
                delay(20)
            }
        }
    }
    private fun refresh() {
        val version = repository.state.value.version
        repository.refresh()
        awaitIdle { it.version > version }
    }
    fun testIncrementalRefreshRenameAndDurableMemberships() {
        val old = repository.state.value.records.single()
        assertEquals(1, access.reads)
        runBlocking { repository.editPlaylists { it.add(FAVORITES, old.id, 10) } }
        refresh()
        assertEquals(1, access.reads)
        access.file = "renamed"
        refresh()
        val renamed = repository.state.value.records.single()
        assertEquals(old.id, renamed.id)
        assertTrue(repository.state.value.playlists.contains(FAVORITES, old.id))
        runBlocking { repository.close() }
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.singleOrNull()?.location?.endsWith("renamed") == true }
        assertTrue(repository.state.value.playlists.contains(FAVORITES, old.id))
    }
    fun testIncompleteTraversalNeverConfirmsMissingAndCleanupRevalidates() {
        val id = repository.state.value.records.single().id
        access.fail = true
        refresh()
        assertEquals(Availability.DISCONNECTED, repository.state.value.records.single().availability)
        runBlocking { repository.removeMissing(setOf(id)) }
        assertEquals(1, repository.state.value.records.size)
        access.fail = false
        access.file = null
        refresh()
        assertEquals(Availability.MISSING, repository.state.value.records.single().availability)
        runBlocking { repository.removeMissing(setOf(id)) }
        assertTrue(repository.state.value.records.isEmpty())
    }
    fun testCancellationRetainsCompletedImportsAndDirtyRequestGetsFollowup() {
        access.file = "slow"
        access.slow = true
        access.started = false
        repository.refresh()
        runBlocking { withTimeout(5000) { while (!access.started) delay(10) } }
        repository.cancel()
        awaitIdle { it.summary.incomplete }
        assertEquals(Availability.AVAILABLE, repository.state.value.records.single().availability)
        access.slow = false
        repository.refresh()
        awaitIdle { it.records.singleOrNull()?.location?.endsWith("slow") == true }
    }
    fun testOverlappingSourcesExclusionAndRemovalKeepOnlyCoveredMemberships() {
        val id = repository.state.value.records.single().id
        val prefs = context.getSharedPreferences("onboarding", 0)
        val other = "content://test.documents/tree/other"
        saveOnboarding(prefs, loadOnboarding(prefs).add(OnboardingFolder(other, "Other", "Test other")))
        repository.refresh()
        awaitIdle { it.records.singleOrNull()?.paths?.size == 2 }
        assertEquals(id, repository.state.value.records.single().id)
        assertEquals(1, access.reads)
        runBlocking { repository.editPlaylists { it.add(FAVORITES, id, 10) } }
        saveOnboarding(prefs, loadOnboarding(prefs).remove(source))
        repository.refresh()
        awaitIdle { it.records.singleOrNull()?.paths?.singleOrNull()?.source == other }
        assertTrue(repository.state.value.playlists.contains(FAVORITES, id))
        saveOnboarding(prefs, loadOnboarding(prefs).remove(other))
        repository.refresh()
        awaitIdle { it.records.isEmpty() }
        assertTrue(repository.state.value.playlists.favorites.isEmpty())
    }
    fun testMetadataReaderAcceptsUntaggedShortWaveWithFilenameFallback() = runBlocking {
        val file = File(context.cacheDir, "short.wav")
        val pcm = ByteArray(1600)
        val bytes = java.nio.ByteBuffer.allocate(44 + pcm.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        file.writeBytes(bytes.array())
        val (track, hash) = readAudio(context, AudioDocument(Uri.fromFile(file), "wave", file.name, "audio/wav", file.length(), file.lastModified()), "wave")
        assertEquals("short.wav", track.title)
        assertEquals("Unknown artist", track.artist)
        assertEquals("Unknown album", track.album)
        assertTrue(track.tags!!.artists.isEmpty())
        assertEquals(64, hash.length)
        file.delete()
        Unit
    }
    fun testSourceScopedExclusionKeepsOverlappingGrantAndEmptyDirectories() {
        access.nested = true
        refresh()
        val id = repository.state.value.records.single().id
        val prefs = context.getSharedPreferences("onboarding", 0)
        val overlapping = "content://test.documents/tree/sub"
        saveOnboarding(prefs, loadOnboarding(prefs).add(OnboardingFolder(overlapping, "Sub", "Nested source")))
        repository.refresh()
        awaitIdle { it.records.singleOrNull()?.paths?.size == 2 }
        val excluded = "content://test.documents/tree/music/document/sub"
        saveOnboarding(prefs, loadOnboarding(prefs).copy(exclusions = setOf(excluded)))
        repository.refresh()
        awaitIdle { it.records.singleOrNull()?.paths?.singleOrNull()?.source == overlapping }
        assertEquals(id, repository.state.value.records.single().id)
        val empty = "content://test.documents/tree/music/document/empty"
        assertNotNull(repository.state.value.folders.get(empty))
        assertTrue(repository.state.value.folders.contents(empty).isEmpty())
    }
    fun testCleanupRechecksFileReturnedAfterPreview() {
        val id = repository.state.value.records.single().id
        access.file = null
        refresh()
        assertEquals(Availability.MISSING, repository.state.value.records.single().availability)
        access.file = "song"
        runBlocking { repository.removeMissing(setOf(id)) }
        assertEquals(id, repository.state.value.records.single().id)
        refresh()
        assertEquals(Availability.AVAILABLE, repository.state.value.records.single().availability)
    }
    fun testChangeDuringScanQueuesFollowupWithoutLosingDirtyState() {
        access.file = "during"
        access.started = false
        access.slow = true
        repository.refresh()
        runBlocking { withTimeout(5000) { while (!access.started) delay(10) } }
        access.file = "after"
        repository.refresh(source)
        access.slow = false
        awaitIdle { it.records.singleOrNull()?.filename == "after.wav" }
        assertTrue(access.reads >= 3)
    }
    fun testResetInvalidatesSlowWorkAndPreservesEmptyCustomPlaylists() {
        runBlocking { repository.editPlaylists { it.create("Road trip").add(FAVORITES, repository.state.value.records.single().id, 10) } }
        access.file = "slow"
        access.slow = true
        access.started = false
        repository.refresh()
        runBlocking { withTimeout(5000) { while (!access.started) delay(10) } }
        val prefs = context.getSharedPreferences("onboarding", 0)
        saveOnboarding(prefs, loadOnboarding(prefs).copy(folders = emptyList(), resetPending = true))
        runBlocking { repository.reset() }
        access.slow = false
        saveOnboarding(prefs, loadOnboarding(prefs).copy(resetPending = false))
        repository.refresh()
        awaitIdle { it.records.isEmpty() }
        assertEquals("Road trip", repository.state.value.playlists.custom.single().name)
        assertTrue(repository.state.value.playlists.custom.single().tracks.isEmpty())
        assertTrue(repository.state.value.playlists.favorites.isEmpty())
        runBlocking { repository.close() }
        repository = LocalLibrary(context, access)
        awaitIdle { it.playlists.custom.size == 1 }
        assertTrue(repository.state.value.records.isEmpty())
    }
    fun testAlbumYearUsesReleaseTagInsteadOfContainerCreationDate() = runBlocking {
        val file = File(context.cacheDir, "release-year.m4a")
        instrumentation.context.assets.open("release-year.m4a").use { input -> file.outputStream().use { input.copyTo(it) } }
        val (track, _) = readAudio(context, AudioDocument(Uri.fromFile(file), "release-year", file.name, "audio/mp4", file.length(), file.lastModified()), "year")
        assertEquals("1985", track.year)
        assertEquals("1985", track.tags!!.releaseDate)
        file.delete()
        Unit
    }
    fun testFullDateTagBecomesYearAndAbsentReleaseTagsStayEmpty() = runBlocking {
        for ((asset, expected) in listOf("full-release-date.flac" to "2004", "no-release-year.m4a" to null)) {
            val file = File(context.cacheDir, asset)
            try {
                instrumentation.context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
                val (track, _) = readAudio(context, AudioDocument(Uri.fromFile(file), asset, asset,
                    if (asset.endsWith("flac")) "audio/flac" else "audio/mp4", file.length(), file.lastModified()), asset)
                assertEquals(asset, expected, track.year)
            } finally { file.delete() }
        }
        Unit
    }
    fun testMetadataReaderUpdateRefreshesCachedTracksWithoutChangingIdentity() {
        val original = repository.state.value.records.single()
        val reads = access.reads
        runBlocking { repository.close() }
        context.getSharedPreferences("library-metadata", 0).edit().putInt("readerVersion", 1).commit()
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.singleOrNull()?.id == original.id && access.reads > reads }
        assertEquals(original.importedAt, repository.state.value.records.single().importedAt)
        val updatedReads = access.reads
        refresh()
        assertEquals(updatedReads, access.reads)
    }
    private class FakeDocuments : DocumentAccess {
        @Volatile var file: String? = "song"
        @Volatile var fail = false
        @Volatile var slow = false
        @Volatile var started = false
        @Volatile var reads = 0
        @Volatile var nested = false
        override fun children(tree: Uri, id: String): MatrixCursor {
            if (fail) error("Drive disconnected")
            return MatrixCursor(arrayOf("id", "name", "mime", "size", "modified")).apply {
                if (nested && id == "music") {
                    addRow(arrayOf<Any>("sub", "Sub", DocumentsContract.Document.MIME_TYPE_DIR, 0, 1))
                    addRow(arrayOf<Any>("empty", "Empty", DocumentsContract.Document.MIME_TYPE_DIR, 0, 1))
                } else if (id != "empty") file?.let { addRow(arrayOf<Any>(it, "$it.wav", "audio/wav", 100, 1)) }
            }
        }
        override suspend fun audio(document: AudioDocument, id: String): Pair<TrackMenuEntry, String> {
            reads++
            started = true
            while (slow) delay(10)
            return TrackMenuEntry(document.name, "Unknown artist", "Unknown album", id = id, tags = TrackTags()) to "unique-content"
        }
    }
}

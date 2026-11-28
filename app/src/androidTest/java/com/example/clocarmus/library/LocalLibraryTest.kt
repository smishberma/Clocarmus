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
        context.getSharedPreferences("library-metadata", 0).edit().clear().commit()
        access = FakeDocuments()
        saveOnboarding(context.getSharedPreferences("onboarding", 0), OnboardingState(completed = true,
            folders = listOf(OnboardingFolder(source, "Music", "Test music"))))
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.size == 1 && repository.completedScan.value != null }
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
        val queries = access.queries
        repository.refresh()
        awaitIdle { access.queries > queries }
    }
    fun testBatchedImportAndUnchangedRefreshAvoidDatabaseWrites() {
        startWriteAudit()
        access.extraFiles = 512
        val version = repository.state.value.version
        val started = android.os.SystemClock.elapsedRealtime()
        refresh()
        val importMs = android.os.SystemClock.elapsedRealtime() - started
        val imported = repository.state.value
        assertEquals(513, imported.records.size)
        assertEquals(512, imported.summary.added)
        assertEquals(513, imported.playlists.imported.size)
        assertEquals(512L, writes("tracks"))
        assertEquals(0L, writes("directories"))
        assertTrue("Expected batched commits, got ${imported.version - version}", imported.version - version < 128)
        val totalWrites = writes()
        val reads = access.reads
        val refreshStarted = android.os.SystemClock.elapsedRealtime()
        refresh()
        val refreshMs = android.os.SystemClock.elapsedRealtime() - refreshStarted
        assertEquals(reads, access.reads)
        assertEquals(totalWrites, writes())
        assertEquals(imported.version, repository.state.value.version)
        assertSame(imported.records, repository.state.value.records)
        android.util.Log.i("ScannerPerformance", "512 new files: ${importMs}ms, ${imported.version - version} commits; unchanged 513 files: ${refreshMs}ms, zero writes")

        access.changedFile = "extra-300"
        refresh()
        assertEquals(reads + 1, access.reads)
        assertEquals(513L, writes("tracks"))
        assertEquals(0L, writes("directories"))
        assertEquals(1, repository.state.value.summary.updated)
        val updated = repository.state.value.records.single { it.filename == "extra-300.wav" }
        val old = imported.records.single { it.id == updated.id }
        assertEquals(old.importedAt, updated.importedAt)
        assertEquals(imported.playlists, repository.state.value.playlists)
    }

    fun testDirectoryChangesOnlyWriteChangedDirectories() {
        startWriteAudit()
        access.emptyFolders = 40
        refresh()
        assertEquals(40L, writes("directories"))
        assertEquals(0L, writes("tracks"))
        val totalWrites = writes()
        refresh()
        assertEquals(totalWrites, writes())
        access.emptyFolders = 39
        refresh()
        assertEquals(41L, writes("directories"))
        assertEquals(0L, writes("tracks"))
    }

    fun testCancellationFlushesCompletedBatchAndKeepsConcurrentPlaylistEdit() {
        val original = repository.state.value.records.single()
        access.extraFiles = 10
        access.blockFile = "extra-5"
        repository.refresh()
        awaitBlocked()
        runBlocking { repository.editPlaylists { it.add(FAVORITES, original.id, 10) } }
        repository.cancel()
        awaitIdle { it.summary.incomplete && it.records.size == 6 }
        assertEquals(5, repository.state.value.summary.added)
        assertTrue(repository.state.value.playlists.contains(FAVORITES, original.id))
        runBlocking { repository.close() }
        access.fail = true
        repository = LocalLibrary(context, access)
        awaitIdle { repository.completedScan.value != null }
        assertEquals(6, repository.state.value.records.size)
        assertEquals(6, repository.state.value.playlists.imported.size)
        assertTrue(repository.state.value.playlists.contains(FAVORITES, original.id))
    }

    fun testResetDiscardsUncommittedBatch() {
        access.extraFiles = 10
        access.blockFile = "extra-5"
        repository.refresh()
        awaitBlocked()
        val prefs = context.getSharedPreferences("onboarding", 0)
        saveOnboarding(prefs, loadOnboarding(prefs).copy(folders = emptyList(), resetPending = true))
        runBlocking { repository.reset(); repository.close() }
        repository = LocalLibrary(context, access)
        awaitIdle()
        assertTrue(repository.state.value.records.isEmpty())
        assertTrue(repository.state.value.playlists.imported.isEmpty())
    }

    fun testLoadingCursorNeverConfirmsAbsence() {
        access.file = null
        access.loading = true
        refresh()
        assertTrue(repository.state.value.summary.incomplete)
        assertEquals(Availability.AVAILABLE, repository.state.value.records.single().availability)
    }

    fun testAmbiguousCopiesStaySeparateAndOverlappingRefreshDoesNotRewriteThem() {
        val original = repository.state.value.records.single()
        runBlocking {
            repository.editPlaylists { it.add(FAVORITES, original.id, 10) }
            repository.close()
        }
        access.file = null
        access.extraFiles = 2
        access.duplicateContent = true
        val prefs = context.getSharedPreferences("onboarding", 0)
        val other = "content://test.documents/tree/other"
        saveOnboarding(prefs, loadOnboarding(prefs).add(OnboardingFolder(other, "Other", "Other")))
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.size == 3 && it.records.count { record -> record.paths.size == 2 } == 2 }
        val copies = repository.state.value.records.filter { it.id != original.id }
        assertEquals(2, copies.map { it.id }.toSet().size)
        assertEquals(Availability.MISSING, repository.state.value.byId[original.id]!!.availability)
        assertTrue(repository.state.value.playlists.contains(FAVORITES, original.id))
        assertEquals(3, access.reads)
        startWriteAudit()
        refresh()
        assertEquals(0L, writes())
        assertEquals(3, access.reads)
    }

    fun testFailedTransactionRollsBackRecordsAndDoesNotAdvanceCachedState() {
        val original = repository.state.value.records.single()
        runBlocking { repository.close() }
        val database = LibraryDatabase(context)
        try {
            val version = database.version()
            val playlists = database.playlists()!!
            val directories = database.directories()
            val changed = original.copy(filename = "changed.wav")
            database.writableDatabase.execSQL("CREATE TRIGGER fail_scan BEFORE UPDATE ON state BEGIN SELECT RAISE(ABORT, 'Simulated write failure'); END")
            try {
                database.commit(listOf(changed), emptyList(), playlists, emptyList())
                fail("Commit should fail")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(version, database.version())
            assertEquals(original, database.records().single())
            database.writableDatabase.execSQL("DROP TRIGGER fail_scan")
            database.commit(listOf(changed), emptyList(), playlists, emptyList())
            assertEquals(version + 1, database.version())
            assertEquals(changed, database.records().single())
            assertTrue(database.directories().isEmpty())
            assertTrue(directories.isNotEmpty())
        } finally { database.close() }
        repository = LocalLibrary(context, access)
        awaitIdle { repository.completedScan.value != null }
    }

    private fun awaitBlocked() = runBlocking {
        withTimeout(5000) { while (!access.blocked) delay(10) }
    }

    private fun startWriteAudit() {
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("local-library.db").path, null, 0).use { db ->
            db.execSQL("CREATE TABLE scan_writes (kind TEXT NOT NULL)")
            for (table in listOf("tracks", "directories", "state")) {
                for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
                    db.execSQL("CREATE TRIGGER audit_${table}_$operation AFTER $operation ON $table BEGIN INSERT INTO scan_writes VALUES ('$table'); END")
                }
            }
        }
    }

    private fun writes(table: String? = null): Long =
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("local-library.db").path, null, 0).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM scan_writes" + if (table == null) "" else " WHERE kind=?",
                table?.let { arrayOf(it) }).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
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
    fun testDisconnectedDriveRetainsIdentityAndPlaylistsAcrossRestartAndReconnect() =
        verifyUnavailableSourceRecovery(Availability.DISCONNECTED)

    fun testLostPermissionRetainsIdentityAndPlaylistsAcrossRestartAndRegrant() =
        verifyUnavailableSourceRecovery(Availability.ACCESS_LOST)

    private fun verifyUnavailableSourceRecovery(expected: Availability) {
        val original = repository.state.value.records.single()
        runBlocking { repository.editPlaylists { it.add(FAVORITES, original.id, 10).create("Storage recovery") } }
        val playlist = repository.state.value.playlists.custom.single().id
        runBlocking { repository.editPlaylists { it.add(playlist, original.id, 11) } }
        access.fail = expected == Availability.DISCONNECTED
        access.accessLost = expected == Availability.ACCESS_LOST
        refresh()
        assertEquals(expected, repository.state.value.records.single().availability)
        runBlocking { repository.removeMissing(setOf(original.id)) }
        assertEquals(original.id, repository.state.value.records.single().id)
        runBlocking { repository.close() }
        repository = LocalLibrary(context, access)
        // Cached readiness can precede the startup scan; finish that scan before
        // simulating a regrant, otherwise its failure can race the recovery pass.
        awaitIdle { it.records.singleOrNull()?.availability == expected && repository.completedScan.value != null }
        assertTrue(repository.state.value.playlists.contains(FAVORITES, original.id))
        assertTrue(repository.state.value.playlists.contains(playlist, original.id))
        access.fail = false
        access.accessLost = false
        refresh()
        val returned = repository.state.value.records.single()
        assertEquals(Availability.AVAILABLE, returned.availability)
        assertEquals(original.id, returned.id)
        assertEquals(original.importedAt, returned.importedAt)
        assertEquals(original.track, returned.track)
        assertTrue(repository.state.value.playlists.contains(FAVORITES, original.id))
        assertTrue(repository.state.value.playlists.contains(playlist, original.id))
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
        runBlocking { repository.editPlaylists { it.add(FAVORITES, original.id, 10) } }
        val reads = access.reads
        runBlocking { repository.close() }
        context.getSharedPreferences("library-metadata", 0).edit().putInt("readerVersion", 2).commit()
        access.year = "2007"
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.singleOrNull()?.track?.year == "2007" && access.reads > reads }
        assertEquals(original.id, repository.state.value.records.single().id)
        assertEquals(original.importedAt, repository.state.value.records.single().importedAt)
        assertTrue(repository.state.value.playlists.contains(FAVORITES, original.id))
        assertEquals(METADATA_READER_VERSION, context.getSharedPreferences("library-metadata", 0).getInt("readerVersion", 0))
        val updatedReads = access.reads
        refresh()
        assertEquals(updatedReads, access.reads)
    }
    fun testId3DatesReachBothPlayerYearAndTrackInfo() = runBlocking {
        for ((asset, date) in listOf(
            "id3-recording-release.mp3" to "2007",
            "id3-conflicting-dates.mp3" to "2007",
            "id3-release-only.mp3" to "2007-08-21",
            "id3-no-date.mp3" to null
        )) {
            val file = File(context.cacheDir, asset)
            try {
                instrumentation.context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
                val before = file.readBytes()
                val (track, fingerprint) = readAudio(context, AudioDocument(Uri.fromFile(file), asset, asset,
                    "audio/mpeg", file.length(), file.lastModified()), asset)
                assertEquals(asset, date?.take(4), track.year)
                assertEquals(asset, date, track.tags!!.releaseDate)
                assertEquals(asset, date?.take(4), track.tags.fields().toMap()["Year"])
                assertEquals(asset, date?.take(4), releaseYear(track.year, track.tags.releaseDate))
                assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(before)
                    .joinToString("") { "%02x".format(it) }, fingerprint)
                assertTrue(before.contentEquals(file.readBytes()))
            } finally { file.delete() }
        }
        Unit
    }
    fun testMetadataRefreshStaysPendingAcrossUnavailableSourceAndRestart() {
        val original = repository.state.value.records.single()
        runBlocking { repository.close() }
        val prefs = context.getSharedPreferences("library-metadata", 0)
        prefs.edit().putInt("readerVersion", 2).commit()
        access.fail = true
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.singleOrNull()?.availability == Availability.DISCONNECTED }
        assertEquals(2, prefs.getInt("readerVersion", 0))
        runBlocking { repository.close() }
        access.fail = false
        access.year = "2007"
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.singleOrNull()?.track?.year == "2007" }
        assertEquals(original.id, repository.state.value.records.single().id)
        assertEquals(original.importedAt, repository.state.value.records.single().importedAt)
        assertEquals(METADATA_READER_VERSION, prefs.getInt("readerVersion", 0))
    }
    fun testCancelledMetadataRefreshRetriesAfterRestart() {
        val original = repository.state.value.records.single()
        runBlocking { repository.close() }
        val prefs = context.getSharedPreferences("library-metadata", 0)
        prefs.edit().putInt("readerVersion", 2).commit()
        access.slow = true
        access.started = false
        repository = LocalLibrary(context, access)
        runBlocking { withTimeout(5000) { while (!access.started) delay(10) } }
        runBlocking { repository.close() }
        assertEquals(2, prefs.getInt("readerVersion", 0))
        access.slow = false
        access.year = "2007"
        repository = LocalLibrary(context, access)
        awaitIdle { it.records.singleOrNull()?.track?.year == "2007" }
        assertEquals(original.id, repository.state.value.records.single().id)
        assertEquals(METADATA_READER_VERSION, prefs.getInt("readerVersion", 0))
    }
    private class FakeDocuments : DocumentAccess {
        @Volatile var file: String? = "song"
        @Volatile var fail = false
        @Volatile var accessLost = false
        @Volatile var slow = false
        @Volatile var started = false
        @Volatile var reads = 0
        @Volatile var nested = false
        @Volatile var year: String? = null
        @Volatile var queries = 0
        @Volatile var extraFiles = 0
        @Volatile var emptyFolders = 0
        @Volatile var changedFile: String? = null
        @Volatile var blockFile: String? = null
        @Volatile var blocked = false
        @Volatile var loading = false
        @Volatile var duplicateContent = false
        override fun children(tree: Uri, id: String): MatrixCursor {
            queries++
            if (accessLost) throw SecurityException("Simulated persisted grant loss")
            if (fail) error("Drive disconnected")
            return object : MatrixCursor(arrayOf("id", "name", "mime", "size", "modified")) {
                override fun getExtras() = android.os.Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, loading) }
            }.apply {
                if (id.startsWith("empty-")) return@apply
                if (id == "music" || id == "other") {
                    repeat(emptyFolders) { addRow(arrayOf<Any>("empty-$it", "Empty $it", DocumentsContract.Document.MIME_TYPE_DIR, 0, 1)) }
                    repeat(extraFiles) {
                        val name = "extra-$it"
                        addRow(arrayOf<Any>(name, "$name.wav", "audio/wav", 100, if (name == changedFile) 2 else 1))
                    }
                }
                if (nested && id == "music") {
                    addRow(arrayOf<Any>("sub", "Sub", DocumentsContract.Document.MIME_TYPE_DIR, 0, 1))
                    addRow(arrayOf<Any>("empty", "Empty", DocumentsContract.Document.MIME_TYPE_DIR, 0, 1))
                } else if (id != "empty") file?.let { addRow(arrayOf<Any>(it, "$it.wav", "audio/wav", 100, 1)) }
            }
        }
        override suspend fun audio(document: AudioDocument, id: String): Pair<TrackMenuEntry, String> {
            reads++
            started = true
            if (document.name == "$blockFile.wav") {
                blocked = true
                while (blockFile != null) delay(10)
            }
            while (slow) delay(10)
            return TrackMenuEntry(document.name, "Unknown artist", "Unknown album", year = year, id = id,
                tags = TrackTags(releaseDate = year)) to if (document.name.startsWith("extra-") && !duplicateContent) "${document.name}:${document.modified}" else "unique-content"
        }
    }
}

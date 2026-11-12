package com.example.clocarmus.playback.shuffle

import com.example.clocarmus.library.*
import com.example.clocarmus.ui.player.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShuffleSessionTest {
    private fun queue() = TrackQueue.fromTracks(listOf("a", "b", "c", "d"), "a")
    private val available: (String) -> Boolean = { true }

    @Test fun forwardJumpsConsumePassedOccurrencesAndPreviousRetainsReplaySequence() = runBlocking {
        val original = queue()
        val atC = original.step(1, available).step(1, available)
        val atB = atC.step(-1, available)
        assertEquals(original.entries.take(2).map { it.id }.toSet(), atB.consumedIds)
        assertEquals(setOf(original.entries[2].id), atB.replayIds)
        val ordered = prepareShuffle(ShufflePreparationInput(atB, ShufflePreparationKind.REMAINDER,
            setOf("a", "b", "c", "d"), emptyList(), "c"))
        assertEquals(listOf("a", "b", "c", "d"), ordered.map { it.trackId })
        assertEquals("c", atB.complete(0, available).queue.current!!.trackId)
    }

    @Test fun completedReplayDoesNotRegainAutomaticEligibilityThroughBackwardCursor() {
        val original = queue()
        val atC = original.select(original.entries[2].id)
        val back = atC.select(original.entries[1].id)
        val noReplay = back.copy(replayIds = emptySet()).eligibleRemainder()
        assertEquals(listOf("a", "b", "c", "d"), noReplay.entries.map { it.trackId })
        // C was not consumed yet; jumping to D consumes it, and backward navigation marks it replay.
        val atD = atC.step(1, available)
        val deliberatelyBack = atD.step(-1, available)
        assertTrue(original.entries[0].id in deliberatelyBack.consumedIds)
        assertTrue(original.entries[1].id in deliberatelyBack.consumedIds)
        assertEquals("d", deliberatelyBack.step(1, available).current!!.trackId)
    }

    @Test fun originalOrderRestorationPinsSchedulesAndRetainsExplicitDuplicateAppendOrder() {
        val initial = queue().append("b").append("a")
        val current = initial.currentId
        val reordered = initial.reorder(initial.entries.map { it.id }, initial.entries.last().id, 1)
        val restored = reordered.schedule("d").originalRemainder()
        assertEquals(current, restored.currentId)
        assertEquals(listOf("a", "d", "b", "c", "b", "a"), restored.entries.map { it.trackId })
        assertEquals(initial.originalEntries, restored.originalEntries)
    }

    @Test fun consumedTailIsMovedBeforeCurrentUnlessReplayWasRequested() {
        val original = queue()
        val consumed = original.copy(consumedIds = setOf(original.entries[2].id))
        val eligible = consumed.eligibleRemainder()
        assertEquals(listOf("c", "a", "b", "d"), eligible.entries.map { it.trackId })
        val replay = consumed.reorder(consumed.entries.map { it.id }, original.entries[2].id, 3)
        assertTrue(original.entries[2].id in replay.replayIds)
        assertEquals(replay.entries, replay.eligibleRemainder().entries)
    }

    private fun snapshot(vararg tracks: TrackMenuEntry, playlists: PlaylistLibrary = PlaylistLibrary()) = LibrarySnapshot(
        records = tracks.map { LibraryRecord(it.id, it.id, "content://${it.id}", it.title, 1, 1, "", it, 1, emptyList(),
            if (it.available) Availability.AVAILABLE else Availability.DISCONNECTED) }, playlists = playlists, ready = true)
    private fun track(id: String, title: String, artist: String = "Artist", album: String = "Album", available: Boolean = true) =
        TrackMenuEntry(title, artist, album, id = id, available = available, genres = listOf("Rock"))

    @Test fun searchScopeAndNormalizedQueryIdentitySurviveMembershipChanges() {
        val source = ShuffleCollection(kind = "artist", artist = "Artist", genre = "rock", query = "Sun")
        val first = snapshot(track("a", "Sunrise"), track("x", "Sunshine", artist = "Other"))
        assertEquals(listOf("a"), source.resolve(first, emptyList()))
        val next = snapshot(track("a", "Sunrise"), track("b", "Sunset"), track("x", "Sunshine", artist = "Other"))
        assertEquals(listOf("a", "b"), source.resolve(next, emptyList()))
        assertEquals(source.key, source.copy(query = "sUN", token = "unrelated-ui-state").key)
        assertNotEquals(source.key, source.copy(artistScope = ArtistScope.ALBUM).key)
    }

    @Test fun emptiedPlaylistStopsWhileDeletedPlaylistUsesRetainedSnapshot() {
        val a = track("a", "A")
        val b = track("b", "B")
        val source = ShuffleCollection(kind = "playlist", token = "trip")
        assertEquals(emptyList<String>(), source.resolve(snapshot(a, b,
            playlists = PlaylistLibrary(custom = listOf(SavedPlaylist("trip", "Trip")))), listOf("a")))
        assertEquals(listOf("a"), source.resolve(snapshot(a, b), listOf("a")))
        assertEquals(emptyList<String>(), source.resolve(snapshot(b), listOf("a")))
    }

    @Test fun manualCollectionsPreserveMultiplicityAndOriginalOrderAcrossRefresh() {
        val source = ShuffleCollection()
        assertEquals(listOf("b", "a", "b"), source.resolve(snapshot(track("a", "A"), track("b", "B")), listOf("b", "a", "b")))
        assertEquals(listOf("b", "b"), source.resolve(snapshot(track("a", "A", available = false), track("b", "B")), listOf("b", "a", "b")))
    }

    @Test fun albumScopeUsesAlbumArtistAndDoesNotExpandGenreArtistSelection() {
        val compilation = track("a", "A", "Performer").copy(albumArtist = "Various")
        val other = track("b", "B")
        val source = ShuffleCollection(kind = "album", album = compilation.albumIdentity)
        assertEquals(listOf("a"), source.resolve(snapshot(compilation, other), listOf("b")))
    }
    @Test fun folderSearchRetainsFilenameQueryAndRecursiveBoundary() {
        val a = track("local:a", "Metadata title")
        val b = track("local:b", "Other metadata")
        val c = track("local:c", "Outside source")
        val tree = FolderTree(listOf(FolderEntry("root", "Root"), FolderEntry("other", "Other"),
            FolderEntry("sub", "Sub", "root"), FolderEntry("fa", "Road2.flac", "root", a),
            FolderEntry("fb", "Road10.flac", "sub", b), FolderEntry("fc", "Road3.flac", "other", c)))
        val library = snapshot(a, b, c).copy(folders = tree)
        val direct = ShuffleCollection(kind = "folder", directory = "root")
        assertEquals(listOf(a.id), direct.resolve(library, emptyList()))
        val search = direct.copy(query = "road")
        assertEquals(listOf(a.id, b.id), search.resolve(library, emptyList()))
        assertNotEquals(direct.key, search.key)
        assertEquals(search.key, search.copy(query = "ROAD").key)
    }

}

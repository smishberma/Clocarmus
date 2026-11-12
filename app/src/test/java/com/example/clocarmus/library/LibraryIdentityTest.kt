package com.example.clocarmus.library

import com.example.clocarmus.ui.player.*
import org.junit.Assert.*
import org.junit.Test

class LibraryIdentityTest {
    private fun record(id: String, hash: String = "same") = LibraryRecord(id, id, id, "$id.mp3", 10, 20, hash,
        TrackMenuEntry(id, "Artist", "Album", id = id), 1, listOf(LibraryPath("source", emptyList(), emptyList())))
    @Test fun uniqueMovePreservesOldIdentityOnlyWithConfirmedAbsence() {
        val old = listOf(record("old")); val incoming = listOf(record("new"))
        assertTrue(relocationMatches(old, incoming, emptySet()).isEmpty())
        assertEquals(mapOf("new" to "old"), relocationMatches(old, incoming, setOf("old")))
    }
    @Test fun identicalPhysicalCopiesAreNeverGuessed() {
        assertTrue(relocationMatches(listOf(record("old"), record("copy")), listOf(record("new")), setOf("old")).isEmpty())
        assertTrue(relocationMatches(listOf(record("old")), listOf(record("new"), record("copy")), setOf("old")).isEmpty())
    }
    @Test fun simultaneousEditAndMoveHasNoIdentityEvidence() {
        assertTrue(relocationMatches(listOf(record("old")), listOf(record("new", "edited")), setOf("old")).isEmpty())
        assertTrue(relocationMatches(listOf(record("old", "")), listOf(record("new", "")), setOf("old")).isEmpty())
    }
    @Test fun cleanupRetainsEmptyPlaylistIdentitiesAndOnlySurvivingReferences() {
        val playlists = PlaylistLibrary(custom = listOf(SavedPlaylist("trip", "Trip", listOf("old", "keep"))),
            favorites = listOf(TimedTrack("old", 1)), imported = listOf(TimedTrack("keep", 2)), played = listOf(TimedTrack("old", 3)))
        val cleaned = playlists.retaining(setOf("keep"))
        assertEquals(listOf("keep"), cleaned.custom.single().tracks)
        assertTrue(cleaned.favorites.isEmpty()); assertTrue(cleaned.played.isEmpty())
        assertEquals("trip", cleaned.retaining(emptySet()).custom.single().id)
    }
}

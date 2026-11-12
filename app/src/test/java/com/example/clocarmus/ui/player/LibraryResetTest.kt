package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test

class LibraryResetTest {
    @Test fun resetRemovesAllReferencesButRetainsEmptyCustomPlaylists() {
        val original = PlaylistLibrary(
            custom = listOf(SavedPlaylist("trip", "Road trip", listOf("a", "b")), SavedPlaylist("empty", "Empty")),
            favorites = listOf(TimedTrack("a", 1)), imported = listOf(TimedTrack("b", 2)), played = listOf(TimedTrack("a", 3)))
        val reset = original.withoutMusic()
        assertEquals(listOf(SavedPlaylist("trip", "Road trip"), SavedPlaylist("empty", "Empty")), reset.custom)
        assertTrue((builtInPlaylists.keys + reset.custom.map { it.id }).all { reset.trackIds(it).isEmpty() })
        assertEquals(reset, reset.withoutMusic()) // interrupted reset may replay safely
        assertEquals(listOf("a", "b"), original.custom.first().tracks)
    }
}

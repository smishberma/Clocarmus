package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test

class ArtistsTest {
    @Test fun groupingKeepsDisconnectedCopiesButCountsDistinctAlbums() {
        val tracks = listOf(
            TrackMenuEntry("Zulu", "Ripple", "Album", "2002", "usb:1"),
            TrackMenuEntry("Amber", "Ripple", "Album", "2002", "sd:1", available = false),
            TrackMenuEntry("Amber", "Ripple", "", null, "usb:2"),
            TrackMenuEntry("Other", "Pebble", "Album", "1999", "usb:3")
        )
        val artists = groupArtists(tracks)
        assertEquals(listOf("Pebble", "Ripple"), artists.map { it.name })
        val ripple = artists.last()
        assertEquals(3, ripple.tracks.size)
        assertEquals(2, ripple.albums.size)
        assertEquals(listOf("Amber", "Amber", "Zulu"), ripple.tracks.map { it.title })
        assertEquals(3, ripple.tracks.map { it.id }.distinct().size)
        assertFalse(ripple.tracks.first { it.id == "sd:1" }.available)
        assertEquals("Unknown year", ripple.albums.first { it.title == "Unknown album" }.subtitle)
        assertEquals("1999", artists.first().albums.single().subtitle)
    }

    @Test fun missingArtistAndAlbumUseFallbacks() {
        val artist = groupArtists(listOf(TrackMenuEntry("Song", "  ", "  ", " ", "1"))).single()
        assertEquals("Unknown artist", artist.name)
        assertEquals("Unknown album", artist.albums.single().title)
        assertEquals("Unknown year", artist.albums.single().subtitle)
    }

    @Test fun alphabeticalSortingIgnoresCaseAndBreaksDuplicateTitleTiesByIdentity() {
        val artist = groupArtists(listOf(
            TrackMenuEntry("zebra", "beta", "z", id = "3"),
            TrackMenuEntry("Amber", "beta", "Amber", id = "2"),
            TrackMenuEntry("Amber", "beta", "Amber", id = "1"),
            TrackMenuEntry("song", "Alpha", "a", id = "4")
        ))
        assertEquals(listOf("Alpha", "beta"), artist.map { it.name })
        assertEquals(listOf("1", "2", "3"), artist.last().tracks.map { it.id })
        assertEquals(listOf("Amber", "z"), artist.last().albums.map { it.title })
    }

    @Test fun demoCountsAndSelectionsAreScopedToArtist() {
        assertEquals(1, demoArtists.first { it.name == "Ripple" }.albums.size)
        assertEquals(2, demoArtists.first { it.name == "Saffron" }.albums.size)
        assertEquals(3, demoArtists.first { it.name == "Summit" }.albums.size)
        assertEquals(demoArtistTracks.size, demoArtistTracks.map { it.id }.distinct().size)
        assertTrue(demoArtists.all { artist -> artist.tracks.all { it.artist == artist.name } })
    }
}

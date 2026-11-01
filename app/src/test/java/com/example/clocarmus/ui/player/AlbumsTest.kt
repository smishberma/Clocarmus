package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test

class AlbumsTest {
    @Test fun identitiesUseAlbumArtistAndKeepCopiesAndDisconnectedTracks() {
        val tracks = listOf(
            TrackMenuEntry("One", "Singer A", "Shared", "2001", "usb", albumArtist = "Various Artists"),
            TrackMenuEntry("Two", "Singer B", "Shared", "2002", "sd", available = false, albumArtist = "Various Artists"),
            TrackMenuEntry("Copy", "Singer A", "Shared", "2002", "copy", albumArtist = "Various Artists"),
            TrackMenuEntry("Other", "Singer A", "Shared", id = "other")
        )
        val albums = groupAlbums(tracks)
        assertEquals(2, albums.size)
        val compilation = albums.first { it.identity.artist == "Various Artists" }
        assertEquals(3, compilation.tracks.size)
        assertEquals("2002", compilation.year)
        assertEquals("Various Artists - 2002 - 3 songs", compilation.subtitle)
        assertEquals(1, compilation.tracks.count { !it.available })
        assertEquals("Singer A - Unknown year - 1 song", albums.first().subtitle)
    }

    @Test fun numericDiscAndTrackOrderPutsUnnumberedLastAndBreaksTies() {
        fun track(id: String, disc: Int?, number: Int?, title: String = id) =
            TrackMenuEntry(title, "A", "B", id = id, discNumber = disc, trackNumber = number)
        val sorted = albumTrackOrder(listOf(
            track("disc2", 2, 1), track("ten", 1, 10), track("two", 1, 2),
            track("missing-z", 1, null, "Zulu"), track("missing-a", 1, 0, "Amber"),
            track("first", null, 1), track("same-b", 0, 2, "two"), track("same-a", -1, 2, "two")
        ))
        assertEquals(listOf("first", "same-a", "same-b", "two", "ten", "missing-a", "missing-z", "disc2"), sorted.map { it.id })
    }

    @Test fun fallbacksDurationsAndYearTies() {
        val missing = TrackMenuEntry("Song", " ", " ", albumArtist = " ")
        assertEquals(AlbumIdentity("Unknown album", "Unknown artist"), missing.albumIdentity)
        assertEquals("-- - Song", albumTrackRow(missing).title)
        assertEquals("--:--", albumTrackRow(missing).subtitle)
        assertEquals("03:30", albumTrackRow(missing.copy(trackNumber = 1, durationSeconds = 210)).subtitle)
        assertEquals("01 - Song", albumTrackRow(missing.copy(trackNumber = 1)).title)
        assertEquals("1999", groupAlbums(listOf(missing.copy(year = "2002"), missing.copy(year = "1999"))).single().year)
    }

    @Test fun equalArtistNamesHaveSeparateScopesAndArtistCountsStillUseTitles() {
        val tracks = listOf(
            TrackMenuEntry("A", "Same", "Shared", id = "1", albumArtist = "Other"),
            TrackMenuEntry("B", "Other", "Shared", id = "2", albumArtist = "Same"),
            TrackMenuEntry("C", "Same", "Shared", id = "3", albumArtist = "Same")
        )
        assertEquals(listOf("1", "3"), artistCollection(tracks, "Same", ArtistScope.TRACK).map { it.id })
        assertEquals(listOf("2", "3"), artistCollection(tracks, "Same", ArtistScope.ALBUM).map { it.id })
        val artist = groupArtists(tracks).first { it.name == "Same" }
        assertEquals(1, artist.distinctAlbumCount)
        assertEquals(2, artist.albums.size)
        val full = groupAlbums(tracks).first { it.identity.artist == "Same" }
        assertEquals(listOf("2", "3"), full.tracks.map { it.id })
    }

    @Test fun albumSearchPreservesOrderAndFiltersOnlyTitles() {
        val tracks = albumTrackOrder(listOf(
            TrackMenuEntry("Match ten", "A", "B", id = "10", trackNumber = 10),
            TrackMenuEntry("Match two", "A", "B", id = "2", trackNumber = 2),
            TrackMenuEntry("Other", "Match", "B", id = "1", trackNumber = 1)
        )).filter { it.title.contains("match", true) }
        assertEquals(listOf("2", "10"), tracks.map { it.id })
    }
    @Test fun unavailableSelectionSkipsOnlyWithinQueueAndStopsWhenExhausted() {
        val unavailable = TrackMenuEntry("A", "A", "B", id = "1", available = false)
        val available = unavailable.copy(id = "2", available = true)
        assertEquals("2", playableTrack(listOf(unavailable, available), "1")?.id)
        assertNull(playableTrack(listOf(unavailable), "1"))
        assertNull(playableTrack(listOf(available), "absent"))
        assertNull(playableTrack(emptyList(), "1"))
    }
}

package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class GenresTest {
    private fun track(id: String, vararg genres: String) = TrackMenuEntry(
        id, "Singer", "Album", id = id, genres = genres.toList())

    @Test fun normalizesExplicitValuesWithoutSplittingNamesOrInflatingMembership() {
        val tracks = listOf(track("1", " Rock ", "rock", "Folk"), track("2", "ROCK"),
            track("3", "Pop/Rock"), track("4"), track("5", "  "),
            track("copy", "Rock").copy(available = false))
        val genres = groupGenres(tracks + tracks.first())
        assertEquals(listOf("Folk", "Pop/Rock", "Rock", "Unknown genre"), genres.map { it.name })
        assertEquals(listOf("1", "2", "copy"), genreCollection(tracks, " ROCK ").map { it.id })
        assertEquals(2, genres.first { it.key == "unknown genre" }.tracks.size)
        assertEquals(1, genres.first { it.key == "pop/rock" }.tracks.size)
        assertFalse(genreCollection(tracks, "Rock").last().available)
        assertTrue(genreCollection(tracks, "missing").isEmpty())
    }

    @Test fun matchingUsesStableLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("indie", genreKey(" INDIE "))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun partialAlbumsQualifyWithFullCountsWithoutPropagatingGenre() {
        val library = (1..12).map { n -> track("$n", if (n <= 3) "Rock" else "Jazz")
            .copy(albumArtist = "Various Artists", trackNumber = n, available = n != 12) } +
            track("unrelated", "Jazz").copy(album = "Other")
        val matching = genreCollection(library, "rock")
        val album = genreAlbums(library, matching).single()
        assertEquals(3, matching.size)
        assertEquals(12, album.tracks.size)
        assertEquals("Various Artists - Unknown year - 12 songs", album.subtitle)
        assertEquals((1..12).map(Int::toString), album.tracks.map { it.id })
        assertEquals(3, genreCollection(album.tracks, "Rock").size)
        assertEquals(12, artistCollection(library, "Various Artists", ArtistScope.ALBUM).size)
    }

    @Test fun artistScopeCountsOnlyMatchingAlbumTitlesAndKeepsCompilationIdentity() {
        val library = listOf(
            track("b", "Rock").copy(album = "Shared", albumArtist = "One"),
            track("a", "Rock").copy(album = "Shared", albumArtist = "Two"),
            track("c", "Jazz").copy(album = "Nonmatching"),
            track("d", "Rock").copy(artist = "Other", album = "Shared", albumArtist = "One"),
            track("e", "Rock").copy(album = " ")
        )
        val matching = genreCollection(library, "Rock")
        val artist = groupArtists(matching).first { it.name == "Singer" }
        assertEquals(2, artist.distinctAlbumCount)
        assertEquals(3, artist.albums.size)
        assertEquals(listOf("a", "b", "e"), artistCollection(matching, "Singer", ArtistScope.TRACK).map { it.id })
        val compilation = genreAlbums(library, artist.tracks).first { it.identity.artist == "One" }
        assertEquals(listOf("b", "d"), compilation.tracks.map { it.id })
    }

    @Test fun emptyOrUnavailableGenresCannotFallBackToUnrelatedTracks() {
        val library = listOf(track("a", "Rock").copy(available = false), track("b", "Jazz"))
        val rock = genreCollection(library, "Rock")
        assertNull(playableTrack(rock, "a"))
        assertNull(playableTrack(genreCollection(library, "Missing"), "b"))
    }
}

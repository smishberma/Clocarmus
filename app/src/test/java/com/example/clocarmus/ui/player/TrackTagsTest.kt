package com.example.clocarmus.ui.player

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.*
import org.junit.Test

class TrackTagsTest {
    private fun track(tags: TrackTags? = null, available: Boolean = true) =
        TrackMenuEntry("Title", "Unknown artist", "Unknown album", "Unknown year",
            available = available, trackNumber = 99, tags = tags)

    @Test fun allTenFieldsStayInOrderAndMissingTagsNeverUseLibraryFallbacks() {
        val fields = trackInfoContent(track()).fields
        assertEquals(listOf("Artist(s)", "Album", "Year", "Comment", "Description",
            "Total album tracks", "Track number", "Composer", "Copyright", "Publisher"), fields.map { it.first })
        assertTrue(fields.all { it.second == null })
    }

    @Test fun compoundTrackTagSuppliesBothNumbersWithoutInferringATotal() {
        assertEquals("3", TrackTags(trackNumber = "3/12").fields().toMap()["Track number"])
        assertEquals("12", TrackTags(trackNumber = "3/12").fields().toMap()["Total album tracks"])
        assertNull(TrackTags(trackNumber = "3").fields().toMap()["Total album tracks"])
        assertEquals("15", TrackTags(trackNumber = "3/12", totalAlbumTracks = "15").fields().toMap()["Total album tracks"])
    }

    @Test fun artistOrderSpellingAndSeparatorsArePreservedAndDateUsesYear() {
        val fields = TrackTags(artists = listOf("AC/DC", "Simon & Garfunkel", "  Björk  "),
            releaseDate = "2004-06-15").fields().toMap()
        assertEquals("AC/DC, Simon & Garfunkel,   Björk  ", fields["Artist(s)"])
        assertEquals("2004", fields["Year"])
        assertEquals("2004", TrackTags(releaseDate = "2004").fields().toMap()["Year"])
    }

    @Test fun blankValuesAreEmptyWhileMultilineCommentAndDescriptionStaySeparate() {
        val longText = "A long line of text. ".repeat(100) + "\nA second line."
        val fields = TrackTags(artists = listOf(" ", "\n"), album = "\t", releaseDate = " ",
            comment = longText, description = "Independent description", composer = " ").fields().toMap()
        assertNull(fields["Artist(s)"]); assertNull(fields["Album"]); assertNull(fields["Year"])
        assertNull(fields["Composer"])
        assertEquals(longText, fields["Comment"])
        assertEquals("Independent description", fields["Description"])
    }

    @Test fun disconnectedTracksDistinguishCachedEmptyTagsFromNoCache() {
        val cached = trackInfoContent(track(TrackTags(), false))
        assertEquals(10, cached.fields.size)
        assertEquals("File unavailable — showing saved tags", cached.notice)
        val missing = trackInfoContent(track(available = false))
        assertTrue(missing.fields.isEmpty())
        assertEquals("File unavailable — no saved tags available", missing.notice)
        assertEquals(missing, trackInfoContent(null))
    }

    @Test fun rawSnapshotSurvivesStorageRoundTripWithEveryFieldIntact() {
        val tags = TrackTags(listOf("AC/DC", "Artist"), "Album", "2004-06-15", "Comment\nline 2",
            "Description", "12", "3/12", "Composer", "Copyright", "Publisher")
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(tags) }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
        assertEquals(tags, restored)
    }
}

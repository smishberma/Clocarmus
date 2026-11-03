package com.example.clocarmus.ui.player

import java.util.Locale

internal fun genreKey(value: String): String = value.trim().ifBlank { "Unknown genre" }.lowercase(Locale.ROOT)

internal data class GenreEntry(val key: String, val name: String, val tracks: List<TrackMenuEntry>)

/** Only explicit metadata values create memberships; a slash is part of a genre's name. */
internal fun groupGenres(tracks: List<TrackMenuEntry>): List<GenreEntry> {
    val names = linkedMapOf<String, String>()
    val members = linkedMapOf<String, MutableList<TrackMenuEntry>>()
    tracks.forEach { track ->
        track.genres.ifEmpty { listOf("") }.distinctBy(::genreKey).forEach { value ->
            val key = genreKey(value)
            names.getOrPut(key) { value.trim().ifBlank { "Unknown genre" } }
            members.getOrPut(key) { mutableListOf() }.add(track)
        }
    }
    return members.map { (key, entries) ->
        GenreEntry(key, names.getValue(key), alphabeticalTracks(entries.distinctBy { it.id }))
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

internal fun alphabeticalTracks(tracks: List<TrackMenuEntry>): List<TrackMenuEntry> = tracks.sortedWith(
    compareBy<TrackMenuEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id })

internal fun genreCollection(tracks: List<TrackMenuEntry>, genre: String?): List<TrackMenuEntry> =
    if (genre == null) tracks else groupGenres(tracks).firstOrNull { it.key == genreKey(genre) }?.tracks.orEmpty()

/** Qualify using matching tracks, then count and open the full album from the library. */
internal fun genreAlbums(library: List<TrackMenuEntry>, matching: List<TrackMenuEntry>): List<AlbumEntry> {
    val identities = matching.map { it.albumIdentity }.toSet()
    return groupAlbums(library).filter { it.identity in identities }
}

// Reference-only fixtures keep mockup labels independent of the interactive demo library.
internal val genreReferenceTracks = listOf("New Day", "Oh, My", "Pebble", "Queen of me", "Stay with me", "Thistle", "Umbra").mapIndexed { i, title ->
    TrackMenuEntry(title, "Artist", "Album", id = "genre:track:$i", genres = listOf("Alternative Rock"))
}

package com.example.clocarmus.ui.player

import java.io.Serializable

internal enum class ArtistScope { TRACK, ALBUM }
internal data class AlbumIdentity(val title: String, val artist: String) : Serializable {
    val key: String get() = "${title.length}:$title$artist"
}
internal val TrackMenuEntry.effectiveAlbumArtist: String
    get() = albumArtist?.takeIf { it.isNotBlank() } ?: artist.ifBlank { "Unknown artist" }
internal val TrackMenuEntry.albumIdentity: AlbumIdentity
    get() = AlbumIdentity(album.ifBlank { "Unknown album" }, effectiveAlbumArtist)

internal data class AlbumEntry(val identity: AlbumIdentity, val tracks: List<TrackMenuEntry>) {
    val year: String get() = tracks.mapNotNull { it.year?.takeIf(String::isNotBlank) }
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()?.key ?: "Unknown year"
    val subtitle: String get() = "${identity.artist} - $year - ${tracks.size} ${if (tracks.size == 1) "song" else "songs"}"
}
internal fun albumTrackOrder(tracks: List<TrackMenuEntry>): List<TrackMenuEntry> = tracks.sortedWith(
    compareBy<TrackMenuEntry> { it.discNumber?.takeIf { n -> n > 0 } ?: 1 }
        .thenBy { it.trackNumber?.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id })
internal fun groupAlbums(tracks: List<TrackMenuEntry>): List<AlbumEntry> = tracks.groupBy { it.albumIdentity }
    .map { (id, entries) -> AlbumEntry(id, albumTrackOrder(entries)) }
    .sortedWith(compareBy<AlbumEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.identity.title }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.identity.artist })
internal fun artistCollection(tracks: List<TrackMenuEntry>, name: String, scope: ArtistScope): List<TrackMenuEntry> =
    tracks.filter { (if (scope == ArtistScope.ALBUM) it.effectiveAlbumArtist else it.artist.ifBlank { "Unknown artist" }) == name }
        .sortedWith(compareBy<TrackMenuEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id })
internal fun albumTrackRow(track: TrackMenuEntry): BrowserRow {
    val number = track.trackNumber?.takeIf { it > 0 }?.toString()?.padStart(2, '0') ?: "--"
    val duration = track.durationSeconds?.takeIf { it >= 0 }?.let {
        "${(it / 60).toString().padStart(2, '0')}:${(it % 60).toString().padStart(2, '0')}"
    } ?: "--:--"
    return BrowserRow(track.id, "$number - ${track.title}", duration)
}

/** Every pushed destination preserves its actual parent, including compilation shortcuts. */
internal data class BrowserDestination(
    val page: String, val artist: String? = null, val scope: ArtistScope = ArtistScope.TRACK,
    val album: AlbumIdentity? = null, val search: Boolean = false, val playlistId: String? = null,
    val genre: String? = null, val directory: String? = null
) : Serializable

internal val demoAlbumTracks = demoTrackTitles.flatMapIndexed { albumIndex, album ->
    val titles = listOf("Pebble", "Asphalt", "Cake", "Saffron", "Break", "Ember", "Fern", "Glimmer", "Harbor", "Iris", "Juniper", "Kite", "Lantern")
    titles.mapIndexed { index, title ->
        TrackMenuEntry(title, "Artist", album,
            when (album) { "Pebble" -> "1996"; "Quartz" -> "2001"; "Ripple" -> "2005"; "Saffron" -> "2020"; "Summit" -> "1982"; else -> (1990 + albumIndex).toString() },
            id = "album:$album:$index", albumArtist = "Artist", discNumber = 1, trackNumber = index + 1,
            durationSeconds = listOf(210, 250, 236, 300, 257)[index % 5])
    }
} + listOf(
    TrackMenuEntry("Together", "Ripple", "Road collection", "2024", "compilation:1", albumArtist = "Various Artists", trackNumber = 2, durationSeconds = 210),
    TrackMenuEntry("Journey", "Saffron", "Road collection", "2024", "compilation:2", albumArtist = "Various Artists", trackNumber = 10, durationSeconds = 245)
)
internal val demoLibraryTracks = (demoArtistTracks + demoAlbumTracks + marqueeDemoTrack).mapIndexed { index, track ->
    track.copy(genres = when {
        track.id == marqueeDemoTrack.id -> listOf("Alternative Rock")
        track.title in setOf("Pebble", "New Day", "Oh, My", "Queen of me", "Stay with me") -> listOf("Alternative Rock")
        index % 41 == 0 -> emptyList()
        index % 43 == 0 -> listOf(" Rock ", "rock", "Pop/Rock")
        index % 47 == 0 -> listOf("Folk", "Acoustic")
        else -> listOf(listOf("Acoustic", "African", "Classical", "Folk", "Jazz", "Pop", "Rock")[index % 7])
    })
}

/** Finite traversal of the displayed queue; never falls back to an unrelated collection. */
internal fun playableTrack(tracks: List<TrackMenuEntry>, selectedId: String): TrackMenuEntry? {
    val index = tracks.indexOfFirst { it.id == selectedId }
    if (index < 0) return null
    return (tracks.drop(index) + tracks.take(index)).firstOrNull { it.available }
}

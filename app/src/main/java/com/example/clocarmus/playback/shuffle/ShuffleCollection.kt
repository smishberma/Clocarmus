package com.example.clocarmus.playback.shuffle

import com.example.clocarmus.library.LibrarySnapshot
import com.example.clocarmus.ui.player.*
import java.util.Locale
import java.util.UUID

/** Semantic identity is independent of membership, browser state and generated queue order. */
internal data class ShuffleCollection(
    val kind: String = "manual",
    val token: String = UUID.randomUUID().toString(),
    val artist: String? = null,
    val album: AlbumIdentity? = null,
    val genre: String? = null,
    val artistScope: ArtistScope = ArtistScope.TRACK,
    val directory: String? = null,
    val query: String = ""
) {
    val key: String get() = listOf(kind, if (kind in setOf("manual", "snapshot", "playlist")) token else "",
        artist.orEmpty(), album?.key.orEmpty(), genre.orEmpty(), artistScope.name, directory.orEmpty(),
        query.lowercase(Locale.ROOT)).joinToString("") { "${it.length}:$it" }

    fun resolve(snapshot: LibrarySnapshot, retained: List<String>, eligibleOnly: Boolean = true): List<String> {
        val tracks = snapshot.tracks
        val indexed = tracks.associateBy { it.id }
        val members = when (kind) {
            "manual", "snapshot" -> retained.mapNotNull(indexed::get)
            "playlist" -> {
                val deleted = token !in builtInPlaylists && snapshot.playlists.custom.none { it.id == token }
                (if (deleted) retained else snapshot.playlists.trackIds(token)).mapNotNull(indexed::get)
            }
            "folder" -> return snapshot.folders.queue(directory, query).filter { !eligibleOnly || it.available }.map { it.id }
            "album" -> albumTrackOrder(tracks.filter { it.albumIdentity == album })
            "artist" -> artistCollection(genreCollection(tracks, genre), artist.orEmpty(), artistScope)
            "genre" -> genreCollection(tracks, genre)
            "all" -> alphabeticalTracks(tracks)
            else -> error("Unknown playback collection: $kind")
        }
        return members.filter { (!eligibleOnly || it.available) && (kind == "folder" || it.title.contains(query, true)) }.map { it.id }
    }
}

internal data class ShuffleSession(
    val collection: ShuffleCollection = ShuffleCollection(),
    val retained: List<String> = emptyList(),
    val generated: List<String> = emptyList(),
    val lastAudible: String? = null,
    val pendingRemainder: Boolean = false,
    val freshSelection: Boolean = false,
    val historyRevision: Long = 0
)

internal data class ShuffleHistory(val order: List<String> = emptyList(), val revision: Long = 0)

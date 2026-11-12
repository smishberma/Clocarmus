package com.example.clocarmus.library

import com.example.clocarmus.ui.player.*
import java.io.Serializable

internal data class LibraryDirectory(val source: String, val id: String, val name: String, val parent: String?, val available: Boolean = true) : Serializable

internal enum class Availability { AVAILABLE, DISCONNECTED, ACCESS_LOST, MISSING, UNREADABLE }
internal data class LibraryPath(val source: String, val directories: List<String>, val names: List<String>) : Serializable
internal data class LibraryRecord(
    val id: String, val location: String, val uri: String, val filename: String,
    val size: Long, val modified: Long, val fingerprint: String,
    val track: TrackMenuEntry, val importedAt: Long,
    val paths: List<LibraryPath>, val availability: Availability = Availability.AVAILABLE
) : Serializable
internal data class ScanSummary(
    val added: Int = 0, val updated: Int = 0, val missing: Int = 0,
    val failures: List<String> = emptyList(), val incomplete: Boolean = false,
    val running: Boolean = false
)
internal data class LibrarySnapshot(
    val records: List<LibraryRecord> = emptyList(), val playlists: PlaylistLibrary = PlaylistLibrary(),
    val folders: FolderTree = FolderTree(emptyList()), val version: Long = 0,
    val summary: ScanSummary = ScanSummary(), val ready: Boolean = false
) {
    val byId = records.associateBy { it.id }
    val tracks = records.map { it.track.copy(id = it.id, available = it.availability == Availability.AVAILABLE) }
    val tracksById = tracks.associateBy { it.id }
}

internal fun PlaylistLibrary.retaining(ids: Set<String>) = copy(
    custom = custom.map { it.copy(tracks = it.tracks.filter(ids::contains)) },
    favorites = favorites.filter { it.id in ids }, imported = imported.filter { it.id in ids },
    played = played.filter { it.id in ids })

/** A fingerprint is relocation evidence only when exactly one old and one new copy exist.
 * The caller must establish complete absence of the old location first.
 */
internal fun relocationMatches(old: List<LibraryRecord>, incoming: List<LibraryRecord>, missing: Set<String>): Map<String, String> {
    val oldHashes = old.groupBy { it.fingerprint }
    val newHashes = incoming.groupBy { it.fingerprint }
    return incoming.mapNotNull { candidate ->
        val previous = oldHashes[candidate.fingerprint]?.singleOrNull()
        if (candidate.fingerprint.isNotEmpty() && previous != null && previous.id in missing &&
            newHashes[candidate.fingerprint]?.size == 1) candidate.id to previous.id else null
    }.toMap()
}

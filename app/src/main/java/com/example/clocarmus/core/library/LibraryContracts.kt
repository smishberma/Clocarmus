package com.example.clocarmus.core.library

import kotlinx.coroutines.flow.Flow

@JvmInline value class TrackId(val value: Long) { init { require(value > 0) } }
@JvmInline value class SourceId(val value: Long) { init { require(value > 0) } }

/** Provider document identity, independent of the tree grant used to reach it.
 * Document IDs are opaque: never infer ancestry with string-prefix comparisons.
 */
data class DocumentKey(val authority: String, val documentId: String) {
    init { require(authority.isNotBlank() && documentId.isNotBlank()) }
}

data class LibrarySource(
    val id: SourceId,
    val treeUri: String,
    val root: DocumentKey,
    val excludedDirectories: Set<DocumentKey> = emptySet()
)

enum class Availability { AVAILABLE, DISCONNECTED, PERMISSION_REQUIRED, CONFIRMED_MISSING, READ_FAILED }

/** null metadata means not cached; an empty value means the file had no tags.
 * Display fallbacks belong in presentation, never in this raw record.
 */
data class RawTrackMetadata(
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val albumArtist: String? = null,
    val album: String? = null,
    val genres: List<String> = emptyList(),
    val releaseDate: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val totalAlbumTracks: String? = null,
    val comment: String? = null,
    val description: String? = null,
    val composer: String? = null,
    val copyright: String? = null,
    val publisher: String? = null
)

data class LibraryTrack(
    val id: TrackId,
    val document: DocumentKey,
    val filename: String,
    val availability: Availability,
    val durationMs: Long?,
    val metadata: RawTrackMetadata?
) {
    init { require(durationMs == null || durationMs >= 0) }
}

enum class CollectionKind { ALL_TRACKS, ARTIST, ALBUM, PLAYLIST, GENRE, FOLDER, MANUAL }

/** stableKey includes scope (e.g. Album Artist, genre artist filter, folder recursion).
 * A search stores its normalized query here, independently of later browser edits.
 */
data class CollectionKey(
    val kind: CollectionKind,
    val stableKey: String,
    val normalizedQuery: String? = null
) { init { require(stableKey.isNotBlank()) } }

/** Changes only for membership, availability or collection order, not metadata edits. */
data class CollectionRevision(val membership: Long, val availability: Long, val order: Long) {
    init { require(membership >= 0 && availability >= 0 && order >= 0) }
}

/** Compact original collection order. Manual collections retain requested multiplicities. */
class CollectionSnapshot(
    val key: CollectionKey,
    val revision: CollectionRevision,
    trackIds: LongArray
) {
    private val ids = trackIds.copyOf()
    init {
        require(ids.all { it > 0 })
        require(key.kind == CollectionKind.MANUAL || ids.toSet().size == ids.size)
    }
    val size: Int get() = ids.size
    fun trackAt(index: Int): TrackId = TrackId(ids[index])
    fun copyTrackIds(): LongArray = ids.copyOf()
}

/** Implementations perform IO off-main and read each snapshot in one consistent transaction.
 * Paged reads must retain the requested revision or report that it is stale.
 */
interface LibraryRepository {
    fun observeTrack(id: TrackId): Flow<LibraryTrack?>
    suspend fun snapshot(key: CollectionKey): CollectionSnapshot
    suspend fun tracks(ids: List<TrackId>): Map<TrackId, LibraryTrack>
}

/** Storage implementations resolve ancestry through the provider, deduplicate DocumentKey,
 * and check exclusions per source. Read grants suffice; do not request write access.
 */
interface StorageAccess {
    suspend fun availability(source: LibrarySource): Availability
    suspend fun isDescendant(source: LibrarySource, directory: DocumentKey, child: DocumentKey): Boolean
}

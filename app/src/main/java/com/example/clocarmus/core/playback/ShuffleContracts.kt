package com.example.clocarmus.core.playback

import com.example.clocarmus.core.library.CollectionKey
import com.example.clocarmus.core.library.CollectionRevision
import com.example.clocarmus.core.library.TrackId

/** Increment intentRevision on pause/stop, ordering/scheduling edits, or a new request.
 * Metadata-only writes do not invalidate this token. Compare again on the owner thread
 * immediately before activation; a delayed worker result cannot undo an intervening pause.
 */
data class PreparationToken(
    val queueVersion: QueueVersion,
    val collection: CollectionKey,
    val collectionRevision: CollectionRevision,
    val intentRevision: Long,
    val lastAudibleTrack: TrackId?
) { init { require(intentRevision >= 0) } }

/** OS-backed fresh entropy throughout a generation, buffered on a worker; no fixed seed
 * replay scheme. The implementation must establish unbiased bounded draws/reachability.
 */
interface BoundedRandom {
    suspend fun nextInt(boundExclusive: Int): Int
}

/** Relevant history is read on demand. Preparation never commits history; only successful
 * activation commits its originally generated order with the session transaction.
 * Feasibility, duplicate-aware freshness and unbiased rejection remain implementation work.
 */
interface ShuffleHistoryStore {
    suspend fun generatedOrder(collection: CollectionKey): LongArray?
}

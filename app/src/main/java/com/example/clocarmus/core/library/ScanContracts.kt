package com.example.clocarmus.core.library

import kotlinx.coroutines.flow.Flow

enum class ScanOutcome { RUNNING, COMPLETE, CANCELLED, INCOMPLETE }

data class ScanSummary(
    val outcome: ScanOutcome,
    val added: Int = 0,
    val updated: Int = 0,
    val confirmedMissing: Int = 0,
    val failedFiles: Int = 0
) {
    init { require(added >= 0 && updated >= 0 && confirmedMissing >= 0 && failedFiles >= 0) }
}

/** Evidence is scoped to one successfully traversed accessible directory, not an entire drive.
 * Partial imports survive failure; only a successful traversal may establish absence.
 */
data class TraversalEvidence(
    val outcome: ScanOutcome,
    val access: Availability
) {
    val canConfirmAbsence: Boolean
        get() = outcome == ScanOutcome.COMPLETE && access == Availability.AVAILABLE
}

/** One coordinated scan. Coalesce requests, but retain dirtied-after-visit scopes for a
 * follow-up pass. Cancellation must not turn unvisited records into missing records.
 */
interface LibraryScanner {
    val summary: Flow<ScanSummary>
    suspend fun request(sourceIds: Set<SourceId>)
    suspend fun cancel()
}

/** Full-file SHA-256 plus size; partial hashes/title/duration alone are not relocation proof.
 * Hashing belongs on a worker and may be deferred; lack of evidence means a separate import.
 */
data class ContentFingerprint(val sha256: String, val byteCount: Long) {
    init { require(sha256.matches(Regex("[0-9a-f]{64}")) && byteCount >= 0) }
}

data class KnownFingerprint(
    val id: TrackId,
    val document: DocumentKey,
    val fingerprint: ContentFingerprint,
    val availability: Availability
)
data class DiscoveredFingerprint(val document: DocumentKey, val fingerprint: ContentFingerprint)
data class RelocationMatch(val trackId: TrackId, val newDocument: DocumentKey)

/** Inputs contain ALL known copies and discoveries in the reconciliation scope, including
 * accessible copies. Caller must retain unmatched records and import unmatched discoveries.
 * Returns only 1:1 moves from confirmed absence; duplicate copies never inherit references.
 * Provider identity at the same location is reconciled separately from relocation.
 */
fun matchUniqueRelocations(
    known: List<KnownFingerprint>,
    discovered: List<DiscoveredFingerprint>
): List<RelocationMatch> {
    require(known.map { it.id }.toSet().size == known.size)
    require(known.map { it.document }.toSet().size == known.size)
    require(discovered.map { it.document }.toSet().size == discovered.size)
    val oldGroups = known.groupBy { it.fingerprint }
    val newGroups = discovered.groupBy { it.fingerprint }
    val knownDocuments = known.map { it.document }.toSet()
    return oldGroups.mapNotNull { (fingerprint, copies) ->
        val old = copies.singleOrNull() ?: return@mapNotNull null
        val new = newGroups[fingerprint]?.singleOrNull() ?: return@mapNotNull null
        if (old.availability != Availability.CONFIRMED_MISSING || old.document == new.document ||
            new.document in knownDocuments) null
        else RelocationMatch(old.id, new.document)
    }
}

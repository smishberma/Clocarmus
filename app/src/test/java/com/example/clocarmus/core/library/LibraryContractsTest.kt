package com.example.clocarmus.core.library

import org.junit.Assert.*
import org.junit.Test

class LibraryContractsTest {
    private val fingerprint = ContentFingerprint("a".repeat(64), 100)
    private fun document(id: String) = DocumentKey("local.provider", id)
    private fun known(id: Long, location: String, availability: Availability = Availability.CONFIRMED_MISSING) =
        KnownFingerprint(TrackId(id), document(location), fingerprint, availability)
    private fun found(location: String) = DiscoveredFingerprint(document(location), fingerprint)

    @Test fun uniqueMoveRetainsStableIdentity() {
        assertEquals(listOf(RelocationMatch(TrackId(7), document("new"))),
            matchUniqueRelocations(listOf(known(7, "old")), listOf(found("new"))))
    }

    @Test fun identicalOldCopiesNeverGuessWhichReferencesToTransfer() {
        assertTrue(matchUniqueRelocations(listOf(known(1, "a"), known(2, "b")), listOf(found("c"))).isEmpty())
    }

    @Test fun identicalNewCopiesNeverGuessTheDestination() {
        assertTrue(matchUniqueRelocations(listOf(known(1, "a")), listOf(found("b"), found("c"))).isEmpty())
    }

    @Test fun accessibleCopyMakesRelocationAmbiguous() {
        assertTrue(matchUniqueRelocations(listOf(known(1, "a"), known(2, "b", Availability.AVAILABLE)),
            listOf(found("c"))).isEmpty())
    }

    @Test fun temporaryAccessLossIsNeverEvidenceOfRelocation() {
        for (availability in Availability.entries.filter { it != Availability.CONFIRMED_MISSING }) {
            assertTrue(matchUniqueRelocations(listOf(known(1, "old", availability)), listOf(found("new"))).isEmpty())
        }
    }

    @Test fun sameLocationAndDifferentContentDoNotBecomeMoves() {
        assertTrue(matchUniqueRelocations(listOf(known(1, "a")), listOf(found("a"))).isEmpty())
        assertTrue(matchUniqueRelocations(listOf(known(1, "a")), listOf(
            DiscoveredFingerprint(document("b"), ContentFingerprint("b".repeat(64), 100)))).isEmpty())
    }

    @Test fun newLocationAlreadyOwnedByAnotherTrackDoesNotInheritReferences() {
        val other = known(2, "new").copy(fingerprint = ContentFingerprint("b".repeat(64), 100))
        assertTrue(matchUniqueRelocations(listOf(known(1, "old"), other), listOf(found("new"))).isEmpty())
    }

    @Test fun onlyCompletedAccessibleTraversalCanConfirmMissingFiles() {
        for (outcome in ScanOutcome.entries) for (access in Availability.entries) {
            assertEquals(outcome == ScanOutcome.COMPLETE && access == Availability.AVAILABLE,
                TraversalEvidence(outcome, access).canConfirmAbsence)
        }
    }

    @Test fun collectionSnapshotRetainsOrderAndDoesNotShareMutableBuffers() {
        val ids = longArrayOf(3, 2, 1)
        val snapshot = CollectionSnapshot(CollectionKey(CollectionKind.PLAYLIST, "playlist:1"),
            CollectionRevision(1, 2, 3), ids)
        ids[0] = 99
        snapshot.copyTrackIds()[1] = 99
        assertArrayEquals(longArrayOf(3, 2, 1), snapshot.copyTrackIds())
    }

    @Test fun manualCollectionPreservesDuplicates() {
        val snapshot = CollectionSnapshot(CollectionKey(CollectionKind.MANUAL, "session:1"),
            CollectionRevision(0, 0, 0), longArrayOf(1, 1))
        assertEquals(2, snapshot.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun automaticCollectionCannotAccidentallyRepeatTrackIdentities() {
        CollectionSnapshot(CollectionKey(CollectionKind.ALL_TRACKS, "library"),
            CollectionRevision(0, 0, 0), longArrayOf(1, 1))
    }
}

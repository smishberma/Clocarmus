package com.example.clocarmus.core.playback

import com.example.clocarmus.core.library.*
import org.junit.Assert.*
import org.junit.Test

class SessionContractsTest {
    private val source = CollectionSnapshot(CollectionKey(CollectionKind.MANUAL, "manual:1"),
        CollectionRevision(1, 1, 1), longArrayOf(7, 7))
    private fun queue(version: Long = 1) = QueueSnapshot(QueueVersion(version), source,
        longArrayOf(10, 11), longArrayOf(7, 7))
    private fun progress() = ShuffleProgress(QueueVersion(1), longArrayOf(7, 7), longArrayOf(10), longArrayOf())

    @Test fun exactDuplicateOccurrenceRemainsAddressable() {
        val queue = queue()
        assertEquals(queue.trackAt(0), queue.trackAt(1))
        assertNotEquals(queue.occurrenceAt(0), queue.occurrenceAt(1))
        assertTrue(PlaybackCheckpoint(queue.version, OccurrenceId(11)).isCompatibleWith(queue))
    }

    @Test fun mixedVersionCursorIsRejectedEvenIfOccurrenceStillExists() {
        assertFalse(PlaybackCheckpoint(QueueVersion(1), OccurrenceId(10)).isCompatibleWith(queue(2)))
    }

    @Test fun danglingCursorAndStopMarkerAreRejected() {
        assertFalse(PlaybackCheckpoint(QueueVersion(1), OccurrenceId(99)).isCompatibleWith(queue()))
        assertFalse(PlaybackCheckpoint(QueueVersion(1), OccurrenceId(10), stopAfter = OccurrenceId(99))
            .isCompatibleWith(queue()))
    }

    @Test fun restorationIsPausedAndRetainsPriorPlaybackEvidenceAndPosition() {
        val checkpoint = PlaybackCheckpoint(QueueVersion(1), OccurrenceId(11), 42_000,
            RepeatMode.ALL, true, OccurrenceId(11), wasPlaying = true)
        val restored = RestoredSession(queue(), checkpoint, progress())
        assertFalse(restored.isPlaying)
        assertTrue(restored.checkpoint.wasPlaying)
        assertEquals(checkpoint, restored.checkpoint)
    }

    @Test fun restorationPreservesAllDistinctContinuationStates() {
        for (continuation in Continuation.entries) {
            val checkpoint = PlaybackCheckpoint(QueueVersion(1), OccurrenceId(11), 42_000,
                continuation = continuation)
            assertEquals(continuation, RestoredSession(queue(), checkpoint, progress()).checkpoint.continuation)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun consumedStopCannotRestoreAnUnconsumedMarker() {
        PlaybackCheckpoint(QueueVersion(1), OccurrenceId(11), stopAfter = OccurrenceId(11),
            continuation = Continuation.SCHEDULED_STOP)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recoveryCannotCombineDifferentQueueAndProgressVersions() {
        RestoredSession(queue(2), PlaybackCheckpoint(QueueVersion(2), OccurrenceId(10)), progress())
    }

    @Test fun emptySessionRestoresWithoutSelectingUnrelatedMusic() {
        val emptySource = CollectionSnapshot(source.key, source.revision, longArrayOf())
        val queue = QueueSnapshot(QueueVersion(1), emptySource, longArrayOf(), longArrayOf())
        val restored = RestoredSession(queue, PlaybackCheckpoint(queue.version, null), progress())
        assertNull(restored.checkpoint.current)
        assertFalse(restored.isPlaying)
    }

    @Test fun queueBuffersCannotBeMutatedByCallerOrConsumer() {
        val occurrences = longArrayOf(10, 11)
        val tracks = longArrayOf(7, 7)
        val scheduled = longArrayOf(11)
        val queue = QueueSnapshot(QueueVersion(1), source, occurrences, tracks, scheduled)
        occurrences[0] = 99
        tracks[0] = 99
        scheduled[0] = 99
        queue.copyOccurrenceIds()[1] = 99
        queue.copyTrackIds()[1] = 99
        queue.copyScheduledIds()[0] = 99
        assertEquals(OccurrenceId(10), queue.occurrenceAt(0))
        assertEquals(TrackId(7), queue.trackAt(0))
        assertArrayEquals(longArrayOf(11), queue.copyScheduledIds())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateOccurrenceIdentityIsRejected() {
        QueueSnapshot(QueueVersion(1), source, longArrayOf(10, 10), longArrayOf(7, 7))
    }

    @Test fun repeatUsesAgreedCycle() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
    }

    @Test fun interveningPauseAndMembershipChangeInvalidatePreparation() {
        val token = PreparationToken(QueueVersion(1), source.key, source.revision, 1, TrackId(7))
        assertNotEquals(token, token.copy(intentRevision = 2))
        assertNotEquals(token, token.copy(collectionRevision = source.revision.copy(membership = 2)))
        assertNotEquals(token, token.copy(lastAudibleTrack = TrackId(8)))
    }
}

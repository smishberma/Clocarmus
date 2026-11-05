package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class TrackSchedulingTest {
    private fun queue(vararg tracks: String) = TrackQueue.fromTracks(tracks.toList(), tracks.firstOrNull())
    private fun TrackQueue.tracks() = entries.map { it.trackId }

    @Test fun movesEarliestUpcomingOccurrenceWithoutMergingDuplicates() {
        val original = queue("current", "x", "a", "b", "a", "z")
        val result = original.schedule("a")
        assertEquals(listOf("current", "a", "x", "b", "a", "z"), result.tracks())
        assertEquals(original.entries[2].id, result.entries[1].id)
        assertEquals(original.entries.map { it.id }.toSet(), result.entries.map { it.id }.toSet())
        assertEquals(original.currentId, result.currentId)
    }

    @Test fun latestRequestWinsAndUntouchedEffectiveShuffleOrderStaysStable() {
        val original = queue("current", "z", "x", "y")
        val result = original.schedule("a").schedule("b")
        assertEquals(listOf("current", "b", "a", "z", "x", "y"), result.tracks())
        assertEquals(original.entries.drop(1), result.entries.drop(3))
    }

    @Test fun targetingCurrentTrackInsertsAnIndependentOccurrence() {
        val original = queue("a", "b")
        val result = original.schedule("a")
        assertEquals(listOf("a", "a", "b"), result.tracks())
        assertEquals(original.currentId, result.currentId)
        assertNotEquals(result.entries[0].id, result.entries[1].id)
    }

    @Test fun targetingCurrentTrackMovesItsUpcomingCopy() {
        val original = queue("a", "b", "a", "c")
        val result = original.schedule("a")
        assertEquals(listOf("a", "a", "b", "c"), result.tracks())
        assertEquals(original.entries[2], result.entries[1])
    }

    @Test fun noCurrentTrackSchedulesFirstAndKeepsRemainder() {
        val empty = TrackQueue().schedule("a", true)
        assertEquals("a", empty.current?.trackId)
        assertEquals(empty.currentId, empty.stopAfterId)
        val unselected = TrackQueue(entries = listOf(QueueOccurrence("b"), QueueOccurrence("a")))
        val result = unselected.schedule("a")
        assertEquals(listOf("a", "b"), result.tracks())
        assertEquals("a", result.current?.trackId)
    }

    @Test fun ordinarySchedulingPreservesMarkerOnItsSpecificOccurrence() {
        val marked = queue("current", "a", "x").schedule("a", true)
        val result = marked.schedule("b")
        assertEquals(listOf("current", "b", "a", "x"), result.tracks())
        assertEquals(marked.stopAfterId, result.stopAfterId)
        assertEquals("a", result.entries.first { it.id == result.stopAfterId }.trackId)
    }

    @Test fun newStopRequestReplacesOnlyMarker() {
        val marked = queue("current", "x").schedule("a", true)
        val result = marked.schedule("b", true)
        assertNotEquals(marked.stopAfterId, result.stopAfterId)
        assertEquals(listOf("current", "b", "a", "x"), result.tracks())
        assertEquals(result.entries[1].id, result.stopAfterId)
    }

    @Test fun markedCompletionStopsOnceOverridesEveryRepeatModeAndRetainsQueue() {
        for (repeat in 0..2) {
            val marked = queue("current", "x").schedule("a", true).step(1) { true }
            val result = marked.complete(repeat) { true }
            assertFalse(result.playing)
            assertNull(result.queue.stopAfterId)
            assertEquals(marked.entries, result.queue.entries)
            assertEquals(marked.currentId, result.queue.currentId)
        }
    }

    @Test fun explicitNextOverridesRepeatOneThenResumesEstablishedOrder() {
        val scheduled = queue("current", "x", "y").schedule("a").schedule("b")
        val b = scheduled.complete(1) { true }
        assertEquals("b", b.queue.current?.trackId)
        val a = b.queue.complete(1) { true }
        assertEquals("a", a.queue.current?.trackId)
        assertEquals("x", a.queue.complete(0) { true }.queue.current?.trackId)
    }

    @Test fun unavailableMarkedOccurrenceIsSkippedAndMarkerCleared() {
        val marked = queue("current", "a", "x").schedule("a", true)
        val result = marked.complete(0) { it != "a" }
        assertEquals("x", result.queue.current?.trackId)
        assertNull(result.queue.stopAfterId)
    }

    @Test fun replacingQueueClearsAllSchedulingState() {
        val old = queue("current", "x").schedule("a", true)
        val replacement = TrackQueue.fromTracks(old.tracks(), "x")
        assertNull(replacement.stopAfterId)
        assertTrue(replacement.scheduledIds.isEmpty())
    }

    @Test fun snapshotRoundTripRetainsOccurrenceIdentitySchedulingAndMarker() {
        val original = queue("current", "a", "x", "a").schedule("a", true).schedule("b")
        val bytes = ByteArrayOutputStream().also { buffer -> ObjectOutputStream(buffer).use { it.writeObject(original) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as TrackQueue }
        assertEquals(original, restored)
        assertEquals("b", restored.step(1) { true }.current?.trackId)
    }

    @Test fun exhaustedQueueStopsAndRepeatAllWraps() {
        val original = queue("a", "b").step(1) { true }
        assertFalse(original.complete(0) { true }.playing)
        assertEquals("a", original.complete(2) { true }.queue.current?.trackId)
    }
    @Test fun unavailableLastOccurrenceClearsMarkerEvenWhenNoNextTrackExists() {
        val marked = queue("current").schedule("missing", true)
        val result = marked.complete(0) { it != "missing" }
        assertFalse(result.playing)
        assertNull(result.queue.stopAfterId)
        assertEquals("current", result.queue.current?.trackId)
    }

}

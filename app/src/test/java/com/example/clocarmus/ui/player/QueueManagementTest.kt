package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test

class QueueManagementTest {
    private fun queue(vararg ids: String) = TrackQueue.fromTracks(ids.toList(), ids.firstOrNull())
    private val playable: (String) -> Boolean = { it != "missing" }

    @Test fun appendAllowsDuplicatesAndSelectsOnlyWhenEmpty() {
        val empty = TrackQueue().append("a")
        assertEquals(empty.entries.single().id, empty.currentId)
        val appended = empty.append("a")
        assertNotEquals(appended.entries[0].id, appended.entries[1].id)
        assertEquals(empty.currentId, appended.currentId)
        assertEquals(empty.entries, appended.entries.take(1))
    }

    @Test fun exactSelectionDoesNotReplaceOrReorderOccurrences() {
        val q = queue("a", "b", "a")
        val selected = q.select(q.entries.last().id)
        assertEquals(q.entries, selected.entries)
        assertEquals(q.entries.last().id, selected.currentId)
    }

    @Test fun reorderPreservesCurrentAndMarkerAndClearsOldSchedulingPriority() {
        val q = queue("a", "b", "c", "b").scheduleOccurrence("invalid", false)
        val scheduled = q.scheduleOccurrence(q.entries.last().id, true)
        val reordered = scheduled.reorder(scheduled.entries.map { it.id }, scheduled.currentId!!, 3)
        assertEquals(q.currentId, reordered.currentId)
        assertEquals(scheduled.stopAfterId, reordered.stopAfterId)
        assertTrue(reordered.scheduledIds.isEmpty())
        assertEquals(q.currentId, reordered.entries.last().id)
    }

    @Test fun dragCommitUsesLatestPlaybackStateAndRejectsReplacedOrChangedQueue() {
        val q = queue("a", "b", "c")
        val ids = q.entries.map { it.id }
        val advanced = q.step(1, playable)
        val edited = advanced.reorder(ids, ids.last(), 0)
        assertEquals(advanced.currentId, edited.currentId)
        assertEquals(ids.last(), edited.entries.first().id)
        val changed = advanced.append("d")
        assertEquals(changed, changed.reorder(ids, ids.last(), 0))
        val replaced = queue("a", "b", "c")
        assertEquals(replaced, replaced.reorder(ids, ids.last(), 0))
    }

    @Test fun removeTargetsExactDuplicateAndClearsItsMarker() {
        val q = queue("a", "b", "a")
        val marked = q.scheduleOccurrence(q.entries.last().id, true)
        val result = marked.remove(q.entries.last().id, playable)
        assertEquals(listOf("a", "b"), result.entries.map { it.trackId })
        assertEquals(q.currentId, result.currentId)
        assertNull(result.stopAfterId)
    }

    @Test fun removingCurrentSkipsFailuresWithoutWrappingAndRetainsEarlierEntries() {
        val q = queue("a", "b", "missing", "c").select("invalid")
        val selected = q.select(q.entries[1].id)
        val next = selected.remove(selected.currentId!!, playable)
        assertEquals("c", next.current?.trackId)
        val end = next.remove(next.currentId!!, playable)
        assertNull(end.currentId)
        assertEquals(listOf("a", "missing"), end.entries.map { it.trackId })
    }

    @Test fun exactSchedulingMovesEarlierCopyAndCurrentStopDoesNotDuplicate() {
        val q = queue("a", "b", "a", "c").let { it.select(it.entries[2].id) }
        assertEquals(q, q.scheduleOccurrence(q.currentId!!, false))
        val marked = q.scheduleOccurrence(q.currentId!!, true)
        assertEquals(q.entries, marked.entries)
        assertEquals(q.currentId, marked.stopAfterId)
        val scheduled = marked.scheduleOccurrence(q.entries.first().id, false)
        assertEquals(q.entries.first().id, scheduled.entries[scheduled.currentIndex + 1].id)
        assertEquals(marked.stopAfterId, scheduled.stopAfterId)
    }

    @Test fun markedCompletionOverridesRepeatAndExplicitPlayContinuesOnce() {
        val q = queue("a", "b", "c").let { it.scheduleOccurrence(it.currentId!!, true) }
        for (repeat in 0..2) {
            val stopped = q.complete(repeat, playable)
            assertFalse(stopped.playing)
            assertNull(stopped.queue.stopAfterId)
            assertEquals(q.entries, stopped.queue.entries)
            val resumed = stopped.queue.resume(playable)
            assertTrue(resumed.playing)
            assertEquals("b", resumed.queue.current?.trackId)
            assertFalse(resumed.queue.resumeAfterStop)
        }
    }

    @Test fun playAfterFinalMarkedCompletionDoesNotRestartOrInventTrack() {
        val q = queue("a").let { it.scheduleOccurrence(it.currentId!!, true) }
        val stopped = q.complete(2, playable).queue
        val resumed = stopped.resume(playable)
        assertFalse(resumed.playing)
        assertEquals(stopped.entries, resumed.queue.entries)
        assertEquals(stopped.currentId, resumed.queue.currentId)
    }
    @Test fun virtualPreviewMatchesEveryCommittedMoveWithoutChangingTheQueue() {
        val q = queue("a", "b", "a", "c", "d", "e")
        val original = q.entries.toList()
        val ids = q.entries.map { it.id }
        for (from in q.entries.indices) for (to in q.entries.indices) {
            assertEquals(q.reorder(ids, ids[from], to).entries, queuePreview(q.entries, from, to))
            assertEquals(original, q.entries)
        }
    }

}

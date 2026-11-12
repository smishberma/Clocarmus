package com.example.clocarmus.playback.shuffle

import com.example.clocarmus.ui.player.TrackQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer

@OptIn(ExperimentalCoroutinesApi::class)
class ShufflePreparationTest {
    private fun input() = ShufflePreparationInput(TrackQueue.fromTracks(listOf("a", "b", "c"), "a"),
        ShufflePreparationKind.PASS, setOf("a", "b", "c"), emptyList(), "a")

    @Test fun cancelledUncooperativeResultCannotBecomeReady() = runTest {
        val gate = CompletableDeferred<Unit>()
        var notifications = 0
        val preparation = ShufflePreparation(this, { request ->
            withContext(NonCancellable) { gate.await() }
            request.queue.entries.reversed()
        }) { notifications++ }
        preparation.prepare(input())
        runCurrent()
        preparation.invalidate()
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(preparation.input)
        assertNull(preparation.result)
        assertEquals(0, notifications)
    }

    @Test fun onlyNewestSnapshotCanPublishDespiteLateOldCompletion() = runTest {
        val first = input()
        val second = first.copy(lastAudible = "b", available = setOf("a", "b"))
        val gate = CompletableDeferred<Unit>()
        var notifications = 0
        val preparation = ShufflePreparation(this, { request ->
            if (request == first) withContext(NonCancellable) { gate.await() }
            request.queue.entries.filter { it.trackId in request.available }
        }) { notifications++ }
        preparation.prepare(first)
        runCurrent()
        preparation.prepare(second)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(second, preparation.input)
        assertEquals(listOf("a", "b"), preparation.result!!.map { it.trackId })
        assertEquals(1, notifications)
    }

    @Test fun unchangedInputReusesPreparedPassUntilExplicitInvalidation() = runTest {
        var calls = 0
        val request = input()
        val preparation = ShufflePreparation(this, { calls++; it.queue.entries }) {}
        preparation.prepare(request)
        advanceUntilIdle()
        preparation.prepare(request.copy())
        advanceUntilIdle()
        assertEquals(1, calls)
        assertNotNull(preparation.result)
        preparation.invalidate()
        assertNull(preparation.result)
        preparation.prepare(request)
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun errorsAreRetainedWithoutRetryOrUncheckedResult() = runTest {
        var calls = 0
        val request = input()
        val preparation = ShufflePreparation(this, { calls++; throw IOException("fixture") }) {}
        preparation.prepare(request)
        advanceUntilIdle()
        preparation.prepare(request)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertNull(preparation.result)
        assertTrue(preparation.failure is IOException)
    }

    private fun stream(vararg values: Int) = DataInputStream(ByteArrayInputStream(
        ByteBuffer.allocate(values.size * 4).apply { values.forEach { putInt(it) } }.array()))

    @Test fun boundedDrawRejectsBiasedTailAndUsesUnsignedWords() {
        stream(-1, 5, Int.MIN_VALUE).use {
            val random = StreamShuffleRandom(it)
            assertEquals(2, random.nextInt(3)) // unsigned 0xffffffff is rejected, 5 accepted
            assertEquals(2, random.nextInt(3)) // unsigned 0x80000000
        }
        stream(-1).use { assertEquals(7, StreamShuffleRandom(it).nextInt(8)) }
    }

    @Test fun failedEntropyReadPropagatesWithoutFallback() {
        try { stream(-1).use { StreamShuffleRandom(it).nextInt(3) }; fail("Expected EOF") }
        catch (_: EOFException) { }
    }

    @Test fun boundedDrawChecksCancellationEvenWhenEveryWordIsRejected() {
        var checks = 0
        val random = StreamShuffleRandom(stream(-1, -1, -1)) {
            if (++checks == 3) throw CancellationException("fixture")
        }
        try { random.nextInt(3); fail("Expected cancellation") }
        catch (_: CancellationException) { assertEquals(3, checks) }
    }

    @Test fun remainderPreparationPinsHeadAndScheduledPriorityWithOccurrenceIdentity() = runTest {
        val queue = TrackQueue.fromTracks(listOf("a", "b", "c", "d"), "b").schedule("d", true)
        val request = ShufflePreparationInput(queue, ShufflePreparationKind.REMAINDER,
            setOf("a", "b", "c", "d"), emptyList(), "b")
        val order = prepareShuffle(request)
        assertEquals(queue.entries.take(2), order.take(2))
        assertEquals(queue.scheduledIds, order.drop(2).take(1).map { it.id })
        assertEquals(queue.entries.toSet(), order.toSet())
        assertEquals(queue.stopAfterId, order[2].id)
    }

    @Test fun passPreparationUsesOnlyAvailableSnapshotOccurrences() = runTest {
        val queue = TrackQueue.fromTracks(listOf("a", "a", "b", "c"), "a")
        val order = prepareShuffle(ShufflePreparationInput(queue, ShufflePreparationKind.PASS,
            setOf("a", "b"), emptyList(), "a"))
        assertEquals("b", order.first().trackId)
        assertEquals(queue.entries.filter { it.trackId != "c" }.toSet(), order.toSet())
        assertEquals(2, order.count { it.trackId == "a" })
    }
    @Test fun disablingShufflePreparesOriginalOrderWithSchedulesAndOccurrenceIdentity() = runTest {
        val original = TrackQueue.fromTracks(listOf("a", "b", "c", "d"), "a")
        val edited = original.reorder(original.entries.map { it.id }, original.entries.last().id, 1).schedule("c", true)
        val request = ShufflePreparationInput(edited, ShufflePreparationKind.ORIGINAL, emptySet(), emptyList(), "a")
        assertEquals(listOf("a", "c", "b", "d"), prepareShuffle(request).map { it.trackId })
    }

}

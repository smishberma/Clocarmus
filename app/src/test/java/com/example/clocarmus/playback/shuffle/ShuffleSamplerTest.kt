package com.example.clocarmus.playback.shuffle

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ShuffleSamplerTest {
    private fun sampler(seed: Long = 42): ShuffleSampler {
        val source = Random(seed)
        return ShuffleSampler(ShuffleRandom { source.nextInt(it) })
    }

    @Test fun firstTinySessionCanReachEveryOccurrencePermutation() = runBlocking {
        val orders = (1..6).map { selected ->
            ShuffleSampler(ShuffleRandom { bound -> if (bound == selected) 0 else bound - 1 })
                .sample(longArrayOf(10, 20, 30)).toList()
        }
        assertEquals(6, orders.toSet().size)
        assertTrue(orders.all { it.toSet() == setOf(0, 1, 2) })
    }

    @Test fun emptySingletonAndConflictingTinyOpenings() = runBlocking {
        assertArrayEquals(intArrayOf(), sampler().sample(longArrayOf()))
        assertArrayEquals(intArrayOf(0), sampler().sample(longArrayOf(7), longArrayOf(7), 7))
        // Audible avoidance wins when avoiding both openings is impossible.
        assertArrayEquals(intArrayOf(0, 1), sampler().sample(longArrayOf(1, 2), longArrayOf(1, 2), 2))
        assertArrayEquals(intArrayOf(1, 0), sampler().sample(longArrayOf(1, 2), longArrayOf(1, 2), 1))
    }

    @Test fun tinySessionSelectsAllAndOnlyMinimumSimilarityOrders() = runBlocking {
        // With previous ABC and opening A forbidden, BAC and CBA each match one
        // position; BCA and CAB each match one directed pair. All four tie.
        val ids = longArrayOf(1, 2, 3)
        val results = (1..4).map { rank ->
            ShuffleSampler(ShuffleRandom { bound -> if (bound == rank) 0 else bound - 1 })
                .sample(ids, ids).toList()
        }.toSet()
        assertEquals(setOf(listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0)), results)
    }

    @Test fun scoringProjectsBeforeCountingDirectedPairs() {
        assertEquals(Similarity(3, 2), similarity(longArrayOf(1, 99, 2, 3), longArrayOf(1, 2, 88, 3)))
        assertEquals(Similarity(1, 0), similarity(longArrayOf(3, 2, 1), longArrayOf(1, 2, 3)))
        assertEquals(Similarity(0, 0), similarity(longArrayOf(7, 8), longArrayOf(1, 2)))
    }

    @Test fun duplicatePairMultiplicityIsNotCollapsedToASet() {
        assertEquals(Similarity(4, 3), similarity(longArrayOf(1, 2, 1, 2), longArrayOf(1, 2, 1, 2)))
        assertEquals(2, similarity(longArrayOf(1, 2, 1, 2, 1, 2), longArrayOf(1, 2, 2, 2, 1, 1)).directedPairs)
    }

    @Test fun duplicatesRetainEveryOccurrenceAndUnavoidableRepetition() = runBlocking {
        val result = sampler().sample(LongArray(8) { 1 }, LongArray(8) { 1 }, 1)
        assertEquals((0..7).toSet(), result.toSet())
        val mixed = longArrayOf(1, 1, 2, 2)
        val order = sampler().sample(mixed, mixed, 1)
        assertEquals(2L, mixed[order.first()])
        assertEquals(mixed.indices.toSet(), order.toSet())
    }

    @Test fun chosenOpeningOverridesHistoryAndAudibleAvoidance() = runBlocking {
        val ids = LongArray(20) { it.toLong() }
        val result = sampler().sample(ids, ids, 0, fixedFirst = 0)
        assertEquals(0, result.first())
        assertEquals(ids.indices.toSet(), result.toSet())
        val free = result.drop(1).map { ids[it] }.toLongArray()
        val score = similarity(free, ids.drop(1).toLongArray())
        assertTrue(score.positions <= 3 && score.directedPairs <= 3)
    }

    @Test fun changedMembershipDoesNotRejectAFeasibleNonsharedOpening() = runBlocking {
        val ids = LongArray(12) { it.toLong() }
        repeat(30) {
            val order = sampler().sample(ids, longArrayOf(0, 1), 1)
            assertTrue(ids[order.first()] !in setOf(0L, 1L))
            assertEquals(0, similarity(order.map { ids[it] }.toLongArray(), longArrayOf(0, 1)).total)
        }
    }

    @Test fun largeUniqueOrdersMeetSeparateLimitsWithoutMutatingInputs() = runBlocking {
        val ids = LongArray(100_000) { it.toLong() }
        val result = sampler().sample(ids, ids, 1)
        assertEquals(ids.indices.toSet(), result.toSet())
        assertTrue(result.first() != 0 && result.first() != 1)
        val score = similarity(result.map { ids[it] }.toLongArray(), ids)
        assertTrue(score.positions <= 3)
        assertTrue(score.directedPairs <= 3)
        assertTrue(ids.indices.all { ids[it] == it.toLong() })
    }

    @Test fun cancellationTerminatesRejectionWithoutFallback() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val job = async {
            ShuffleSampler(ShuffleRandom { bound -> entered.complete(Unit); bound - 1 })
                .sample(LongArray(20) { it.toLong() }, LongArray(20) { it.toLong() })
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test fun unsupportedLargeDuplicatesFailBeforeSampling() = runBlocking {
        var draws = 0
        try {
            ShuffleSampler(ShuffleRandom { draws++; 0 }).sample(LongArray(9) { 1 })
            fail("Must not attempt rejection without a feasibility policy")
        } catch (_: IllegalArgumentException) { assertEquals(0, draws) }
    }
}

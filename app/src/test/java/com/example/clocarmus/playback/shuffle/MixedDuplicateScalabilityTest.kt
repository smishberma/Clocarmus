package com.example.clocarmus.playback.shuffle

import java.io.File
import java.util.Random
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in host measurement; synthetic IDs require no music files or Android device. */
class MixedDuplicateScalabilityTest {
    @Test fun measureMixedQueues() = runBlocking {
        val output = System.getenv("GENUINE_SHUFFLE_SCALABILITY_OUTPUT")
        assumeTrue("Set GENUINE_SHUFFLE_SCALABILITY_OUTPUT to run measurements", output != null)
        val report = File(requireNotNull(output))
        report.parentFile?.mkdirs()
        report.writeText("pattern,entries,identities,seed,budget_ms,status,elapsed_ms,random_draws,positions,directed_pairs\n")
        val patterns = listOf("balanced_two", "balanced_three_grouped", "balanced_three_interleaved", "light_duplicates")
        for (size in listOf(9, 12, 18, 24, 48, 96, 384)) {
            for (pattern in patterns) {
                val ids = LongArray(size) { i ->
                    when (pattern) {
                        "balanced_two" -> (i * 2 / size).toLong()
                        "balanced_three_grouped" -> (i * 3 / size).toLong()
                        "balanced_three_interleaved" -> (i % 3).toLong()
                        else -> (i / 2).toLong()
                    }
                }
                val original = ids.copyOf()
                for (seed in listOf(42L, 2026L)) {
                    val random = Random(seed)
                    var draws = 0L
                    var positions: Int? = null
                    var pairs: Int? = null
                    var status = "completed"
                    val start = System.nanoTime()
                    try {
                        val order = withTimeout(1_000) {
                            ShuffleSampler(ShuffleRandom { bound -> draws++; random.nextInt(bound) })
                                .sample(ids, ids)
                        }
                        assertEquals(ids.indices.toSet(), order.toSet())
                        assertEquals(ids.size, order.size)
                        assertTrue(ids[order.first()] != ids.first())
                        val score = similarity(LongArray(size) { ids[order[it]] }, ids)
                        positions = score.positions
                        pairs = score.directedPairs
                    } catch (_: TimeoutCancellationException) {
                        status = if (draws == 0L) "timeout_before_sampling" else "timeout_during_sampling"
                    }
                    val elapsed = (System.nanoTime() - start) / 1_000_000.0
                    assertArrayEquals(original, ids)
                    val row = "$pattern,$size,${ids.toSet().size},$seed,1000,$status,$elapsed,$draws,${positions ?: ""},${pairs ?: ""}"
                    report.appendText("$row\n")
                    println(row)
                }
            }
        }
    }
}

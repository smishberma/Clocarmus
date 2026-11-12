package com.example.clocarmus.playback.shuffle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** An audited source must supply independent uniform draws; no production provider is chosen here. */
internal fun interface ShuffleRandom {
    fun nextInt(bound: Int): Int
}

/**
 * Returns occurrence indices, leaving identity allocation and session/history commits to the owner.
 * [fixedFirst] is a deliberate browser selection, excluded from freshness comparisons.
 * Inputs must already be the eligible snapshot, not the full library.
 */
internal class ShuffleSampler(private val random: ShuffleRandom) {
    suspend fun sample(
        tracks: LongArray,
        previous: LongArray = longArrayOf(),
        lastAudible: Long? = null,
        fixedFirst: Int? = null
    ): IntArray = withContext(Dispatchers.Default) {
        require(fixedFirst == null || fixedFirst in tracks.indices)
        // Own inputs across suspension/cancellation; preparation never edits an active queue.
        val ids = tracks.copyOf()
        val old = previous.copyOf()
        val context = currentCoroutineContext()
        var work = 0
        fun check() { if (work++ % 256 == 0) context.ensureActive() }
        val free = ids.indices.filter { it != fixedFirst }.toIntArray()
        val fixedTrack = fixedFirst?.let { ids[it] }
        val oldFree = old.toMutableList().apply { if (fixedFirst != null) remove(fixedTrack) }.toLongArray()
        require(ids.size <= 8 || (ids.toSet().size == ids.size && old.toSet().size == old.size)) {
            "Large duplicate collections require a separate feasibility policy"
        }
        val opening = openingRule(ids, old.firstOrNull(), lastAudible, fixedFirst)
        fun allowed(order: IntArray): Boolean = fixedFirst != null || order.isEmpty() || opening(ids[order[0]])
        fun score(order: IntArray): Similarity = similarity(
            order.asSequence().filter { it != fixedFirst }.map { ids[it] }.toList().toLongArray(), oldFree
        )
        fun assemble(order: IntArray): IntArray = if (fixedFirst == null) order else intArrayOf(fixedFirst) + order

        if (ids.size <= 8) {
            // Reservoir selection over ALL minimizing occurrence permutations. Equal track IDs
            // remain distinct occurrences; exchanging them cannot improve the identity score.
            var best = Int.MAX_VALUE
            var count = 0
            var chosen = intArrayOf()
            permutations(free, ::check) { permutation ->
                val candidate = assemble(permutation)
                if (allowed(candidate)) {
                    val cost = score(candidate).total
                    if (cost < best) { best = cost; count = 0 }
                    if (cost == best && random.nextInt(++count) == 0) chosen = candidate.copyOf()
                }
            }
            context.ensureActive()
            return@withContext chosen
        }

        val currentSet = free.mapTo(HashSet()) { ids[it] }
        val projection = oldFree.filter { it in currentSet }.toLongArray()
        val previousPositions = projection.withIndex().associate { it.value to it.index }
        val previousSuccessors = projection.asList().zipWithNext().toMap()
        val overlap = projection.size
        var minimum = 0
        if (overlap in 1..8) {
            minimum = Int.MAX_VALUE
            val nonsharedOpening = free.any { ids[it] !in previousPositions && opening(ids[it]) }
            permutations(IntArray(overlap) { it }, ::check) { order ->
                val projected = LongArray(overlap) { projection[order[it]] }
                // When nonshared entries exist they can open the full permutation. A forbidden
                // projected opening alone must never exclude an otherwise feasible full order.
                if (fixedFirst != null || nonsharedOpening || opening(projected[0])) {
                    minimum = minOf(minimum, similarity(projected, projection).total)
                }
            }
        }
        val candidate = free.copyOf()
        while (true) {
            check()
            for (i in candidate.indices) { check(); candidate[i] = free[i] }
            for (i in candidate.lastIndex downTo 1) {
                check()
                val j = random.nextInt(i + 1)
                val value = candidate[i]; candidate[i] = candidate[j]; candidate[j] = value
            }
            if (fixedFirst == null && !opening(ids[candidate[0]])) continue
            var positions = 0
            var pairs = 0
            var projectedIndex = 0
            var prior: Long? = null
            for (index in candidate) {
                check()
                val id = ids[index]
                val oldIndex = previousPositions[id] ?: continue
                if (oldIndex == projectedIndex++) positions++
                if (prior != null && previousSuccessors[prior] == id) pairs++
                prior = id
            }
            if (if (overlap <= 8) positions + pairs == minimum else positions <= 3 && pairs <= 3) {
                context.ensureActive()
                return@withContext assemble(candidate)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("Unreachable")
    }

    private fun openingRule(ids: LongArray, previousFirst: Long?, audible: Long?, fixed: Int?): (Long) -> Boolean {
        if (fixed != null || ids.isEmpty()) return { true }
        val avoidAudible = ids.any { it != audible }
        val avoidPrevious = ids.any { (!avoidAudible || it != audible) && it != previousFirst }
        return { (!avoidAudible || it != audible) && (!avoidPrevious || it != previousFirst) }
    }
}

internal data class Similarity(val positions: Int, val directedPairs: Int) {
    val total get() = positions + directedPairs
}

/** Project onto shared identities before scoring; pair counts retain duplicate multiplicities. */
internal fun similarity(current: LongArray, previous: LongArray): Similarity {
    val shared = current.toSet().intersect(previous.toSet())
    val a = current.filter { it in shared }
    val b = previous.filter { it in shared }
    val pairs = b.zipWithNext().groupingBy { it }.eachCount().toMutableMap()
    var matches = 0
    for (pair in a.zipWithNext()) {
        val remaining = pairs[pair] ?: 0
        if (remaining > 0) { matches++; pairs[pair] = remaining - 1 }
    }
    return Similarity(a.zip(b).count { (left, right) -> left == right }, matches)
}

private inline fun permutations(values: IntArray, check: () -> Unit, visit: (IntArray) -> Unit) {
    // Lexicographic index permutations enumerate occurrences even when track identities repeat.
    val indices = IntArray(values.size) { it }
    do {
        check()
        visit(IntArray(indices.size) { values[indices[it]] })
        var pivot = indices.size - 2
        while (pivot >= 0 && indices[pivot] >= indices[pivot + 1]) pivot--
        if (pivot < 0) break
        var successor = indices.lastIndex
        while (indices[successor] <= indices[pivot]) successor--
        val value = indices[pivot]; indices[pivot] = indices[successor]; indices[successor] = value
        var left = pivot + 1
        var right = indices.lastIndex
        while (left < right) {
            val swap = indices[left]; indices[left++] = indices[right]; indices[right--] = swap
        }
    } while (true)
}

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
        fixedFirst: Int? = null,
        openingConstraints: Boolean = true
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
        val opening = if (openingConstraints) openingRule(ids, old.firstOrNull(), lastAudible, fixedFirst) else { _: Long -> true }
        fun allowed(order: IntArray): Boolean = fixedFirst != null || order.isEmpty() || opening(ids[order[0]])
        val comparison by lazy(LazyThreadSafetyMode.NONE) {
            SimilarityReference(LongArray(free.size) { ids[free[it]] }, oldFree, ::check)
        }
        fun score(order: IntArray): Similarity = comparison.score(
            order.asSequence().filter { it != fixedFirst }.map { ids[it] }.toList().toLongArray()
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

        if (ids.toSet().size != ids.size || old.toSet().size != old.size) {
            val freeIds = LongArray(free.size) { ids[free[it]] }
            val order = duplicateShuffle(freeIds, oldFree,
                { fixedFirst != null || opening(it) }, random, ::check)
            context.ensureActive()
            return@withContext assemble(IntArray(order.size) { free[order[it]] })
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
internal fun similarity(current: LongArray, previous: LongArray, check: () -> Unit = {}): Similarity =
    SimilarityReference(current, previous, check).score(current)

/** Compile the shared projection and duplicate transition counts once, even when history shrinks. */
internal class SimilarityReference(current: LongArray, previous: LongArray, private val check: () -> Unit = {}) {
    private val shared: Set<Long>
    private val old = ArrayList<Long>()
    private val pairs = HashMap<Pair<Long, Long>, Int>()
    init {
        val currentIds = HashSet<Long>()
        val previousIds = HashSet<Long>()
        current.forEach { check(); currentIds.add(it) }
        previous.forEach { check(); previousIds.add(it) }
        shared = currentIds.intersect(previousIds)
        var prior: Long? = null
        for (id in previous) {
            check()
            if (id !in shared) continue
            old.add(id)
            prior?.let { val pair = it to id; pairs[pair] = (pairs[pair] ?: 0) + 1 }
            prior = id
        }
    }
    fun score(current: LongArray): Similarity {
        val used = HashMap<Pair<Long, Long>, Int>()
        var prior: Long? = null
        var index = 0
        var positions = 0
        var matches = 0
        for (id in current) {
            check()
            if (id !in shared) continue
            if (old.getOrNull(index++) == id) positions++
            prior?.let {
                val pair = it to id
                val count = used[pair] ?: 0
                if (count < (pairs[pair] ?: 0)) { matches++; used[pair] = count + 1 }
            }
            prior = id
        }
        return Similarity(positions, matches)
    }
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

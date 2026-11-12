package com.example.clocarmus.playback.shuffle

/** Exact duplicate policy. Deterministic feasibility work never supplies the random result. */
internal fun duplicateShuffle(
    tracks: LongArray, previous: LongArray, allowedOpening: (Long) -> Boolean,
    random: ShuffleRandom, check: () -> Unit
): IntArray {
    val counts = tracks.asList().groupingBy { it }.eachCount()
    val shared = counts.keys.intersect(previous.toSet())
    val old = previous.filter { it in shared }.toLongArray()
    val projected = tracks.filter { it in shared }.toLongArray()
    fun shuffle(values: IntArray): IntArray {
        for (i in values.lastIndex downTo 1) {
            check()
            val j = random.nextInt(i + 1)
            val v = values[i]; values[i] = values[j]; values[j] = v
        }
        return values
    }
    fun sample(accept: (LongArray) -> Boolean): IntArray {
        val order = IntArray(tracks.size) { it }
        val candidate = LongArray(tracks.size)
        while (true) {
            check()
            for (i in order.indices) { check(); order[i] = i }
            shuffle(order)
            for (i in order.indices) { check(); candidate[i] = tracks[order[i]] }
            if (allowedOpening(candidate[0]) && accept(candidate)) return order
        }
    }
    // No overlap or a single identity has invariant similarity. Every occurrence permutation is eligible.
    if (counts.size == 1) return shuffle(IntArray(tracks.size) { it })
    if (shared.isEmpty()) return sample { true }

    // One minority occurrence: evaluate every identity ordering in O(n), then shuffle the majority
    // occurrences independently. Each accepted identity order has exactly (n-1)! occurrence orders.
    val minority = counts.entries.singleOrNull { it.value == 1 }
    if (counts.size == 2 && minority != null) {
        val b = minority.key
        val a = counts.keys.first { it != b }
        val bothShared = shared.size == 2
        var base = 0
        for (i in 0 until minOf(old.size, tracks.size)) { check(); if (old[i] == a) base++ }
        var oldAA = 0
        var oldAB = 0
        var oldBA = 0
        for (i in 1 until old.size) {
            check()
            if (old[i - 1] == a && old[i] == a) oldAA++
            else if (old[i - 1] == a && old[i] == b) oldAB++
            else if (old[i - 1] == b && old[i] == a) oldBA++
        }
        fun score(position: Int): Similarity {
            val positions = base + if (position < old.size) (if (old[position] == b) 1 else -1) else 0
            val aa = tracks.size - 1 - (if (position > 0) 1 else 0) - (if (position < tracks.lastIndex) 1 else 0)
            val adjacent = minOf(aa, oldAA) +
                (if (position > 0) minOf(1, oldAB) else 0) +
                (if (position < tracks.lastIndex) minOf(1, oldBA) else 0)
            return Similarity(positions, adjacent)
        }
        val invariant = if (bothShared) null else similarity(tracks, old, check)
        var ordinaryCount = 0
        var minimumCount = 0
        var minimum = Int.MAX_VALUE
        val tiny = minOf(projected.size, old.size) <= 8
        for (position in tracks.indices) {
            check()
            if (!allowedOpening(if (position == 0) b else a)) continue
            val s = invariant ?: score(position)
            if (s.positions <= 3 && s.directedPairs <= 3) ordinaryCount++
            if (s.total < minimum) { minimum = s.total; minimumCount = 1 }
            else if (s.total == minimum) minimumCount++
        }
        val minimizing = tiny || ordinaryCount == 0
        var rank = random.nextInt(if (minimizing) minimumCount else ordinaryCount)
        var chosen = 0
        for (position in tracks.indices) {
            check()
            if (!allowedOpening(if (position == 0) b else a)) continue
            val s = invariant ?: score(position)
            if (if (minimizing) s.total == minimum else s.positions <= 3 && s.directedPairs <= 3) {
                if (rank-- == 0) { chosen = position; break }
            }
        }
        val majority = shuffle(tracks.indices.filter { tracks[it] == a }.toIntArray())
        val minorityIndex = tracks.indexOf(b)
        var next = 0
        return IntArray(tracks.size) { if (it == chosen) minorityIndex else majority[next++] }
    }

    val comparison = SimilarityReference(tracks, previous, check)
    // A tiny shared projection is independent of all nonshared interleavings, including duplicates.
    if (projected.size <= 8) {
        val permutation = projected.sortedArray()
        val nonsharedCanOpen = tracks.any { it !in shared && allowedOpening(it) }
        var minimum = Int.MAX_VALUE
        do {
            check()
            if (nonsharedCanOpen || allowedOpening(permutation[0])) minimum = minOf(minimum, comparison.score(permutation).total)
        } while (nextIdentityPermutation(permutation, check))
        return sample { comparison.score(it).total == minimum }
    }

    val useMinimum = minOf(projected.size, old.size) <= 8
    fun ordinary(candidate: LongArray): Boolean {
        if (!allowedOpening(candidate[0])) return false
        val s = comparison.score(candidate)
        return s.positions <= 3 && s.directedPairs <= 3
    }
    // Fast existence witnesses avoid exhaustive feasibility work for common, lightly duplicated queues.
    val sorted = tracks.sortedArray()
    val reversed = sorted.reversedArray()
    val candidate = LongArray(tracks.size)
    for (base in listOf(tracks, sorted, reversed)) {
        for (shift in 0 until minOf(16, tracks.size)) {
            for (i in candidate.indices) { check(); candidate[i] = base[(i + shift) % base.size] }
            if (!useMinimum && ordinary(candidate)) return sample(::ordinary)
        }
    }
    // Exact fallback: finite multiset enumeration, cancellable between bounded batches. This can
    // be combinatorial for mixed duplicate-heavy queues; do not claim general 100k scalability.
    var minimum = Int.MAX_VALUE
    do {
        check()
        if (!useMinimum && ordinary(sorted)) return sample(::ordinary)
        if (allowedOpening(sorted[0])) minimum = minOf(minimum, comparison.score(sorted).total)
    } while (nextIdentityPermutation(sorted, check))
    return sample { comparison.score(it).total == minimum }
}

private fun nextIdentityPermutation(values: LongArray, check: () -> Unit): Boolean {
    var pivot = values.size - 2
    while (pivot >= 0 && values[pivot] >= values[pivot + 1]) { check(); pivot-- }
    if (pivot < 0) return false
    var next = values.lastIndex
    while (values[next] <= values[pivot]) { check(); next-- }
    val swap = values[pivot]; values[pivot] = values[next]; values[next] = swap
    var left = pivot + 1
    var right = values.lastIndex
    while (left < right) {
        check()
        val value = values[left]; values[left++] = values[right]; values[right--] = value
    }
    return true
}

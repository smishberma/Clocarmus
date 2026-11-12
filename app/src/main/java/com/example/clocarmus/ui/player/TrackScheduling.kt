package com.example.clocarmus.ui.player

import java.io.Serializable
import java.util.UUID

internal data class QueueOccurrence(val trackId: String, val id: String = UUID.randomUUID().toString()) : Serializable

/** Occurrences are stored in effective playback order, including the established shuffle order. */
internal data class TrackQueue(
    val entries: List<QueueOccurrence> = emptyList(),
    val currentId: String? = null,
    val stopAfterId: String? = null,
    val scheduledIds: List<String> = emptyList(),
    val resumeAfterStop: Boolean = false,
    val originalEntries: List<QueueOccurrence> = entries,
    val consumedIds: Set<String> = emptySet(),
    val replayIds: Set<String> = emptySet()
) : Serializable {
    val currentIndex: Int get() = entries.indexOfFirst { it.id == currentId }
    val current: QueueOccurrence? get() = entries.getOrNull(currentIndex)

    fun append(trackId: String): TrackQueue {
        val entry = QueueOccurrence(trackId)
        return copy(entries = entries + entry, originalEntries = originalEntries + entry, currentId = currentId ?: if (entries.isEmpty()) entry.id else null)
    }

    fun select(id: String): TrackQueue {
        val target = entries.indexOfFirst { it.id == id }
        if (target < 0) return this
        val forward = if (target > currentIndex) entries.subList((currentIndex).coerceAtLeast(0), target) else emptyList()
        val replays = if (target < currentIndex) entries.subList(target + 1, currentIndex + 1) else emptyList()
        return copy(currentId = id, resumeAfterStop = false,
            consumedIds = consumedIds + forward.map { it.id },
            replayIds = (replayIds - forward.map { it.id }.toSet()) + replays.map { it.id },
            scheduledIds = scheduledIds.filter { scheduled -> entries.indexOfFirst { it.id == scheduled } > target })
    }

    fun reorder(originalIds: List<String>, id: String, index: Int): TrackQueue {
        if (entries.map { it.id } != originalIds || entries.none { it.id == id }) return this
        val reordered = entries.filterNot { it.id == id }.toMutableList()
        reordered.add(index.coerceIn(0, reordered.size), entries.first { it.id == id })
        val moved = entries.first { it.id == id }
        val future = reordered.indexOfFirst { it.id == id } > reordered.indexOfFirst { it.id == currentId }
        val crossed = if (id == currentId && reordered.indexOf(moved) < currentIndex)
            entries.subList(reordered.indexOf(moved), currentIndex).mapTo(HashSet()) { it.id } else emptySet()
        val skipped = if (id == currentId && reordered.indexOf(moved) > currentIndex)
            entries.subList(currentIndex + 1, reordered.indexOf(moved) + 1).mapTo(HashSet()) { it.id } else emptySet()
        return copy(entries = reordered, scheduledIds = emptyList(), consumedIds = consumedIds + skipped,
            replayIds = replayIds + crossed + if (future && id in consumedIds) setOf(moved.id) else emptySet())
    }

    fun remove(id: String, available: (String) -> Boolean): TrackQueue {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return this
        val next = if (id == currentId) entries.drop(index + 1).firstOrNull { available(it.trackId) }?.id else currentId
        return copy(entries = entries.filterNot { it.id == id }, originalEntries = originalEntries.filterNot { it.id == id },
            consumedIds = consumedIds - id, replayIds = replayIds - id, currentId = next,
            stopAfterId = stopAfterId?.takeIf { marker -> marker != id && entries.any { it.id == marker && available(it.trackId) } }, scheduledIds = scheduledIds - id,
            resumeAfterStop = if (id == currentId) false else resumeAfterStop)
    }

    fun scheduleOccurrence(id: String, stopAfter: Boolean): TrackQueue {
        val occurrence = entries.firstOrNull { it.id == id } ?: return this
        if (id == currentId) return if (stopAfter) copy(stopAfterId = id) else this
        val reordered = entries.filterNot { it.id == id }.toMutableList()
        val index = reordered.indexOfFirst { it.id == currentId } + 1
        reordered.add(index, occurrence)
        return copy(entries = reordered, currentId = currentId ?: id,
            stopAfterId = if (stopAfter) id else stopAfterId,
            scheduledIds = if (currentId == null) emptyList() else listOf(id) + (scheduledIds - id),
            replayIds = if (id in consumedIds) replayIds + id else replayIds)
    }

    fun resume(available: (String) -> Boolean): QueueCompletion {
        if (!resumeAfterStop) return QueueCompletion(this, current?.let { available(it.trackId) } == true)
        val next = step(1, available)
        return QueueCompletion(next, next.currentId != currentId)
    }

    fun schedule(trackId: String, stopAfter: Boolean = false): TrackQueue {
        val index = currentIndex
        val upcoming = entries.drop(index + 1).firstOrNull { it.trackId == trackId }
        val occurrence = upcoming ?: QueueOccurrence(trackId)
        val reordered = entries.filterNot { it.id == occurrence.id }.toMutableList()
        reordered.add(index + 1, occurrence)
        return copy(entries = reordered,
            originalEntries = if (upcoming == null) originalEntries + occurrence else originalEntries,
            currentId = currentId ?: occurrence.id,
            stopAfterId = if (stopAfter) occurrence.id else stopAfterId,
            scheduledIds = if (currentId == null) emptyList() else listOf(occurrence.id) + scheduledIds.filterNot { it == occurrence.id })
    }

    fun step(direction: Int, available: (String) -> Boolean): TrackQueue {
        val indices = if (direction > 0) (currentIndex + 1)..entries.lastIndex else (currentIndex - 1) downTo 0
        val next = indices.firstOrNull { available(entries[it].trackId) }
        val marker = stopAfterId?.takeIf { id -> entries.any { it.id == id && available(it.trackId) } }
        if (direction < 0) return if (next == null) copy(stopAfterId = marker) else select(entries[next].id).copy(stopAfterId = marker)
        val passed = entries.subList(currentIndex.coerceAtLeast(0), next ?: entries.size).mapTo(HashSet()) { it.id }
        return copy(currentId = next?.let { entries[it].id } ?: currentId, stopAfterId = marker,
            consumedIds = consumedIds + passed, replayIds = replayIds - passed,
            resumeAfterStop = if (next == null) resumeAfterStop else false,
            scheduledIds = if (next == null) scheduledIds else scheduledIds.filter { id -> entries.indexOfFirst { it.id == id } > next })
    }

    /** Move consumed, non-replay tail occurrences into history before the current occurrence. */
    fun eligibleRemainder(): TrackQueue {
        if (currentIndex < 0) return this
        val tail = entries.drop(currentIndex + 1)
        val excluded = tail.filter { it.id in consumedIds && it.id !in replayIds && it.id !in scheduledIds }
        if (excluded.isEmpty()) return this
        val excludedIds = excluded.mapTo(HashSet()) { it.id }
        return copy(entries = entries.take(currentIndex) + excluded + listOfNotNull(current) + tail.filterNot { it.id in excludedIds })
    }

    fun originalRemainder(): TrackQueue {
        val eligible = eligibleRemainder()
        val tail = eligible.entries.drop(eligible.currentIndex + 1)
        val byId = tail.associateBy { it.id }
        val pinned = scheduledIds.mapNotNull(byId::get)
        val scheduled = scheduledIds.toHashSet()
        val rank = originalEntries.withIndex().associate { it.value.id to it.index }
        return eligible.copy(entries = eligible.entries.take(eligible.currentIndex + 1) + pinned +
            tail.filterNot { it.id in scheduled }.sortedBy { rank[it.id] ?: Int.MAX_VALUE })
    }

    fun complete(repeat: Int, available: (String) -> Boolean): QueueCompletion {
        if (currentId != null && currentId == stopAfterId) return QueueCompletion(copy(stopAfterId = null, resumeAfterStop = true,
            consumedIds = consumedIds + currentId, replayIds = replayIds - currentId), false)
        // Explicit next requests take precedence over repeat-one until consumed.
        if (repeat == 1 && scheduledIds.isEmpty()) return QueueCompletion(copy(consumedIds = consumedIds + listOfNotNull(currentId),
            replayIds = replayIds - listOfNotNull(currentId).toSet()), true)
        val next = step(1, available)
        if (next.currentId != currentId) return QueueCompletion(next, true)
        if (repeat == 2) {
            val first = entries.firstOrNull { available(it.trackId) }
            if (first != null) return QueueCompletion(copy(currentId = first.id, scheduledIds = emptyList(), consumedIds = emptySet(), replayIds = emptySet()), true)
        }
        return QueueCompletion(next, false)
    }

    companion object {
        fun fromTracks(ids: List<String>, selected: String?, shuffle: Boolean = false): TrackQueue {
            val session = UUID.randomUUID().toString()
            val entries = ids.mapIndexed { index, track -> QueueOccurrence(track, "$session:$index") }
            val current = entries.firstOrNull { it.trackId == selected }
            val ordered = if (shuffle && current != null) listOf(current) + entries.filterNot { it.id == current.id }.shuffled() else entries
            return TrackQueue(ordered, current?.id, originalEntries = entries,
                consumedIds = if (shuffle) emptySet() else entries.take(entries.indexOf(current).coerceAtLeast(0)).mapTo(HashSet()) { it.id })
        }
    }
}
internal data class QueueCompletion(val queue: TrackQueue, val playing: Boolean)

/** Constant-space preview for virtualized rows; only a drop commits a new queue list. */
internal fun queuePreview(entries: List<QueueOccurrence>, from: Int, to: Int): List<QueueOccurrence> {
    if (from !in entries.indices) return entries
    val target = to.coerceIn(entries.indices)
    return object : AbstractList<QueueOccurrence>() {
        override val size get() = entries.size
        override fun get(index: Int): QueueOccurrence = entries[when {
            index == target -> from
            from < target && index in from until target -> index + 1
            from > target && index in (target + 1)..from -> index - 1
            else -> index
        }]
    }
}

package com.example.clocarmus.ui.player

import java.io.Serializable
import java.util.UUID

internal data class QueueOccurrence(val trackId: String, val id: String = UUID.randomUUID().toString()) : Serializable

/** Occurrences are stored in effective playback order, including the established shuffle order. */
internal data class TrackQueue(
    val entries: List<QueueOccurrence> = emptyList(),
    val currentId: String? = null,
    val stopAfterId: String? = null,
    val scheduledIds: List<String> = emptyList()
) : Serializable {
    val currentIndex: Int get() = entries.indexOfFirst { it.id == currentId }
    val current: QueueOccurrence? get() = entries.getOrNull(currentIndex)

    fun schedule(trackId: String, stopAfter: Boolean = false): TrackQueue {
        val index = currentIndex
        val upcoming = entries.drop(index + 1).firstOrNull { it.trackId == trackId }
        val occurrence = upcoming ?: QueueOccurrence(trackId)
        val reordered = entries.filterNot { it.id == occurrence.id }.toMutableList()
        reordered.add(index + 1, occurrence)
        return copy(entries = reordered, currentId = currentId ?: occurrence.id,
            stopAfterId = if (stopAfter) occurrence.id else stopAfterId,
            scheduledIds = if (currentId == null) emptyList() else listOf(occurrence.id) + scheduledIds.filterNot { it == occurrence.id })
    }

    fun step(direction: Int, available: (String) -> Boolean): TrackQueue {
        val indices = if (direction > 0) (currentIndex + 1)..entries.lastIndex else (currentIndex - 1) downTo 0
        val next = indices.firstOrNull { available(entries[it].trackId) }
        // A marker on an unavailable occurrence must not migrate to another occurrence.
        val marker = stopAfterId?.takeIf { id -> entries.any { it.id == id && available(it.trackId) } }
        if (next == null) return copy(stopAfterId = marker)
        return copy(currentId = entries[next].id, stopAfterId = marker,
            scheduledIds = scheduledIds.filter { id -> entries.indexOfFirst { it.id == id } > next })
    }

    fun complete(repeat: Int, available: (String) -> Boolean): QueueCompletion {
        if (currentId != null && currentId == stopAfterId) return QueueCompletion(copy(stopAfterId = null), false)
        // Explicit next requests take precedence over repeat-one until consumed.
        if (repeat == 1 && scheduledIds.isEmpty()) return QueueCompletion(this, true)
        val next = step(1, available)
        if (next.currentId != currentId) return QueueCompletion(next, true)
        if (repeat == 2) {
            val first = entries.firstOrNull { available(it.trackId) }
            if (first != null) return QueueCompletion(copy(currentId = first.id, scheduledIds = emptyList()), true)
        }
        return QueueCompletion(next, false)
    }

    companion object {
        fun fromTracks(ids: List<String>, selected: String?, shuffle: Boolean = false): TrackQueue {
            val entries = ids.map { QueueOccurrence(it) }
            val current = entries.firstOrNull { it.trackId == selected }
            val ordered = if (shuffle && current != null) listOf(current) + entries.filterNot { it.id == current.id }.shuffled() else entries
            return TrackQueue(ordered, current?.id)
        }
    }
}
internal data class QueueCompletion(val queue: TrackQueue, val playing: Boolean)

package com.example.clocarmus.core.playback

import com.example.clocarmus.core.library.CollectionSnapshot
import com.example.clocarmus.core.library.TrackId
import kotlinx.coroutines.flow.StateFlow

@JvmInline value class OccurrenceId(val value: Long) { init { require(value > 0) } }
@JvmInline value class QueueVersion(val value: Long) { init { require(value > 0) } }

enum class RepeatMode { OFF, ALL, ONE;
    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]
}

/** These states must survive restart; they have different explicit-Play behavior. */
enum class Continuation { NORMAL, NATURAL_END, SCHEDULED_STOP, PAUSED_AT_END }

/** Large structural state is persisted only on edits, never for position ticks.
 * Parallel arrays are in effective playback order. Distinct occurrences may share a track.
 * The source snapshot retains provenance/original order, including deleted-playlist fallback.
 * Manual queues use a MANUAL source snapshot, never an implicit all-library fallback.
 */
class QueueSnapshot(
    val version: QueueVersion,
    val source: CollectionSnapshot,
    occurrenceIds: LongArray,
    trackIds: LongArray,
    scheduledIds: LongArray = longArrayOf()
) {
    private val occurrences = occurrenceIds.copyOf()
    private val tracks = trackIds.copyOf()
    private val scheduled = scheduledIds.copyOf()
    init {
        require(occurrences.size == tracks.size)
        require(occurrences.all { it > 0 } && tracks.all { it > 0 })
        val occurrenceSet = occurrences.toSet()
        require(occurrenceSet.size == occurrences.size)
        require(scheduled.toSet().size == scheduled.size && scheduled.all { it in occurrenceSet })
    }
    val size: Int get() = tracks.size
    fun occurrenceAt(index: Int): OccurrenceId = OccurrenceId(occurrences[index])
    fun trackAt(index: Int): TrackId = TrackId(tracks[index])
    fun contains(id: OccurrenceId): Boolean = id.value in occurrences
    fun copyOccurrenceIds(): LongArray = occurrences.copyOf()
    fun copyTrackIds(): LongArray = tracks.copyOf()
    fun copyScheduledIds(): LongArray = scheduled.copyOf()
}

/** Small cursor checkpoint, linked to a specific durable queue. wasPlaying is evidence for
 * a separate opt-in startup policy; restoring it must never itself issue Play.
 */
data class PlaybackCheckpoint(
    val queueVersion: QueueVersion,
    val current: OccurrenceId?,
    val positionMs: Long = 0,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val stopAfter: OccurrenceId? = null,
    val continuation: Continuation = Continuation.NORMAL,
    val wasPlaying: Boolean = false
) {
    init {
        require(positionMs >= 0)
        require(current != null || (positionMs == 0L && continuation == Continuation.NORMAL && !wasPlaying))
        require(continuation != Continuation.SCHEDULED_STOP || (stopAfter == null && !wasPlaying))
        require(continuation != Continuation.NATURAL_END || !wasPlaying)
        require(continuation != Continuation.PAUSED_AT_END || !wasPlaying)
    }
    fun isCompatibleWith(queue: QueueSnapshot): Boolean = queueVersion == queue.version &&
        (current == null || queue.contains(current)) && (stopAfter == null || queue.contains(stopAfter))
}

/** Progress/history are separate from five-second position writes. Implementations persist
 * generated order, consumption bits and replay intent on transitions, atomically with cursor.
 */
interface PlaybackStateStore {
    /** Returns the newest coherent snapshot, falling back to a prior committed version.
     * Restored playback is paused; keep continuation and pre-stop intent intact.
     */
    suspend fun restore(): RestoredSession?
    /** Atomically commit queue, cursor and progress; publish only after durable success.
     * expectedVersion is null only for a store with no prior session. Reject stale commits.
     * Keep the previous coherent version for recovery. Never erase a failed-write session.
     */
    suspend fun commit(expectedVersion: QueueVersion?, session: RestoredSession): Boolean
    /** Reject stale versions. Never serialize/rewrite the queue for this small update. */
    suspend fun checkpoint(value: PlaybackCheckpoint): Boolean
}

data class RestoredSession(
    val queue: QueueSnapshot,
    val checkpoint: PlaybackCheckpoint,
    val shuffleProgress: ShuffleProgress
) {
    init {
        require(checkpoint.isCompatibleWith(queue))
        require(shuffleProgress.queueVersion == queue.version)
    }
    val isPlaying: Boolean get() = false
}

/** Persist the actual generated order, consumption and deliberate replay occurrences.
 * generated order is immutable history; dragging the effective queue does not rewrite it.
 * Workers encode these buffers on transitions, separately from periodic position writes.
 */
class ShuffleProgress(
    val queueVersion: QueueVersion,
    generatedTrackIds: LongArray,
    consumedOccurrenceIds: LongArray,
    replayOccurrenceIds: LongArray
) {
    private val generated = generatedTrackIds.copyOf()
    private val consumed = consumedOccurrenceIds.copyOf()
    private val replays = replayOccurrenceIds.copyOf()
    init {
        require(generated.all { it > 0 } && consumed.all { it > 0 } && replays.all { it > 0 })
        require(consumed.toSet().size == consumed.size && replays.toSet().size == replays.size)
    }
    fun copyGeneratedTrackIds(): LongArray = generated.copyOf()
    fun copyConsumedOccurrenceIds(): LongArray = consumed.copyOf()
    fun copyReplayOccurrenceIds(): LongArray = replays.copyOf()
}

/** Lightweight UI state; full queues must be paged separately, never placed in saved state. */
data class PlaybackState(
    val queueVersion: QueueVersion?,
    val current: OccurrenceId?,
    val trackId: TrackId?,
    val positionMs: Long,
    val durationMs: Long?,
    val isPlaying: Boolean,
    val repeat: RepeatMode,
    val shuffle: Boolean
)

sealed interface PlaybackCommand {
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data class Seek(val occurrence: OccurrenceId, val positionMs: Long) : PlaybackCommand {
        init { require(positionMs >= 0) }
    }
    data class SelectOccurrence(val version: QueueVersion, val id: OccurrenceId) : PlaybackCommand
    data class Append(val trackId: TrackId) : PlaybackCommand
    data class RemoveOccurrence(val version: QueueVersion, val id: OccurrenceId) : PlaybackCommand
    /** Reject a stale version instead of reverting intervening edits or queue replacement. */
    data class MoveOccurrence(val version: QueueVersion, val id: OccurrenceId, val index: Int) : PlaybackCommand {
        init { require(index >= 0) }
    }
    data class ScheduleTrack(val trackId: TrackId, val stopAfter: Boolean) : PlaybackCommand
    data class ScheduleOccurrence(val version: QueueVersion, val id: OccurrenceId, val stopAfter: Boolean) : PlaybackCommand
    data class StartCollection(val snapshot: CollectionSnapshot, val selected: TrackId) : PlaybackCommand
    data class SetRepeat(val mode: RepeatMode) : PlaybackCommand
    data class SetShuffle(val enabled: Boolean) : PlaybackCommand
}

/** Service-owned authority. Serialize commands on the player's owning thread; all IO,
 * scans, queue serialization and shuffle preparation run on workers. Activity reattachment
 * observes the existing session and does not run fresh-process restoration again.
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>
    suspend fun send(command: PlaybackCommand)
}

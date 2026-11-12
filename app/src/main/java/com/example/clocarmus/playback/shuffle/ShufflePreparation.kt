package com.example.clocarmus.playback.shuffle

import com.example.clocarmus.ui.player.QueueOccurrence
import com.example.clocarmus.ui.player.TrackQueue
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.FileInputStream

internal enum class ShufflePreparationKind { REMAINDER, PASS }

/** All inputs affecting activation, captured by the controller on its owning thread. */
internal data class ShufflePreparationInput(
    val queue: TrackQueue,
    val kind: ShufflePreparationKind,
    val available: Set<String>,
    val previous: List<String>,
    val lastAudible: String?
)

/** Owning-thread lifecycle; generation may suspend or even return after cancellation. */
internal class ShufflePreparation(
    private val scope: CoroutineScope,
    private val generate: suspend (ShufflePreparationInput) -> List<QueueOccurrence> = ::prepareShuffle,
    private val changed: () -> Unit
) {
    private var generation = 0L
    private var job: Job? = null
    var input: ShufflePreparationInput? = null
        private set
    var result: List<QueueOccurrence>? = null
        private set
    var failure: Exception? = null
        private set

    fun invalidate() {
        generation++
        job?.cancel(); job = null
        input = null; result = null; failure = null
    }

    fun prepare(next: ShufflePreparationInput) {
        if (input == next) return
        invalidate()
        input = next
        val ticket = generation
        job = scope.launch {
            try {
                val candidate = generate(next)
                currentCoroutineContext().ensureActive()
                if (ticket == generation && input == next) {
                    result = candidate
                    changed()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ticket == generation && input == next) {
                    failure = error
                    changed()
                }
            }
        }
    }
}

/** Reads OS bytes throughout generation, with no app-owned seed or provider fallback. */
internal class StreamShuffleRandom(private val input: DataInputStream, private val checkActive: () -> Unit = {}) : ShuffleRandom {
    override fun nextInt(bound: Int): Int {
        require(bound > 0)
        val range = 1L shl 32
        val limit = range - range % bound
        while (true) {
            checkActive()
            val draw = input.readInt().toLong() and 0xffffffffL
            if (draw < limit) return (draw % bound).toInt()
        }
    }
}

internal suspend fun prepareShuffle(input: ShufflePreparationInput): List<QueueOccurrence> = withContext(Dispatchers.IO) {
    val queue = input.queue
    val tail = queue.entries.drop(queue.currentIndex + 1)
    val scheduledSet = queue.scheduledIds.toHashSet()
    val byId = tail.associateBy { it.id }
    val scheduled = queue.scheduledIds.mapNotNull { byId[it] }
    val eligible = if (input.kind == ShufflePreparationKind.REMAINDER) tail.filterNot { it.id in scheduledSet }
        else queue.entries.filter { it.trackId in input.available }
    val identities = HashMap<String, Long>()
    fun identity(track: String): Long = identities.getOrPut(track) { identities.size.toLong() }
    val ids = LongArray(eligible.size) { identity(eligible[it].trackId) }
    val old = input.previous.map { identity(it) }.toLongArray()
    val audible = if (input.kind == ShufflePreparationKind.PASS) input.lastAudible?.let(::identity) else null
    DataInputStream(BufferedInputStream(FileInputStream("/dev/urandom"), 8192)).use { entropy ->
        val context = currentCoroutineContext()
        val order = ShuffleSampler(StreamShuffleRandom(entropy) { context.ensureActive() }).sample(ids, old, audible)
        currentCoroutineContext().ensureActive()
        val shuffled = order.map { eligible[it] }
        if (input.kind == ShufflePreparationKind.REMAINDER)
            queue.entries.take(queue.currentIndex + 1) + scheduled + shuffled
        else shuffled
    }
}

package com.example.clocarmus.playback

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.example.clocarmus.playback.shuffle.*
import com.example.clocarmus.ui.player.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.BitSet

internal data class PlaybackCheckpoint(
    val session: DemoSession, val ended: Boolean = false, val waitingForShuffle: Boolean = false,
    val shuffleSession: ShuffleSession? = null, val waitingForRemainder: Boolean = false, val automaticRemainderAdvance: Boolean = true
)

/** Immutable content-addressed structures plus atomically replaced, version-linked cursors. IO only. */
internal class PlaybackStore(context: Context, private val beforeWrite: (File) -> Unit = {}) {
    private val directory = context.filesDir
    private val file = AtomicFile(File(directory, "playback-v1.json")) // Retain the legacy discovery path.
    private val recovery = AtomicFile(File(directory, "playback-recovery.json"))
    private val historyDirectory = File(directory, "shuffle-history")
    private var cached: PlaybackCheckpoint? = null
    private var structureId: String? = null
    private var indices = emptyMap<String, Int>()
    private var committed: ByteArray? = null
    var recovered = false
        private set

    fun load(): PlaybackCheckpoint? = synchronized(lock) {
        recovered = false
        val primary = runCatching { read(file) }
        val previous = runCatching { read(recovery) }
        if (primary.getOrNull() == null && previous.getOrNull() == null && primary.isSuccess && previous.isSuccess) return@synchronized null
        val result = runCatching { decode(primary.getOrThrow() ?: error("Missing primary checkpoint")) }
        if (result.isSuccess) {
            committed = primary.getOrNull()
            result.getOrThrow().also { rememberLoaded(it) }
        } else {
            val bytes = previous.getOrThrow() ?: throw checkNotNull(result.exceptionOrNull())
            decode(bytes).also { recovered = true; committed = bytes; rememberLoaded(it) }
        }
    }

    private fun rememberLoaded(value: PlaybackCheckpoint) {
        cached = value
        structureId = committed?.let { JSONObject(String(it)).optString("structure").takeIf(String::isNotEmpty) }
        indices = value.session.queue.entries.withIndex().associate { it.value.id to it.index }
    }

    fun save(checkpoint: PlaybackCheckpoint) = synchronized(lock) {
        val s = checkpoint.session
        val q = s.queue
        val metadata = checkpoint.shuffleSession
        val old = cached
        val oldMetadata = old?.shuffleSession
        val sameStructure = old != null && old.session.queue.entries == q.entries &&
            old.session.queue.originalEntries == q.originalEntries && oldMetadata?.collection == metadata?.collection &&
            oldMetadata?.retained == metadata?.retained && oldMetadata?.generated == metadata?.generated
        if (!sameStructure || structureId == null) {
            val structure = JSONObject().put("version", 2).put("entries", occurrences(q.entries))
                .put("originalEntries", occurrences(q.originalEntries))
            metadata?.let {
                structure.put("collection", encodeCollection(it.collection)).put("retained", JSONArray(it.retained))
                    .put("generated", JSONArray(it.generated))
            }
            val bytes = structure.toString().toByteArray()
            val id = digest(bytes)
            val target = AtomicFile(File(directory, "playback-structure-$id.json"))
            // Always replace on a structural change: a corrupt existing hash-named file is repairable.
            write(target, bytes)
            write(AtomicFile(File(directory, "playback-structure-$id.recovery")), bytes)
            structureId = id
            indices = q.entries.withIndex().associate { it.value.id to it.index }
        }
        val cursor = JSONObject().put("version", 2).put("structure", structureId)
            .put("current", q.currentId).put("stop", q.stopAfterId).put("scheduled", JSONArray(q.scheduledIds))
            .put("continuation", q.resumeAfterStop).put("position", s.position.toDouble())
            .put("shuffle", s.shuffle).put("repeat", s.repeat).put("ended", checkpoint.ended)
            .put("shuffleBoundary", checkpoint.waitingForShuffle).put("remainderBoundary", checkpoint.waitingForRemainder).put("automaticAdvance", checkpoint.automaticRemainderAdvance)
            .put("consumed", flags(q.consumedIds)).put("replay", flags(q.replayIds))
            .put("audible", metadata?.lastAudible).put("pending", metadata?.pendingRemainder ?: false)
            .put("fresh", metadata?.freshSelection ?: false).put("historyRevision", metadata?.historyRevision ?: 0)
        // Keep the previous validated generation, including its matching immutable structure.
        if (committed == null) committed = runCatching { read(file)?.takeIf { runCatching { decode(it) }.isSuccess } }.getOrNull()
        committed?.let { write(recovery, it) }
        val payload = cursor.toString()
        val cursorBytes = JSONObject().put("version", 3).put("structure", structureId).put("payload", payload)
            .put("checksum", digest(payload.toByteArray())).toString().toByteArray()
        write(file, cursorBytes)
        committed = cursorBytes
        cached = checkpoint
        val keep = listOfNotNull(read(file), read(recovery)).mapNotNull {
            runCatching { JSONObject(String(it)).optString("structure").takeIf(String::isNotEmpty) }.getOrNull()
        }.toSet()
        directory.listFiles()?.filter { it.name.startsWith("playback-structure-") && it.extension == "json" }
            ?.filterNot { it.name.removePrefix("playback-structure-").removeSuffix(".json") in keep }
            ?.forEach { stale ->
                File(directory, stale.name.removeSuffix(".json") + ".recovery").delete()
                stale.delete()
            }
    }

    fun loadHistory(key: String): List<String> = loadHistoryRecord(key).order

    fun loadHistoryRecord(key: String): ShuffleHistory = synchronized(lock) {
        val name = digest(key.toByteArray())
        val primary = AtomicFile(File(historyDirectory, "$name.json"))
        val backup = AtomicFile(File(historyDirectory, "$name.recovery"))
        val first = runCatching { read(primary)?.let { decodeHistory(key, it) } }
        if (first.isSuccess && first.getOrNull() != null) return@synchronized first.getOrThrow()!!
        val previous = read(backup)
        if (previous != null) decodeHistory(key, previous)
        else if (first.isFailure) throw checkNotNull(first.exceptionOrNull()) else ShuffleHistory()
    }

    fun saveHistory(key: String, order: List<String>, revision: Long = 0) = synchronized(lock) {
        check(historyDirectory.isDirectory || historyDirectory.mkdirs())
        val name = digest(key.toByteArray())
        val primary = AtomicFile(File(historyDirectory, "$name.json"))
        val backup = AtomicFile(File(historyDirectory, "$name.recovery"))
        val bytes = JSONObject().put("version", 2).put("key", key).put("order", JSONArray(order)).put("revision", revision)
            .put("checksum", historyDigest(key, revision, order)).toString().toByteArray()
        val old = listOf(primary, backup).firstNotNullOfOrNull { candidate ->
            runCatching { read(candidate)?.takeIf { decodeHistory(key, it); true } }.getOrNull()
        }
        if (old != null && JSONObject(String(old)).optLong("revision") > revision) return@synchronized
        write(backup, old ?: bytes)
        write(primary, bytes)
    }

    private fun decodeHistory(key: String, bytes: ByteArray): ShuffleHistory {
        val record = JSONObject(String(bytes))
        val version = record.getInt("version")
        check(version in 1..2 && record.getString("key") == key)
        val order = strings(record.getJSONArray("order"))
        val revision = record.optLong("revision").also { check(it >= 0 && it < Long.MAX_VALUE) }
        if (version == 2) check(record.getString("checksum") == historyDigest(key, revision, order))
        return ShuffleHistory(order, revision)
    }

    private fun historyDigest(key: String, revision: Long, order: List<String>): String {
        val hash = MessageDigest.getInstance("SHA-256")
        val length = java.nio.ByteBuffer.allocate(4)
        fun part(value: String) {
            val bytes = value.toByteArray()
            length.clear(); length.putInt(bytes.size)
            hash.update(length.array()); hash.update(bytes)
        }
        part(key); part(revision.toString()); order.forEach(::part)
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    fun clearHistory() = synchronized(lock) { check(!historyDirectory.exists() || historyDirectory.deleteRecursively()) }

    private fun flags(ids: Set<String>): String {
        val bits = BitSet(indices.size)
        ids.forEach { bits.set(checkNotNull(indices[it])) }
        return "u:" + Base64.encodeToString(bits.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decode(bytes: ByteArray): PlaybackCheckpoint {
        val envelope = JSONObject(String(bytes))
        val cursor = if (envelope.getInt("version") == 3) {
            val payload = envelope.getString("payload")
            check(digest(payload.toByteArray()) == envelope.getString("checksum"))
            JSONObject(payload).also {
                check(it.getInt("version") == 2 && it.getString("structure") == envelope.getString("structure"))
            }
        } else envelope
        val version = cursor.getInt("version")
        check(version in 1..2)
        val structure = if (version == 1) cursor else {
            val id = cursor.getString("structure")
            check(id.matches(Regex("[0-9a-f]{64}")))
            val payload = listOf("json", "recovery").firstNotNullOfOrNull { suffix ->
                runCatching { read(AtomicFile(File(directory, "playback-structure-$id.$suffix")))?.takeIf { digest(it) == id } }.getOrNull()
            } ?: error("No valid queue structure")
            JSONObject(String(payload)).also { check(it.getInt("version") in 1..2) }
        }
        val entries = structure.getJSONArray("entries").let { array -> (0 until array.length()).map {
            val row = array.getJSONObject(it)
            QueueOccurrence(row.getString("track"), row.getString("id"))
        } }
        val byId = entries.associateBy { it.id }
        check(byId.size == entries.size)
        fun marker(key: String): String? = nullable(cursor, key)?.also { check(it in byId) }
        val current = marker("current")
        val scheduled = strings(cursor.getJSONArray("scheduled"))
        check(scheduled.distinct().size == scheduled.size && scheduled.all { it in byId })
        val original = if (version == 1) entries else if (structure.getInt("version") == 1)
            strings(structure.getJSONArray("original")).map { checkNotNull(byId[it]) }
        else structure.getJSONArray("originalEntries").let { array -> (0 until array.length()).map {
            val row = array.getJSONObject(it)
            QueueOccurrence(row.getString("track"), row.getString("id"))
        } }
        val originalById = original.associateBy { it.id }
        check(originalById.size == original.size && entries.all { originalById[it.id] == it })
        fun ids(key: String): Set<String> {
            if (version == 1) return if (key == "consumed")
                entries.take(entries.indexOfFirst { it.id == current }.coerceAtLeast(0)).mapTo(HashSet()) { it.id } else emptySet()
            val encoded = cursor.getString(key)
            val decoded = if (encoded.startsWith("u:")) Base64.decode(encoded.drop(2), Base64.NO_WRAP or Base64.URL_SAFE)
                else Base64.decode(encoded, Base64.NO_WRAP)
            val bits = BitSet.valueOf(decoded)
            check(bits.length() <= entries.size)
            return entries.indices.filter { bits[it] }.mapTo(HashSet()) { entries[it].id }
        }
        val position = cursor.getDouble("position").toFloat()
        check(position.isFinite() && position >= 0)
        val repeat = cursor.getInt("repeat").also { check(it in 0..2) }
        val metadata = structure.optJSONObject("collection")?.let {
            ShuffleSession(decodeCollection(it), strings(structure.getJSONArray("retained")), strings(structure.getJSONArray("generated")),
                nullable(cursor, "audible"), cursor.optBoolean("pending"), cursor.optBoolean("fresh"), cursor.optLong("historyRevision"))
        }
        return PlaybackCheckpoint(DemoSession(TrackQueue(entries, current, marker("stop"), scheduled,
            cursor.getBoolean("continuation"), original, ids("consumed"), ids("replay")), position,
            cursor.getBoolean("shuffle"), repeat), cursor.optBoolean("ended"), cursor.optBoolean("shuffleBoundary"), metadata, cursor.optBoolean("remainderBoundary"), cursor.optBoolean("automaticAdvance", true))
    }

    private fun encodeCollection(c: ShuffleCollection) = JSONObject().put("kind", c.kind).put("token", c.token)
        .put("artist", c.artist).put("albumTitle", c.album?.title).put("albumArtist", c.album?.artist)
        .put("genre", c.genre).put("scope", c.artistScope.name).put("directory", c.directory).put("query", c.query)

    private fun decodeCollection(j: JSONObject): ShuffleCollection {
        val kind = j.getString("kind").also { check(it in setOf("manual", "snapshot", "all", "artist", "album", "genre", "playlist", "folder")) }
        val album = nullable(j, "albumTitle")?.let { AlbumIdentity(it, j.getString("albumArtist")) }
        return ShuffleCollection(kind, j.getString("token"), nullable(j, "artist"), album, nullable(j, "genre"),
            ArtistScope.valueOf(j.getString("scope")), nullable(j, "directory"), j.getString("query"))
    }

    private fun occurrences(entries: List<QueueOccurrence>) = JSONArray().apply {
        entries.forEach { put(JSONObject().put("id", it.id).put("track", it.trackId)) }
    }

    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    private fun nullable(j: JSONObject, key: String) = if (j.isNull(key)) null else j.getString(key).takeIf(String::isNotEmpty)
    private fun read(file: AtomicFile): ByteArray? = if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
        else file.openRead().use { it.readBytes() }
    private fun write(file: AtomicFile, bytes: ByteArray) {
        beforeWrite(file.baseFile)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private companion object { val lock = Any() }
}

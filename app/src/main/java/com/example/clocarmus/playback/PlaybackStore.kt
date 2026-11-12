package com.example.clocarmus.playback

import android.content.Context
import android.util.AtomicFile
import com.example.clocarmus.ui.player.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class PlaybackCheckpoint(val session: DemoSession, val ended: Boolean = false, val waitingForShuffle: Boolean = false)

/** Atomic replacement retains the previous complete file when a write is interrupted. IO only. */
internal class PlaybackStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "playback-v1.json"))
    fun load(): PlaybackCheckpoint? = synchronized(lock) { loadLocked() }
    private fun loadLocked(): PlaybackCheckpoint? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        check(json.getInt("version") == 1)
        val entries = json.getJSONArray("entries").let { array -> (0 until array.length()).map {
            val row = array.getJSONObject(it)
            QueueOccurrence(row.getString("track"), row.getString("id"))
        } }
        check(entries.map { it.id }.distinct().size == entries.size)
        fun marker(key: String): String? = json.optString(key).takeIf { it.isNotEmpty() && it != "null" }?.also { id -> check(entries.any { it.id == id }) }
        val scheduled = json.getJSONArray("scheduled").let { a -> (0 until a.length()).map { a.getString(it) } }
        check(scheduled.all { id -> entries.any { it.id == id } })
        val position = json.getDouble("position").toFloat()
        check(position.isFinite() && position >= 0)
        return PlaybackCheckpoint(DemoSession(TrackQueue(entries, marker("current"), marker("stop"), scheduled,
            json.getBoolean("continuation")), position, json.getBoolean("shuffle"), json.getInt("repeat").also { check(it in 0..2) }), json.optBoolean("ended"), json.optBoolean("shuffleBoundary"))
    }
    fun save(checkpoint: PlaybackCheckpoint) = synchronized(lock) { saveLocked(checkpoint) }
    private fun saveLocked(checkpoint: PlaybackCheckpoint) {
        val s = checkpoint.session
        val q = s.queue
        val json = JSONObject().put("version", 1).put("entries", JSONArray().apply {
            q.entries.forEach { put(JSONObject().put("id", it.id).put("track", it.trackId)) }
        }).put("current", q.currentId).put("stop", q.stopAfterId).put("scheduled", JSONArray(q.scheduledIds))
            .put("continuation", q.resumeAfterStop).put("position", s.position.toDouble())
            .put("shuffle", s.shuffle).put("repeat", s.repeat).put("ended", checkpoint.ended)
            .put("shuffleBoundary", checkpoint.waitingForShuffle)
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray()); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
    private companion object { val lock = Any() }
}

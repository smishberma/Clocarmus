package com.example.clocarmus.ui.player

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class DemoSession(val queue: TrackQueue, val position: Float, val shuffle: Boolean, val repeat: Int)

internal fun saveDemoSession(preferences: SharedPreferences, session: DemoSession, durable: Boolean = false) {
    val queue = session.queue
    val json = JSONObject().put("entries", JSONArray().apply {
        queue.entries.forEach { put(JSONObject().put("id", it.id).put("track", it.trackId)) }
    }).put("current", queue.currentId).put("stop", queue.stopAfterId)
        .put("resumeAfterStop", queue.resumeAfterStop).put("scheduled", JSONArray(queue.scheduledIds)).put("position", session.position.toDouble())
        .put("shuffle", session.shuffle).put("repeat", session.repeat)
    val edit = preferences.edit().putString("snapshot", json.toString())
    if (durable) check(edit.commit()) { "Could not clear the queue. Please try again." } else edit.apply()
}

internal fun loadDemoSession(preferences: SharedPreferences): DemoSession? = try {
    preferences.getString("snapshot", null)?.let { raw ->
        val json = JSONObject(raw)
        val entries = json.getJSONArray("entries").let { array -> (0 until array.length()).map {
            val entry = array.getJSONObject(it)
            QueueOccurrence(entry.getString("track"), entry.getString("id"))
        } }
        fun marker(key: String) = json.optString(key).takeIf { id -> entries.any { it.id == id } }
        val scheduled = json.getJSONArray("scheduled").let { array -> (0 until array.length()).map { array.getString(it) } }
        DemoSession(TrackQueue(entries, marker("current"), marker("stop"), scheduled.filter { id -> entries.any { it.id == id } }, json.optBoolean("resumeAfterStop", false)),
            json.getDouble("position").toFloat().coerceAtLeast(0f), json.getBoolean("shuffle"), json.getInt("repeat").coerceIn(0, 2))
    }
} catch (_: org.json.JSONException) { null }

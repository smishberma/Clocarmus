package com.example.clocarmus.ui.player

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local demo state only; the transactional music database remains a later integration. */
internal fun loadPlaylists(preferences: SharedPreferences): PlaylistLibrary {
    val raw = preferences.getString("library", null) ?: return PlaylistLibrary()
    return try {
        val root = JSONObject(raw)
        fun timed(key: String): List<TimedTrack> {
            val array = root.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { i -> array.getJSONObject(i).let { TimedTrack(it.getString("id"), it.getLong("time")) } }
        }
        val custom = root.getJSONArray("custom")
        PlaylistLibrary((0 until custom.length()).map { i ->
            val item = custom.getJSONObject(i)
            val tracks = item.getJSONArray("tracks")
            SavedPlaylist(item.getString("id"), item.getString("name"), (0 until tracks.length()).map { tracks.getString(it) })
        }, timed("favorites"), timed("imported"), timed("played"))
    } catch (_: org.json.JSONException) { PlaylistLibrary() }
}
internal fun savePlaylists(preferences: SharedPreferences, library: PlaylistLibrary, durable: Boolean = false) {
    fun timed(entries: List<TimedTrack>) = JSONArray().apply {
        entries.forEach { put(JSONObject().put("id", it.id).put("time", it.timestamp)) }
    }
    val root = JSONObject().put("custom", JSONArray().apply {
        library.custom.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("tracks", JSONArray(it.tracks))) }
    }).put("favorites", timed(library.favorites)).put("imported", timed(library.imported)).put("played", timed(library.played))
    val edit = preferences.edit().putString("library", root.toString())
    if (durable) check(edit.commit()) { "Could not clear music memberships. Please try again." } else edit.apply()
}

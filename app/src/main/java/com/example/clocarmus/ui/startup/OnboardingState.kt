package com.example.clocarmus.ui.startup

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Storage grants are deliberately separate from the player's sample folder IDs. */
internal data class OnboardingFolder(val uri: String, val name: String, val location: String)
internal data class OnboardingState(
    val completed: Boolean = false,
    val folders: List<OnboardingFolder> = emptyList(),
    val exclusions: Set<String> = emptySet()
) {
    fun add(folder: OnboardingFolder) = copy(folders = folders.filterNot { it.uri == folder.uri } + folder)
    fun remove(uri: String) = copy(folders = folders.filterNot { it.uri == uri },
        exclusions = exclusions.filterNot { it == uri || it.startsWith("$uri/") }.toSet())
}

internal fun loadOnboarding(prefs: SharedPreferences): OnboardingState {
    val folders = runCatching {
        val array = JSONArray(prefs.getString("folders", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let {
            OnboardingFolder(it.getString("uri"), it.getString("name"), it.getString("location"))
        } }
    }.getOrDefault(emptyList())
    return OnboardingState(prefs.getBoolean("completed", false), folders,
        prefs.getStringSet("exclusions", emptySet())!!.toSet())
}

/** Called on IO; completion is only published after a successful durable write. */
internal fun saveOnboarding(prefs: SharedPreferences, state: OnboardingState) {
    val array = JSONArray()
    state.folders.forEach { array.put(JSONObject().put("uri", it.uri).put("name", it.name).put("location", it.location)) }
    check(prefs.edit().putBoolean("completed", state.completed).putString("folders", array.toString())
        .putStringSet("exclusions", state.exclusions).commit()) { "Could not save setup. Please try again." }
}

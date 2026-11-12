package com.example.clocarmus.ui.startup

import android.content.Context
import com.example.clocarmus.ui.player.*

/** Replayable reset journal: startup finishes an interrupted reset before exposing saved state.
 * The repository reset invalidates in-flight scanner writes before clearing durable records.
 * Call on IO. Never touches provider files or appearance/onboarding-completion preferences.
 */
internal fun finishPendingLibraryReset(context: Context) {
    val setup = context.getSharedPreferences("onboarding", 0)
    val state = loadOnboarding(setup)
    if (!state.resetPending) return
    kotlinx.coroutines.runBlocking {
        (context.applicationContext as com.example.clocarmus.ClocarmusApplication).library.reset()
    }
    val playlists = context.getSharedPreferences("demo-playlists", 0)
    savePlaylists(playlists, loadPlaylists(playlists).withoutMusic(), durable = true)
    val sessions = context.getSharedPreferences("demo-session", 0)
    val session = loadDemoSession(sessions)
    saveDemoSession(sessions, DemoSession(TrackQueue(), 0f, session?.shuffle ?: false, session?.repeat ?: 0), durable = true)
    check(context.getSharedPreferences("player-settings", 0).edit().putBoolean("demoLibraryRemoved", true)
        .putStringSet("sources", emptySet()).putStringSet("exclusions", emptySet()).commit()) {
        "Could not finish removing libraries. Please try again."
    }
    saveOnboarding(setup, state.copy(resetPending = false))
}

package com.example.clocarmus

import android.app.Application
import android.content.Context
import com.example.clocarmus.ui.player.PlaylistLibrary
import com.example.clocarmus.ui.player.loadPlaylists
import com.example.clocarmus.ui.startup.StartupSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ClocarmusApplication : Application() {
    internal lateinit var startup: StartupSession<PlaylistLibrary>
        private set

    override fun onCreate() {
        super.onCreate()
        startup = StartupSession(CoroutineScope(SupervisorJob() + Dispatchers.IO), PlaylistLibrary()) {
            // This demo has no onboarding/settings/session repository yet. Load only
            // its existing local state; future discovery must stay outside this gate.
            loadPlaylists(getSharedPreferences("demo-playlists", Context.MODE_PRIVATE))
        }
    }
}

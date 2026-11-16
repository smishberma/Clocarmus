package com.example.clocarmus

import android.app.Application
import android.content.Context
import com.example.clocarmus.ui.player.PlaylistLibrary
import com.example.clocarmus.ui.player.loadPlaylists
import com.example.clocarmus.ui.startup.OnboardingState
import com.example.clocarmus.ui.startup.loadOnboarding
import com.example.clocarmus.ui.startup.StartupSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ClocarmusApplication : Application() {
    private val playbackDelegate = lazy { com.example.clocarmus.playback.PlaybackController(this) }
    internal val playback by playbackDelegate
    internal fun existingPlayback() = if (playbackDelegate.isInitialized()) playback else null

    internal val library by lazy { com.example.clocarmus.library.LocalLibrary(this) }

    internal lateinit var startup: StartupSession<PlaylistLibrary>
        private set

    internal var onboarding = OnboardingState()
        private set

    internal fun completeOnboarding() { onboarding = onboarding.copy(completed = true) }

    override fun onCreate() {
        super.onCreate()
        startup = StartupSession(CoroutineScope(SupervisorJob() + Dispatchers.IO), PlaylistLibrary()) {
            // Only local readiness belongs here; discovery must not hold branding.
            onboarding = loadOnboarding(getSharedPreferences("onboarding", Context.MODE_PRIVATE))
            com.example.clocarmus.ui.startup.finishPendingLibraryReset(this)
            onboarding = loadOnboarding(getSharedPreferences("onboarding", Context.MODE_PRIVATE))
            loadPlaylists(getSharedPreferences("demo-playlists", Context.MODE_PRIVATE))
        }
    }
}

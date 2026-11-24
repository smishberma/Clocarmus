package com.example.clocarmus

import android.content.Intent
import com.example.clocarmus.launcher.LauncherController
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import com.example.clocarmus.ui.startup.SplashScreen
import com.example.clocarmus.ui.startup.WelcomeFlow
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.clocarmus.ui.player.MainPlayerScreen
import com.example.clocarmus.ui.theme.ClocarmusTheme

class MainActivity : ComponentActivity() {
    internal lateinit var launcher: LauncherController
        private set
    internal var homeActivation by mutableStateOf(0)
        private set

    private val homeSelection = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        if (::launcher.isInitialized) launcher.reconcile()
    }

    private var startupFullscreen = true
    private var statusBarVisible = false
    private var platformLaunchReleased by mutableStateOf(Build.VERSION.SDK_INT < 31)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) {
            // No second platform exit animation or keep-on-screen delay.
            splashScreen.setOnExitAnimationListener {
                it.remove()
                platformLaunchReleased = true
            }
        }
        launcher = LauncherController(this) { homeSelection.launch(it) }
        if (savedInstanceState == null && (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra("home_activation", false))) homeActivation++
        val startup = (application as ClocarmusApplication).startup
        val freshStartup = startup.claimBranding()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        statusBarVisible = getSharedPreferences("player-settings", MODE_PRIVATE).getBoolean("statusBar", false)
        hideSystemBars()
        // A stable visual fixture for AVD comparisons; unavailable in release builds.
        val referencePreview = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("reference_preview", false)
        setContent {
            ClocarmusTheme {
                val staticSplash = referencePreview && intent.getBooleanExtra("splash_preview", false)
                val ready by startup.ready.collectAsState()
                val welcomePreview = if (referencePreview) intent.getStringExtra("welcome_preview")?.takeIf {
                    it in setOf("welcome", "warning", "permission", "folders", "loading")
                } else null
                var setupComplete by rememberSaveable { mutableStateOf(false) }
                val onboarding = (application as ClocarmusApplication).onboarding
                val showWelcome = !setupComplete && (welcomePreview != null || (!referencePreview && !onboarding.completed))
                var branding by remember { mutableStateOf(freshStartup && !referencePreview) }
                val opacity = remember { Animatable(1f) }
                var brandingDrawn by remember { mutableStateOf(false) }
                LaunchedEffect(ready != null, brandingDrawn, platformLaunchReleased) {
                    if (ready != null && branding && brandingDrawn && platformLaunchReleased) {
                        // Let the ready destination compose behind the overlay before fading.
                        withFrameNanos { }
                        opacity.animateTo(0f, tween(300))
                        branding = false
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (!staticSplash && !showWelcome && (ready != null || referencePreview)) MainPlayerScreen(referencePreview,
                        menuPreview = referencePreview && intent.getBooleanExtra("menu_preview", false),
                        tracksPreview = referencePreview && intent.getBooleanExtra("tracks_preview", false),
                        artistsPreview = if (referencePreview) intent.getStringExtra("artists_preview")?.takeIf { it in setOf("artists", "artist-detail", "artist-tracks", "artist-albums") } else null,
                        albumsPreview = if (referencePreview) intent.getStringExtra("albums_preview")?.takeIf { it in setOf("albums", "album-options", "album-tracks") } else null,
                        genresPreview = if (referencePreview) intent.getStringExtra("genres_preview")?.takeIf { it in setOf("genres", "genre-options", "genre-tracks", "genre-artists", "genre-albums") } else null,
                        foldersPreview = if (referencePreview) intent.getStringExtra("folders_preview")?.takeIf { it in setOf("folders", "folder-directory") } else null,
                        collectionsPreview = if (referencePreview) intent.getStringExtra("collections_preview")?.takeIf { it in setOf("collections-favorites", "collections-empty", "collections-custom", "collections-member") } else null,
                        playlistsPreview = if (referencePreview) intent.getStringExtra("playlists_preview")?.takeIf { it in setOf("playlists-empty", "playlists", "playlist-favorites", "playlist-added", "playlist-played", "playlist-custom", "playlist-custom-empty", "playlist-favorites-empty", "playlist-added-empty", "playlist-played-empty") } else null,
                        settingsPreview = if (referencePreview) intent.getStringExtra("settings_preview")?.takeIf { it in setOf("settings-about", "settings-developer", "settings-donate", "settings", "settings-scrolled", "settings-folders", "settings-background", "settings-background-expanded", "settings-background-solid", "settings-background-gradient", "settings-clock", "settings-clock-vertical", "settings-clock-lcd", "settings-clock-dotmatrix", "settings-divider-dotted", "settings-divider-squiggly", "settings-divider-beam") } else null,
                        queuePreview = referencePreview && intent.getBooleanExtra("queue_preview", false),
                        initialPlaylists = ready?.value,
                        onPlaylistsChanged = startup::update,
                        freshStartup = freshStartup && !setupComplete && !(intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra("home_activation", false)) && !intent.getBooleanExtra("boot_open", false))
                    SideEffect {
                        startupFullscreen = staticSplash || branding || showWelcome
                        hideSystemBars()
                    }
                    if (!staticSplash && showWelcome && (ready != null || referencePreview)) {
                        WelcomeFlow(onboarding, welcomePreview) { completed, skipped ->
                            if (welcomePreview == null) (application as ClocarmusApplication).completeOnboarding(completed, skipped)
                            setupComplete = true
                        }
                    }
                    var showReadError by remember { mutableStateOf(true) }
                    if (!branding && ready?.failed == true && showReadError && !referencePreview) {
                        TextButton(onClick = { showReadError = false }, modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black)) {
                            Text("Saved state could not be loaded. Using defaults. Tap to dismiss.", color = Color.White)
                        }
                    }
                    if (staticSplash || branding) SplashScreen(Modifier
                        .drawWithContent {
                            drawContent()
                            brandingDrawn = true
                        }
                        .graphicsLayer { alpha = if (staticSplash) 1f else opacity.value }
                        .pointerInput(Unit) {
                            // Branding is not an interactive surface; do not tap through the fade.
                            awaitEachGesture {
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra("home_activation", false))) homeActivation++
    }

    override fun onResume() {
        super.onResume()
        if (::launcher.isInitialized) launcher.reconcile()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            // Some launches (including shell launches) have no system splash view
            // and therefore no exit callback. Focus also releases that handoff.
            platformLaunchReleased = true
        }
    }

    fun setStatusBarVisible(visible: Boolean) {
        if (statusBarVisible != visible) { statusBarVisible = visible; hideSystemBars() }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (if (statusBarVisible && !startupFullscreen) 0 else View.SYSTEM_UI_FLAG_FULLSCREEN) or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

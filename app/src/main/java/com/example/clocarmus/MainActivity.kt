package com.example.clocarmus

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.clocarmus.ui.player.MainPlayerScreen
import com.example.clocarmus.ui.theme.ClocarmusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        // A stable visual fixture for AVD comparisons; unavailable in release builds.
        val referencePreview = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("reference_preview", false)
        setContent {
            ClocarmusTheme {
                MainPlayerScreen(referencePreview,
                    menuPreview = referencePreview && intent.getBooleanExtra("menu_preview", false),
                    tracksPreview = referencePreview && intent.getBooleanExtra("tracks_preview", false),
                    artistsPreview = if (referencePreview) intent.getStringExtra("artists_preview")?.takeIf { it in setOf("artists", "artist-detail", "artist-tracks", "artist-albums") } else null,
                    albumsPreview = if (referencePreview) intent.getStringExtra("albums_preview")?.takeIf { it in setOf("albums", "album-options", "album-tracks") } else null,
                    genresPreview = if (referencePreview) intent.getStringExtra("genres_preview")?.takeIf { it in setOf("genres", "genre-options", "genre-tracks", "genre-artists", "genre-albums") } else null,
                    playlistsPreview = if (referencePreview) intent.getStringExtra("playlists_preview")?.takeIf { it in setOf("playlists-empty", "playlists", "playlist-favorites", "playlist-added", "playlist-played", "playlist-custom", "playlist-custom-empty", "playlist-favorites-empty", "playlist-added-empty", "playlist-played-empty") } else null)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

package com.example.clocarmus.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

internal const val PLAYER_PANE_FADE_DURATION_MS = 300

internal enum class PlayerPaneRoot { PLAYER, MENU, TRACKS, QUEUE, BROWSER, COLLECTIONS }

/** A navigation snapshot keeps the outgoing page intact while live playback continues. */
internal data class PlayerPaneDestination(
    val root: PlayerPaneRoot,
    val browser: BrowserDestination? = null,
    val collectionTrack: String? = null
)

internal data class PlayerOverlayDestination(
    val trackId: String,
    val info: TrackInfoRequest? = null,
    val collections: Boolean = false,
    val occurrence: String? = null
)

/** Crossfade navigation while preventing outgoing controls and Back handlers from firing. */
@Composable
internal fun <T> PlayerPaneCrossfade(target: T, content: @Composable (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val inactiveOwner = remember {
        object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
        }
    }
    Crossfade(targetState = target, modifier = Modifier.fillMaxSize(),
        animationSpec = tween(PLAYER_PANE_FADE_DURATION_MS), label = "Player navigation") { displayed ->
        val outgoing = displayed != target
        val input = if (outgoing) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        } else Modifier
        CompositionLocalProvider(LocalLifecycleOwner provides if (outgoing) inactiveOwner else lifecycleOwner) {
            Box(Modifier.fillMaxSize().then(input)) { content(displayed) }
        }
    }
}

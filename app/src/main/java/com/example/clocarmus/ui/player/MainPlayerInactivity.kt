package com.example.clocarmus.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Transient UI state. All entry points run on the UI thread, including animation updates. */
@Stable
internal class MainPlayerInactivity(private val scope: CoroutineScope) {
    var opacity by mutableFloatStateOf(1f)
        private set
    private var active = false
    private var fingerDown = false
    private var countdown: Job? = null

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        fingerDown = false
        restart()
    }

    fun touch(anyFingerDown: Boolean) {
        if (!active) return
        fingerDown = anyFingerDown
        restart()
    }

    fun cancelTouches() {
        if (fingerDown) touch(false)
    }

    private fun restart() {
        countdown?.cancel()
        countdown = null
        opacity = 1f
        if (active && !fingerDown) {
            countdown = scope.launch {
                delay(10_000)
                animate(1f, 0.20f, animationSpec = tween(500, easing = LinearEasing)) { value, _ ->
                    opacity = value
                }
            }
        }
    }
}

@Composable
internal fun rememberMainPlayerInactivity(enabled: Boolean): MainPlayerInactivity {
    val scope = rememberCoroutineScope()
    val inactivity = remember(scope) { MainPlayerInactivity(scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, enabled, inactivity) {
        // Observe lifecycle events directly so suspension also happens when no UI frames are drawn.
        val observer = LifecycleEventObserver { _, _ ->
            inactivity.setActive(enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        inactivity.setActive(enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            inactivity.setActive(false)
        }
    }
    return inactivity
}

/** Observe before child handlers, including consumed drags, without consuming any pointer changes. */
internal fun Modifier.observePlayerTouches(inactivity: MainPlayerInactivity, sliderPointers: SliderPointers): Modifier = pointerInput(inactivity, sliderPointers) {
    try {
        awaitPointerEventScope {
            while (true) {
                val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                sliderPointers.pressedCount = changes.count { it.pressed }
                if (changes.any { it.pressed || it.previousPressed }) {
                    inactivity.touch(changes.any { it.pressed })
                }
            }
        }
    } finally {
        sliderPointers.pressedCount = 0
        inactivity.cancelTouches()
    }
}

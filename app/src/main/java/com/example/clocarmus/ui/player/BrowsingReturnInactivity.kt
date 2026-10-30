package com.example.clocarmus.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A selection arms the return; ordinary interaction can reset it but never rearm a cancellation. */
@Stable
internal class BrowsingReturnInactivity(private val scope: CoroutineScope, private val onReturn: () -> Unit) {
    private var active = false
    private var armed = false
    private var fingerDown = false
    private var scrolling = false
    private var countdown: Job? = null

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        fingerDown = false
        restart()
    }

    fun trackSelected() {
        armed = true
        restart()
    }

    fun interaction() = restart()

    fun touch(anyFingerDown: Boolean) {
        if (!active) return
        fingerDown = anyFingerDown
        restart()
    }

    fun cancelTouches() {
        if (fingerDown) touch(false)
    }

    fun setScrolling(value: Boolean) {
        if (scrolling == value) return
        scrolling = value
        restart()
    }

    fun cancel() {
        armed = false
        restart()
    }

    private fun restart() {
        countdown?.cancel()
        countdown = null
        if (active && armed && !fingerDown && !scrolling) {
            countdown = scope.launch {
                delay(15_000)
                armed = false
                countdown = null
                onReturn()
            }
        }
    }
}

@Composable
internal fun rememberBrowsingReturnInactivity(
    browsing: Boolean,
    suspended: Boolean,
    onReturn: () -> Unit
): BrowsingReturnInactivity {
    val scope = rememberCoroutineScope()
    val latestReturn by rememberUpdatedState(onReturn)
    val inactivity = remember(scope) { BrowsingReturnInactivity(scope) { latestReturn() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, browsing, suspended, inactivity) {
        if (!browsing) inactivity.cancel()
        fun updateActive() {
            inactivity.setActive(browsing && !suspended && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        val observer = LifecycleEventObserver { _, _ -> updateActive() }
        lifecycle.addObserver(observer)
        updateActive()
        onDispose {
            lifecycle.removeObserver(observer)
            inactivity.setActive(false)
        }
    }
    return inactivity
}

/** Includes the clock pane and consumed child drags without intercepting the user's action. */
internal fun Modifier.observeBrowsingTouches(inactivity: BrowsingReturnInactivity): Modifier = pointerInput(inactivity) {
    try {
        awaitPointerEventScope {
            while (true) {
                val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                if (changes.any { it.pressed || it.previousPressed }) inactivity.touch(changes.any { it.pressed })
            }
        }
    } finally {
        inactivity.cancelTouches()
    }
}

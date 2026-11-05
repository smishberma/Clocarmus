package com.example.clocarmus.ui.player

import androidx.compose.runtime.staticCompositionLocalOf

/** Clears unfinished pane-local work before the existing saveable browser state is captured. */
internal class PlayerHomeNavigation(private val navigate: () -> Unit) {
    private val cancellations = mutableSetOf<() -> Unit>()

    fun registerCancellation(cancel: () -> Unit): () -> Unit {
        cancellations += cancel
        return { cancellations -= cancel }
    }

    fun returnToPlayer() {
        cancellations.toList().forEach { it() }
        navigate()
    }
}

internal val LocalPlayerHome = staticCompositionLocalOf<PlayerHomeNavigation?> { null }

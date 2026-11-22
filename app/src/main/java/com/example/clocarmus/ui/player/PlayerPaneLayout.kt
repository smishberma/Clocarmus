package com.example.clocarmus.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/** Place the stable player/clock subtrees on either side without mirroring their contents. */
@Composable
internal fun PlayerPaneLayout(rightHandDrive: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val leftWidth = constraints.maxWidth / 2
        val rightWidth = constraints.maxWidth - leftWidth
        val playerWidth = if (rightHandDrive) rightWidth else leftWidth
        val clockWidth = constraints.maxWidth - playerWidth
        val player = measurables[0].measure(Constraints.fixed(playerWidth, constraints.maxHeight))
        val clock = measurables[1].measure(Constraints.fixed(clockWidth, constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            player.place(if (rightHandDrive) clockWidth else 0, 0)
            clock.place(if (rightHandDrive) 0 else playerWidth, 0)
        }
    }
}

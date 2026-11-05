package com.example.clocarmus.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R

@Composable
internal fun PlayerSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier,
                         label: String, scale: Float, volume: Boolean = false,
                         enabled: Boolean = true, gestureKey: Any? = Unit, pointers: SliderPointers,
                         onPreview: (Float?) -> Unit = {}) {
    val update by rememberUpdatedState(onValueChange)
    val previewUpdate by rememberUpdatedState(onPreview)
    val currentValue by rememberUpdatedState(value.coerceIn(0f, 1f))
    val currentGestureKey by rememberUpdatedState(gestureKey)
    var dragValue by remember(gestureKey, enabled) { mutableStateOf<Float?>(null) }
    val displayedValue = dragValue ?: currentValue
    val trackHeight = (if (volume) 61f else 36f) * scale
    val thumbRadius = 33f * scale
    val density = LocalDensity.current
    val trackPx = with(density) { trackHeight.dp.toPx() }
    val thumbPx = with(density) { thumbRadius.dp.toPx() }
    val targetPx = with(density) { maxOf(48f, if (volume) 0f else thumbRadius * 2).dp.toPx() }
    Box(modifier.defaultMinSize(minHeight = 48.dp).semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(displayedValue, 0f..1f)
        if (!enabled) disabled()
        setProgress {
            if (enabled) update(it.coerceIn(0f, 1f))
            enabled
        }
    }.pointerInput(gestureKey, enabled, targetPx, pointers) {
        try {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!enabled) return@awaitEachGesture
                val keyAtStart = currentGestureKey
                val drag = SliderDrag(currentValue, down.position.x, down.position.y,
                    size.width.toFloat(), size.height.toFloat(), targetPx, viewConfiguration.touchSlop)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed || currentGestureKey != keyAtStart) break
                        if (!change.pressed) {
                            // A second finger still held means the initiating pointer was lost.
                            if (pointers.pressedCount == 0) {
                                drag.finish()?.let { if (!volume) update(it) }
                            }
                            break
                        }
                        drag.move(change.position.x, change.position.y)?.let {
                            change.consume()
                            if (!volume) dragValue = it
                            if (volume) update(it) else previewUpdate(it)
                        }
                    }
                } finally {
                    drag.cancel()
                    dragValue = null
                    previewUpdate(null)
                }
            }
        } finally {
            dragValue = null
            previewUpdate(null)
        }
    }, contentAlignment = Alignment.Center) {
        if (volume) {
            Image(painterResource(R.drawable.player_volume_track), null,
                Modifier.fillMaxWidth().height(trackHeight.dp), contentScale = ContentScale.Fit)
        }
        Canvas(Modifier.fillMaxSize()) {
            val top = (size.height - trackPx) / 2
            val radius = if (volume) CornerRadius(trackPx / 2) else CornerRadius(5f * scale * density.density, trackPx / 2)
            val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, top, size.width, top + trackPx, radius.x, radius.y)) }
            if (!volume) drawPath(outline, Color(0xff494949))
            clipPath(outline) { drawRect(Color.White, Offset(0f, top), Size(size.width * displayedValue, trackPx)) }
            if (!volume) drawCircle(Color.White, thumbPx, Offset(size.width * displayedValue, size.height / 2))
        }
        if (volume) Image(painterResource(R.drawable.player_volume_slider_icon), null,
            Modifier.align(Alignment.CenterStart).padding(start = (15f * scale).dp).size(trackHeight.dp), contentScale = ContentScale.Fit)
    }
}


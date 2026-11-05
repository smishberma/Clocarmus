package com.example.clocarmus.ui.player

import kotlin.math.abs

/** One pointer's relative drag. Coordinates and thresholds are in pixels. */
internal class SliderDrag(
    private val startValue: Float,
    private val startX: Float,
    private val startY: Float,
    private val width: Float,
    height: Float,
    targetSize: Float,
    private val touchSlop: Float
) {
    private var eligible = width > 0f && height > 0f &&
        startX in maxOf(0f, startValue * width - targetSize / 2)..minOf(width, startValue * width + targetSize / 2) &&
        startY in maxOf(0f, height / 2 - targetSize / 2)..minOf(height, height / 2 + targetSize / 2)
    private var dragging = false
    private var preview: Float? = null

    fun move(x: Float, y: Float): Float? {
        if (!eligible) return null
        val dx = x - startX
        val dy = y - startY
        if (!dragging) {
            // A vertical gesture cannot become a scrub later in the same contact.
            if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                cancel()
                return null
            }
            if (abs(dx) <= touchSlop || abs(dx) <= abs(dy)) return null
            dragging = true
        }
        return (startValue + dx / width).coerceIn(0f, 1f).also { preview = it }
    }

    fun finish(): Float? = preview.also { cancel() }

    fun cancel() {
        eligible = false
        dragging = false
        preview = null
    }
}

/** Root-level count: a slider's hit-tested event omits fingers over other controls. */
internal class SliderPointers {
    var pressedCount: Int = 0
}

package com.example.clocarmus.ui.player

/** Pixel offsets for the custom scrollbar without measuring offscreen Compose rows. */
internal class BrowserListGeometry(heights: IntArray, gap: Int) {
    private val starts = IntArray(heights.size + 1).apply {
        heights.forEachIndexed { index, height ->
            this[index + 1] = this[index] + height + if (index < heights.lastIndex) gap else 0
        }
    }
    val totalHeight: Int get() = starts.last()

    fun maximum(viewport: Int): Int = (totalHeight - viewport).coerceAtLeast(0)

    fun pixels(index: Int, offset: Int): Int =
        starts[index.coerceIn(0, starts.lastIndex)] + offset

    fun position(pixels: Int): BrowserScrollPosition {
        if (starts.size == 1) return BrowserScrollPosition(0, 0)
        val bounded = pixels.coerceIn(0, (totalHeight - 1).coerceAtLeast(0))
        val found = starts.binarySearch(bounded)
        val index = if (found >= 0) found else -found - 2
        return BrowserScrollPosition(index, bounded - starts[index])
    }
}

internal data class BrowserScrollPosition(val index: Int, val offset: Int)

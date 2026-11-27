package com.example.clocarmus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserListGeometryTest {
    @Test fun scrollbarReachesBothEndsWithoutTrailingGap() {
        val geometry = BrowserListGeometry(IntArray(10_000) { 126 }, 14)
        assertEquals(1_399_986, geometry.totalHeight)
        assertEquals(BrowserScrollPosition(0, 0), geometry.position(-100))
        val end = geometry.position(geometry.maximum(700))
        assertEquals(BrowserScrollPosition(9_994, 126), end)
        assertEquals(geometry.maximum(700), geometry.pixels(end.index, end.offset))
    }

    @Test fun mixedFolderRowsAndGapsMapToExactOffsets() {
        val geometry = BrowserListGeometry(intArrayOf(98, 126, 98), 44)
        assertEquals(410, geometry.totalHeight)
        assertEquals(BrowserScrollPosition(0, 120), geometry.position(120))
        assertEquals(BrowserScrollPosition(1, 0), geometry.position(142))
        assertEquals(BrowserScrollPosition(2, 0), geometry.position(312))
        for (pixel in 0 until geometry.totalHeight) {
            val position = geometry.position(pixel)
            assertEquals(pixel, geometry.pixels(position.index, position.offset))
        }
    }

    @Test fun emptyAndShortListsHaveNoScrollRange() {
        val empty = BrowserListGeometry(intArrayOf(), 14)
        assertEquals(0, empty.maximum(700))
        assertEquals(0, empty.pixels(20, 0))
        assertEquals(BrowserScrollPosition(0, 0), empty.position(100))
        val single = BrowserListGeometry(intArrayOf(126), 14)
        assertEquals(126, single.totalHeight)
        assertEquals(0, single.maximum(700))
        assertEquals(BrowserScrollPosition(0, 125), single.position(500))
    }
}

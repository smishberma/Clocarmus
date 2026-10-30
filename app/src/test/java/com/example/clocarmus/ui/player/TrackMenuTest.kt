package com.example.clocarmus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMenuTest {
    @Test fun demoLibraryCoversAlphabetWithExactly28UniqueSortedTitles() {
        assertEquals(28, demoTrackTitles.size)
        assertEquals(28, demoTrackTitles.distinct().size)
        assertEquals(demoTrackTitles.sorted(), demoTrackTitles)
        assertEquals(('A'..'Z').toSet(), demoTrackTitles.map { it.first() }.toSet())
    }

    @Test fun dragReachesBothEndsAndClampsOutsideRail() {
        assertEquals(0f, fastScrollFraction(-50f, 620f))
        assertEquals(0f, fastScrollFraction(0f, 620f))
        assertEquals(.5f, fastScrollFraction(310f, 620f))
        assertEquals(1f, fastScrollFraction(620f, 620f))
        assertEquals(1f, fastScrollFraction(900f, 620f))
    }

    @Test fun nonScrollableRailDoesNotDivideByZero() {
        assertEquals(0f, fastScrollFraction(50f, 0f))
        assertEquals(0f, fastScrollFraction(50f, -1f))
    }

    @Test fun bubbleTracksEntireAlphabetIncludingLastTitle() {
        assertEquals("A", fastScrollLetter(demoTrackTitles, 0f))
        assertEquals("N", fastScrollLetter(demoTrackTitles, .5f))
        assertEquals("Z", fastScrollLetter(demoTrackTitles, 1f))
        val letters = (0..100).map { fastScrollLetter(demoTrackTitles, it / 100f) }
        assertTrue(letters.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test fun filteredListsUseTheirOwnLettersAndEmptyListHasNoLetter() {
        assertEquals("", fastScrollLetter(emptyList(), .5f))
        assertEquals("Q", fastScrollLetter(listOf("Quartz"), 1f))
        assertEquals("A", fastScrollLetter(listOf("Amber", "Zenith"), -1f))
        assertEquals("Z", fastScrollLetter(listOf("Amber", "Zenith"), 2f))
    }
}

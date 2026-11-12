package com.example.clocarmus.library

import org.junit.Assert.*
import org.junit.Test

class ReleaseYearTest {
    @Test fun yearTakesPrecedenceOverDate() {
        assertEquals("1985", releaseYear("1985", "2026-11-12"))
    }
    @Test fun fullDatesYieldOnlyFourDigitYear() {
        assertEquals("2004", releaseYear(null, "2004-06-15"))
        assertEquals("2004", releaseYear("2004-06-15", null))
        assertEquals("2004", releaseYear(" ", " 2004-06-15T12:00:00Z "))
    }
    @Test fun absentAndEmptyTagsDoNotInventAYear() {
        assertNull(releaseYear(null, null))
        assertNull(releaseYear(" ", "\n"))
        assertNull(releaseYear("Unknown year", ""))
    }
    @Test fun invalidYearFallsBackToDateWithoutTruncatingLongNumbers() {
        assertEquals("1999", releaseYear("unknown", "1999-12-01"))
        assertNull(releaseYear(null, "12345"))
    }
}

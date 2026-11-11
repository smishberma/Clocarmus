package com.example.clocarmus.ui.startup

import org.junit.Assert.*
import org.junit.Test

class OnboardingStateTest {
    private val music = OnboardingFolder("content://storage/tree/music", "Music", "USB storage - Music")
    @Test fun addingSameGrantUpdatesWithoutDuplicating() {
        val updated = music.copy(name = "Renamed")
        assertEquals(listOf(updated), OnboardingState().add(music).add(updated).folders)
    }
    @Test fun removingSourceDropsOnlyItsExclusions() {
        val other = OnboardingFolder("content://storage/tree/other", "Other", "Other")
        val state = OnboardingState(folders = listOf(music, other), exclusions = setOf(
            "${music.uri}/document/music%2Fpodcasts", "${other.uri}/document/other%2Fa"))
        val removed = state.remove(music.uri)
        assertEquals(listOf(other), removed.folders)
        assertEquals(setOf("${other.uri}/document/other%2Fa"), removed.exclusions)
        assertFalse(removed.completed)
    }
}

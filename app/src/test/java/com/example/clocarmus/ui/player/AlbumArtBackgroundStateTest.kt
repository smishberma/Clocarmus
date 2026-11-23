package com.example.clocarmus.ui.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumArtBackgroundStateTest {
    private fun art(id: String) = BackgroundArtwork(id, id)

    @Test fun rapidSelectionsRetainPreviousImageUntilLatestIsStable() = runTest {
        val state = AlbumArtBackgroundState<String>(backgroundScope)
        state.select(true, "track-1") { art("old") }
        advanceTimeBy(3_000); runCurrent()
        state.finishFade(state.frame.value.revision)
        state.select(true, "track-2") { art("A") }
        advanceTimeBy(2_000)
        state.select(true, "track-3") { art("B") }
        advanceTimeBy(2_000)
        state.select(true, "track-4") { art("C") }
        advanceTimeBy(2_999); runCurrent()
        assertEquals("old", state.frame.value.current?.image)
        advanceTimeBy(1); runCurrent()
        assertEquals("C", state.frame.value.current?.image)
        assertEquals("old", state.frame.value.outgoing?.image)
        assertTrue(state.frame.value.transitioning)
        state.finishFade(state.frame.value.revision)
        assertNull(state.frame.value.outgoing)
        assertFalse(state.frame.value.transitioning)
    }

    @Test fun coldStartAndMissingOrFailedArtFadeFromAndToBlack() = runTest {
        val state = AlbumArtBackgroundState<String>(backgroundScope)
        assertNull(state.frame.value.current)
        state.select(true, "track-5") { art("A") }
        advanceTimeBy(2_999); runCurrent()
        assertNull(state.frame.value.current)
        advanceTimeBy(1); runCurrent()
        assertTrue(state.frame.value.transitioning)
        assertNull(state.frame.value.outgoing) // An opaque black outgoing layer.
        state.finishFade(state.frame.value.revision)
        state.select(true, "track-6") { error("corrupt cover") }
        advanceTimeBy(3_000); runCurrent()
        assertNull(state.frame.value.current)
        assertEquals("A", state.frame.value.outgoing?.image)
    }

    @Test fun slowDecodeKeepsExistingImageAndIdenticalCoversAvoidAnotherFade() = runTest {
        val state = AlbumArtBackgroundState<String>(backgroundScope)
        state.select(true, "track-7") { art("same") }
        advanceTimeBy(3_000); runCurrent()
        state.finishFade(state.frame.value.revision)
        val revision = state.frame.value.revision
        val decode = CompletableDeferred<BackgroundArtwork<String>?>()
        state.select(true, "track-8") { decode.await() }
        advanceTimeBy(8_000); runCurrent()
        assertEquals("same", state.frame.value.current?.image)
        decode.complete(art("same")); runCurrent()
        assertEquals(revision, state.frame.value.revision)
        assertFalse(state.frame.value.transitioning)
    }

    @Test fun typeChangeAndReentryCancelEvenAnUncooperativeOldDecode() = runTest {
        val state = AlbumArtBackgroundState<String>(backgroundScope)
        val decode = CompletableDeferred<BackgroundArtwork<String>?>()
        state.select(true, "track-9") { withContext(NonCancellable) { decode.await() } }
        runCurrent()
        advanceTimeBy(3_100)
        state.select(false, "track-10") { error("must not load") }
        decode.complete(art("stale")); runCurrent()
        assertNull(state.frame.value.current)
        state.select(true, "track-11") { art("new") }
        advanceTimeBy(2_999); runCurrent()
        assertNull(state.frame.value.current)
        advanceTimeBy(1); runCurrent()
        assertEquals("new", state.frame.value.current?.image)
        val current = state.frame.value
        state.finishFade(current.revision - 1)
        assertEquals(current, state.frame.value)
    }

    @Test fun artworkArrivingForSameTrackUsesRemainingDelayAndThenAppliesImmediately() = runTest {
        val state = AlbumArtBackgroundState<String>(backgroundScope)
        state.select(true, "track") { null }
        advanceTimeBy(2_000)
        state.select(true, "track") { art("available") }
        advanceTimeBy(999); runCurrent()
        assertNull(state.frame.value.current)
        advanceTimeBy(1); runCurrent()
        assertEquals("available", state.frame.value.current?.image)
        advanceTimeBy(1_000)
        state.select(true, "track") { art("updated") }
        runCurrent()
        assertEquals("updated", state.frame.value.current?.image)
    }

    @Test fun laterSelectionRejectsOldResultAndSlowNewDecodeWaitsPastDeadline() = runTest {
        val state = AlbumArtBackgroundState<String>(backgroundScope)
        val old = CompletableDeferred<BackgroundArtwork<String>?>()
        val latest = CompletableDeferred<BackgroundArtwork<String>?>()
        state.select(true, "track-12") { withContext(NonCancellable) { old.await() } }
        runCurrent()
        state.select(true, "track-13") { latest.await() }
        advanceTimeBy(4_000); runCurrent()
        old.complete(art("stale")); runCurrent()
        assertNull(state.frame.value.current)
        latest.complete(art("latest")); runCurrent()
        assertEquals("latest", state.frame.value.current?.image)
    }
}

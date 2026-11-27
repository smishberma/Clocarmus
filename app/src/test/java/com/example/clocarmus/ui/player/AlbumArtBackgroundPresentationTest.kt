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
class AlbumArtBackgroundPresentationTest {
    private fun art(id: String) = BackgroundArtwork(id, id)

    @Test fun togglesTransformBothFadeLayersWithoutChangingFadeRevision() = runTest {
        val presentation = AlbumArtBackgroundPresentation<String>(backgroundScope)
        val source = AlbumArtFrame(art("new"), art("old"), 42, true)
        presentation.update(source, true) { "$it-blurred" }
        runCurrent()
        assertEquals(source.copy(current = art("new").copy(image = "new-blurred"),
            outgoing = art("old").copy(image = "old-blurred")), presentation.frame.value)
        presentation.update(source, false) { error("off must not process") }
        assertEquals(source, presentation.frame.value)
    }

    @Test fun lateBlurCannotReplaceNewerToggleTrackOrDisabledFrame() = runTest {
        val presentation = AlbumArtBackgroundPresentation<String>(backgroundScope)
        val slow = CompletableDeferred<String>()
        presentation.update(AlbumArtFrame(current = art("old")), true) {
            withContext(NonCancellable) { slow.await() }
        }
        runCurrent()
        val latest = AlbumArtFrame(current = art("latest"), revision = 2)
        presentation.update(latest, false) { error("off") }
        slow.complete("stale")
        runCurrent()
        assertEquals(latest, presentation.frame.value)
        presentation.update(latest, true) { "$it-blurred" }
        runCurrent()
        assertEquals("latest-blurred", presentation.frame.value.current?.image)
        presentation.update(AlbumArtFrame(revision = 3), true) { error("black must not process") }
        assertNull(presentation.frame.value.current)
    }

    @Test fun toggleUpdatesDisplayedTrackWithoutBypassingOrRestartingTransportDeadline() = runTest {
        val selection = AlbumArtBackgroundState<String>(backgroundScope)
        val presentation = AlbumArtBackgroundPresentation<String>(backgroundScope)
        selection.select(true, "a") { art("A") }
        runCurrent()
        selection.finishFade(selection.frame.value.revision)
        selection.select(true, "b", 1, 3_000) { art("B") }
        advanceTimeBy(1_000)
        presentation.update(selection.frame.value, true) { "$it-blurred" }
        runCurrent()
        assertEquals("A-blurred", presentation.frame.value.current?.image)
        advanceTimeBy(1_999); runCurrent()
        assertEquals("A", selection.frame.value.current?.image)
        advanceTimeBy(1); runCurrent()
        presentation.update(selection.frame.value, true) { "$it-blurred" }
        runCurrent()
        assertEquals("B-blurred", presentation.frame.value.current?.image)
        assertEquals("A-blurred", presentation.frame.value.outgoing?.image)
    }

    @Test fun finishingFadeReusesPreparedCurrentAndCloseRejectsPendingResults() = runTest {
        val presentation = AlbumArtBackgroundPresentation<String>(backgroundScope)
        val source = AlbumArtFrame(art("new"), art("old"), 7, true)
        var processed = 0
        presentation.update(source, true) { processed++; "$it-blurred" }
        runCurrent()
        presentation.update(source.copy(outgoing = null, transitioning = false), true) {
            error("current must be cached")
        }
        runCurrent()
        assertEquals(2, processed)
        assertNull(presentation.frame.value.outgoing)
        val frame = presentation.frame.value
        val slow = CompletableDeferred<String>()
        presentation.update(AlbumArtFrame(current = art("next")), true) {
            withContext(NonCancellable) { slow.await() }
        }
        runCurrent()
        presentation.close()
        slow.complete("stale"); runCurrent()
        assertEquals(frame, presentation.frame.value)
    }
}

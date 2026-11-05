package com.example.clocarmus.ui.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowsingReturnInactivityTest {
    private fun TestScope.advance(ms: Long) { advanceTimeBy(ms); runCurrent() }

    @Test fun queueEntryArmsWithoutSelectionAndPopupReturnRearmsWithFreshInterval() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected() // Queue uses the shared arm operation on entry.
        advance(14_999)
        timer.cancel()
        timer.setActive(false)
        advance(60_000)
        assertEquals(0, returns)
        timer.setActive(true)
        timer.trackSelected() // Only Queue rearms on popup return.
        advance(14_999)
        assertEquals(0, returns)
        advance(1)
        assertEquals(1, returns)
    }

    @Test fun browsingAndInteractionsAloneNeverStartAReturn() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.interaction()
        timer.touch(true)
        timer.touch(false)
        advance(60_000)
        assertEquals(0, returns)
    }

    @Test fun selectionReturnsAtExactly15SecondsAndOnlyOnce() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        advance(14_999); assertEquals(0, returns)
        advance(1); assertEquals(1, returns)
        timer.interaction()
        advance(60_000); assertEquals(1, returns)
    }

    @Test fun tapOrKeyboardInteractionRestartsTheFullInterval() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        advance(14_000)
        timer.interaction()
        advance(14_999); assertEquals(0, returns)
        advance(1); assertEquals(1, returns)
    }

    @Test fun holdDragAndMultipleFingersWaitUntilFinalRelease() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        advance(14_000)
        timer.touch(true)
        advance(30_000)
        timer.touch(true) // Movement or one of multiple pointers released; another remains down.
        advance(30_000); assertEquals(0, returns)
        timer.touch(false)
        advance(14_999); assertEquals(0, returns)
        advance(1); assertEquals(1, returns)
    }

    @Test fun flingScrollingKeepsBrowsingOpenAndStartsFreshWhenItStops() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        advance(14_000)
        timer.setScrolling(true)
        timer.touch(false)
        advance(60_000); assertEquals(0, returns)
        timer.setScrolling(false)
        advance(14_999); assertEquals(0, returns)
        advance(1); assertEquals(1, returns)
    }

    @Test fun cancellationWinsOverInteractionsScrollAndTouchReleaseUntilNewSelection() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        advance(14_999)
        timer.cancel() // Typing in search, Settings, playlist editing, or manual exit.
        timer.interaction()
        timer.touch(true)
        timer.touch(false)
        timer.setScrolling(true)
        timer.setScrolling(false)
        advance(60_000); assertEquals(0, returns)
        timer.trackSelected()
        advance(15_000); assertEquals(1, returns)
    }

    @Test fun suspensionForSearchDialogOrBackgroundCannotReturnAndResumesWithFreshInterval() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        advance(14_000)
        timer.setActive(false)
        advance(60_000); assertEquals(0, returns)
        timer.setActive(true)
        advance(14_999); assertEquals(0, returns)
        advance(1); assertEquals(1, returns)
    }

    @Test fun cancelledSearchDoesNotRearmWhenDialogClosesOrAppResumes() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        timer.setActive(false)
        timer.cancel()
        timer.interaction()
        timer.setActive(true)
        advance(60_000); assertEquals(0, returns)
    }

    @Test fun anotherSelectionReplacesThePendingDeadline() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        advance(14_999)
        timer.trackSelected()
        advance(14_999); assertEquals(0, returns)
        advance(1); assertEquals(1, returns)
    }

    @Test fun repeatedLifecycleAndScrollStateUpdatesDoNotPostponeReturn() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        repeat(14) {
            advance(1_000)
            timer.setActive(true)
            timer.setScrolling(false)
        }
        advance(1_000); assertEquals(1, returns)
    }

    @Test fun cancelledGestureReleasesTheHoldButDoesNotRearmACancelledTimer() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        timer.touch(true)
        timer.cancelTouches()
        advance(15_000); assertEquals(1, returns)
        timer.trackSelected()
        timer.touch(true)
        timer.cancel()
        timer.cancelTouches()
        advance(60_000); assertEquals(1, returns)
    }

    @Test fun manualExitThenReentryRequiresAnotherSelection() = runTest {
        var returns = 0
        val timer = BrowsingReturnInactivity(backgroundScope) { returns++ }
        timer.setActive(true)
        timer.trackSelected()
        advance(14_000)
        timer.cancel()
        timer.setActive(false)
        timer.setActive(true)
        advance(60_000); assertEquals(0, returns)
    }
}

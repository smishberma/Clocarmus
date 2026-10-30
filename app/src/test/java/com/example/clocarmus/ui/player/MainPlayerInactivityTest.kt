package com.example.clocarmus.ui.player

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainPlayerInactivityTest {
    /** Frames share the delay scheduler; no real-time sleeping or Android main-thread mocks. */
    private class FrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(1)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    private fun TestScope.player(active: Boolean = true) = MainPlayerInactivity(
        CoroutineScope(backgroundScope.coroutineContext + FrameClock(testScheduler))
    ).also { it.setActive(active); runCurrent() }

    private fun TestScope.advance(ms: Long) { advanceTimeBy(ms); runCurrent() }
    private fun full(player: MainPlayerInactivity) = assertEquals(1f, player.opacity, 0f)
    private fun faded(player: MainPlayerInactivity) = assertEquals(.20f, player.opacity, .001f)

    @Test fun fadesOnlyAfter10SecondsThenAnimatesFor500Milliseconds() = runTest {
        val player = player()
        advance(9_999); full(player)
        advance(1); full(player)
        advance(251); assertEquals(.60f, player.opacity, .01f)
        advance(250); faded(player)
        advance(30_000); faded(player)
    }

    @Test fun touchInterruptsAnInProgressAnimationImmediately() = runTest {
        val player = player()
        advance(10_250)
        assertTrue(player.opacity in .21f.. .99f)
        player.touch(true); full(player)
        advance(30_000); full(player)
        player.touch(false); runCurrent()
        advance(9_999); full(player)
        advance(502); faded(player)
    }

    @Test fun quickTapRestartsTheEntireCountdownWithoutWaitingForAnotherFrame() = runTest {
        val player = player()
        advance(10_501); faded(player)
        player.touch(true); full(player)
        player.touch(false); full(player)
        runCurrent()
        advance(9_999); full(player)
        advance(502); faded(player)
    }

    @Test fun holdsAndDragsStayOpaqueUntilRelease() = runTest {
        val player = player()
        advance(9_900)
        player.touch(true); runCurrent()
        advance(20_000); full(player)
        player.touch(true) // Drag movement, with a finger still held.
        advance(20_000); full(player)
        player.touch(false); runCurrent()
        advance(9_999); full(player)
        advance(502); faded(player)
    }

    @Test fun releasingOneOfSeveralFingersDoesNotStartTheTimer() = runTest {
        val player = player()
        player.touch(true) // First pointer down.
        player.touch(true) // Second pointer down.
        advance(20_000); full(player)
        player.touch(true) // First pointer up; second remains pressed.
        advance(20_000); full(player)
        player.touch(false); runCurrent() // Last pointer up.
        advance(9_999); full(player)
        advance(502); faded(player)
    }

    @Test fun suspensionResetsFadedControlsAndResumingStartsFresh() = runTest {
        val player = player()
        advance(10_501); faded(player)
        player.setActive(false); full(player)
        advance(60_000); full(player)
        player.setActive(true); runCurrent()
        advance(9_999); full(player)
        advance(502); faded(player)
    }

    @Test fun suspendingDuringAnimationCancelsStaleUpdatesAndHeldPointers() = runTest {
        val player = player()
        advance(10_250)
        player.setActive(false); full(player)
        advance(1_000); full(player)
        player.setActive(true)
        player.touch(true)
        player.setActive(false)
        player.setActive(true); runCurrent()
        advance(9_999); full(player)
        advance(502); faded(player)
    }

    @Test fun disabledReferencePreviewNeverFades() = runTest {
        val player = player(active = false)
        player.touch(true)
        player.touch(false)
        advance(120_000); full(player)
    }

    @Test fun repeatedActiveUpdatesDoNotResetAnExistingCountdown() = runTest {
        val player = player()
        repeat(10) { advance(1_000); player.setActive(true) }
        advance(501); faded(player)
    }

    @Test fun cancelledGestureRestartsFromTheCancellationTime() = runTest {
        val player = player()
        player.touch(true)
        advance(20_000); full(player)
        player.cancelTouches(); runCurrent()
        advance(9_999); full(player)
        advance(502); faded(player)
    }

    @Test fun aRecreatedPlayerDoesNotRestoreFadedState() = runTest {
        val previous = player()
        advance(10_501); faded(previous)
        previous.setActive(false)
        val recreated = player()
        full(recreated)
        advance(9_999); full(recreated)
        advance(502); faded(recreated)
    }
}

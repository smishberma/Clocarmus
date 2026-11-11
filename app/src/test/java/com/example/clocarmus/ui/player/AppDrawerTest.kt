package com.example.clocarmus.ui.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppDrawerTest {
    @Test fun nineEntriesPerPageAndRemovedPackagesClampRetainedPage() {
        assertEquals(1, drawerPageCount(0))
        assertEquals(1, drawerPageCount(9))
        assertEquals(2, drawerPageCount(10))
        assertEquals(3, drawerPageCount(27))
        assertEquals(1, clampDrawerPage(4, 10))
        assertEquals(0, clampDrawerPage(4, 0))
        assertEquals(0, clampDrawerPage(-1, 30))
    }

    @Test fun drawerTimeoutDoesNotArmSettingsReturnOrChangePlayerFade() = runTest {
        var closes = 0
        var navigations = 0
        val drawer = BrowsingReturnInactivity(backgroundScope) { closes++ }
        val browser = BrowsingReturnInactivity(backgroundScope) { navigations++ }
        val fade = MainPlayerInactivity(backgroundScope)
        browser.setActive(true) // Settings has cancelled the independent browser return.
        drawer.setActive(true); drawer.trackSelected()
        drawer.touch(true); browser.touch(true)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(0, closes)
        drawer.touch(false); browser.touch(false)
        advanceTimeBy(15_000); runCurrent()
        assertEquals(1, closes)
        assertEquals(0, navigations)
        assertEquals(1f, fade.opacity)
    }

    @Test fun backgroundAndCloseReopenCancelStaleDrawerTimeouts() = runTest {
        var closes = 0
        val drawer = BrowsingReturnInactivity(backgroundScope) { closes++ }
        drawer.setActive(true); drawer.trackSelected()
        advanceTimeBy(14_999)
        drawer.setActive(false) // Unrelated backgrounding retains the open drawer.
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, closes)
        drawer.setActive(true)
        advanceTimeBy(14_999); runCurrent()
        assertEquals(0, closes)
        drawer.cancel(); drawer.setActive(false) // Manual close or successful launch.
        drawer.setActive(true); drawer.trackSelected() // A new opening gets its own interval.
        advanceTimeBy(1); runCurrent()
        assertEquals(0, closes)
        advanceTimeBy(14_999); runCurrent()
        assertEquals(1, closes)
    }
}

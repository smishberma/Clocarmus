package com.example.clocarmus.ui.player

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.example.clocarmus.MainActivity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** A large, generated fixture exercises the real shared browser without library or preference writes. */
@Suppress("DEPRECATION")
class BrowserPerformanceTest : InstrumentationTestCase() {
    fun testLargeBrowserOnlyBuildsNearbyRowsAndScrollbarRetainsActions() {
        val activity = startPlayer()
        awaitNode("Menu")
        val reads = AtomicInteger()
        val tracks = object : AbstractList<TrackMenuEntry>() {
            override val size = 10_000
            override fun get(index: Int): TrackMenuEntry {
                reads.incrementAndGet()
                return TrackMenuEntry("Track ${index.toString().padStart(5, '0')}", "Artist", "Album", id = "track:$index")
            }
        }
        val scroll = LazyListState()
        val selected = mutableStateOf<String?>(null)
        var options: String? = null
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    CompositionLocalProvider(LocalTrackOptions provides TrackOptionsEntry({ options = it }, false)) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            TrackMenuPane(PlayerGrid(maxWidth.value, maxHeight.value), tracks, selected.value, false,
                                scroll, "", {}, {}, {}, {}, { selected.value = it })
                        }
                    }
                }
            }
            await { scroll.layoutInfo.visibleItemsInfo.isNotEmpty() }
            assertTrue("Opening 10,000 tracks must not build every row: ${reads.get()}", reads.get() < 300)
            tapRail(bottom = true)
            await { !scroll.canScrollForward && scroll.layoutInfo.visibleItemsInfo.any { it.index == 9_999 } }
            assertTrue("Jumping to the end must not build intermediate rows: ${reads.get()}", reads.get() < 1_000)
            val last = clickable(awaitNode("Track 09999"))
            assertTrue(last.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { selected.value == "track:9999" }
            assertTrue(clickable(awaitNode("Track 09999")).performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            await { options == "track:9999" }
            tapRail(bottom = false)
            await { scroll.firstVisibleItemIndex == 0 && scroll.firstVisibleItemScrollOffset == 0 }
            runBlocking { withContext(Dispatchers.Main) { scroll.scrollToItem(5_000, 12) } }
            await { scroll.firstVisibleItemIndex == 5_000 }
            instrumentation.runOnMainSync { selected.value = "track:5001" }
            await { scroll.firstVisibleItemIndex == 5_000 && scroll.firstVisibleItemScrollOffset == 12 }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    fun testTrackOptionsAndMenuReturnPreserveBrowserPosition() {
        val activity = startPlayer()
        try {
            click("Menu")
            click("All tracks")
            awaitNode("Amber")
            tapRail(bottom = true)
            awaitNode("Zenith")
            assertTrue(clickable(awaitNode("Zenith")).performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            awaitNode("Track info")
            click("CLOSE")
            awaitNode("Zenith")
            click("Back")
            click("All tracks")
            awaitNode("Zenith")
            assertNull("Returning must retain the bottom of the list", find("Amber"))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    fun testSearchFromBottomResetsResultsAndSurvivesMenuReturn() {
        val activity = startPlayer()
        try {
            click("Menu")
            click("All tracks")
            awaitNode("Amber")
            tapRail(bottom = true)
            awaitNode("Zenith")
            click("Search tracks")
            var input: AccessibilityNodeInfo? = null
            for (attempt in 0 until 100) {
                fun editable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                    if (node.isEditable) return node
                    for (index in 0 until node.childCount) node.getChild(index)?.let { editable(it)?.let { found -> return found } }
                    return null
                }
                input = instrumentation.uiAutomation.rootInActiveWindow?.let { editable(it) }
                if (input != null) break
                SystemClock.sleep(100)
            }
            assertNotNull("Search must expose its text field", input)
            assertTrue(input!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Quartz")
            }))
            click("Search")
            awaitNode("Quartz")
            assertNull(find("Zenith"))
            click("Back")
            click("All tracks")
            awaitNode("Quartz")
            assertNull(find("Amber"))
            click("Search tracks")
            click("Show all")
            awaitNode("Amber")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun startPlayer() = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("reference_preview", true)) as MainActivity

    private fun tapRail(bottom: Boolean) {
        val rect = Rect().also { awaitNode("tracks scrollbar").getBoundsInScreen(it) }
        val x = rect.centerX().toFloat()
        val y = (if (bottom) rect.bottom - 1 else rect.top + 1).toFloat()
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
        instrumentation.waitForIdleSync()
    }

    private fun click(label: String) {
        assertTrue("Cannot click $label", clickable(awaitNode(label)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun clickable(initial: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var node = initial
        while (!node.isClickable && node.parent != null) node = node.parent
        return node
    }

    private fun find(label: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString()?.lineSequence()?.any { it == label } == true || node.contentDescription?.toString() == label) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let { visit(it) }
    }

    private fun awaitNode(label: String): AccessibilityNodeInfo {
        repeat(100) {
            find(label)?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("Screen did not expose $label")
    }

    private fun await(condition: () -> Boolean) {
        repeat(100) {
            var success = false
            instrumentation.runOnMainSync { success = condition() }
            if (success) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Browser state did not settle")
    }
}

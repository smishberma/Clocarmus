package com.example.clocarmus.ui.player

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import java.util.concurrent.atomic.AtomicInteger
import android.view.accessibility.AccessibilityNodeInfo
import com.example.clocarmus.MainActivity

/** Real Compose navigation, toggle/reset behavior and touch shielding on API 23+. */
@Suppress("DEPRECATION")
class BackgroundSettingsScreenTest : InstrumentationTestCase() {
    fun testLawIsVisibleBelowModernAndGradientListScrolls() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("reference_preview", true).putExtra("settings_preview", "settings-background-gradient"))
        try {
            val modern = Rect().also { awaitNode("Modern").getBoundsInScreen(it) }
            val law = Rect().also { awaitNode("Law").getBoundsInScreen(it) }
            assertTrue("Law must be visible below Modern", !law.isEmpty && law.top >= modern.bottom)
            click("Law")
            assertSelected("Law")
            awaitNode("Background presets scrollbar")
            var list = awaitNode("Law")
            while (!list.isScrollable && list.parent != null) list = list.parent
            assertTrue("Preset list must scroll", list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            instrumentation.waitForIdleSync()
            click("Lava")
            assertSelected("Lava")
            back()
            click("Customize")
            assertSelected("Lava")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    fun testTypeCustomizationResetAndBackHierarchy() {
        val activity = startBackground()
        try {
            click("Solid color (Default)")
            val albumArt = awaitNode("Album art")
            assertTrue(clickable(albumArt).isEnabled)
            click("Album art")
            assertTrue(clickable(awaitNode("Customize")).isEnabled)
            click("Customize")
            assertChecked("Blur album art", false)
            click("Blur album art")
            assertChecked("Blur album art", true)
            back()
            click("Customize")
            assertChecked("Blur album art", true)
            back()
            click("Reset all")
            click("Cancel")
            click("Customize")
            assertChecked("Blur album art", true)
            back()
            click("Reset all")
            click("Reset")
            click("Solid color (Default)")
            click("Album art")
            click("Customize")
            assertChecked("Blur album art", false)
            back()
            click("Album art")
            click("Gradient")
            awaitNode("Customize")
            assertNull(find("Album art"))
            click("Customize")
            click("Potion")
            awaitNode("Lava")
            back()
            click("Gradient")
            click("Solid color (Default)", last = true)
            click("Customize")
            click("Indigo")
            awaitNode("Coffee")
            back()
            click("Solid color")
            click("Gradient")
            click("Customize")
            assertSelected("Potion")
            back()
            click("Reset all")
            awaitNode("Reset background to default?")
            click("Cancel")
            awaitNode("Gradient")
            click("Reset all")
            click("Reset")
            awaitNode("Solid color (Default)")
            click("Solid color (Default)")
            click("Gradient")
            click("Customize")
            assertSelected("Potion")
            back()
            back()
            awaitNode("Manual rescan")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    fun testQueuesRetainsArtworkAndMenuUsesSolidBackground() {
        val artworkFile = java.io.File(instrumentation.targetContext.filesDir, "reference-album-art.jpg")
        val previousArtwork = artworkFile.takeIf { it.exists() }?.readBytes()
        val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        artworkFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        var activity: MainActivity? = null
        fun backgroundPixel(): Int {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            return try { screenshot.getPixel(screenshot.width * 3 / 4, screenshot.height / 10) }
            finally { screenshot.recycle() }
        }
        try {
            activity = instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("reference_preview", true).putExtra("album_art_preview", true)) as MainActivity
            awaitNode("Queues")
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (android.graphics.Color.red(backgroundPixel()) < 70 && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
            }
            SystemClock.sleep(ALBUM_ART_FADE_MS.toLong() + 100)
            val prepared = backgroundPixel()
            assertTrue("Embedded-art fixture must be visible", android.graphics.Color.red(prepared) in 70..80)
            click("Queues")
            awaitNode("QUEUE")
            SystemClock.sleep(PLAYER_PANE_FADE_DURATION_MS.toLong() + 200)
            assertEquals("Queues must retain the prepared artwork", prepared, backgroundPixel())
            back()
            awaitNode("Queues")
            SystemClock.sleep(PLAYER_PANE_FADE_DURATION_MS.toLong() + 200)
            assertEquals("Returning must retain the prepared artwork", prepared, backgroundPixel())
            click("Menu")
            awaitNode("Settings")
            SystemClock.sleep(PLAYER_PANE_FADE_DURATION_MS.toLong() + 200)
            assertEquals("Menus keep the remembered solid preset", android.graphics.Color.BLACK, backgroundPixel())
        } finally {
            activity?.let { current -> instrumentation.runOnMainSync { current.finish() } }
            if (previousArtwork != null) artworkFile.writeBytes(previousArtwork) else artworkFile.delete()
        }
    }

    fun testDismissalConsumesTouchesAndRetainsLongPressBack() {
        val activity = startBackground()
        try {
            awaitNode("Customize")
            val transport = find("Pause") ?: awaitNode("Play")
            val transportLabel = transport.contentDescription.toString()
            val bounds = Rect().also { transport.getBoundsInScreen(it) }
            click("Solid color (Default)")
            awaitNode("Album art")
            back()
            awaitNode("Customize")
            click("Solid color (Default)")
            awaitNode("Album art")
            tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            awaitNode("Customize")
            awaitNode(transportLabel)
            click("Solid color (Default)")
            awaitNode("Album art")
            click("Back")
            awaitNode("Customize")
            click("Solid color (Default)")
            awaitNode("Album art")
            val backBounds = Rect().also { awaitNode("Back").getBoundsInScreen(it) }
            tap(backBounds.centerX().toFloat(), backBounds.centerY().toFloat(), hold = 800)
            awaitNode("Menu")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    fun testDropdownKeepsImmersiveActivityWindowFocused() {
        val activity = startBackground()
        val focusLosses = AtomicInteger()
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) focusLosses.incrementAndGet()
        }
        fun assertImmersiveFocus() {
            var focused = false
            var visibility = 0
            instrumentation.runOnMainSync {
                focused = activity.hasWindowFocus()
                visibility = activity.window.decorView.systemUiVisibility
            }
            assertTrue("Dropdown must retain Activity window focus", focused)
            assertTrue(visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
            assertTrue(visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
            assertEquals("Dropdown must not transfer window focus", 0, focusLosses.get())
        }
        try {
            awaitNode("Customize")
            instrumentation.runOnMainSync {
                activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            }
            assertImmersiveFocus()
            click("Solid color (Default)")
            awaitNode("Album art")
            assertImmersiveFocus()
            click("Gradient")
            awaitNode("Customize")
            assertImmersiveFocus()
            click("Gradient")
            awaitNode("Album art")
            assertImmersiveFocus()
            back()
            awaitNode("Customize")
            assertImmersiveFocus()
        } finally {
            instrumentation.runOnMainSync {
                activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
                activity.finish()
            }
        }
    }

    private fun startBackground() = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("reference_preview", true).putExtra("settings_preview", "settings-background"))

    private fun assertChecked(text: String, expected: Boolean) {
        repeat(50) {
            find(text)?.let { if (clickable(it).isChecked == expected) return }
            Thread.sleep(100)
        }
        throw AssertionError("$text did not become checked=$expected")
    }

    private fun assertSelected(text: String) {
        repeat(50) {
            var node = find(text)
            while (node != null) {
                if (node.isChecked || node.isSelected) return
                node = node.parent
            }
            Thread.sleep(100)
        }
        throw AssertionError("$text not selected")
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
    }

    private fun tap(x: Float, y: Float, hold: Long = 80) {
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        Thread.sleep(hold)
        instrumentation.sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
        instrumentation.waitForIdleSync()
    }

    private fun clickable(initial: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var node = initial
        while (!node.isClickable && node.parent != null) node = node.parent
        return node
    }

    private fun click(text: String, last: Boolean = false) {
        val node = clickable(awaitNode(text, last))
        assertTrue("Cannot click $text", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun find(text: String, last: Boolean = false): AccessibilityNodeInfo? {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) matches += node
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it) }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let { visit(it) }
        return if (last) matches.lastOrNull() else matches.firstOrNull()
    }

    private fun awaitNode(text: String, last: Boolean = false): AccessibilityNodeInfo {
        repeat(100) {
            find(text, last)?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("Screen did not expose $text")
    }
}

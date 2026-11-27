package com.example.clocarmus.ui.player

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.clocarmus.MainActivity

/** Shared dropdown behavior, immediate choices, reset isolation and Back hierarchy. */
@Suppress("DEPRECATION")
class DividerSettingsScreenTest : InstrumentationTestCase() {
    fun testTypesChoicesResetAndBack() {
        val activity = startDivider()
        try {
            assertFalse(clickable(awaitNode("Customize")).isEnabled)
            click("None (Default)")
            click("Static")
            click("Customize")
            assertSelected("Line")
            assertNull(find("None (Default)"))
            click("Squiggly")
            assertSelected("Squiggly")
            back()
            click("Static")
            click("Animated")
            click("Customize")
            assertSelected("Vertical visualizer")
            click("Color line")
            assertSelected("Color line")
            back()
            click("Animated")
            click("Static")
            click("Customize")
            assertSelected("Squiggly")
            back()
            click("Reset all")
            awaitNode("Reset divider to default?")
            click("Cancel")
            awaitNode("Static")
            click("Reset all")
            click("Reset")
            awaitNode("None (Default)")
            assertFalse(clickable(awaitNode("Customize")).isEnabled)
            click("None (Default)")
            click("Static")
            click("Customize")
            assertSelected("Squiggly")
            back()
            click("Static")
            click("Animated")
            click("Customize")
            assertSelected("Color line")
            back()
            back()
            awaitNode("Manual rescan")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    fun testDropdownConsumesOutsideTapAndBackAndAllowsLongPressHome() {
        val activity = startDivider()
        try {
            val transport = find("Pause") ?: awaitNode("Play")
            val label = transport.contentDescription.toString()
            val bounds = Rect().also { transport.getBoundsInScreen(it) }
            click("None (Default)")
            awaitNode("Animated")
            back()
            awaitAbsent("Animated")
            click("None (Default)")
            awaitNode("Animated")
            tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            awaitNode(label)
            awaitAbsent("Animated")
            click("None (Default)")
            awaitNode("Animated")
            val backBounds = Rect().also { awaitNode("Back").getBoundsInScreen(it) }
            tap(backBounds.centerX().toFloat(), backBounds.centerY().toFloat(), hold = 800)
            awaitNode("Menu")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    fun testEqualizerFreezesResumesAndResetsWithoutTrack() = checkAnimation(DividerStyle.EQUALIZER)

    fun testColorLineFreezesResumesAndResetsWithoutTrack() = checkAnimation(DividerStyle.COLOR_LINE)

    private fun checkAnimation(asset: DividerStyle) {
        val activity = startDivider()
        val playing = mutableStateOf(false)
        val hasTrack = mutableStateOf(false)
        val style = mutableStateOf(asset)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        fun dividerPixels(): IntArray {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            return try {
                val width = screenshot.width / 8
                val height = screenshot.height * 4 / 5
                IntArray(width * height).also {
                    screenshot.getPixels(it, 0, width, (screenshot.width - width) / 2,
                        (screenshot.height - height) / 2, width, height)
                }
            } finally { screenshot.recycle() }
        }
        fun awaitPixelsChange(previous: IntArray) {
            repeat(40) {
                SystemClock.sleep(100)
                if (!previous.contentEquals(dividerPixels())) return
            }
            throw AssertionError("Animation did not advance within two source loops")
        }
        fun settledPixels(): IntArray {
            instrumentation.waitForIdleSync()
            var previous = dividerPixels()
            var stableSamples = 0
            repeat(40) {
                SystemClock.sleep(100)
                val current = dividerPixels()
                stableSamples = if (previous.contentEquals(current)) stableSamples + 1 else 0
                if (stableSamples >= 3) return current
                previous = current
            }
            throw AssertionError("Paused animation did not settle")
        }
        fun assertFrozen(previous: IntArray) {
            // Sample beyond Color line's blank tail, so a running loop cannot pass as frozen.
            repeat(5) {
                SystemClock.sleep(200)
                assertTrue("Suspended animation must freeze", previous.contentEquals(dividerPixels()))
            }
        }
        try {
            instrumentation.runOnMainSync {
                owner.lifecycle.currentState = Lifecycle.State.RESUMED
                activity.setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            PlayerDivider(style.value, Modifier.align(Alignment.Center), playing.value, hasTrack.value)
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(250)
            // Color line begins with zero-width shapes; the visualizer begins with white bars.
            var first = dividerPixels()
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (asset == DividerStyle.EQUALIZER && first.none { it == android.graphics.Color.WHITE } && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
                first = dividerPixels()
            }
            if (asset == DividerStyle.EQUALIZER) assertTrue("Visualizer should render white", first.any { it == android.graphics.Color.WHITE })
            instrumentation.runOnMainSync { playing.value = true }
            SystemClock.sleep(250)
            assertTrue("No track must retain first frame", first.contentEquals(dividerPixels()))
            instrumentation.runOnMainSync { hasTrack.value = true }
            SystemClock.sleep(350)
            val moving = dividerPixels()
            if (asset == DividerStyle.COLOR_LINE) {
                assertTrue("Color line must retain source colors", moving.any {
                    android.graphics.Color.red(it) != android.graphics.Color.blue(it)
                })
            }
            SystemClock.sleep(350)
            assertFalse("Playback should animate", moving.contentEquals(dividerPixels()))
            instrumentation.runOnMainSync { playing.value = false }
            val paused = settledPixels()
            assertFrozen(paused)
            instrumentation.runOnMainSync { style.value = DividerStyle.HIDDEN }
            SystemClock.sleep(150)
            instrumentation.runOnMainSync { style.value = asset }
            SystemClock.sleep(250)
            assertTrue("Hiding must retain the paused frame", paused.contentEquals(dividerPixels()))
            instrumentation.runOnMainSync { playing.value = true }
            awaitPixelsChange(paused)
            instrumentation.runOnMainSync { owner.lifecycle.currentState = Lifecycle.State.STARTED }
            val background = settledPixels()
            assertFrozen(background)
            instrumentation.runOnMainSync { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            awaitPixelsChange(background)
            instrumentation.runOnMainSync { hasTrack.value = false }
            SystemClock.sleep(200)
            assertTrue("Clearing the track must restore first frame", first.contentEquals(dividerPixels()))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun startDivider(): MainActivity {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("reference_preview", true).putExtra("settings_preview", "settings-divider")) as MainActivity
        awaitNode("Customize")
        return activity
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
        SystemClock.sleep(PLAYER_PANE_FADE_DURATION_MS.toLong() + 100)
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
        repeat(100) {
            val node = find(text, last)?.let(::clickable)
            if (node?.isClickable == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                instrumentation.waitForIdleSync()
                return
            }
            Thread.sleep(100)
        }
        throw AssertionError("Cannot click $text")
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

    private fun awaitAbsent(text: String) {
        repeat(100) {
            if (find(text) == null) return
            Thread.sleep(100)
        }
        throw AssertionError("Screen still exposes $text")
    }

    private fun awaitNode(text: String, last: Boolean = false): AccessibilityNodeInfo {
        repeat(100) {
            find(text, last)?.let { return it }
            Thread.sleep(100)
        }
        throw AssertionError("Screen did not expose $text")
    }
}

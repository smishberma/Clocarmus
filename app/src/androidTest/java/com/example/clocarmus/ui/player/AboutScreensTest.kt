package com.example.clocarmus.ui.player

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import com.example.clocarmus.MainActivity

/** Exercises the real Compose actions and Android clipboard on API 23+. */
@Suppress("DEPRECATION")
class AboutScreensTest : InstrumentationTestCase() {
    fun testDeveloperDonationCopyAndBackNavigation() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("reference_preview", true)
                .putExtra("settings_preview", "settings-about")
        )
        try {
            click("Developer")
            awaitNode("Ismail Al Bermah")
            click("DONATE")
            awaitNode("Donate Bitcoin")
            click("COPY ADDRESS")
            instrumentation.runOnMainSync {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                assertEquals("bc1q78uhgjzje48jxsshngsh5ted7dfv39x426ny2z",
                    clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            }
            click("CLOSE")
            awaitNode("Ismail Al Bermah")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            awaitNode("Developer")
            click("Privacy policy")
            awaitNode("Privacy policy")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            awaitNode("Developer")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun click(text: String) {
        var node: AccessibilityNodeInfo? = awaitNode(text)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("No clickable parent for $text", node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun findNode(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findNode(child, text)?.let { return it }
        }
        return null
    }

    private fun awaitNode(text: String): AccessibilityNodeInfo {
        repeat(300) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val node = root?.let { findNode(it, text) }
            if (node != null) return node
            Thread.sleep(100)
        }
        throw AssertionError("Screen did not expose $text")
    }
}

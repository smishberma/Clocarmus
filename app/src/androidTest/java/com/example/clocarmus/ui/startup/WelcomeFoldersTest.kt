package com.example.clocarmus.ui.startup

import android.content.Intent
import android.graphics.Rect
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.example.clocarmus.MainActivity
import com.example.clocarmus.ui.theme.ClocarmusTheme

/** Larger line metrics must not be constrained by the reference-grid row height. */
@Suppress("DEPRECATION")
class WelcomeFoldersTest : InstrumentationTestCase() {
    fun testSingleFolderShowsBothLinesWithLargeLineMetrics() = checkFolders(1)

    fun testMultipleFoldersCanScrollToLastCompleteLocation() = checkFolders(12)

    private fun checkFolders(count: Int) {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("reference_preview", true)
                .putExtra("welcome_preview", "folders")
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    ClocarmusTheme {
                        ProvideTextStyle(TextStyle(lineHeight = 40.sp)) {
                            WelcomeScreen(
                                WelcomePage.Folders,
                                (1..count).map { OnboardingFolder("folder-$it", "Folder $it", "Storage location $it") },
                                onNext = {}, onPermission = {}, onAdd = {}, onRemove = {}, onExclusions = {}
                            )
                        }
                    }
                }
            }
            val expectedHeight = 40 * activity.resources.displayMetrics.scaledDensity
            assertCompleteLine("Storage location 1", expectedHeight)
            if (count > 1) {
                repeat(30) {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    val last = root?.let { find(it) { node -> node.text?.toString() == "Storage location $count" } }
                    if (last != null && bounds(last).height() >= expectedHeight - 2) {
                        assertCompleteLine("Storage location $count", expectedHeight)
                        return
                    }
                    val scroll = root?.let { find(it) { node -> node.isScrollable } }
                    assertNotNull("Folder list must scroll to remaining folders", scroll)
                    assertTrue(scroll!!.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                    Thread.sleep(250)
                    instrumentation.waitForIdleSync()
                }
                fail("Last folder location was not fully reachable")
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun assertCompleteLine(text: String, expectedHeight: Float) {
        repeat(50) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val node = root?.let { find(it) { candidate -> candidate.text?.toString() == text } }
            if (node != null && bounds(node).height() >= expectedHeight - 2) return
            Thread.sleep(100)
        }
        fail("$text was clipped below its full line height ($expectedHeight px)")
    }

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }

    private fun find(node: AccessibilityNodeInfo, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (matches(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            find(child, matches)?.let { return it }
        }
        return null
    }
}

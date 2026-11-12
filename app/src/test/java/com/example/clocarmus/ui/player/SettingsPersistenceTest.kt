package com.example.clocarmus.ui.player

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class SettingsPersistenceTest {
    private fun preferences(commitResult: Boolean, calls: MutableList<String>): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, _ ->
            calls += method.name
            if (method.name == "commit") commitResult else proxy
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            check(method.name == "edit")
            editor
        } as SharedPreferences
    }
    @Test fun settingsReportDiskFailureInsteadOfClaimingSuccess() {
        val calls = mutableListOf<String>()
        try {
            savePlayerSettings(preferences(false, calls), PlayerSettings())
            fail("Disk failure must be reported")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Could not save settings"))
        }
        assertEquals("commit", calls.last())
        assertFalse(calls.contains("apply"))
    }
    @Test fun settingsWaitForDurableWrite() {
        val calls = mutableListOf<String>()
        savePlayerSettings(preferences(true, calls), PlayerSettings(openAfterBoot = true))
        assertEquals("commit", calls.last())
        assertFalse(calls.contains("apply"))
    }
}

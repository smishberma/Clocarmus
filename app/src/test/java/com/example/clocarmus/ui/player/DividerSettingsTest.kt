package com.example.clocarmus.ui.player

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class DividerSettingsTest {
    private fun preferences(values: MutableMap<String, Any> = mutableMapOf()): SharedPreferences {
        val pending = mutableMapOf<String, Any>()
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when {
                method.name.startsWith("put") -> {
                    pending[args!![0] as String] = args[1]
                    proxy
                }
                method.name == "commit" -> { values.putAll(pending); pending.clear(); true }
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getString", "getStringSet", "getBoolean" -> values[args!![0]] ?: args[1]
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }

    @Test fun legacyValuesKeepRenderedMeaning() {
        (staticDividerStyles + DividerStyle.HIDDEN).forEach { style ->
            val loaded = loadPlayerSettings(preferences(mutableMapOf("divider" to style.name)))
            assertEquals(style, loaded.divider)
            assertEquals(if (style == DividerStyle.HIDDEN) DividerType.NONE else DividerType.STATIC, loaded.dividerType)
            assertEquals(if (style == DividerStyle.HIDDEN) DividerStyle.DEFAULT else style, loaded.staticDivider)
        }
        assertEquals(DividerType.NONE, loadPlayerSettings(preferences()).dividerType)
    }

    @Test fun typesAndStaticMemoryPersistIndependently() {
        val prefs = preferences()
        var settings = PlayerSettings().selectDividerType(DividerType.STATIC)
        assertEquals(DividerStyle.DEFAULT, settings.divider)
        staticDividerStyles.forEach { style ->
            settings = settings.selectStaticDivider(style).selectDividerType(DividerType.ANIMATED)
            savePlayerSettings(prefs, settings)
            settings = loadPlayerSettings(prefs)
            assertEquals(DividerStyle.EQUALIZER, settings.divider)
            assertEquals(style, settings.staticDivider)
            settings = settings.selectDividerType(DividerType.NONE)
            savePlayerSettings(prefs, settings)
            settings = loadPlayerSettings(prefs).selectDividerType(DividerType.STATIC)
            assertEquals(style, settings.divider)
        }
    }

    @Test fun resetRetainsMemoryAndOtherSettings() {
        val original = PlayerSettings(clock = ClockStyle.VERTICAL, rightHandDrive = true, autoplay = true)
            .selectBackground(PlayerBackground.GRADIENT_POTION).selectStaticDivider(DividerStyle.SQUIGGLY)
            .selectDividerType(DividerType.ANIMATED)
        val reset = original.resetDivider()
        assertEquals(original.copy(divider = DividerStyle.HIDDEN), reset)
        val prefs = preferences()
        savePlayerSettings(prefs, reset)
        assertEquals(reset, loadPlayerSettings(prefs))
        assertEquals(DividerStyle.SQUIGGLY, loadPlayerSettings(prefs).selectDividerType(DividerType.STATIC).divider)
    }

    @Test fun invalidStaticMemoryFallsBackToLine() {
        listOf("unknown", "HIDDEN", "EQUALIZER", "COLOR_LINE").forEach { invalid ->
            val loaded = loadPlayerSettings(preferences(mutableMapOf("dividerType" to "STATIC", "staticDivider" to invalid)))
            assertEquals(DividerStyle.DEFAULT, loaded.divider)
        }
    }
    @Test fun animatedPresetsSurviveTypeChangesRestartsAndReset() {
        val prefs = preferences()
        animatedDividerStyles.forEach { style ->
            val selected = PlayerSettings().selectStaticDivider(DividerStyle.BEAM).selectAnimatedDivider(style)
            savePlayerSettings(prefs, selected)
            assertEquals(selected, loadPlayerSettings(prefs))
            listOf(DividerType.NONE, DividerType.STATIC).forEach { type ->
                savePlayerSettings(prefs, loadPlayerSettings(prefs).selectDividerType(type))
                val restored = loadPlayerSettings(prefs).selectDividerType(DividerType.ANIMATED)
                assertEquals(style, restored.divider)
                assertEquals(DividerStyle.BEAM, restored.staticDivider)
            }
            val reset = selected.resetDivider()
            assertEquals(selected.copy(divider = DividerStyle.HIDDEN), reset)
            savePlayerSettings(prefs, reset)
            assertEquals(style, loadPlayerSettings(prefs).selectDividerType(DividerType.ANIMATED).divider)
        }
    }

    @Test fun existingAnimatedSelectionLoadsWithoutNewMemoryKey() {
        val loaded = loadPlayerSettings(preferences(mutableMapOf("dividerType" to "ANIMATED", "divider" to "EQUALIZER")))
        assertEquals(DividerStyle.EQUALIZER, loaded.divider)
        assertEquals(DividerStyle.EQUALIZER, loaded.animatedDivider)
    }

    @Test fun invalidAnimatedMemoryFallsBackToVisualizer() {
        listOf("unknown", "HIDDEN", "DEFAULT", "BEAM").forEach { invalid ->
            val loaded = loadPlayerSettings(preferences(mutableMapOf("dividerType" to "ANIMATED", "animatedDivider" to invalid)))
            assertEquals(DividerStyle.EQUALIZER, loaded.divider)
        }
    }

}

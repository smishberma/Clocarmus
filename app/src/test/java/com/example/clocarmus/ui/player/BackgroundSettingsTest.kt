package com.example.clocarmus.ui.player

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class BackgroundSettingsTest {
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

    @Test fun blurDefaultsOffAndPersistsIndependentlyOfBackgroundType() {
        val prefs = preferences(mutableMapOf("background" to PlayerBackground.ALBUM_ART.name))
        assertFalse(loadPlayerSettings(prefs).blurAlbumArt)
        var settings = loadPlayerSettings(prefs).copy(blurAlbumArt = true)
        BackgroundType.entries.forEach { type ->
            settings = settings.selectBackgroundType(type)
            savePlayerSettings(prefs, settings)
            settings = loadPlayerSettings(prefs)
            assertTrue(settings.blurAlbumArt)
            assertEquals(type, settings.backgroundType)
        }
        assertTrue(settings.resetDivider().blurAlbumArt)
        assertFalse(settings.resetBackground().blurAlbumArt)
        assertEquals(settings.copy(background = PlayerBackground.DEFAULT,
            solidBackground = PlayerBackground.DEFAULT, blurAlbumArt = false), settings.resetBackground())
    }

    @Test fun everyLegacySelectionSurvivesUpgradeWithOtherTypeDefaulted() {
        PlayerBackground.entries.filter { it != PlayerBackground.ALBUM_ART }.forEach { preset ->
            val settings = loadPlayerSettings(preferences(mutableMapOf("background" to preset.name)))
            assertEquals(preset, settings.background)
            if (preset.gradientAsset == null) {
                assertEquals(preset, settings.solidBackground)
                assertEquals(PlayerBackground.GRADIENT_DEFAULT, settings.gradientBackground)
            } else {
                assertEquals(PlayerBackground.DEFAULT, settings.solidBackground)
                assertEquals(preset, settings.gradientBackground)
            }
        }
    }

    @Test fun independentPresetsSurviveSwitchesAndRestarts() {
        val prefs = preferences()
        var settings = loadPlayerSettings(prefs).selectBackground(PlayerBackground.INDIGO)
        assertEquals("Solid color", settings.solidBackgroundLabel)
        settings = settings.selectBackgroundType(BackgroundType.GRADIENT)
        assertEquals(PlayerBackground.GRADIENT_DEFAULT, settings.background)
        settings = settings.selectBackground(PlayerBackground.GRADIENT_POTION)
        savePlayerSettings(prefs, settings)
        settings = loadPlayerSettings(prefs)
        assertEquals(PlayerBackground.GRADIENT_POTION, settings.background)
        settings = settings.selectBackgroundType(BackgroundType.SOLID)
        assertEquals(PlayerBackground.INDIGO, settings.background)
        savePlayerSettings(prefs, settings)
        settings = loadPlayerSettings(prefs).selectBackgroundType(BackgroundType.GRADIENT)
        assertEquals(PlayerBackground.GRADIENT_POTION, settings.background)
    }

    @Test fun albumArtPersistsWithoutChangingPresetsAndResetRetainsGradient() {
        val prefs = preferences()
        val selected = PlayerSettings().selectBackground(PlayerBackground.INDIGO)
            .selectBackground(PlayerBackground.GRADIENT_LAVA).selectBackgroundType(BackgroundType.ALBUM_ART)
        savePlayerSettings(prefs, selected)
        val loaded = loadPlayerSettings(prefs)
        assertEquals(selected, loaded)
        assertEquals(BackgroundType.ALBUM_ART, loaded.backgroundType)
        assertEquals(PlayerBackground.INDIGO, loaded.selectBackgroundType(BackgroundType.SOLID).background)
        assertEquals(PlayerBackground.GRADIENT_LAVA, loaded.selectBackgroundType(BackgroundType.GRADIENT).background)
        assertEquals(PlayerBackground.DEFAULT, loaded.resetBackground().background)
        assertEquals(PlayerBackground.GRADIENT_LAVA, loaded.resetBackground().gradientBackground)
    }

    @Test fun resetOnlyChangesAppliedBackgroundAndSolidMemory() {
        val original = PlayerSettings(clock = ClockStyle.VERTICAL, divider = DividerStyle.BEAM,
            rightHandDrive = true, statusBar = true, sources = setOf("mix"), blurAlbumArt = true)
            .selectBackground(PlayerBackground.COFFEE).selectBackground(PlayerBackground.GRADIENT_LAVA)
        val reset = original.resetBackground()
        assertEquals(original.copy(background = PlayerBackground.DEFAULT, solidBackground = PlayerBackground.DEFAULT, blurAlbumArt = false), reset)
        assertEquals("Solid color (Default)", reset.solidBackgroundLabel)
        val prefs = preferences()
        savePlayerSettings(prefs, reset)
        assertEquals(reset, loadPlayerSettings(prefs))
        assertEquals(PlayerBackground.GRADIENT_LAVA, loadPlayerSettings(prefs).selectBackgroundType(BackgroundType.GRADIENT).background)
    }

    @Test fun invalidOrWrongTypeRememberedPresetsFallBackToTheirOwnDefaults() {
        listOf("unknown", PlayerBackground.COFFEE.name).forEach { invalidGradient ->
            val settings = loadPlayerSettings(preferences(mutableMapOf("gradientBackground" to invalidGradient)))
            assertEquals(PlayerBackground.GRADIENT_DEFAULT, settings.selectBackgroundType(BackgroundType.GRADIENT).background)
        }
        listOf("unknown", PlayerBackground.GRADIENT_MODERN.name).forEach { invalidSolid ->
            val settings = loadPlayerSettings(preferences(mutableMapOf(
                "background" to PlayerBackground.GRADIENT_LAVA.name, "solidBackground" to invalidSolid)))
            assertEquals(PlayerBackground.DEFAULT, settings.selectBackgroundType(BackgroundType.SOLID).background)
        }
    }
}

package com.example.clocarmus.ui.player

import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import com.example.clocarmus.R
import androidx.compose.ui.graphics.Color

internal enum class PlayerBackground(val label: String, val color: Color, @DrawableRes val gradientAsset: Int? = null) {
    DEFAULT("Default", Color.Black), INDIGO("Indigo", Color(0xff330055)),
    COFFEE("Coffee", Color(0xff502c14)), GREY("Grey", Color(0xff4d4d4d)),
    GRADIENT_DEFAULT("Default", Color.Black, R.drawable.settings_default_gradient),
    GRADIENT_MODERN("Modern", Color.Black, R.drawable.settings_modern_gradient),
    GRADIENT_POTION("Potion", Color.Black, R.drawable.settings_potion_gradient),
    GRADIENT_LAVA("Lava", Color.Black, R.drawable.settings_lava_gradient),
    ALBUM_ART("Album art", Color.Black)
}
internal enum class BackgroundType { SOLID, GRADIENT, ALBUM_ART }

// Keep SEGMENTED as the stored key so existing LCD selections survive the font redesign.
internal enum class ClockStyle(val label: String) { DEFAULT("Default"), VERTICAL("Vertical"), SEGMENTED("LCD"), DOT_MATRIX("Dot matrix") }
// Preserve stored keys so existing divider selections keep their appearance.
internal enum class DividerStyle(val label: String) { HIDDEN("Default"), DEFAULT("Line"), DOTTED("Dotted"), SQUIGGLY("Squiggly"), BEAM("Beam") }

/** These source IDs belong to the existing demo tree, not Android storage grants. */
internal data class PlayerSettings(
    val autoplay: Boolean = false, val statusBar: Boolean = false, val disableFade: Boolean = false,
    val background: PlayerBackground = PlayerBackground.DEFAULT,
    val clock: ClockStyle = ClockStyle.DEFAULT, val twentyFourHour: Boolean = false,
    val divider: DividerStyle = DividerStyle.HIDDEN,
    val sources: Set<String> = setOf("music", "oriental", "mix"), val exclusions: Set<String> = emptySet(),
    val rightHandDrive: Boolean = false, val openAfterBoot: Boolean = false,
    val solidBackground: PlayerBackground = background.takeIf { it.gradientAsset == null && it != PlayerBackground.ALBUM_ART } ?: PlayerBackground.DEFAULT,
    val gradientBackground: PlayerBackground = background.takeIf { it.gradientAsset != null } ?: PlayerBackground.GRADIENT_DEFAULT
) {
    val backgroundType: BackgroundType
        get() = when {
            background == PlayerBackground.ALBUM_ART -> BackgroundType.ALBUM_ART
            background.gradientAsset != null -> BackgroundType.GRADIENT
            else -> BackgroundType.SOLID
        }
    val solidBackgroundLabel: String
        get() = if (solidBackground == PlayerBackground.DEFAULT) "Solid color (Default)" else "Solid color"

    fun selectBackground(preset: PlayerBackground): PlayerSettings = if (preset == PlayerBackground.ALBUM_ART) {
        copy(background = preset)
    } else if (preset.gradientAsset == null) {
        copy(background = preset, solidBackground = preset)
    } else {
        copy(background = preset, gradientBackground = preset)
    }

    fun selectBackgroundType(type: BackgroundType): PlayerSettings =
        selectBackground(when (type) {
            BackgroundType.SOLID -> solidBackground
            BackgroundType.GRADIENT -> gradientBackground
            BackgroundType.ALBUM_ART -> PlayerBackground.ALBUM_ART
        })

    fun resetBackground(): PlayerSettings = selectBackground(PlayerBackground.DEFAULT)

    fun folderTree(tree: FolderTree): FolderTree = FolderTree(tree.entries.filter { entry ->
        tree.source(entry.id)?.id in sources && generateSequence(entry) { tree.get(it.parent) }.none { it.id in exclusions }
    })
}

internal fun loadPlayerSettings(prefs: SharedPreferences): PlayerSettings {
    fun <T : Enum<T>> choice(key: String, values: Array<T>, default: T) = values.firstOrNull { it.name == prefs.getString(key, null) } ?: default
    val background = choice("background", PlayerBackground.entries.toTypedArray(), PlayerBackground.DEFAULT)
    return PlayerSettings(prefs.getBoolean("autoplay", false), prefs.getBoolean("statusBar", false),
        prefs.getBoolean("disableFade", false), background,
        choice("clock", ClockStyle.entries.toTypedArray(), ClockStyle.DEFAULT), prefs.getBoolean("twentyFourHour", false),
        choice("divider", DividerStyle.entries.toTypedArray(), DividerStyle.HIDDEN),
        prefs.getStringSet("sources", setOf("music", "oriental", "mix"))!!.toSet(), prefs.getStringSet("exclusions", emptySet())!!.toSet(),
        prefs.getBoolean("rightHandDrive", false), prefs.getBoolean("openAfterBoot", false),
        // The legacy applied selection remains authoritative, including on upgrade.
        if (background.gradientAsset == null && background != PlayerBackground.ALBUM_ART) background else
            choice("solidBackground", PlayerBackground.entries.filter { it.gradientAsset == null && it != PlayerBackground.ALBUM_ART }.toTypedArray(), PlayerBackground.DEFAULT),
        if (background.gradientAsset != null) background else
            choice("gradientBackground", PlayerBackground.entries.filter { it.gradientAsset != null }.toTypedArray(), PlayerBackground.GRADIENT_DEFAULT))
}
internal fun savePlayerSettings(prefs: SharedPreferences, settings: PlayerSettings) {
    prefs.edit().putBoolean("autoplay", settings.autoplay).putBoolean("statusBar", settings.statusBar)
        .putBoolean("disableFade", settings.disableFade).putString("background", settings.background.name)
        .putString("solidBackground", settings.solidBackground.name).putString("gradientBackground", settings.gradientBackground.name)
        .putString("clock", settings.clock.name).putBoolean("twentyFourHour", settings.twentyFourHour)
        .putString("divider", settings.divider.name).putStringSet("sources", settings.sources)
        .putStringSet("exclusions", settings.exclusions).putBoolean("rightHandDrive", settings.rightHandDrive).putBoolean("openAfterBoot", settings.openAfterBoot).commit().also { check(it) { "Could not save settings. Please try again." } }
}

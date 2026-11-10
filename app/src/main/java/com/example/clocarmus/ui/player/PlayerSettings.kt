package com.example.clocarmus.ui.player

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color

internal enum class PlayerBackground(val label: String, val color: Color) {
    DEFAULT("Default", Color.Black), INDIGO("Indigo", Color(0xff330055)),
    COFFEE("Coffee", Color(0xff502c14)), GREY("Grey", Color(0xff4d4d4d))
}
// Keep SEGMENTED as the stored key so existing LCD selections survive the font redesign.
internal enum class ClockStyle(val label: String) { DEFAULT("Default"), VERTICAL("Vertical"), SEGMENTED("LCD"), DOT_MATRIX("Dot matrix") }
internal enum class DividerStyle(val label: String) { DEFAULT("Default"), HIDDEN("Hidden"), DOTTED("Dotted"), SQUIGGLY("Squiggly"), BEAM("Beam") }

/** These source IDs belong to the existing demo tree, not Android storage grants. */
internal data class PlayerSettings(
    val autoplay: Boolean = false, val statusBar: Boolean = false, val disableFade: Boolean = false,
    val background: PlayerBackground = PlayerBackground.DEFAULT,
    val clock: ClockStyle = ClockStyle.DEFAULT, val twentyFourHour: Boolean = false,
    val divider: DividerStyle = DividerStyle.DEFAULT,
    val sources: Set<String> = setOf("music", "oriental", "mix"), val exclusions: Set<String> = emptySet()
) {
    fun folderTree(tree: FolderTree): FolderTree = FolderTree(tree.entries.filter { entry ->
        tree.source(entry.id)?.id in sources && generateSequence(entry) { tree.get(it.parent) }.none { it.id in exclusions }
    })
}

internal fun loadPlayerSettings(prefs: SharedPreferences): PlayerSettings {
    fun <T : Enum<T>> choice(key: String, values: Array<T>, default: T) = values.firstOrNull { it.name == prefs.getString(key, null) } ?: default
    return PlayerSettings(prefs.getBoolean("autoplay", false), prefs.getBoolean("statusBar", false),
        prefs.getBoolean("disableFade", false), choice("background", PlayerBackground.entries.toTypedArray(), PlayerBackground.DEFAULT),
        choice("clock", ClockStyle.entries.toTypedArray(), ClockStyle.DEFAULT), prefs.getBoolean("twentyFourHour", false),
        choice("divider", DividerStyle.entries.toTypedArray(), DividerStyle.DEFAULT),
        prefs.getStringSet("sources", setOf("music", "oriental", "mix"))!!.toSet(), prefs.getStringSet("exclusions", emptySet())!!.toSet())
}
internal fun savePlayerSettings(prefs: SharedPreferences, settings: PlayerSettings) {
    prefs.edit().putBoolean("autoplay", settings.autoplay).putBoolean("statusBar", settings.statusBar)
        .putBoolean("disableFade", settings.disableFade).putString("background", settings.background.name)
        .putString("clock", settings.clock.name).putBoolean("twentyFourHour", settings.twentyFourHour)
        .putString("divider", settings.divider.name).putStringSet("sources", settings.sources)
        .putStringSet("exclusions", settings.exclusions).apply()
}

package com.example.clocarmus.ui.player

import java.io.Serializable
import java.util.UUID

internal const val FAVORITES = "builtin:favorites"
internal const val RECENT_ADDED = "builtin:added"
internal const val RECENT_PLAYED = "builtin:played"
internal val builtInPlaylists = linkedMapOf(FAVORITES to "Favorites", RECENT_ADDED to "Recently added", RECENT_PLAYED to "Recently played")
internal data class SavedPlaylist(val id: String, val name: String, val tracks: List<String> = emptyList()) : Serializable
internal data class TimedTrack(val id: String, val timestamp: Long) : Serializable

/** Immutable demo repository: queues keep their own snapshots, independently of these lists. */
internal data class PlaylistLibrary(
    val custom: List<SavedPlaylist> = emptyList(),
    val favorites: List<TimedTrack> = emptyList(),
    val imported: List<TimedTrack> = emptyList(),
    val played: List<TimedTrack> = emptyList()
) : Serializable {
    val sortedCustom get() = custom.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    fun nameError(name: String, excluding: String? = null): String? = when {
        name.trim().isEmpty() -> "Enter a playlist name"
        (builtInPlaylists.values + custom.filter { it.id != excluding }.map { it.name }).any { it.equals(name.trim(), true) } -> "A playlist with this name already exists"
        else -> null
    }
    fun create(name: String): PlaylistLibrary = if (nameError(name) != null) this else
        copy(custom = custom + SavedPlaylist(UUID.randomUUID().toString(), name.trim()))
    fun contains(id: String, track: String) = track in trackIds(id)
    fun hasMembership(track: String) = contains(FAVORITES, track) || custom.any { track in it.tracks }
    fun toggleMembership(id: String, track: String, now: Long): PlaylistLibrary =
        if (contains(id, track)) remove(id, track) else add(id, track, now)
    fun createForTrack(name: String, target: String, selected: String?, now: Long): PlaylistLibrary {
        if (target != selected || nameError(name) != null) return this
        val created = create(name)
        return created.add(created.custom.last().id, target, now)
    }
    fun rename(id: String, name: String): PlaylistLibrary = if (nameError(name, id) != null) this else
        copy(custom = custom.map { if (it.id == id) it.copy(name = name.trim()) else it })
    fun delete(id: String) = copy(custom = custom.filterNot { it.id == id })
    fun add(id: String, track: String, now: Long): PlaylistLibrary = if (id == FAVORITES) {
        if (favorites.any { it.id == track }) this else copy(favorites = favorites + TimedTrack(track, now))
    } else copy(custom = custom.map { if (it.id == id && track !in it.tracks) it.copy(tracks = it.tracks + track) else it })
    fun remove(id: String, track: String) = if (id == FAVORITES) copy(favorites = favorites.filterNot { it.id == track })
        else copy(custom = custom.map { if (it.id == id) it.copy(tracks = it.tracks - track) else it })
    fun reorder(id: String, track: String, target: Int) = copy(custom = custom.map { playlist ->
        if (playlist.id != id || track !in playlist.tracks) playlist else {
            val entries = playlist.tracks.toMutableList()
            entries.remove(track)
            entries.add(target.coerceIn(0, entries.size), track)
            playlist.copy(tracks = entries)
        }
    })
    fun imported(track: String, now: Long) = if (imported.any { it.id == track }) this else
        copy(imported = (imported + TimedTrack(track, now)).sortedByDescending { it.timestamp })
    fun playbackStarted(track: TrackMenuEntry, now: Long): PlaylistLibrary = if (!track.available) this else
        copy(played = boundedRecent(played.filterNot { it.id == track.id } + TimedTrack(track.id, now)))
    fun trackIds(id: String): List<String> = when (id) {
        FAVORITES -> favorites.asReversed().sortedByDescending { it.timestamp }.map { it.id }
        RECENT_ADDED -> imported.take(50).map { it.id }
        RECENT_PLAYED -> played.map { it.id }
        else -> custom.firstOrNull { it.id == id }?.tracks.orEmpty()
    }
}
private fun boundedRecent(tracks: List<TimedTrack>) = tracks.asReversed().sortedByDescending { it.timestamp }.take(50)

internal val playlistFixtureTracks = listOf("Imagine", "Science", "Love", "Frat", "Summit", "New Day", "Lollypop", "Ripple", "Ada", "Hello")
    .map { TrackMenuEntry(it, "Artist", "Album", "Year", id = "playlist:$it") }
internal fun playlistFixture(preview: String?): PlaylistLibrary {
    if (preview in setOf("playlist-favorites-empty", "playlist-added-empty", "playlist-played-empty")) return PlaylistLibrary()
    if (preview == "playlists-empty") return PlaylistLibrary()
    if (preview == "playlist-custom-empty") return PlaylistLibrary(custom = listOf(SavedPlaylist("demo:empty", "New playlist")))
    val ids = playlistFixtureTracks.map { it.id }
    val recent = ids.drop(5) + listOf("Amber", "Anchor")
    return PlaylistLibrary(custom = listOf(SavedPlaylist("demo:new", "New playlist 01"), SavedPlaylist("demo:special", "Special"),
        SavedPlaylist("demo:trip", "Trip 02", recent), SavedPlaylist("demo:weekend", "Weekend"), SavedPlaylist("demo:work", "Work")), favorites = (ids.take(5) + listOf("Amber", "Anchor")).mapIndexed { i, id -> TimedTrack(id, (10 - i).toLong()) },
        imported = recent.mapIndexed { i, id -> TimedTrack(id, (10 - i).toLong()) })
}

internal val demoPlayerTrack = TrackMenuEntry("Superman", "Lazlo Bane", "All the Time in the World", "2002", id = "demo:player")
internal fun collectionsFixture(preview: String): PlaylistLibrary {
    if (preview == "collections-empty") return PlaylistLibrary(favorites = listOf(TimedTrack(demoPlayerTrack.id, 1)))
    val names = listOf("New playlist 01", "Special", "Trip 01", "Trip 02", "Weekend")
    return PlaylistLibrary(custom = names.map { name -> SavedPlaylist("collections:$name", name,
        if (preview == "collections-custom" && name in setOf("New playlist 01", "Trip 01")) listOf(demoPlayerTrack.id) else emptyList()) },
        favorites = if (preview in setOf("collections-favorites", "collections-member")) listOf(TimedTrack(demoPlayerTrack.id, 1)) else emptyList())
}

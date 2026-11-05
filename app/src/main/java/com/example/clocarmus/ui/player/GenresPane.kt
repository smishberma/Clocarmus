package com.example.clocarmus.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import java.util.Locale

@Composable
internal fun GenreHeading(grid: PlayerGrid, name: String, subtitle: String? = null) {
    // Measure icon and text together so short and long genre names remain centered.
    Row(grid.place(60f, 166f, 880f, 48f), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.menu_genres), null, Modifier.size((48f * grid.scale).dp))
        Spacer(Modifier.width((22f * grid.scale).dp))
        PlayerLabel(name.uppercase(Locale.ROOT), Modifier.widthIn(max = (790f * grid.scale).dp)
            .height((48f * grid.scale).dp), 40f * grid.scale, bold = true, marquee = true)
    }
    subtitle?.let {
        PlayerLabel(it.uppercase(Locale.ROOT), grid.place(60f, 222f, 880f, 54f), 40f * grid.scale,
            bold = true, marquee = true)
    }
}

@Composable
internal fun GenresPane(
    grid: PlayerGrid, destination: BrowserDestination, preview: String?,
    selectedTrackId: String?, playing: Boolean, inactivity: BrowsingReturnInactivity,
    onSearchVisibilityChanged: (Boolean) -> Unit, onBack: () -> Unit,
    onNavigate: (BrowserDestination) -> Unit,
    onSelectTrack: (String, List<TrackMenuEntry>) -> Unit, onPlayAll: (List<TrackMenuEntry>) -> Unit
) {
    val genres = remember { groupGenres(demoLibraryTracks) }
    val genre = genres.firstOrNull { it.key == destination.genre }
    val name = genre?.name ?: "Unknown genre"
    val page = destination.page
    if (page == "genre-options") {
        // Reuse the established options geometry and supplied play/back assets.
        ArtistDetailPane(grid, name, onBack, { onPlayAll(genre?.tracks.orEmpty()) },
            { onNavigate(destination.copy(page = "genre-tracks")) },
            { onNavigate(destination.copy(page = "genre-albums")) },
            genre = true, onArtists = { onNavigate(destination.copy(page = "genre-artists")) },
            onSearch = { onNavigate(destination.copy(page = "genre-tracks", search = true)) })
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(when (preview) {
        "genres" -> "alternative rock"; "genre-artists" -> "Ripple"; "genre-albums" -> AlbumIdentity("Saffron", "Artist").key
        else -> null
    }) }
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    ObserveBrowserScroll(scroll, inactivity)
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (previousQuery != query) scroll.scrollTo(0); previousQuery = query }
    val tracks = (if (preview == "genre-tracks") genreReferenceTracks else genre?.tracks.orEmpty())
        .filter { it.title.contains(query, true) }
    val artists = groupArtists(genre?.tracks.orEmpty())
    val albums = genreAlbums(demoLibraryTracks, genre?.tracks.orEmpty())
    val rows = when (page) {
        "genre-list" -> if (preview == "genres") listOf("Acoustic" to 4, "African" to 6, "Alternative Rock" to 9,
            "Classical" to 4, "Folk" to 2).map { (title, count) -> BrowserRow(genreKey(title), title, "$count Tracks") }
            else genres.map { BrowserRow(it.key, it.name, "${it.tracks.size} ${if (it.tracks.size == 1) "Track" else "Tracks"}") }
        "genre-artists" -> if (preview == "genre-artists") listOf("Pebble" to 1, "Quartz" to 1, "Ripple" to 1,
            "Saffron" to 2, "Summit" to 3).map { (title, count) -> BrowserRow(title, title, "$count ${if (count == 1) "Album" else "Albums"}") }
            else artists.map { BrowserRow(it.name, it.name, "${it.distinctAlbumCount} ${if (it.distinctAlbumCount == 1) "Album" else "Albums"}") }
        "genre-albums" -> if (preview == "genre-albums") groupAlbums(demoAlbumTracks).filter { it.identity.title in listOf("Pebble", "Quartz", "Ripple", "Saffron", "Summit") }
            .map { BrowserRow(it.identity.key, it.identity.title, it.subtitle) }
            else albums.map { BrowserRow(it.identity.key, it.identity.title, it.subtitle) }
        else -> tracks.map { BrowserRow(it.id, it.title, "${it.artist} - ${it.album}") }
    }.filter { it.title.contains(query, true) }
    BrowserPane(grid, rows, if (page == "genre-tracks") selectedTrackId else selected, playing,
        scroll, query, { query = it }, onSearchVisibilityChanged, { inactivity.cancel() }, onBack,
        onSelect = { id ->
            when (page) {
                "genre-list" -> { selected = id; onNavigate(BrowserDestination("genre-options", genre = id)) }
                "genre-artists" -> { selected = id; onNavigate(BrowserDestination("detail", artist = id, genre = destination.genre)) }
                "genre-albums" -> {
                    selected = id
                    albums.firstOrNull { it.identity.key == id }?.let {
                        onNavigate(BrowserDestination("album-options", album = it.identity))
                    }
                }
                else -> onSelectTrack(id, tracks)
            }
        }, heading = "GENRES", icon = R.drawable.menu_genres,
        searchName = when (page) { "genre-list" -> "genres"; "genre-artists" -> "artists"; "genre-albums" -> "albums"; else -> "tracks" },
        trackRows = page == "genre-tracks", showPlayback = page == "genre-tracks", showHeading = false, searchInitiallyOpen = destination.search,
        proportionalScrollbar = page == "genre-tracks",
        listTop = when (page) { "genre-tracks" -> 282f; "genre-artists" -> 301f; "genre-albums" -> 295f; else -> 293f })
    GenreHeading(grid, if (page == "genre-list") "GENRES" else name,
        when (page) { "genre-artists" -> "ARTISTS"; "genre-albums" -> "ALBUMS"; "genre-tracks" -> "TRACKS"; else -> null })
}

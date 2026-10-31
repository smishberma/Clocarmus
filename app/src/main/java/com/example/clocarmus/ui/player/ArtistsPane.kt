package com.example.clocarmus.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import java.util.Locale

internal data class ArtistEntry(val name: String, val tracks: List<TrackMenuEntry>) {
    val albums: List<BrowserRow> get() = tracks.groupBy { it.album.ifBlank { "Unknown album" } }
        .map { (album, entries) -> BrowserRow(album, album,
            entries.mapNotNull { it.year?.takeIf(String::isNotBlank) }.sorted().firstOrNull() ?: "Unknown year") }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
}

/** Group by the track Artist tag, retaining unavailable entries and distinct physical copies. */
internal fun groupArtists(tracks: List<TrackMenuEntry>): List<ArtistEntry> = tracks
    .map { it.copy(artist = it.artist.ifBlank { "Unknown artist" }, album = it.album.ifBlank { "Unknown album" }) }
    .groupBy { it.artist.ifBlank { "Unknown artist" } }
    .map { (name, entries) -> ArtistEntry(name, entries.sortedWith(
        compareBy<TrackMenuEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id })) }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

// The same demo-library boundary as All Tracks; replace with repository metadata later.
internal val demoArtistTracks = demoTrackTitles.flatMapIndexed { artistIndex, artist ->
    val albumCount = when (artist) { "Saffron" -> 2; "Summit" -> 3; else -> 1 }
    demoTrackTitles.mapIndexed { index, title ->
        TrackMenuEntry(title, artist, if (albumCount == 1) "Album" else "Album ${index % albumCount + 1}",
            (2002 + artistIndex % 10).toString(), id = "artist:$artist:$index")
    }
}
internal val demoArtists = groupArtists(demoArtistTracks)

@Composable
internal fun BrowserHeading(grid: PlayerGrid, heading: String, icon: Int, subtitle: String? = null) {
    val artists = heading == "ARTISTS"
    Image(painterResource(icon), null,
        grid.place(if (artists) 389f else 342f, if (artists) 197f else 192f, if (artists) 42f else 52f, if (artists) 42f else 52f))
    PlayerLabel(heading, grid.place(if (artists) 447f else 418f, 191f, 340f, 54f),
        40f * grid.scale, alignment = Alignment.CenterStart, bold = true)
    subtitle?.let {
        PlayerLabel(it, grid.place(60f, 250f, 880f, 54f), 40f * grid.scale, bold = true)
    }
}

/** State keys include artist and screen so navigation restores each query and scroll independently. */
@Composable
internal fun ArtistsPane(
    grid: PlayerGrid, page: String, artistName: String?, preview: String?,
    selectedTrackId: String?, playing: Boolean, inactivity: BrowsingReturnInactivity,
    onSearchVisibilityChanged: (Boolean) -> Unit, onBack: () -> Unit,
    onArtist: (String) -> Unit, onPage: (String) -> Unit,
    onSelectTrack: (String, List<TrackMenuEntry>) -> Unit,
    onPlayAll: (List<TrackMenuEntry>) -> Unit
) {
    val states = rememberSaveableStateHolder()
    val artist = demoArtists.firstOrNull { it.name == artistName }
    if (page == "detail" && artist != null) {
        ArtistDetailPane(grid, artist.name, onBack,
            { onPlayAll(artist.tracks) }, { onPage("tracks") }, { onPage("albums") })
        return
    }
    states.SaveableStateProvider(if (page == "artists") page else "$artistName:$page") {
        var query by rememberSaveable { mutableStateOf("") }
        var selectedAlbum by rememberSaveable { mutableStateOf<String?>(if (preview == "artist-albums") "Ripple" else null) }
        val density = LocalDensity.current
        val initialScroll = if (preview != null) with(density) {
            16 * ((126f * grid.scale).dp.roundToPx() + (14f * grid.scale).dp.roundToPx())
        } else 0
        val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initialScroll) }
        // Immersive mode can resize the first composition; settle the fixture on the final grid.
        LaunchedEffect(initialScroll) {
            if (preview != null && query.isEmpty()) scroll.scrollTo(initialScroll)
        }
        ObserveBrowserScroll(scroll, inactivity)
        val changeQuery: (String) -> Unit = { query = it }
        // Reset only when a query changes, keeping restored offsets on Back.
        var previousQuery by rememberSaveable { mutableStateOf(query) }
        LaunchedEffect(query) {
            if (previousQuery != query) scroll.scrollTo(0)
            previousQuery = query
        }
        val tracks = artist?.tracks.orEmpty().filter { it.title.contains(query, true) }
        val rows = when (page) {
            "artists" -> demoArtists.filter { it.name.contains(query, true) }.map {
                val count = it.albums.size
                BrowserRow(it.name, it.name, "$count ${if (count == 1) "Album" else "Albums"}")
            }
            "albums" -> (if (preview == "artist-albums") demoTrackTitles.map {
                BrowserRow(it, it, when (it) { "Pebble" -> "2012"; "Quartz" -> "1999"; "Ripple" -> "2002"; "Saffron" -> "2003"; "Summit" -> "2004"; else -> "2002" })
            } else artist?.albums.orEmpty()).filter { it.title.contains(query, true) }
            else -> tracks.map { BrowserRow(it.id, it.title,
                if (preview == "artist-tracks") "Artist - Album" else "${it.artist} - ${it.album}") }
        }
        BrowserPane(grid, rows,
            when (page) { "artists" -> artistName; "albums" -> selectedAlbum; else -> selectedTrackId },
            playing, scroll, query, changeQuery, onSearchVisibilityChanged, { inactivity.cancel() }, onBack,
            onSelect = { id ->
                when (page) {
                    "artists" -> onArtist(id)
                    "albums" -> selectedAlbum = id
                    else -> onSelectTrack(id, tracks)
                }
            },
            heading = when (page) { "artists" -> "ARTISTS"; "albums" -> "ALL ALBUMS"; else -> "ALL TRACKS" },
            subtitle = if (page == "artists") null else artistName?.uppercase(Locale.ROOT),
            icon = when (page) { "artists" -> R.drawable.menu_artists; "albums" -> R.drawable.menu_albums; else -> R.drawable.menu_all_tracks },
            searchName = when (page) { "artists" -> "artists"; "albums" -> "albums"; else -> "tracks" },
            showPlayback = page == "tracks")
    }
}

@Composable
private fun ObserveBrowserScroll(scroll: ScrollState, inactivity: BrowsingReturnInactivity) {
    LaunchedEffect(scroll, inactivity) {
        snapshotFlow { scroll.isScrollInProgress }.collect { inactivity.setScrolling(it) }
    }
    LaunchedEffect(scroll, inactivity) {
        snapshotFlow { scroll.value }.collect { inactivity.interaction() }
    }
    DisposableEffect(inactivity) { onDispose { inactivity.setScrolling(false) } }
}

@Composable
private fun ArtistDetailPane(grid: PlayerGrid, name: String, onBack: () -> Unit,
                             onPlayAll: () -> Unit, onTracks: () -> Unit, onAlbums: () -> Unit) {
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
        grid, 55f, 20f, 172f, 172f, onBack)
    BrowserHeading(grid, "ARTISTS", R.drawable.menu_artists, name.uppercase(Locale.ROOT))
    val playAllInteraction = remember { MutableInteractionSource() }
    val playAllPressed by playAllInteraction.collectIsPressedAsState()
    Box(grid.place(350f, 350f, 280f, 240f).semantics(mergeDescendants = true) { contentDescription = "Play all" }
        .clickable(playAllInteraction, indication = null, role = Role.Button, onClick = onPlayAll)) {
        Image(painterResource(if (playAllPressed) R.drawable.player_play_all_pressed else R.drawable.player_play_all),
            null, gridLocal(grid, 75f, 21f, 130f, 130f))
        PlayerLabel("PLAY ALL", gridLocal(grid, 0f, 177f, 280f, 54f), 34f * grid.scale, bold = true)
    }
    Box(grid.place(40f, 636f, 916f, 354f).clip(RoundedCornerShape((66f * grid.scale).dp)).background(Color(0xff333333)))
    PlayerLabel("BROWSE", grid.place(60f, 651f, 880f, 60f), 40f * grid.scale, bold = true)
    ArtistBrowseButton(grid, "Tracks", R.drawable.menu_all_tracks, R.drawable.menu_all_tracks_pressed, 146f, onTracks)
    ArtistBrowseButton(grid, "Albums", R.drawable.player_albums, R.drawable.player_albums_pressed, 570f, onAlbums)
}

private fun gridLocal(grid: PlayerGrid, x: Float, y: Float, width: Float, height: Float) = Modifier
    .offset((x * grid.scale).dp, (y * grid.scale).dp).size((width * grid.scale).dp, (height * grid.scale).dp)

@Composable
private fun ArtistBrowseButton(grid: PlayerGrid, label: String, @DrawableRes icon: Int,
                               @DrawableRes pressedIcon: Int, x: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(grid.place(x, 740f, 280f, 230f).semantics(mergeDescendants = true) { contentDescription = label }
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)) {
        Image(painterResource(if (pressed) pressedIcon else icon), null, gridLocal(grid, 75f, 26f, 130f, 130f))
        PlayerLabel(label.uppercase(Locale.ROOT), gridLocal(grid, 0f, 173f, 280f, 48f), 34f * grid.scale, bold = true)
    }
}

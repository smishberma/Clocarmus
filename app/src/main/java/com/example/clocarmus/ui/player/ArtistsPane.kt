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
    val distinctAlbumCount: Int get() = tracks.map { it.album.ifBlank { "Unknown album" } }.distinct().size
    val albums: List<BrowserRow> get() = groupAlbums(tracks).map {
        BrowserRow(it.identity.key, it.identity.title, it.year)
    }
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
internal fun BrowserHeading(grid: PlayerGrid, heading: String, icon: Int, subtitle: String? = null, centerUnderVolume: Boolean = false) {
    val artists = heading == "ARTISTS" || heading == "ALBUMS"
    val albumTracks = heading == "ALBUM TRACKS"
    val headingOffset = with(LocalDensity.current) {
        (if (albumTracks || (heading == "ALBUMS" && subtitle == null)) -8 else -15).toDp()
    }
    if (centerUnderVolume) {
        // Match the shared volume bar's horizontal bounds; center the visible icon/text group.
        Row(grid.place(283f, 191f, 434f, 54f).offset(y = headingOffset),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(icon), null, Modifier.size((52f * grid.scale).dp))
            Spacer(Modifier.width((24f * grid.scale).dp))
            PlayerLabel(heading, Modifier.fillMaxHeight(), 40f * grid.scale, bold = true)
        }
    } else {
        Image(painterResource(icon), null,
            grid.place(if (artists) 389f else if (albumTracks) 315f else 342f, if (artists) 197f else 192f, if (artists || albumTracks) 42f else 52f, if (artists || albumTracks) 42f else 52f)
                .offset(y = headingOffset))
        PlayerLabel(heading, grid.place(if (artists) 447f else if (albumTracks) 384f else 418f, 191f, if (albumTracks) 520f else 340f, 54f).offset(y = headingOffset),
            40f * grid.scale, alignment = Alignment.CenterStart, bold = true)
    }
    subtitle?.let {
        PlayerLabel(it, grid.place(60f, 250f, 880f, 54f).offset(y = headingOffset), 40f * grid.scale, bold = true, marquee = true)
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
    onPlayAll: (List<TrackMenuEntry>) -> Unit,
    scope: ArtistScope = ArtistScope.TRACK,
    genre: String? = null,
    onAlbum: (AlbumIdentity) -> Unit
) {
    val states = rememberSaveableStateHolder()
    val library = genreCollection(if (preview != null) demoArtistTracks else demoLibraryTracks, genre)
    val artists = groupArtists(library)
    val artist = artistName?.let { ArtistEntry(it, artistCollection(library, it, scope)) }
    if (page == "detail" && artist != null) {
        ArtistDetailPane(grid, artist.name, onBack,
            { onPlayAll(artist.tracks) }, { onPage("tracks") }, { onPage("albums") })
        return
    }
    states.SaveableStateProvider(if (page == "artists") page else "$genre:$scope:$artistName:$page") {
        var query by rememberSaveable { mutableStateOf("") }
        var selectedArtist by rememberSaveable { mutableStateOf(artistName) }
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
            "artists" -> artists.filter { it.name.contains(query, true) }.map {
                val count = it.distinctAlbumCount
                BrowserRow(it.name, it.name, "$count ${if (count == 1) "Album" else "Albums"}")
            }
            "albums" -> (if (preview == "artist-albums") demoTrackTitles.map {
                BrowserRow(it, it, when (it) { "Pebble" -> "2012"; "Quartz" -> "1999"; "Ripple" -> "2002"; "Saffron" -> "2003"; "Summit" -> "2004"; else -> "2002" })
            } else artist?.albums.orEmpty()).filter { it.title.contains(query, true) }
            else -> tracks.map { BrowserRow(it.id, it.title,
                if (preview == "artist-tracks") "Artist - Album" else "${it.artist} - ${it.album}") }
        }
        BrowserPane(grid, rows,
            when (page) { "artists" -> selectedArtist; "albums" -> selectedAlbum; else -> selectedTrackId },
            playing, scroll, query, changeQuery, onSearchVisibilityChanged, { inactivity.cancel() }, onBack,
            onSelect = { id ->
                when (page) {
                    "artists" -> { selectedArtist = id; onArtist(id) }
                    "albums" -> {
                        selectedAlbum = id
                        groupAlbums(artist?.tracks.orEmpty()).firstOrNull { it.identity.key == id }?.let { onAlbum(it.identity) }
                    }
                    else -> onSelectTrack(id, tracks)
                }
            },
            heading = when (page) { "artists" -> "ARTISTS"; "albums" -> "ALL ALBUMS"; else -> "ALL TRACKS" },
            subtitle = if (page == "artists") null else artistName?.uppercase(Locale.ROOT),
            icon = when (page) { "artists" -> R.drawable.menu_artists; "albums" -> R.drawable.menu_albums; else -> R.drawable.menu_all_tracks },
            searchName = when (page) { "artists" -> "artists"; "albums" -> "albums"; else -> "tracks" },
            showPlayback = page == "tracks", trackRows = page == "tracks")
    }
}

@Composable
internal fun ObserveBrowserScroll(scroll: ScrollState, inactivity: BrowsingReturnInactivity) {
    LaunchedEffect(scroll, inactivity) {
        snapshotFlow { scroll.isScrollInProgress }.collect { inactivity.setScrolling(it) }
    }
    LaunchedEffect(scroll, inactivity) {
        snapshotFlow { scroll.value }.collect { inactivity.interaction() }
    }
    DisposableEffect(inactivity) { onDispose { inactivity.setScrolling(false) } }
}

@Composable
internal fun ArtistDetailPane(grid: PlayerGrid, name: String, onBack: () -> Unit,
                             onPlayAll: () -> Unit, onTracks: () -> Unit, onAlbums: () -> Unit,
                             album: Boolean = false, onSearch: (() -> Unit)? = null,
                             albumArtist: String? = null, genre: Boolean = false, onArtists: (() -> Unit)? = null) {
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
        grid, 55f, 20f, 172f, 172f, onBack)
    if (genre) GenreHeading(grid, "GENRES", name) else BrowserHeading(grid, if (album) "ALBUMS" else "ARTISTS",
        if (album) R.drawable.menu_albums else R.drawable.menu_artists,
        if (albumArtist != null) "${name.uppercase(Locale.ROOT)} by ${albumArtist.uppercase(Locale.ROOT)}"
        else name.uppercase(Locale.ROOT))
    onSearch?.let { search ->
        Box(grid.touchTarget(902f, 103f, 87f).semantics { contentDescription = if (genre) "Search genre tracks" else "Search album tracks" }
            .clickable(role = Role.Button, onClick = search), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.track_search), null, Modifier.size((87f * grid.scale).dp))
        }
    }
    val playAllInteraction = remember { MutableInteractionSource() }
    val playAllPressed by playAllInteraction.collectIsPressedAsState()
    Box(grid.place(350f, 350f, 280f, 240f).semantics(mergeDescendants = true) { contentDescription = if (album) "Play album" else "Play all" }
        .clickable(playAllInteraction, indication = null, role = Role.Button, onClick = onPlayAll)) {
        Image(painterResource(if (playAllPressed) R.drawable.player_play_all_pressed else R.drawable.player_play_all),
            null, gridLocal(grid, 75f, 21f, 130f, 130f))
        PlayerLabel(if (album) "PLAY ALBUM" else "PLAY ALL", gridLocal(grid, 0f, 177f, 280f, 54f), 34f * grid.scale, bold = true)
    }
    Box(grid.place(40f, 636f, 916f, 354f).clip(RoundedCornerShape((66f * grid.scale).dp)).background(Color(0xff333333)))
    PlayerLabel("BROWSE", grid.place(60f, 651f, 880f, 60f), 40f * grid.scale, bold = true)
    if (genre) {
        ArtistBrowseButton(grid, "Tracks", R.drawable.menu_all_tracks, R.drawable.menu_all_tracks_pressed, 18f, onTracks)
        ArtistBrowseButton(grid, "Artists", R.drawable.menu_artists, R.drawable.menu_artists_pressed, 358f, onArtists ?: {})
        ArtistBrowseButton(grid, "Albums", R.drawable.player_albums, R.drawable.player_albums_pressed, 698f, onAlbums)
    } else {
        ArtistBrowseButton(grid, if (album) "Album Tracks" else "Tracks",
            if (album) R.drawable.album_tracks else R.drawable.menu_all_tracks,
            if (album) R.drawable.album_tracks_pressed else R.drawable.menu_all_tracks_pressed, 146f, onTracks)
        ArtistBrowseButton(grid, if (album) "Artist" else "Albums",
            if (album) R.drawable.menu_artists else R.drawable.player_albums,
            if (album) R.drawable.menu_artists_pressed else R.drawable.player_albums_pressed, 570f, onAlbums)
    }
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
        PlayerLabel(label.uppercase(Locale.ROOT),
            Modifier.align(Alignment.TopCenter).offset(y = (173f * grid.scale).dp)
                .requiredSize((340f * grid.scale).dp, (48f * grid.scale).dp),
            34f * grid.scale, bold = true)
    }
}

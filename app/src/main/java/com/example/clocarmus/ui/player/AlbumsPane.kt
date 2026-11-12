package com.example.clocarmus.ui.player

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import java.util.Locale

@Composable
internal fun AlbumsPane(
    grid: PlayerGrid, destination: BrowserDestination, preview: String?,
    selectedTrackId: String?, playing: Boolean, inactivity: BrowsingReturnInactivity,
    onSearchVisibilityChanged: (Boolean) -> Unit, onBack: () -> Unit,
    onNavigate: (BrowserDestination) -> Unit,
    onSelectTrack: (String, List<TrackMenuEntry>) -> Unit, onPlayAlbum: (List<TrackMenuEntry>) -> Unit
) {
    val libraryTracks = LocalLibraryTracks.current
    val albums = remember(preview, libraryTracks) { groupAlbums(if (preview == "albums") demoAlbumTracks.filter { it.effectiveAlbumArtist == "Artist" } else libraryTracks) }
    val album = albums.firstOrNull { it.identity == destination.album }
    if (destination.page == "album-options" && album != null) {
        ArtistDetailPane(grid, album.identity.title, onBack, { onPlayAlbum(album.tracks) },
            { onNavigate(destination.copy(page = "album-tracks")) },
            { onNavigate(BrowserDestination("detail", artist = album.identity.artist, scope = ArtistScope.ALBUM)) },
            album = true, onSearch = { onNavigate(destination.copy(page = "album-tracks", search = true)) },
            albumArtist = album.identity.artist)
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedAlbum by rememberSaveable { mutableStateOf<String?>(
        if (preview == "albums") AlbumIdentity("Saffron", "Artist").key else null) }
    val density = LocalDensity.current
    val initialScroll = if (preview == "albums") with(density) {
        val index = albums.indexOfFirst { it.identity == AlbumIdentity("Pebble", "Artist") }
        index.coerceAtLeast(0) * ((126f * grid.scale).dp.roundToPx() + (14f * grid.scale).dp.roundToPx())
    } else 0
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initialScroll) }
    LaunchedEffect(initialScroll) { if (preview == "albums" && query.isEmpty()) scroll.scrollTo(initialScroll) }
    ObserveBrowserScroll(scroll, inactivity)
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (previousQuery != query) scroll.scrollTo(0); previousQuery = query }
    val tracksPage = destination.page == "album-tracks"
    val tracks = album?.tracks.orEmpty().filter { it.title.contains(query, true) }
    val rows = if (tracksPage) tracks.map(::albumTrackRow) else albums
        .filter { it.identity.title.contains(query, true) }.map { BrowserRow(it.identity.key, it.identity.title, it.subtitle) }
    BrowserPane(grid, rows, if (tracksPage) selectedTrackId else selectedAlbum, playing, scroll, query,
        { query = it }, onSearchVisibilityChanged, { inactivity.cancel() }, onBack,
        onSelect = { id ->
            if (tracksPage) onSelectTrack(id, tracks) else {
                selectedAlbum = id
                albums.firstOrNull { it.identity.key == id }?.let {
                    onNavigate(BrowserDestination("album-options", album = it.identity))
                }
            }
        }, heading = if (tracksPage) "ALBUM TRACKS" else "ALBUMS",
        subtitle = if (tracksPage && album != null) "${album.identity.title.uppercase(Locale.ROOT)} by ${album.identity.artist.uppercase(Locale.ROOT)}" else null,
        icon = R.drawable.menu_albums, searchName = if (tracksPage) "tracks" else "albums",
        trackRows = tracksPage, showPlayback = tracksPage, searchInitiallyOpen = destination.search)
}

package com.example.clocarmus.ui.player

import com.example.clocarmus.playback.shuffle.ShuffleCollection

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.clocarmus.R
import java.util.Locale

@Composable
internal fun AlbumsPane(
    grid: PlayerGrid, destination: BrowserDestination, preview: String?,
    selectedTrackId: String?, playing: Boolean, inactivity: BrowsingReturnInactivity,
    onSearchVisibilityChanged: (Boolean) -> Unit, onBack: () -> Unit,
    onNavigate: (BrowserDestination) -> Unit,
    onSelectTrack: (String, List<TrackMenuEntry>, ShuffleCollection) -> Unit, onPlayAlbum: (List<TrackMenuEntry>, ShuffleCollection) -> Unit
) {
    val libraryTracks = LocalLibraryTracks.current
    val albums = remember(preview, libraryTracks) { groupAlbums(if (preview == "albums") demoAlbumTracks.filter { it.effectiveAlbumArtist == "Artist" } else libraryTracks) }
    val album = albums.firstOrNull { it.identity == destination.album }
    if (destination.page == "album-options" && album != null) {
        ArtistDetailPane(grid, album.identity.title, onBack, { onPlayAlbum(album.tracks, ShuffleCollection(kind = "album", album = album.identity)) },
            { onNavigate(destination.copy(page = "album-tracks")) },
            { onNavigate(BrowserDestination("detail", artist = album.identity.artist, scope = ArtistScope.ALBUM)) },
            album = true, onSearch = { onNavigate(destination.copy(page = "album-tracks", search = true)) },
            albumArtist = album.identity.artist)
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedAlbum by rememberSaveable { mutableStateOf<String?>(
        if (preview == "albums") AlbumIdentity("Saffron", "Artist").key else null) }
    val initialIndex = if (preview == "albums")
        albums.indexOfFirst { it.identity == AlbumIdentity("Pebble", "Artist") }.coerceAtLeast(0) else 0
    val scroll = rememberLazyListState(initialIndex)
    ObserveBrowserScroll(scroll, inactivity)
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (previousQuery != query) scroll.scrollToItem(0); previousQuery = query }
    val tracksPage = destination.page == "album-tracks"
    val tracks = album?.tracks.orEmpty().filter { it.title.contains(query, true) }
    val rows = if (tracksPage) tracks.map(::albumTrackRow) else albums
        .filter { it.identity.title.contains(query, true) }.map { BrowserRow(it.identity.key, it.identity.title, it.subtitle) }
    BrowserPane(grid, rows, if (tracksPage) selectedTrackId else selectedAlbum, playing, scroll, query,
        { query = it }, onSearchVisibilityChanged, { inactivity.cancel() }, onBack,
        onSelect = { id ->
            if (tracksPage) onSelectTrack(id, tracks, ShuffleCollection(kind = "album", album = destination.album, query = query)) else {
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

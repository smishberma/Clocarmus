package com.example.clocarmus.ui.player

import com.example.clocarmus.playback.shuffle.ShuffleCollection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import java.util.Locale

private fun playlistIcon(id: String) = when (id) {
    FAVORITES -> R.drawable.playlist_favorites
    RECENT_ADDED -> R.drawable.playlist_recent_added
    else -> R.drawable.playlist_recent_played
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistsPane(
    grid: PlayerGrid, playlistId: String?, library: PlaylistLibrary,
    tracks: List<TrackMenuEntry>, selectedTrackId: String?, playing: Boolean,
    inactivity: BrowsingReturnInactivity, onChange: (PlaylistLibrary) -> Unit,
    onBack: () -> Unit, onOpen: (String) -> Unit,
    onSelect: (String, List<TrackMenuEntry>, ShuffleCollection) -> Unit, onEditingChanged: (Boolean) -> Unit
) {
    val trackOptions = LocalTrackOptions.current
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val trackScroll = rememberLazyListState()
    if (playlistId == null) ObserveBrowserScroll(scroll, inactivity) else ObserveBrowserScroll(trackScroll, inactivity)
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var lastCompletion by rememberSaveable { mutableIntStateOf(trackOptions.completed) }
    LaunchedEffect(trackOptions.completed) {
        if (lastCompletion != trackOptions.completed) { action = null; lastCompletion = trackOptions.completed }
    }
    var draft by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val homeNavigation = LocalPlayerHome.current
    DisposableEffect(homeNavigation) {
        val unregister = homeNavigation?.registerCancellation {
            action = null
            target = null
            draft = ""
            editing = false
        }
        onDispose { unregister?.invoke() }
    }
    val activeEditing = action != null || editing
    DisposableEffect(activeEditing) {
        onEditingChanged(activeEditing)
        if (activeEditing) inactivity.cancel()
        onDispose { onEditingChanged(false) }
    }
    val finishEditing = { editing = false }
    BackHandler(enabled = editing && !trackOptions.active, onBack = finishEditing)
    val custom = library.custom.firstOrNull { it.id == playlistId }
    val title = builtInPlaylists[playlistId] ?: custom?.name.orEmpty()
    val tracksById = remember(tracks) { tracks.associateBy { it.id } }
    val shownTracks = remember(library, playlistId, tracksById) {
        library.trackIds(playlistId.orEmpty()).mapNotNull(tracksById::get)
    }
    val trackRows = remember(shownTracks) {
        shownTracks.map { BrowserRow(it.id, it.title, "${it.artist} - ${it.album}") }
    }
    if (playlistId == null) {
        MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed, grid, 55f, 20f, 172f, 172f, onBack)
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(grid.touchTarget(842f, 103f, 88f).semantics { contentDescription = "Create playlist" }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                inactivity.cancel(); target = null; draft = ""; action = "create"
            }, contentAlignment = Alignment.Center) {
            Image(painterResource(if (pressed) R.drawable.add_playlist_pressed else R.drawable.add_playlist), null, Modifier.size((73f * grid.scale).dp))
        }
        Column(grid.place(53f, 293f, 827f, 735f).verticalScroll(scroll)) {
            builtInPlaylists.forEach { (id, name) ->
                Row(Modifier.fillMaxWidth().height((105f * grid.scale).dp)
                    .clickable(role = Role.Button) { onOpen(id) }.padding(start = (31f * grid.scale).dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(playlistIcon(id)), null, Modifier.size((48f * grid.scale).dp))
                    Spacer(Modifier.width((29f * grid.scale).dp))
                    PlayerLabel(name, Modifier.weight(1f).fillMaxHeight(), 58f * grid.scale, alignment = Alignment.CenterStart)
                }
            }
            Spacer(Modifier.height((50f * grid.scale).dp))
            Box(Modifier.padding(start = (31f * grid.scale).dp).fillMaxWidth().height((2f * grid.scale).dp).background(Color.White))
            Spacer(Modifier.height((50f * grid.scale).dp))
            if (library.custom.isEmpty()) PlayerLabel("Tap + to create a playlist",
                Modifier.fillMaxWidth().offset(x = (34f * grid.scale).dp).height((310f * grid.scale).dp), 46f * grid.scale, bold = true, color = Color(0xffaaaaaa))
            library.sortedCustom.forEach { playlist ->
                PlayerLabel(playlist.name, Modifier.fillMaxWidth().height((105f * grid.scale).dp)
                    .combinedClickable(role = Role.Button, onClick = { onOpen(playlist.id) }, onLongClick = {
                        inactivity.cancel(); target = playlist.id; action = "playlist"
                    }).padding(start = (31f * grid.scale).dp), 58f * grid.scale, alignment = Alignment.CenterStart, marquee = true)
            }
        }
        if (scroll.maxValue > 0) TrackScrollbar(grid, scroll, (builtInPlaylists.values + library.sortedCustom.map { it.name }).toList(), "playlists", proportional = true, top = 319f)
    } else {
        BrowserPane(grid, trackRows, selectedTrackId, playing,
            trackScroll, "", {}, {}, {}, { if (editing) finishEditing() else onBack() }, { onSelect(it, shownTracks.toList(), ShuffleCollection(kind = "playlist", token = playlistId!!)) },
            trackRows = true, showSearch = false, showHeading = false, emptyMessage = "", onLongClick = if (custom != null || playlistId == FAVORITES) { id ->
                inactivity.cancel(); target = id; action = "track"
            } else null, reordering = editing,
            onMove = { id, index -> onChange(library.reorder(playlistId, id, index)) },
            showScrollbar = shownTracks.size > 5)
        if (shownTracks.isEmpty()) {
            val emptyColor = Color(0xffaaaaaa)
            if (playlistId == FAVORITES) Image(painterResource(R.drawable.playlist_favorites), null,
                grid.place(463f, 491f, 74f, 74f), colorFilter = ColorFilter.tint(emptyColor))
            PlayerLabel(when (playlistId) {
                FAVORITES -> "No Favorites yet"
                RECENT_ADDED, RECENT_PLAYED -> "Empty"
                else -> "Empty playlist"
            }, grid.place(60f, 570f, 880f, 96f), 46f * grid.scale, bold = true, color = emptyColor)
        }
        if (editing) TextButton(onClick = finishEditing, modifier = grid.place(770f, 170f, 190f, 100f)) { Text("Done") }
    }
    // Shared playlist heading, positioned from the updated 011–017 references.
    Image(painterResource(R.drawable.menu_playlists), null, grid.place(338f, 180f, 59f, 59f))
    PlayerLabel("PLAYLISTS", grid.place(434f, 181f, 380f, 54f), 40f * grid.scale, bold = true, alignment = Alignment.CenterStart)
    if (playlistId != null) Row(grid.place(60f, 242f, if (shownTracks.isEmpty()) 880f else 850f, 52f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (custom == null) {
            Image(painterResource(playlistIcon(playlistId)), null, Modifier.size((34f * grid.scale).dp))
            Spacer(Modifier.width((24f * grid.scale).dp))
        }
        PlayerLabel(title.uppercase(Locale.ROOT), Modifier.widthIn(max = (760f * grid.scale).dp).height((52f * grid.scale).dp),
            40f * grid.scale, bold = true, marquee = true)
    }
    val targetPlaylist = library.custom.firstOrNull { it.id == target }
    when (if (trackOptions.active || lastCompletion != trackOptions.completed) null else action) {
        "create", "rename" -> {
            PlaylistNamePrompt(library, draft, { draft = it }, { action = null }, {
                onChange(if (action == "create") library.create(draft) else library.rename(target.orEmpty(), draft))
                action = null
            }, renamingId = if (action == "rename") target else null)
        }
        "playlist" -> AlertDialog(onDismissRequest = { action = null }, title = { Text(targetPlaylist?.name.orEmpty()) },
            text = { Column {
                TextButton(onClick = { draft = targetPlaylist?.name.orEmpty(); action = "rename" }) { Text("Rename") }
                TextButton(onClick = { action = "delete" }) { Text("Remove") }
            } }, confirmButton = { TextButton(onClick = { action = null }) { Text("Cancel") } })
        "delete" -> AlertDialog(onDismissRequest = { action = null }, title = { Text("Remove ‘${targetPlaylist?.name.orEmpty()}’?") },
            confirmButton = { TextButton(onClick = { onChange(library.delete(target.orEmpty())); action = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { action = null }) { Text("Cancel") } })
        "track" -> AlertDialog(onDismissRequest = { action = null }, title = { Text(shownTracks.firstOrNull { it.id == target }?.title.orEmpty()) },
            text = { Column {
                TextButton(onClick = { target?.let { trackOptions.open(it) } }) { Text("Track options") }
                TextButton(onClick = { onChange(library.remove(playlistId.orEmpty(), target.orEmpty())); action = null }) {
                    Text(if (playlistId == FAVORITES) "Remove from Favorites" else "Remove from playlist")
                }
                if (custom != null) TextButton(onClick = { action = null; editing = true }) { Text("Reorder") }
            } }, confirmButton = { TextButton(onClick = { action = null }) { Text("Cancel") } })
    }
}

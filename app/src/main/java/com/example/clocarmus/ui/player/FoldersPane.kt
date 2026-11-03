package com.example.clocarmus.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import java.util.Locale

@Composable
internal fun FoldersPane(
    grid: PlayerGrid, directory: String?, preview: String?, selectedTrackId: String?, playing: Boolean,
    inactivity: BrowsingReturnInactivity, onSearchVisibilityChanged: (Boolean) -> Unit,
    onExit: () -> Unit, onDirectory: (String?) -> Unit,
    onSelectTrack: (String, List<TrackMenuEntry>) -> Unit,
    tree: FolderTree = demoFolderTree, onSourceLost: (String?) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(if (preview == "folders") "music" else null) }
    var reconnect by remember { mutableStateOf<String?>(null) }
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    ObserveBrowserScroll(scroll, inactivity)
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (query != previousQuery) scroll.scrollTo(0); previousQuery = query }
    val sourceId = remember(directory) { directory?.let { tree.source(it)?.id } }
    LaunchedEffect(tree, directory) {
        if (directory != null && tree.get(directory)?.let { tree.accessible(it) } != true) {
            onSourceLost(sourceId); onDirectory(null)
        }
    }
    val back: () -> Unit = {
        inactivity.interaction()
        when {
            query.isNotEmpty() -> query = ""
            directory != null -> onDirectory(tree.get(directory)?.parent)
            else -> onExit()
        }
    }
    BackHandler(onBack = back)
    val contents = tree.contents(directory, query).let { entries ->
        // The reference root preserves its drawn order; normal browsing always sorts naturally.
        if (preview == "folders" && directory == null && query.isEmpty())
            listOf("music", "oriental", "mix").mapNotNull { id -> entries.find { it.id == id } }
        else entries
    }
    val rows = buildList {
        if (directory != null && query.isEmpty()) add(BrowserRow("folder:parent", "Previous directory", "", R.drawable.folder_previous_folder))
        contents.forEach { entry -> add(BrowserRow(entry.id, entry.name,
            if (query.isNotEmpty()) tree.path(entry, directory) else if (!tree.accessible(entry)) "Unavailable — reconnect source" else "",
            if (entry.directory) R.drawable.folder_folder_icon else R.drawable.folder_audio_file_icon,
            audio = !entry.directory)) }
    }
    BrowserPane(grid, rows, if (contents.any { it.id == selectedTrackId }) selectedTrackId else selectedFolder,
        playing, scroll, query, { query = it }, onSearchVisibilityChanged, { inactivity.cancel() }, back,
        onSelect = { id ->
            if (id == "folder:parent") back() else tree.get(id)?.let { entry ->
                when {
                    !tree.accessible(entry) -> reconnect = tree.source(id)?.name ?: entry.name
                    entry.directory -> { selectedFolder = id; query = ""; onDirectory(id) }
                    else -> onSelectTrack(id, tree.queue(directory, query))
                }
            }
        }, heading = "FOLDERS", searchName = "folders and audio files", showHeading = false,
        folderRows = true, folderTextInset = if (directory == null) 115f else 130f,
        folderRowGap = if (directory == null) 42f else 44f, scrollbarTop = 312f,
        listTop = if (directory == null) 284f else 303f,
        emptyMessage = "", showScrollbar = directory == null || scroll.maxValue > 0)
    Row(grid.place(60f, 161f, 880f, 48f), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.folder_folder_icon), null, Modifier.size((44f * grid.scale).dp))
        Spacer(Modifier.width((20f * grid.scale).dp))
        PlayerLabel("FOLDERS", Modifier.height((48f * grid.scale).dp), 40f * grid.scale, bold = true)
    }
    tree.get(directory)?.let { entry ->
        PlayerLabel(entry.name.uppercase(Locale.ROOT), grid.place(60f, 222f, 880f, 54f),
            40f * grid.scale, bold = true, marquee = true)
    }
    if (contents.isEmpty()) PlayerLabel(if (query.isNotEmpty()) "No matching folders or audio files" else "No folders or audio files",
        grid.place(83f, if (directory != null && query.isEmpty()) 455f else 312f, 760f, 130f),
        40f * grid.scale)
    reconnect?.let { name -> AlertDialog(onDismissRequest = { reconnect = null },
        title = { Text("Source unavailable") }, text = { Text("Reconnect $name to browse its folders and audio files.") },
        confirmButton = { TextButton(onClick = { reconnect = null }) { Text("OK") } }) }
}

package com.example.clocarmus.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Fixed demo data for exercising scrolling before the offline library is connected. */
internal val demoTrackTitles = listOf(
    "Amber", "Anchor", "Blossom", "Canyon", "Drift", "Ember", "Fern", "Glimmer",
    "Harbor", "Iris", "Juniper", "Kite", "Lantern", "Meadow", "Nectar", "Oasis",
    "Pebble", "Quartz", "Ripple", "Saffron", "Summit", "Thistle", "Umbra", "Velvet",
    "Willow", "Xenon", "Yonder", "Zenith"
)

internal data class TrackMenuEntry(
    val title: String, val artist: String, val album: String, val year: String? = null,
    val id: String = title, val available: Boolean = true,
    val albumArtist: String? = null, val discNumber: Int? = null,
    val trackNumber: Int? = null, val durationSeconds: Int? = null,
    val genres: List<String> = emptyList()
)

// Placeholder metadata matches the visual fixture until the library supplies real tags.
internal val marqueeDemoTrack = TrackMenuEntry(
    title = "Departing From a Station That Was Demolished Before It Was Ever Built",
    artist = "Bartholomew Quillfeather-Ashworth & The Interdimensional Orchestra",
    album = "The Unabridged and Occasionally Contradictory Chronicles of Seventeen Thousand Midnight Trains",
    year = "2026", id = "demo:long-metadata", trackNumber = 1, durationSeconds = 225
)
internal val demoTracks = (demoTrackTitles.map { TrackMenuEntry(it, "Artist", "Album", "Year") } + marqueeDemoTrack)
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

// Both gestures and accessibility use the same clamped mapping, including both endpoints.
internal fun fastScrollFraction(pillTop: Float, travel: Float): Float =
    if (travel > 0f) (pillTop / travel).coerceIn(0f, 1f) else 0f

internal fun fastScrollLetter(titles: List<String>, fraction: Float): String =
    titles.getOrNull(((titles.size - 1).coerceAtLeast(0) * fraction.coerceIn(0f, 1f)).roundToInt())
        ?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()

internal data class BrowserRow(val id: String, val title: String, val subtitle: String, val rowIcon: Int? = null, val audio: Boolean = false)

@Composable
internal fun TrackMenuPane(
    grid: PlayerGrid, tracks: List<TrackMenuEntry>, selectedId: String?, playing: Boolean,
    scroll: ScrollState, query: String, onQuery: (String) -> Unit,
    onSearchVisibilityChanged: (Boolean) -> Unit, onSearchEdited: () -> Unit,
    onBack: () -> Unit, onSelect: (String) -> Unit, artist: String? = null
) = BrowserPane(grid, tracks.map { BrowserRow(it.id, it.title, "${it.artist} - ${it.album}") },
    selectedId, playing, scroll, query, onQuery, onSearchVisibilityChanged, onSearchEdited,
    onBack, onSelect, subtitle = artist?.uppercase(java.util.Locale.ROOT), trackRows = true)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BrowserPane(
    grid: PlayerGrid,
    rows: List<BrowserRow>,
    selectedId: String?,
    playing: Boolean,
    scroll: ScrollState,
    query: String,
    onQuery: (String) -> Unit,
    onSearchVisibilityChanged: (Boolean) -> Unit,
    onSearchEdited: () -> Unit,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    heading: String = "ALL TRACKS",
    subtitle: String? = null,
    icon: Int = R.drawable.menu_all_tracks,
    searchName: String = "tracks",
    showPlayback: Boolean = true,
    searchInitiallyOpen: Boolean = false,
    showSearch: Boolean = true,
    showHeading: Boolean = true,
    emptyMessage: String = "No matching $searchName",
    onLongClick: ((String) -> Unit)? = null,
    trackRows: Boolean = false,
    reordering: Boolean = false,
    onMove: (String, Int) -> Unit = { _, _ -> },
    showScrollbar: Boolean = true,
    proportionalScrollbar: Boolean = false,
    folderRows: Boolean = false,
    folderTextInset: Float = 130f,
    folderRowGap: Float = 42f,
    scrollbarTop: Float? = null,
    listTop: Float = if (showSearch) 293f else 303f
) {
    val trackOptions = LocalTrackOptions.current
    val titles = remember(rows) { rows.map { it.title } }
    var searching by remember { mutableStateOf(searchInitiallyOpen) }
    var draft by remember { mutableStateOf(query) }
    DisposableEffect(searching) {
        onSearchVisibilityChanged(searching)
        onDispose { onSearchVisibilityChanged(false) }
    }
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
        grid, 55f, 20f, 172f, 172f, onBack)
    if (showHeading) BrowserHeading(grid, heading, icon, subtitle)
    if (showSearch) Box(grid.touchTarget(902f, 103f, 87f).semantics { contentDescription = "Search $searchName" }
        .clickable(role = Role.Button) { draft = query; searching = true }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.track_search), null,
            Modifier.size((87f * grid.scale).dp), contentScale = ContentScale.Fit)
    }

    val latestRows by rememberUpdatedState(rows)
    val latestMove by rememberUpdatedState(onMove)
    val rowHeightPx = with(LocalDensity.current) { (140f * grid.scale).dp.toPx() }
    Column(grid.place(if (showSearch) 53f else 42f, listTop, 827f, 700f).verticalScroll(scroll)) {
        rows.forEachIndexed { index, row ->
            key(row.id) {
                val title = row.title
                val selected = row.id == selectedId
                Box(Modifier.fillMaxWidth().height(((if (folderRows && row.subtitle.isEmpty()) 98f else 126f) * grid.scale).dp)
                    .clip(RoundedCornerShape(((if (folderRows) 28f else 36f) * grid.scale).dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .semantics(mergeDescendants = true) {
                        this.selected = selected
                        if (selected && showPlayback && (!folderRows || row.audio)) stateDescription = if (playing) "Playing" else "Paused"
                    }
                    .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.Button, onLongClickLabel = if (trackRows || row.audio) "Track options" else null,
                        onLongClick = if (reordering) null else onLongClick?.let { { it(row.id) } }
                            ?: if (trackRows || row.audio) { { trackOptions.open(row.id) } } else null,
                        onClick = { if (!reordering) onSelect(row.id) })) {
                    val textColor = if (selected) Color.Black else Color.White
                    row.rowIcon?.let { asset ->
                        Image(painterResource(asset), null,
                            Modifier.align(Alignment.CenterStart).padding(start = ((if (asset == R.drawable.folder_previous_folder) 27f else 36f) * grid.scale).dp)
                                .size(((if (asset == R.drawable.folder_previous_folder) 60f else 48f) * grid.scale).dp), contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(if (selected) Color.Black else if (row.audio) Color(0xffaaaaaa) else Color.White))
                    }
                    Column(Modifier.fillMaxSize().padding(start = ((if (folderRows) folderTextInset else 32f) * grid.scale).dp,
                        end = ((if (reordering || (selected && showPlayback && (!folderRows || row.audio))) 100f else 28f) * grid.scale).dp)) {
                        PlayerLabel(title, Modifier.fillMaxWidth().height(((if (folderRows && row.subtitle.isEmpty()) 98f else 76f) * grid.scale).dp),
                            (if (folderRows) 54f else 58f) * grid.scale, alignment = Alignment.CenterStart, color = textColor, marquee = true)
                        if (row.subtitle.isNotEmpty()) PlayerLabel(row.subtitle,
                            Modifier.fillMaxWidth().height((42f * grid.scale).dp),
                            34f * grid.scale, alignment = Alignment.CenterStart, color = textColor, marquee = true)
                    }
                    if (reordering) Box(Modifier.align(Alignment.CenterEnd).width((100f * grid.scale).dp).fillMaxHeight()
                        .semantics {
                            contentDescription = "Reorder ${row.title}"
                            customActions = listOf(
                                CustomAccessibilityAction("Move up") { onMove(row.id, index - 1); true },
                                CustomAccessibilityAction("Move down") { onMove(row.id, index + 1); true })
                        }
                        .pointerInput(row.id, rowHeightPx) {
                            var distance = 0f
                            detectDragGestures(onDragStart = { distance = 0f }, onDrag = { change, amount ->
                                change.consume()
                                distance += amount.y
                                val steps = (distance / rowHeightPx).toInt()
                                if (steps != 0) {
                                    val current = latestRows.indexOfFirst { it.id == row.id }
                                    latestMove(row.id, (current + steps).coerceIn(0, latestRows.lastIndex))
                                    distance -= steps * rowHeightPx
                                }
                            })
                        }) {
                        PlayerLabel("≡", Modifier.fillMaxSize(), 58f * grid.scale, color = textColor)
                    }
                    if (!reordering && selected && showPlayback && (!folderRows || row.audio)) Image(painterResource(if (playing) R.drawable.track_play_indicator else R.drawable.player_pause),
                        null, Modifier.align(Alignment.CenterEnd).padding(end = ((if (folderRows) 56f else 42f) * grid.scale).dp)
                            .size(((if (folderRows) 44f else 48f) * grid.scale).dp), contentScale = ContentScale.Fit,
                        colorFilter = if (playing) null else ColorFilter.tint(Color.Black))
                }
                if (index != rows.lastIndex) Spacer(Modifier.height(((if (folderRows) folderRowGap else 14f) * grid.scale).dp))
            }
        }
    }
    if (titles.isEmpty() && emptyMessage.isNotEmpty()) PlayerLabel(emptyMessage, grid.place(83f, 312f, 760f, 88f), 46f * grid.scale,
        alignment = Alignment.CenterStart)
    if (showScrollbar) TrackScrollbar(grid, scroll, titles, searchName, proportional = proportionalScrollbar || heading == "ALBUM TRACKS" || !showSearch, top = scrollbarTop ?: (listTop + if (showSearch) 19f else 16f))

    if (searching) AlertDialog(onDismissRequest = { searching = false }, title = { Text("Search $searchName") },
        text = { OutlinedTextField(draft, onValueChange = { onSearchEdited(); draft = it }, singleLine = true, label = { Text("Search $searchName") }) },
        confirmButton = { TextButton(onClick = { onQuery(draft.trim()); searching = false }) { Text("Search") } },
        dismissButton = { TextButton(onClick = { onQuery(""); searching = false }) { Text("Show all") } })
}

private val ColorScrollRail = Color(0xff494949)

@Composable
internal fun TrackScrollbar(grid: PlayerGrid, scroll: ScrollState, titles: List<String>, searchName: String, proportional: Boolean = false, top: Float = 312f) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val railHeight = 708f * grid.scale
    // Album tracks use a proportional thumb; other browsers retain the supplied pill ratio.
    val viewportPx = with(density) { (700f * grid.scale).dp.toPx() }
    val pillHeight = if (proportional) (railHeight * viewportPx / (scroll.maxValue + viewportPx)).coerceIn(88f * grid.scale, railHeight)
        else 88f * grid.scale
    val pillWidth = 88f * grid.scale * 2.9479708f / 9.6478851f
    val travelPx = with(density) { (railHeight - pillHeight).dp.toPx() }
    val fraction = if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f
    var dragging by remember { mutableStateOf(false) }
    val currentFraction by rememberUpdatedState(fraction)
    val currentMax by rememberUpdatedState(scroll.maxValue)
    val scrollable = scroll.maxValue > 0
    val jump: (Float) -> Unit = { value ->
        scope.launch { scroll.scrollTo((value.coerceIn(0f, 1f) * scroll.maxValue).roundToInt()) }
    }
    val latestJump by rememberUpdatedState(jump)

    Box(grid.place(908f, top, 48f, 708f)
        .semantics {
            contentDescription = "$searchName scrollbar"
            stateDescription = fastScrollLetter(titles, fraction)
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            setProgress { if (scrollable) { jump(it); true } else false }
        }
        .pointerInput(travelPx, scrollable) {
            if (!scrollable) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pillTop = currentFraction * travelPx
                val pillHeightPx = with(density) { pillHeight.dp.toPx() }
                // Grabbing the pill preserves the finger's offset; tapping the rail centers it.
                val grabOffset = if (down.position.y in pillTop..(pillTop + pillHeightPx))
                    down.position.y - pillTop else pillHeightPx / 2f
                dragging = true
                down.consume()
                latestJump(fastScrollFraction(down.position.y - grabOffset, travelPx))
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        if (currentMax > 0) latestJump(fastScrollFraction(change.position.y - grabOffset, travelPx))
                    } while (true)
                } finally {
                    dragging = false
                }
            }
        }) {
        Box(Modifier.align(Alignment.Center).width((24f * grid.scale).dp).fillMaxHeight().background(ColorScrollRail))
        if (scrollable) Image(painterResource(R.drawable.track_scroll_pill), null,
            Modifier.align(Alignment.TopCenter).offset(y = ((railHeight - pillHeight) * fraction).dp)
                .size(pillWidth.dp, pillHeight.dp), contentScale = if (proportional) ContentScale.FillBounds else ContentScale.Fit)
    }
    if (scrollable && (dragging || scroll.isScrollInProgress)) {
        PlayerLabel(fastScrollLetter(titles, fraction),
            grid.place(806f, top + (708f - 88f) * fraction, 88f, 88f)
                .clip(RoundedCornerShape(percent = 50)).background(ColorScrollRail),
            54f * grid.scale, bold = true)
    }
}

package com.example.clocarmus.ui.player

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

internal data class TrackMenuEntry(val title: String, val artist: String, val album: String, val year: String? = null)

// Placeholder metadata matches the visual fixture until the library supplies real tags.
internal val demoTracks = demoTrackTitles.map { TrackMenuEntry(it, "Artist", "Album", "Year") }

// Both gestures and accessibility use the same clamped mapping, including both endpoints.
internal fun fastScrollFraction(pillTop: Float, travel: Float): Float =
    if (travel > 0f) (pillTop / travel).coerceIn(0f, 1f) else 0f

internal fun fastScrollLetter(titles: List<String>, fraction: Float): String =
    titles.getOrNull(((titles.size - 1).coerceAtLeast(0) * fraction.coerceIn(0f, 1f)).roundToInt())
        ?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()

@Composable
internal fun TrackMenuPane(
    grid: PlayerGrid,
    tracks: List<TrackMenuEntry>,
    selectedTitle: String?,
    playing: Boolean,
    scroll: ScrollState,
    query: String,
    onQuery: (String) -> Unit,
    onSearchVisibilityChanged: (Boolean) -> Unit,
    onSearchEdited: () -> Unit,
    onBack: () -> Unit,
    onSelect: (String) -> Unit
) {
    val titles = remember(tracks) { tracks.map { it.title } }
    var searching by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(query) }
    DisposableEffect(searching) {
        onSearchVisibilityChanged(searching)
        onDispose { onSearchVisibilityChanged(false) }
    }
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
        grid, 55f, 20f, 172f, 172f, onBack)
    Image(painterResource(R.drawable.menu_all_tracks), null,
        grid.place(342f, 192f, 52f, 52f), contentScale = ContentScale.Fit)
    PlayerLabel("ALL TRACKS", grid.place(418f, 191f, 300f, 54f), 40f * grid.scale,
        alignment = Alignment.CenterStart, bold = true)
    Box(grid.touchTarget(902f, 103f, 87f).semantics { contentDescription = "Search tracks" }
        .clickable(role = Role.Button) { draft = query; searching = true }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.track_search), null,
            Modifier.size((87f * grid.scale).dp), contentScale = ContentScale.Fit)
    }

    Column(grid.place(53f, 293f, 827f, 700f).verticalScroll(scroll)) {
        tracks.forEach { track ->
            val title = track.title
            val selected = title == selectedTitle
            Box(Modifier.fillMaxWidth().height((126f * grid.scale).dp)
                .clip(RoundedCornerShape((36f * grid.scale).dp))
                .background(if (selected) Color.White else Color.Transparent)
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    if (selected) stateDescription = if (playing) "Playing" else "Paused"
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button) { onSelect(title) }) {
                val textColor = if (selected) Color.Black else Color.White
                Column(Modifier.fillMaxSize().padding(start = (32f * grid.scale).dp,
                    end = ((if (selected) 100f else 28f) * grid.scale).dp)) {
                    PlayerLabel(title, Modifier.fillMaxWidth().height((76f * grid.scale).dp),
                        58f * grid.scale, alignment = Alignment.CenterStart, color = textColor)
                    PlayerLabel("${track.artist} - ${track.album}",
                        Modifier.fillMaxWidth().height((42f * grid.scale).dp),
                        34f * grid.scale, alignment = Alignment.CenterStart, color = textColor)
                }
                if (selected) Image(painterResource(if (playing) R.drawable.track_play_indicator else R.drawable.player_pause),
                    null, Modifier.align(Alignment.CenterEnd).padding(end = (42f * grid.scale).dp)
                        .size((48f * grid.scale).dp), contentScale = ContentScale.Fit,
                    colorFilter = if (playing) null else ColorFilter.tint(Color.Black))
            }
            if (title != titles.last()) Spacer(Modifier.height((14f * grid.scale).dp))
        }
    }
    if (titles.isEmpty()) PlayerLabel("No matching tracks", grid.place(83f, 312f, 760f, 88f), 46f * grid.scale,
        alignment = Alignment.CenterStart)
    TrackScrollbar(grid, scroll, titles)

    if (searching) AlertDialog(onDismissRequest = { searching = false }, title = { Text("Search tracks") },
        text = { OutlinedTextField(draft, onValueChange = { onSearchEdited(); draft = it }, singleLine = true, label = { Text("Track title") }) },
        confirmButton = { TextButton(onClick = { onQuery(draft.trim()); searching = false }) { Text("Search") } },
        dismissButton = { TextButton(onClick = { onQuery(""); searching = false }) { Text("Show all") } })
}

private val ColorScrollRail = Color(0xff494949)

@Composable
private fun TrackScrollbar(grid: PlayerGrid, scroll: ScrollState, titles: List<String>) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val railHeight = 708f * grid.scale
    // Preserve the supplied pill's aspect ratio (2.9479708 × 9.6478851).
    val pillHeight = 88f * grid.scale
    val pillWidth = pillHeight * 2.9479708f / 9.6478851f
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

    Box(grid.place(908f, 312f, 48f, 708f)
        .semantics {
            contentDescription = "Track scrollbar"
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
                .size(pillWidth.dp, pillHeight.dp), contentScale = ContentScale.Fit)
    }
    if (scrollable && (dragging || scroll.isScrollInProgress)) {
        PlayerLabel(fastScrollLetter(titles, fraction),
            grid.place(806f, 312f + (708f - 88f) * fraction, 88f, 88f)
                .clip(RoundedCornerShape(percent = 50)).background(ColorScrollRail),
            54f * grid.scale, bold = true)
    }
}

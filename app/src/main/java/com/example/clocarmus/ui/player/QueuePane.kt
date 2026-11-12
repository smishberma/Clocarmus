package com.example.clocarmus.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import kotlinx.coroutines.delay

/** Drag state is only a preview. Commit against the original identities, never an old queue snapshot. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QueuePane(grid: PlayerGrid, queue: TrackQueue, tracks: List<TrackMenuEntry>, playing: Boolean,
    scroll: LazyListState, inactivity: BrowsingReturnInactivity, interactive: Boolean,
    onBack: () -> Unit, onSelect: (String) -> Unit, onOptions: (QueueOccurrence) -> Unit,
    onReorder: (List<String>, String, Int) -> Unit) {
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
        grid, 55f, 20f, 172f, 172f, onBack)
    BrowserHeading(grid, "QUEUE", R.drawable.player_queue_button, centerUnderVolume = true)
    val density = LocalDensity.current
    val rowPx = with(density) { ((126f * grid.scale).dp.roundToPx() + (14f * grid.scale).dp.roundToPx()).toFloat() }
    val viewportPx = with(density) { (726f * grid.scale).dp.toPx() }
    LaunchedEffect(rowPx) {
        withFrameNanos { }
        scroll.scrollToItem(queue.currentIndex.coerceAtLeast(0))
    }
    var dragged by remember { mutableStateOf<String?>(null) }
    var originalIds by remember { mutableStateOf(emptyList<String>()) }
    var target by remember { mutableIntStateOf(0) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    val latestQueue by rememberUpdatedState(queue)
    val latestReorder by rememberUpdatedState(onReorder)
    val ids = remember(queue.entries) { queue.entries.map { it.id } }
    val tracksById = remember(tracks) { tracks.associateBy { it.id } }
    suspend fun scrollToPixels(value: Int) {
        val maximum = (queue.entries.size * rowPx - viewportPx).toInt().coerceAtLeast(0)
        val bounded = value.coerceIn(0, maximum)
        scroll.scrollToItem((bounded / rowPx).toInt(), (bounded % rowPx).toInt())
    }
    val validDrag = dragged != null && originalIds == ids && interactive
    fun endDrag() { dragged = null; inactivity.setScrolling(false); inactivity.interaction() }
    LaunchedEffect(ids, interactive) { if (!interactive || (dragged != null && originalIds != ids)) endDrag() }
    DisposableEffect(Unit) { onDispose { inactivity.setScrolling(false) } }
    LaunchedEffect(validDrag, scroll.isScrollInProgress) {
        inactivity.setScrolling(validDrag || scroll.isScrollInProgress)
    }
    LaunchedEffect(validDrag) {
        while (validDrag) {
            val delta = when {
                fingerY < rowPx / 2 -> -rowPx / 8
                fingerY > viewportPx - rowPx / 2 -> rowPx / 8
                else -> 0f
            }
            if (delta != 0f) scrollToPixels((scroll.firstVisibleItemIndex * rowPx + scroll.firstVisibleItemScrollOffset + delta).toInt())
            target = ((fingerY + scroll.firstVisibleItemIndex * rowPx + scroll.firstVisibleItemScrollOffset) / rowPx).toInt().coerceIn(0, (originalIds.size - 1).coerceAtLeast(0))
            delay(32)
        }
    }
    val movedIndex = remember(queue.entries, dragged) { queue.entries.indexOfFirst { it.id == dragged } }
    val shown = if (validDrag) queuePreview(queue.entries, movedIndex, target) else queue.entries
    LazyColumn(grid.place(53f, 277f, 827f, 726f), state = scroll, userScrollEnabled = !validDrag && interactive) {
        items(shown.size, key = { if (validDrag) "preview:$it" else shown[it].id }) { index ->
            val occurrence = shown[index]
            val track = tracksById[occurrence.trackId]
            val current = queue.currentId == occurrence.id
            val color = if (current) Color.Black else Color.White
            Box(Modifier.fillMaxWidth().height((126f * grid.scale).dp)
                .clip(RoundedCornerShape((36f * grid.scale).dp))
                .background(if (current) Color.White else if (dragged == occurrence.id) Color(0xff333333) else Color.Transparent)
                .semantics(mergeDescendants = true) { selected = current; if (current) stateDescription = if (playing) "Playing" else "Paused" }
                .combinedClickable(enabled = interactive && !validDrag, onClick = { onSelect(occurrence.id) },
                    onLongClickLabel = "Track options", onLongClick = { onOptions(occurrence) })) {
                Image(painterResource(R.drawable.queue_handle), null,
                    Modifier.align(Alignment.CenterStart).padding(start = (52f * grid.scale).dp).size((26f * grid.scale).dp, (48f * grid.scale).dp)
                        .semantics {
                            contentDescription = "Reorder ${track?.title ?: "unavailable track"}"
                            customActions = listOf(CustomAccessibilityAction("Move up") {
                                onReorder(ids, occurrence.id, ids.indexOf(occurrence.id) - 1); true
                            }, CustomAccessibilityAction("Move down") {
                                onReorder(ids, occurrence.id, ids.indexOf(occurrence.id) + 1); true
                            })
                        }, colorFilter = ColorFilter.tint(color))
                Column(Modifier.fillMaxSize().padding(start = (130f * grid.scale).dp, end = (100f * grid.scale).dp)) {
                    PlayerLabel(track?.title ?: "Track unavailable", Modifier.fillMaxWidth().height((76f * grid.scale).dp),
                        58f * grid.scale, alignment = Alignment.CenterStart, color = color, marquee = true)
                    PlayerLabel(track?.let { "${it.artist} - ${it.album}" } ?: "File unavailable",
                        Modifier.fillMaxWidth().height((42f * grid.scale).dp), 34f * grid.scale,
                        alignment = Alignment.CenterStart, color = color, marquee = true)
                }
                if (current) Image(painterResource(if (playing) R.drawable.track_play_indicator else R.drawable.player_pause), null,
                    Modifier.align(Alignment.CenterEnd).padding(end = (42f * grid.scale).dp).size((48f * grid.scale).dp),
                    colorFilter = ColorFilter.tint(Color.Black))
            }
            Spacer(Modifier.height((14f * grid.scale).dp))
        }
    }
    // A stationary handle strip owns the gesture even when preview rows move beneath it.
    Box(grid.place(69f, 277f, 96f, 726f).pointerInput(interactive, rowPx) {
        if (interactive) detectDragGestures(onDragStart = { offset ->
            originalIds = latestQueue.entries.map { it.id }
            val index = ((offset.y + scroll.firstVisibleItemIndex * rowPx + scroll.firstVisibleItemScrollOffset) / rowPx).toInt()
            dragged = originalIds.getOrNull(index)
            target = index; fingerY = offset.y
            if (dragged != null) inactivity.setScrolling(true)
        }, onDrag = { change, amount ->
            change.consume()
            fingerY += amount.y
            target = ((fingerY + scroll.firstVisibleItemIndex * rowPx + scroll.firstVisibleItemScrollOffset) / rowPx).toInt().coerceIn(0, (originalIds.size - 1).coerceAtLeast(0))
        }, onDragEnd = {
            dragged?.let { latestReorder(originalIds, it, target) }
            endDrag()
        }, onDragCancel = { endDrag() })
    })
    if (queue.entries.isEmpty()) PlayerLabel("Queue is empty", grid.place(83f, 312f, 760f, 88f), 46f * grid.scale, alignment = Alignment.CenterStart)
    QueueScrollbar(grid, scroll, rowPx, viewportPx, object : AbstractList<String>() {
        override val size get() = shown.size
        override fun get(index: Int) = tracksById[shown[index].trackId]?.title.orEmpty()
    })
}

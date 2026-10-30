package com.example.clocarmus.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clocarmus.R
import com.example.clocarmus.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A uniformly scaled reference grid, centered within each half of the landscape display. */
private class PlayerGrid(width: Float, height: Float) {
    val scale = minOf(width / 1000f, height / 1236f)
    private val left = (width - 1000f * scale) / 2
    private val top = (height - 1236f * scale) / 2

    fun place(x: Float, y: Float, width: Float, height: Float) = Modifier
        .offset((left + x * scale).dp, (top + y * scale).dp)
        .size((width * scale).dp, (height * scale).dp)

    fun touchTarget(cx: Float, cy: Float, visualSize: Float): Modifier {
        val side = maxOf(48f, visualSize * scale)
        return Modifier.offset((left + cx * scale - side / 2).dp, (top + cy * scale - side / 2).dp)
            .size(side.dp)
    }
}

/** Visual player shell; transport state is a demo until the playback service is connected. */
@Composable
fun MainPlayerScreen(referencePreview: Boolean = false) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(if (referencePreview) .62f else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var playing by rememberSaveable { mutableStateOf(referencePreview) }
    var position by rememberSaveable { mutableFloatStateOf(113f) }
    var shuffle by rememberSaveable { mutableStateOf(true) }
    var repeat by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val grid = PlayerGrid(maxWidth.value, maxHeight.value)
                MenuButton(grid) { dialog = "Menu" }
                PlayerSlider(volume, {
                    volume = it
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).toInt(), 0)
                }, grid.place(283f, 61f, 434f, 83f), "Media volume", grid.scale, volume = true)
                AssetButton("Add to playlist", R.drawable.player_playlist_button, grid, 850f, 106f, 104f) { dialog = "Add to playlist" }
                Image(painterResource(R.drawable.player_no_album_art), "No album artwork",
                    grid.place(273f, 187f, 457f, 457f), contentScale = ContentScale.Fit)
                AssetButton("Shuffle", if (shuffle) R.drawable.player_shuffle_active else R.drawable.player_shuffle_inactive,
                    grid, 141f, 291f, 93f, if (shuffle) "On" else "Off") { shuffle = !shuffle }
                AssetButton("Repeat", when (repeat) {
                    1 -> R.drawable.player_repeat_one_active
                    2 -> R.drawable.player_repeat_active
                    else -> R.drawable.player_repeat_inactive
                }, grid, 850f, 291f, 93f, when (repeat) { 1 -> "Repeat one"; 2 -> "Repeat all"; else -> "Off" }) { repeat = (repeat + 1) % 3 }
                AssetButton("Rewind 10 seconds", R.drawable.player_skip_10sec_backward, grid, 141f, 538f, 93f) { position = (position - 10).coerceAtLeast(0f) }
                AssetButton("Forward 10 seconds", R.drawable.player_skip_10sec_forward, grid, 850f, 538f, 93f) { position = (position + 10).coerceAtMost(225f) }
                PlayerLabel("Superman", grid.place(70f, 670f, 860f, 72f), 54f * grid.scale, bold = true)
                PlayerLabel("Lazlo Bane", grid.place(70f, 744f, 860f, 57f), 48f * grid.scale)
                PlayerLabel("All the Time in the World", grid.place(60f, 802f, 880f, 57f), 48f * grid.scale)
                PlayerLabel("2002", grid.place(70f, 859f, 860f, 57f), 48f * grid.scale)
                PlayerSlider(position / 225f, { position = it * 225f }, grid.place(79f, 944f, 841f, 87f), "Track position", grid.scale)
                PlayerLabel(formatPosition(position), grid.place(79f, 1029f, 180f, 53f), 43f * grid.scale, alignment = Alignment.CenterStart)
                PlayerLabel("3:45", grid.place(740f, 1029f, 180f, 53f), 43f * grid.scale, alignment = Alignment.CenterEnd)
                AssetButton("Previous track", R.drawable.player_skip_back, grid, 238f, 1136f, 93f) { position = 0f }
                AssetButton(if (playing) "Pause" else "Play", if (playing) R.drawable.player_pause else R.drawable.player_play,
                    grid, 508f, 1137f, 110f) { playing = !playing }
                AssetButton("Next track", R.drawable.player_skip_next, grid, 778f, 1136f, 93f) { position = 0f }
            }
            ClockPane(referencePreview, Modifier.weight(1f).fillMaxHeight())
        }
        Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxHeight().background(Color(0xffdddddd)))
    }
    dialog?.let { title ->
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(title) },
            text = { Text(if (title == "Menu") "Your music library is empty." else "No playlists yet.") },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
    }
}

@Composable
private fun MenuButton(grid: PlayerGrid, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The SVG's square canvas includes the pill's original padding; preserve that canvas.
    Box(grid.touchTarget(141f, 106f, 178f).semantics { contentDescription = "Menu" }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(if (pressed) R.drawable.player_menu_button_pressed else R.drawable.player_menu_button),
            null, Modifier.size((178f * grid.scale).dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun AssetButton(label: String, @DrawableRes asset: Int, grid: PlayerGrid, cx: Float, cy: Float,
                        size: Float, state: String? = null, onClick: () -> Unit) {
    Box(grid.touchTarget(cx, cy, size).semantics {
        contentDescription = label
        if (state != null) stateDescription = state
    }.clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(asset), null, Modifier.size((size * grid.scale * 0.8f).dp), contentScale = ContentScale.Fit)
    }
}

private fun playerTextStyle(size: Float, bold: Boolean = false) = TextStyle(
    color = Color.White, fontFamily = InterFontFamily,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size.sp, lineHeight = (size * 1.12f).sp, letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

@Composable
private fun PlayerLabel(text: String, modifier: Modifier, size: Float, alignment: Alignment = Alignment.Center, bold: Boolean = false) {
    Box(modifier, contentAlignment = alignment) {
        Text(text, style = playerTextStyle(size, bold), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ClockPane(referencePreview: Boolean, modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(referencePreview) {
        if (!referencePreview) while (true) { now = Date(); delay(1000) }
    }
    val day = if (referencePreview) "Friday" else SimpleDateFormat("EEEE", Locale.ENGLISH).format(now)
    val time = if (referencePreview) "10:30" else SimpleDateFormat("h:mm", Locale.ENGLISH).format(now)
    val period = if (referencePreview) "AM" else SimpleDateFormat("a", Locale.ENGLISH).format(now)
    val date = if (referencePreview) "30-01-2026" else SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(now)
    BoxWithConstraints(modifier) {
        val grid = PlayerGrid(maxWidth.value, maxHeight.value)
        PlayerLabel(day, grid.place(15f, 311f, 900f, 120f), 100f * grid.scale)
        BoxWithConstraints(grid.place(90f, 495f, 850f, 248f), contentAlignment = Alignment.Center) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val mainStyle = playerTextStyle(230f * grid.scale)
            val periodStyle = playerTextStyle(62f * grid.scale, bold = true)
            val gap = (24f * grid.scale).dp
            val naturalWidth = measurer.measure(time, mainStyle).size.width +
                measurer.measure(period, periodStyle).size.width + with(density) { gap.toPx() }
            val availableWidth = with(density) { maxWidth.toPx() }
            // Fit long times by changing font size uniformly, never by transforming glyphs.
            val fit = minOf(1f, availableWidth / naturalWidth)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, Modifier.alignByBaseline(), style = playerTextStyle(230f * grid.scale * fit), maxLines = 1)
                Spacer(Modifier.width(gap * fit))
                Text(period, Modifier.alignByBaseline(), style = playerTextStyle(62f * grid.scale * fit, bold = true), maxLines = 1)
            }
        }
        PlayerLabel(date, grid.place(15f, 800f, 900f, 120f), 100f * grid.scale)
    }
}

@Composable
private fun PlayerSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier,
                         label: String, scale: Float, volume: Boolean = false) {
    val update by rememberUpdatedState(onValueChange)
    val trackHeight = (if (volume) 61f else 36f) * scale
    val thumbRadius = 33f * scale
    val density = LocalDensity.current
    val trackPx = with(density) { trackHeight.dp.toPx() }
    val thumbPx = with(density) { thumbRadius.dp.toPx() }
    Box(modifier.defaultMinSize(minHeight = 48.dp).semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
        setProgress { update(it.coerceIn(0f, 1f)); true }
    }.pointerInput(Unit) { detectTapGestures { update((it.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectDragGestures { change, _ ->
            change.consume(); update((change.position.x / size.width).coerceIn(0f, 1f))
        } }, contentAlignment = Alignment.Center) {
        if (volume) {
            Image(painterResource(R.drawable.player_volume_track), null,
                Modifier.fillMaxWidth().height(trackHeight.dp), contentScale = ContentScale.Fit)
        }
        Canvas(Modifier.fillMaxSize()) {
            val top = (size.height - trackPx) / 2
            val radius = if (volume) CornerRadius(trackPx / 2) else CornerRadius(5f * scale * density.density, trackPx / 2)
            val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, top, size.width, top + trackPx, radius.x, radius.y)) }
            if (!volume) drawPath(outline, Color(0xff494949))
            clipPath(outline) { drawRect(Color.White, Offset(0f, top), Size(size.width * value, trackPx)) }
            if (!volume) drawCircle(Color.White, thumbPx, Offset(size.width * value, size.height / 2))
        }
        if (volume) Image(painterResource(R.drawable.player_volume_slider_icon), null,
            Modifier.align(Alignment.CenterStart).padding(start = (15f * scale).dp).size(trackHeight.dp), contentScale = ContentScale.Fit)
    }
}

private fun formatPosition(position: Float): String {
    val seconds = position.toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

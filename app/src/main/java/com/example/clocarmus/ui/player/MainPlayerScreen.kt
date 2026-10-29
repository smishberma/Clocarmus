package com.example.clocarmus.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.clocarmus.R
import com.example.clocarmus.ui.theme.InterFontFamily

private val White = Color.White
private val Muted = Color(0xff808080)

/** Main-screen visual shell. Sample metadata/transport are replaced by the playback service later. */
@Composable
fun MainPlayerScreen(referencePreview: Boolean = false) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember { mutableFloatStateOf(if (referencePreview) .62f else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var playing by rememberSaveable { mutableStateOf(referencePreview) }
    var position by rememberSaveable { mutableFloatStateOf(113f) }
    var shuffle by rememberSaveable { mutableStateOf(true) }
    var repeat by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Positions and artwork follow the reference grid; type scales to the available space.
        val sx = maxWidth.value / 2000f
        val sy = maxHeight.value / 1236f
        val unit = minOf(sx, sy)
        fun place(x: Float, y: Float, w: Float, h: Float) = Modifier
            .offset((x * sx).dp, (y * sy).dp).size((w * sx).dp, (h * sy).dp)
        @Composable
        fun Label(text: String, x: Float, y: Float, w: Float, h: Float, size: Float, bold: Boolean = false, widthScale: Float = 1f) {
            Box(place(x, y, w, h), contentAlignment = Alignment.Center) {
                Text(text, modifier = Modifier.graphicsLayer(scaleX = widthScale), color = White, fontFamily = InterFontFamily, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontSize = (size * unit).sp, lineHeight = (size * unit * 1.12f).sp,
                    letterSpacing = 0.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
            }
        }
        @Composable
        fun Control(name: String, x: Float, y: Float, w: Float = 130f, h: Float = 130f,
                    selected: Boolean? = null, action: () -> Unit) {
            val touchW = maxOf(w * sx, 48f)
            val touchH = maxOf(h * sy, 48f)
            Box(Modifier.offset((x * sx + (w * sx - touchW) / 2).dp, (y * sy + (h * sy - touchH) / 2).dp).size(touchW.dp, touchH.dp).semantics { contentDescription = name; role = Role.Button
                if (selected != null) { this.selected = selected; stateDescription = if (selected) "On" else "Off" }
            }.clickable(onClick = action), contentAlignment = Alignment.Center) {
                PlayerIcon(name, Modifier.size((w * sx).dp, (h * sy).dp), selected ?: false)
            }
        }
        Canvas(Modifier.fillMaxSize()) { drawLine(Color(0xffdddddd), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2 * sx) }
        Box(place(52f, 67f, 178f, 79f).clip(RoundedCornerShape(50)).clickable { dialog = "Menu" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { val border = 7 * unit
                drawRoundRect(White, topLeft = Offset(border / 2, border / 2), size = Size(size.width - border, size.height - border), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2), style = Stroke(border)) }
            Text("MENU", color = White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = (34 * unit).sp)
        }
        ReferenceSlider(volume, { volume = it; audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).toInt(), 0) },
            place(283f, 72f, 434f, 61f), "Media volume", false)
        Box(place(308f, 82f, 44f, 42f)) { PlayerIcon("Volume", Modifier.fillMaxSize()) }
        Control("Add to playlist", 813f, 76f, 74f, 53f) { dialog = "Add to playlist" }
        Box(place(273f, 187f, 457f, 457f), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.demo_album), "All the Time in the World album artwork", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Control("Shuffle", 95f, 245f, 93f, 93f, selected = shuffle) { shuffle = !shuffle }
        Control("Repeat", 817f, 263f, 66f, 57f, selected = repeat != 0) { repeat = (repeat + 1) % 3 }
        if (repeat == 1) Label("1", 819f, 273f, 60f, 35f, 28f, true)
        Control("Rewind 10 seconds", 98f, 493f, 84f, 90f) { position = (position - 10).coerceAtLeast(0f) }
        Control("Forward 10 seconds", 808f, 493f, 84f, 90f) { position = (position + 10).coerceAtMost(225f) }
        Label("Superman", 100f, 656f, 800f, 72f, 52f, widthScale = 1.125f)
        Label("Lazlo Bane", 100f, 742f, 800f, 57f, 47f, widthScale = 1.04f)
        Label("All the Time in the World", 73f, 800f, 860f, 57f, 47f, widthScale = 1.02f)
        Label("2002", 100f, 857f, 800f, 57f, 47f, widthScale = 1.10f)
        ReferenceSlider(if (referencePreview && position == 113f) .533f else position / 225f, { position = it * 225 }, place(79f, 954f, 841f, 67f), "Track position", true)
        Label("${position.toInt() / 60}:${(position.toInt() % 60).toString().padStart(2, '0')}", 64f, 1026f, 100f, 53f, 43f, widthScale = 1.07f)
        Label("3:45", 826f, 1026f, 100f, 53f, 43f, widthScale = 1.07f)
        Control("Previous track", 206f, 1109f, 62f, 53f) { position = 0f }
        Control(if (playing) "Pause" else "Play", 465f, 1090f, 87f, 95f) { playing = !playing }
        Control("Next track", 749f, 1109f, 62f, 53f) { position = 0f }
        val day = if (referencePreview) "Friday" else SimpleDateFormat("EEEE", Locale.ENGLISH).format(now)
        val time = if (referencePreview) "10:30" else SimpleDateFormat("h:mm", Locale.ENGLISH).format(now)
        val period = if (referencePreview) "AM" else SimpleDateFormat("a", Locale.ENGLISH).format(now)
        val date = if (referencePreview) "30-01-2026" else SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(now)
        Label(day, 1035f, 314f, 855f, 120f, 97f, widthScale = 1.065f)
        Label(time, 1177f, 488f, 580f, 248f, 230f, widthScale = 1.052f)
        Label(period, 1750f, 651f, 120f, 77f, 61f, true, widthScale = 1.05f)
        Label(date, 1110f, 800f, 710f, 120f, 102f, widthScale = 1.048f)
    }
    dialog?.let { title ->
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(title) },
            text = { Text(if (title == "Menu") "Your music library is empty." else "No playlists yet.") },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
    }
}

@Composable
private fun ReferenceSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier, label: String, thumb: Boolean) {
    Canvas(modifier.semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
        setProgress { onValueChange(it.coerceIn(0f, 1f)); true }
    }.pointerInput(Unit) { detectTapGestures { onValueChange((it.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); onValueChange((change.position.x / size.width).coerceIn(0f, 1f)) } }) {
        val barHeight = if (thumb) size.height * .55f else size.height
        val top = (size.height - barHeight) / 2
        val radiusX = if (thumb) size.width * .006f else barHeight / 2
        val radiusY = barHeight / 2
        val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, top, size.width, top + barHeight, radiusX, radiusY)) }
        drawPath(outline, if (thumb) Color(0xff494949) else Color(0xff646464))
        clipPath(outline) { drawRect(White, Offset(0f, top), Size(size.width * value, barHeight)) }
        if (thumb) drawCircle(White, size.height / 2, Offset(size.width * value, size.height / 2))
    }
}

@Composable
private fun PlayerIcon(name: String, modifier: Modifier, active: Boolean = false) {
    val asset = when (name) {
        "Pause" -> R.drawable.ic_player_pause
        "Previous track" -> R.drawable.ic_player_previous
        "Next track" -> R.drawable.ic_player_next
        "Shuffle" -> R.drawable.ic_player_shuffle
        "Repeat" -> R.drawable.ic_player_repeat
        "Rewind 10 seconds" -> R.drawable.ic_player_rewind
        "Forward 10 seconds" -> R.drawable.ic_player_forward
        "Add to playlist" -> R.drawable.ic_player_playlist
        "Volume" -> R.drawable.ic_player_volume
        else -> null
    }
    if (asset != null) {
        val tint = when {
            name == "Volume" -> Color.Black
            (name == "Repeat" || name == "Shuffle") && !active -> Muted
            else -> White
        }
        Image(painterResource(asset), null, modifier, contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(tint))
    } else {
        Canvas(modifier) {
            drawPath(Path().apply { moveTo(size.width * .12f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .12f, size.height); close() }, White)
        }
    }
}

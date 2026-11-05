package com.example.clocarmus.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R

/** Shared browser entry; completion dismisses the retained playlist action parent as well. */
internal class TrackOptionsEntry(val open: (String) -> Unit, val active: Boolean, val completed: Int = 0)
internal val LocalTrackOptions = staticCompositionLocalOf { TrackOptionsEntry({}, false) }

@Composable
internal fun TrackOptionsPopup(grid: PlayerGrid, track: TrackMenuEntry?, onClose: () -> Unit,
    onCollections: () -> Unit, onSchedule: (Boolean) -> Unit) {
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
        grid, 55f, 20f, 172f, 172f, onClose)
    Image(painterResource(R.drawable.track_search), null, grid.place(858.5f, 59.5f, 87f, 87f))
    CollectionsButton("Close", grid, 293f, onClose)
    Box(grid.place(69f, 304f, 862f, 554f).clip(RoundedCornerShape((56f * grid.scale).dp))
        .background(Color(0xff333333)).pointerInput(Unit) { detectTapGestures {} })
    Row(grid.place(69f, 326f, 862f, 50f),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.options_info_icon), null, Modifier.size((30f * grid.scale).dp))
        Spacer(Modifier.width((18f * grid.scale).dp))
        PlayerLabel("TRACK OPTIONS", Modifier.fillMaxHeight().offset(y = (-1f * grid.scale).dp), 28f * grid.scale, bold = true)
    }
    Box(grid.place(86f, 396f, 827f, 126f).clip(RoundedCornerShape((36f * grid.scale).dp)).background(Color.White))
    PlayerLabel(track?.title ?: "Track unavailable", grid.place(125f, 398f, 750f, 76f), 58f * grid.scale,
        alignment = Alignment.CenterStart, color = Color.Black, marquee = true)
    PlayerLabel(track?.let { "${it.artist} - ${it.album}" } ?: "This track is no longer in the library",
        grid.place(125f, 474f, 750f, 42f), 34f * grid.scale, alignment = Alignment.CenterStart, color = Color.Black, marquee = true)
    OptionButton(grid, "Save to playlist", 83f, 546f, track != null, onCollections)
    OptionButton(grid, "Play next", 512f, 546f, track?.available == true) { onSchedule(false) }
    OptionButton(grid, "Add to queue", 83f, 644f, false) {}
    OptionButton(grid, "Stop after this song", 512f, 644f, track?.available == true) { onSchedule(true) }
    OptionButton(grid, "Remove from queue", 83f, 742f, false) {}
    OptionButton(grid, "Track info", 512f, 742f, false) {}
}

@Composable
private fun OptionButton(grid: PlayerGrid, label: String, x: Float, y: Float, enabled: Boolean, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(grid.place(x, y, 404f, 79f).clip(RoundedCornerShape(percent = 50))
        .background(if (pressed) Color.White else Color(0xff5b5b5b))
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, enabled = enabled, onClick = action)) {
        PlayerLabel(label, Modifier.fillMaxSize().padding(horizontal = (28f * grid.scale).dp), 34f * grid.scale,
            alignment = Alignment.CenterStart, color = if (!enabled) Color(0xffaaaaaa) else if (pressed) Color.Black else Color.White,
            bold = false)
    }
}

package com.example.clocarmus.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R

@Composable
internal fun TrackCollectionsPopup(
    grid: PlayerGrid, library: PlaylistLibrary, track: String,
    onToggle: (String) -> Unit, onCreate: () -> Unit, onClose: () -> Unit
) {
    CollectionsButton("Create playlist", grid, 55f, onCreate)
    CollectionsButton("Close", grid, 511f, onClose)
    // The panel consumes blank-space taps; the covered player controls are also absent.
    Box(grid.place(51f, 304f, 893f, 759f).clip(RoundedCornerShape((75f * grid.scale).dp))
        .background(Color(0xff333333)).pointerInput(Unit) { detectTapGestures {} })
    Image(painterResource(R.drawable.collections_playlist_button_untoggled), null, grid.place(287f, 316f, 82f, 82f))
    PlayerLabel("ADD/REMOVE TO", grid.place(378f, 330f, 400f, 55f), 40f * grid.scale,
        alignment = Alignment.CenterStart, bold = true)
    val scroll = rememberScrollState()
    Column(grid.place(110f, 414f, 733f, 577f).verticalScroll(scroll)) {
        CollectionMembershipRow(grid, "Favorites", library.contains(FAVORITES, track), true) { onToggle(FAVORITES) }
        Spacer(Modifier.height((41f * grid.scale).dp))
        Box(Modifier.fillMaxWidth().height((3f * grid.scale).dp).background(Color.White))
        Spacer(Modifier.height((49f * grid.scale).dp))
        if (library.custom.isEmpty()) {
            PlayerLabel("No custom playlists", Modifier.fillMaxWidth().offset(x = (20f * grid.scale).dp).height((340f * grid.scale).dp),
                46f * grid.scale, bold = true, color = Color(0xffb3b3b3))
        } else {
            library.sortedCustom.forEach { playlist ->
                key(playlist.id) {
                    CollectionMembershipRow(grid, playlist.name, library.contains(playlist.id, track), false) { onToggle(playlist.id) }
                }
            }
            Spacer(Modifier.height((38f * grid.scale).dp))
        }
    }
    if (scroll.maxValue > 0) {
        val viewport = scroll.viewportSize.toFloat()
        val fraction = viewport / (viewport + scroll.maxValue)
        Box(grid.place(907f, 441f, 24f, 550f).background(Color(0xff707070))) {
            Box(Modifier.offset(y = (550f * grid.scale * (1f - fraction) * scroll.value / scroll.maxValue).dp)
                .fillMaxWidth().height((550f * grid.scale * fraction).dp)
                .background(Color.White, RoundedCornerShape((2f * grid.scale).dp)))
        }
    }
}

@Composable
private fun CollectionMembershipRow(grid: PlayerGrid, name: String, checked: Boolean, favorite: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(((if (favorite) 100f else 102f) * grid.scale).dp)
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
        .semantics { contentDescription = name }, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(when {
            !checked -> R.drawable.collections_checkbox_inactive
            favorite -> R.drawable.collections_checkbox_favorites_active
            else -> R.drawable.collections_checkbox_active
        }), null, Modifier.size((48f * grid.scale).dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width((50f * grid.scale).dp))
        if (favorite) Image(painterResource(R.drawable.playlist_favorites), null, Modifier.size((48f * grid.scale).dp))
        else Spacer(Modifier.width((48f * grid.scale).dp))
        Spacer(Modifier.width((43f * grid.scale).dp))
        PlayerLabel(name, Modifier.weight(1f).fillMaxHeight(), 58f * grid.scale, alignment = Alignment.CenterStart, marquee = true)
    }
}

@Composable
internal fun CollectionsButton(label: String, grid: PlayerGrid, x: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(grid.place(x, 169f, 419f, 113f).clip(RoundedCornerShape(percent = 50))
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)) {
        Image(painterResource(if (pressed) R.drawable.collections_button_pressed else R.drawable.collections_button_unpressed),
            null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        PlayerLabel(label.uppercase(java.util.Locale.ROOT), Modifier.fillMaxSize(), 36f * grid.scale, bold = true)
    }
}

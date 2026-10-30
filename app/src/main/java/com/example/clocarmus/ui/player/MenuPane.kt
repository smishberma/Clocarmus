package com.example.clocarmus.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R

/** Menu content on the same 1000 × 1236 grid as the player and persistent clock. */
@Composable
internal fun MenuPane(grid: PlayerGrid, onBack: () -> Unit, onDestination: (String) -> Unit) {
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed, grid, 55f, 20f, 172f, 172f, onBack)

    MenuCategory("All tracks", "ALL TRACKS", R.drawable.menu_all_tracks, R.drawable.menu_all_tracks_pressed, grid, 171f, 249f, 412f, onDestination)
    MenuCategory("Artists", "ARTISTS", R.drawable.menu_artists, R.drawable.menu_artists_pressed, grid, 508f, 249f, 412f, onDestination)
    MenuCategory("Albums", "ALBUMS", R.drawable.menu_albums, R.drawable.menu_albums_pressed, grid, 856f, 249f, 412f, onDestination)
    MenuCategory("Playlists", "PLAYLISTS", R.drawable.menu_playlists, R.drawable.menu_playlists_pressed, grid, 171f, 595f, 763f, onDestination)
    MenuCategory("Genres", "GENRES", R.drawable.menu_genres, R.drawable.menu_genres_pressed, grid, 508f, 595f, 763f, onDestination)
    MenuCategory("Folders", "FOLDERS", R.drawable.menu_folders, R.drawable.menu_folders_pressed, grid, 856f, 595f, 763f, onDestination)

    // The supplied button includes its label and vertical whitespace in a square SVG canvas.
    MenuAssetButton("Settings", R.drawable.menu_settings_button, R.drawable.menu_settings_button_pressed, grid, 286.5f, 730.5f, 427f, 427f) {
        onDestination("Settings")
    }
}

@Composable
private fun MenuCategory(destination: String, label: String, @DrawableRes asset: Int, @DrawableRes pressedAsset: Int, grid: PlayerGrid,
                         cx: Float, iconTop: Float, labelTop: Float, onDestination: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(grid.place(cx - 145f, iconTop - 20f, 290f, labelTop - iconTop + 70f)
        .semantics(mergeDescendants = true) { contentDescription = destination }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onDestination(destination) }) {
        Image(painterResource(if (pressed) pressedAsset else asset), null,
            Modifier.align(Alignment.TopCenter).offset(y = (20f * grid.scale).dp).size((130f * grid.scale).dp),
            contentScale = ContentScale.Fit)
        PlayerLabel(label, Modifier.offset(y = ((labelTop - iconTop + 10f) * grid.scale).dp)
            .size((290f * grid.scale).dp, (44f * grid.scale).dp), 34f * grid.scale, bold = true)
    }
}

@Composable
private fun MenuAssetButton(label: String, @DrawableRes asset: Int, @DrawableRes pressedAsset: Int, grid: PlayerGrid,
                            x: Float, y: Float, width: Float, height: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Restrict touch targets to the visible pill rather than the SVG's transparent square.
    val visibleHeight = if (label == "Back") width * 19.9322f / 48f else width / 4f
    Box(grid.place(x, y + (height - visibleHeight) / 2, width, visibleHeight)
        .semantics { contentDescription = label }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(if (pressed) pressedAsset else asset), null, Modifier.requiredSize((width * grid.scale).dp, (height * grid.scale).dp),
            contentScale = ContentScale.Fit)
    }
}

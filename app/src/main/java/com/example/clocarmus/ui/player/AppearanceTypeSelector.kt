package com.example.clocarmus.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import kotlin.math.roundToInt

internal data class AppearanceDropdown(
    val grid: PlayerGrid,
    val settings: PlayerSettings,
    val anchor: IntOffset,
    val onDismiss: () -> Unit,
    val onChange: (PlayerSettings) -> Unit,
    val divider: Boolean = false
)

internal val LocalAppearanceDropdownHost = staticCompositionLocalOf<(AppearanceDropdown?) -> Unit> { {} }

@Composable
internal fun AppearanceTypeSelector(
    grid: PlayerGrid,
    settings: PlayerSettings,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onChange: (PlayerSettings) -> Unit,
    divider: Boolean = false
) {
    var anchor by remember { mutableStateOf(IntOffset.Zero) }
    val host = LocalAppearanceDropdownHost.current
    val change by rememberUpdatedState(onChange)
    val expand by rememberUpdatedState(onExpandedChange)
    DisposableEffect(expanded, anchor, grid.scale, settings, host, divider) {
        host(if (expanded) AppearanceDropdown(grid, settings, anchor, { expand(false) }, { change(it) }, divider) else null)
        onDispose { host(null) }
    }
    val shape = RoundedCornerShape((61f * grid.scale).dp)
    Box(grid.place(110f, 400f, 780f, 122f).onGloballyPositioned {
        val position = it.positionInRoot()
        anchor = IntOffset(position.x.roundToInt(), position.y.roundToInt())
    }.clip(shape).border((2.5f * grid.scale).dp, Color.White, shape)) {
        AppearanceTypeHeader(grid.scale, settings, expanded, divider) { onExpandedChange(!expanded) }
    }
}

/** Render above both panes in the Activity window, retaining its focus and immersive mode. */
@Composable
internal fun AppearanceDropdownOverlay(dropdown: AppearanceDropdown) {
    val s = dropdown.grid.scale
    val settings = dropdown.settings
    val anchor = dropdown.anchor
    val density = LocalDensity.current
    val shape = RoundedCornerShape((61f * s).dp)
    BackHandler(onBack = dropdown.onDismiss)
    Box(Modifier.fillMaxSize().semantics { paneTitle = if (dropdown.divider) "Divider type" else "Background type" }.pointerInput(dropdown.onDismiss) {
        detectTapGestures { dropdown.onDismiss() }
    }) {
        // Keep the shared Back control above the touch shield for long-press return.
        Box(Modifier.offset {
            IntOffset(anchor.x - with(density) { (110f * s).dp.roundToPx() },
                anchor.y - with(density) { (400f * s).dp.roundToPx() })
        }) {
            MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
                PlayerGrid(1000f * s, 1236f * s), 55f, 20f, 172f, 172f, dropdown.onDismiss)
        }
        Column(Modifier.offset { anchor }.size((780f * s).dp, (620f * s).dp).clip(shape)
            .background(Color.Black).border((2.5f * s).dp, Color.White, shape)
            .pointerInput(Unit) { detectTapGestures {} }) {
            AppearanceTypeHeader(s, settings, true, dropdown.divider, dropdown.onDismiss)
            Spacer(Modifier.height((8f * s).dp))
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                (if (dropdown.divider) DividerType.entries.map { it.label } else listOf(settings.solidBackgroundLabel, "Gradient", "Album art")).forEachIndexed { index, label ->
                    val selected = if (dropdown.divider) index == settings.dividerType.ordinal else index == settings.backgroundType.ordinal
                    Row(Modifier.fillMaxWidth().height((97f * s).dp)
                        .clip(RoundedCornerShape((8f * s).dp))
                        .background(if (selected) Color(0xff333333) else Color.Transparent)
                        .selectable(selected, role = Role.RadioButton) {
                            dropdown.onChange(if (dropdown.divider) settings.selectDividerType(DividerType.entries[index]) else settings.selectBackgroundType(BackgroundType.entries[index]))
                            dropdown.onDismiss()
                        }.padding(horizontal = (55f * s).dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerLabel(label, Modifier.weight(1f).fillMaxHeight(), 40f * s, Alignment.CenterStart)
                        Image(painterResource(if (selected) R.drawable.settings_radio_button_active else R.drawable.settings_radio_button_inactive),
                            null, Modifier.size((40f * s).dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceTypeHeader(s: Float, settings: PlayerSettings, expanded: Boolean, divider: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().height((122f * s).dp)
        .clickable(role = Role.Button, onClick = onToggle)
        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        .padding(horizontal = (55f * s).dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerLabel(if (divider) settings.dividerType.label else when (settings.backgroundType) {
            BackgroundType.SOLID -> settings.solidBackgroundLabel
            BackgroundType.GRADIENT -> "Gradient"
            BackgroundType.ALBUM_ART -> "Album art"
        },
            Modifier.weight(1f).fillMaxHeight(), 40f * s, Alignment.CenterStart)
        Image(painterResource(R.drawable.settings_drop_down), null, Modifier.size((34f * s).dp, (20f * s).dp))
    }
}

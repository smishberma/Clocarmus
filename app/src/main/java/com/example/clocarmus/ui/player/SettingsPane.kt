package com.example.clocarmus.ui.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.clocarmus.ui.theme.InterFontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R

@Composable
internal fun SettingsPane(grid: PlayerGrid, settings: PlayerSettings, onChange: (PlayerSettings) -> Unit,
                          onExit: () -> Unit, preview: String? = null) {
    var page by rememberSaveable { mutableStateOf(when (preview) {
        "settings-folders" -> "Manage folders"; "settings-background" -> "Background"; "settings-clock", "settings-clock-vertical", "settings-clock-lcd", "settings-clock-dotmatrix" -> "Clock";
        "settings-divider-dotted", "settings-divider-squiggly", "settings-divider-beam" -> "Divider"; else -> "Settings"
    }) }
    var gradientTab by rememberSaveable { mutableStateOf(settings.background.gradientAsset != null) }
    var prompt by rememberSaveable { mutableStateOf<String?>(null) }
    val home = LocalPlayerHome.current
    DisposableEffect(home) {
        val unregister = home?.registerCancellation { page = "Settings"; prompt = null }
        onDispose { unregister?.invoke() }
    }
    val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val context = LocalContext.current
    val back = { if (page == "Settings") onExit() else page = if (page == "Exclusions") "Manage folders" else "Settings" }
    BackHandler(onBack = back)
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed, grid, 55f, 20f, 172f, 172f, back)
    Row(grid.place(60f, 183f, 880f, 58f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.settings_settings_icon), null, Modifier.size((50f * grid.scale).dp))
        Spacer(Modifier.width((48f * grid.scale).dp))
        PlayerLabel("SETTINGS", Modifier.height((58f * grid.scale).dp), 40f * grid.scale, bold = true)
    }
    val s = grid.scale
    @Composable fun heading(text: String, cyan: Boolean = true, inset: Float = 27f) {
        PlayerLabel(text, Modifier.fillMaxWidth().height((50f * s).dp).padding(start = (inset * s).dp),
            36f * s, Alignment.CenterStart, bold = true, color = if (cyan) Color.Cyan else Color.White)
    }
    @Composable fun gap(height: Float) { Spacer(Modifier.height((height * s).dp)) }
    @Composable fun action(text: String, onClick: () -> Unit) {
        PlayerLabel(text, Modifier.fillMaxWidth().height((94f * s).dp).clip(RoundedCornerShape((32f * s).dp))
            .background(Color(0xff333333)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = (27f * s).dp),
            40f * s, Alignment.CenterStart)
    }
    @Composable fun toggle(text: String, value: Boolean, inset: Float = 27f, change: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().height((83f * s).dp).toggleable(value, role = Role.Switch, onValueChange = change)
            .padding(horizontal = (inset * s).dp), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val density = LocalDensity.current
                val measured = rememberTextMeasurer().measure(text, TextStyle(fontFamily = InterFontFamily, fontSize = (40f * s).sp)).size.width
                val fit = minOf(1f, with(density) { maxWidth.toPx() } / measured.coerceAtLeast(1))
                PlayerLabel(text, Modifier.fillMaxSize(), 40f * s * fit, Alignment.CenterStart)
            }
            Spacer(Modifier.width((20f * s).dp))
            Image(painterResource(if (value) R.drawable.settings_toggle_on else R.drawable.settings_toggle_off), null,
                Modifier.size((78f * s).dp, (50f * s).dp))
        }
    }
    @Composable fun choice(text: String, selected: Boolean, color: Color = Color.Transparent, height: Float = 94f,
                           gradientAsset: Int? = null, onClick: () -> Unit) {
        Box(Modifier.fillMaxWidth().height((height * s).dp).clip(RoundedCornerShape(percent = 50))
            .background(color).then(if (color == Color.Transparent || selected) Modifier.border((2.5f * s).dp, Color.White, RoundedCornerShape(percent = 50)) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)) {
            gradientAsset?.let { Image(painterResource(it), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds) }
            Row(Modifier.fillMaxSize().padding(horizontal = (55f * s).dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerLabel(text, Modifier.weight(1f).fillMaxHeight(), 40f * s, Alignment.CenterStart)
                Image(painterResource(if (selected) R.drawable.settings_radio_button_active else R.drawable.settings_radio_button_inactive), null,
                    Modifier.size((40f * s).dp))
            }
        }
    }
    if (page == "Settings") {
        LaunchedEffect(preview) { if (preview == "settings-scrolled") { snapshotFlow { scroll.maxValue }.collect { if (it > 0) scroll.scrollTo(it) } } }
        Column(grid.place(41f, 264f, 864f, 732f).verticalScroll(scroll)) {
            heading("Library"); gap(8f)
            action("Manual rescan") { Toast.makeText(context, "Demo library: device scanning is not connected yet.", Toast.LENGTH_LONG).show() }
            gap(16f); action("Manage folders") { page = "Manage folders" }
            gap(42f); heading("Playback"); gap(18f)
            toggle("Allow music auto-playback on start", settings.autoplay) { onChange(settings.copy(autoplay = it)) }
            gap(36f); heading("Display"); gap(12f)
            toggle("Show status bar", settings.statusBar) { onChange(settings.copy(statusBar = it)) }
            toggle("Disable buttons fade out in main player", settings.disableFade) { onChange(settings.copy(disableFade = it)) }
            toggle("Change to right-hand drive (RHD)", settings.rightHandDrive) { onChange(settings.copy(rightHandDrive = it)) }
            gap(42f); heading("Appearance"); gap(8f)
            action("Background") { page = "Background" }; gap(16f)
            action("Clock") { page = "Clock" }; gap(16f)
            action("Divider") { page = "Divider" }
            // Keep Appearance at the top when the list reaches its end, as in reference 036.
            gap(358f)
        }
        TrackScrollbar(grid, scroll, emptyList(), "Settings", proportional = true, top = 272f, showAlphabetIndicator = false)
    } else if (page in setOf("Background", "Clock", "Divider")) {
        Column(grid.place(95f, 264f, 800f, if (page == "Clock" || page == "Background") 800f else 732f)) {
            heading(page, inset = 0f); gap(if (page == "Background") 4f else 18f)
            if (page == "Background") {
                Row(Modifier.fillMaxWidth().padding(horizontal = (4f * s).dp).height((85f * s).dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy((18f * s).dp)) {
                    listOf("Normal", "Gradients").forEachIndexed { index, label ->
                        val selected = gradientTab == (index == 1)
                        PlayerLabel(label, Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(percent = 50))
                            .background(if (selected) Color.White else Color.Transparent)
                            .border((2.5f * s).dp, Color.White, RoundedCornerShape(percent = 50))
                            .selectable(selected, role = Role.Tab) { gradientTab = index == 1 },
                            40f * s, bold = selected, color = if (selected) Color.Black else Color.White)
                    }
                }
                gap(31f)
            }
            if (page == "Clock") {
                toggle("24-hour clock", settings.twentyFourHour, inset = 12f) { onChange(settings.copy(twentyFourHour = it)) }
                gap(29f)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = (15f * s).dp).selectableGroup()) {
                when (page) {
                    "Background" -> PlayerBackground.entries.filter { (it.gradientAsset != null) == gradientTab }.forEach {
                        choice(it.label, settings.background == it, it.color, 122f, it.gradientAsset) { onChange(settings.copy(background = it)) }; gap(34f)
                    }
                    "Clock" -> ClockStyle.entries.forEach {
                        choice(it.label, settings.clock == it, height = 122f) { onChange(settings.copy(clock = it)) }; gap(35f)
                    }
                    else -> DividerStyle.entries.forEach {
                        choice(it.label, settings.divider == it) { onChange(settings.copy(divider = it)) }; gap(39f)
                    }
                }
            }
        }
    } else {
        val folderScroll = rememberSaveable(page, saver = ScrollState.Saver) { ScrollState(0) }
        Column(grid.place(41f, 264f, 890f, 732f).verticalScroll(folderScroll)) {
            heading(page, cyan = page != "Manage folders"); gap(30f)
            if (page == "Manage folders") {
                val sources = demoFolderTree.contents(null).filter { it.id in settings.sources }
                if (sources.isEmpty()) { heading("No folders added", false); gap(30f) }
                sources.forEach { source ->
                    Row(Modifier.fillMaxWidth().height((120f * s).dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.folder_folder_icon), null, Modifier.padding((12f * s).dp).size((76f * s).dp))
                        Column(Modifier.weight(1f).padding(start = (18f * s).dp)) {
                            PlayerLabel(if (preview != null && source.id == "music") "My Music" else source.name,
                                Modifier.fillMaxWidth().height((52f * s).dp), 40f * s, Alignment.CenterStart, bold = true)
                            PlayerLabel(if (preview != null) "USB storage - My Music" else "Demo library - ${source.name}",
                                Modifier.fillMaxWidth().height((52f * s).dp), 38f * s, Alignment.CenterStart)
                        }
                        Box(Modifier.size((100f * s).dp).clickable(role = Role.Button, onClickLabel = "Remove ${source.name}") { prompt = source.id }, contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.settings_remove_library_button), "Remove ${source.name}", Modifier.size((54f * s).dp))
                        }
                    }; gap(24f)
                }
                gap(35f)
                PlayerLabel("ADD FOLDER", Modifier.align(Alignment.CenterHorizontally).size((440f * s).dp, (63f * s).dp)
                    .clip(RoundedCornerShape(percent = 50)).background(Color.White).clickable(role = Role.Button) { prompt = "add" },
                    30f * s, bold = true, color = Color.Black)
                gap(45f); action("Exclusions") { page = "Exclusions" }; gap(27f)
                action("Reset all (remove all libraries)") { prompt = "reset" }
            } else {
                val candidates = demoFolderTree.entries.filter { it.directory && it.parent != null && demoFolderTree.source(it.id)?.id in settings.sources }
                if (candidates.isEmpty()) heading("No subfolders to exclude", false)
                candidates.forEach { folder ->
                    toggle(demoFolderTree.path(folder, null), folder.id in settings.exclusions) { excluded ->
                        onChange(settings.copy(exclusions = if (excluded) settings.exclusions + folder.id else settings.exclusions - folder.id))
                    }
                }
            }
        }
    }
    prompt?.let { target ->
        val available = demoFolderTree.contents(null).filter { it.id !in settings.sources }
        AlertDialog(onDismissRequest = { prompt = null }, title = { Text(when (target) { "add" -> "Add demo folder"; "reset" -> "Remove all libraries?"; else -> "Remove folder?" }) },
            text = {
                if (target == "add") Column {
                    Text("Device folder discovery is not connected yet. Choose an existing demo source.")
                    if (available.isEmpty()) Text("All demo folders are already added.")
                    available.forEach { source -> TextButton(onClick = { onChange(settings.copy(sources = settings.sources + source.id)); prompt = null }) { Text(source.name) } }
                } else Text("${if (target == "reset") "All libraries" else demoFolderTree.get(target)?.name ?: "This folder"} will be removed from folder browsing. Files on storage are kept.")
            }, confirmButton = { if (target != "add") TextButton(onClick = {
                onChange(if (target == "reset") settings.copy(sources = emptySet(), exclusions = emptySet())
                    else settings.copy(sources = settings.sources - target, exclusions = settings.exclusions.filterNot { demoFolderTree.source(it)?.id == target }.toSet()))
                prompt = null
            }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { prompt = null }) { Text(if (target == "add") "Close" else "Cancel") } })
    }
}

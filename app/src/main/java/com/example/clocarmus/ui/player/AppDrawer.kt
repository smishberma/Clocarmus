package com.example.clocarmus.ui.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.example.clocarmus.ui.theme.InterFontFamily
import org.json.JSONArray
import kotlinx.coroutines.channels.Channel
import com.example.clocarmus.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

internal data class DrawerApp(val component: ComponentName, val label: String, val icon: ImageBitmap)
internal fun drawerPageCount(size: Int) = maxOf(1, (size + 8) / 9)
internal fun clampDrawerPage(page: Int, size: Int) = page.coerceIn(0, drawerPageCount(size) - 1)

@Suppress("DEPRECATION")
private fun loadDrawerApps(context: Context): List<DrawerApp> {
    val pm = context.packageManager
    val collator = Collator.getInstance()
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.enabled && it.activityInfo.exported }
        .distinctBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        .mapNotNull { entry ->
            try {
                val drawable = entry.loadIcon(pm)
                val bitmap = Bitmap.createBitmap(168, 168, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 168, 168)
                drawable.draw(Canvas(bitmap))
                DrawerApp(ComponentName(entry.activityInfo.packageName, entry.activityInfo.name), entry.loadLabel(pm).toString(), bitmap.asImageBitmap())
            } catch (_: RuntimeException) { null } // Package may disappear while discovery runs.
        }.sortedWith { a, b ->
            val label = collator.compare(a.label, b.label)
            if (label != 0) label else a.component.flattenToString().compareTo(b.component.flattenToString())
        }
}

/** Discovery is serialized; requests arriving during a scan are conflated. */
internal class DrawerCatalog {
    var apps by mutableStateOf<List<DrawerApp>?>(null)
    var refreshing by mutableStateOf(false)
    var pins by mutableStateOf<List<String>>(emptyList())
    var pinsLoaded by mutableStateOf(false)
    val refreshes = Channel<Unit>(Channel.CONFLATED)
    val pinWrites = Channel<List<String>>(Channel.CONFLATED)
    fun refresh() { if (!refreshing) refreshes.trySend(Unit) }
    fun togglePin(identity: String) {
        pins = toggleDrawerPin(pins, identity)
        pinWrites.trySend(pins)
    }
}

@Composable
internal fun rememberDrawerApps(): DrawerCatalog {
    val context = LocalContext.current
    val catalog = remember { DrawerCatalog() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(catalog) {
        val preferences = withContext(Dispatchers.IO) { context.getSharedPreferences("drawer_pins", Context.MODE_PRIVATE) }
        catalog.pins = withContext(Dispatchers.IO) {
            try {
                val array = JSONArray(preferences.getString("components_v1", "[]"))
                (0 until array.length()).map { array.getString(it) }
                    .filter { ComponentName.unflattenFromString(it) != null }.distinct().take(6)
            } catch (_: Exception) { emptyList() }
        }
        catalog.pinsLoaded = true
        for (pins in catalog.pinWrites) withContext(Dispatchers.IO) {
            preferences.edit().putString("components_v1", JSONArray(pins).toString()).commit()
        }
    }
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    val packageName = intent.data?.schemeSpecificPart
                    catalog.pins = catalog.pins.filterNot { ComponentName.unflattenFromString(it)?.packageName == packageName }
                    if (catalog.pinsLoaded) catalog.pinWrites.trySend(catalog.pins)
                }
                catalog.refreshes.trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) catalog.refreshes.trySend(Unit)
        }
        lifecycle.addObserver(observer)
        onDispose { context.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(catalog) {
        catalog.refreshes.trySend(Unit)
        for (request in catalog.refreshes) {
            catalog.refreshing = true
            try { catalog.apps = withContext(Dispatchers.IO) { loadDrawerApps(context) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: RuntimeException) {
                Toast.makeText(context, "Unable to refresh apps. Please try again.", Toast.LENGTH_SHORT).show()
            } finally { catalog.refreshing = false }
        }
    }
    return catalog
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AppDrawer(catalog: DrawerCatalog?, onPopupChanged: (Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var options by remember { mutableStateOf<DrawerApp?>(null) }
    val apps = catalog?.apps
    val pins = catalog?.pins.orEmpty()
    val pinned = pins.mapNotNull { id -> apps?.find { it.component.flattenToString() == id } }
    val rows = drawerPinnedRows(pinned.size)
    val pages = drawerPageCount((apps?.size ?: 0) + rows * 3)
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    val searchScroll = rememberLazyListState()
    val popup = search || options != null
    DisposableEffect(popup) { onPopupChanged(popup); onDispose { onPopupChanged(false) } }
    fun closeSearch() { keyboard?.hide(); query = ""; search = false }
    fun back() { if (options != null) options = null else if (search) closeSearch() else onClose() }
    BackHandler(onBack = ::back)
    LaunchedEffect(pages) { if (pager.currentPage >= pages) pager.scrollToPage(pages - 1) }
    fun launch(intent: Intent) {
        try { context.startActivity(intent); keyboard?.hide(); onClose() }
        catch (_: RuntimeException) { Toast.makeText(context, "Unable to open this app. It may be unavailable or disabled.", Toast.LENGTH_SHORT).show() }
    }
    BoxWithConstraints(Modifier.fillMaxHeight()) {
        val grid = PlayerGrid(maxWidth.value, maxHeight.value)
        val keyboardHeight = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp().value }
        val availableHeight = 1236f - keyboardHeight / grid.scale
        if (!popup) {
            VerticalPager(pager, grid.place(70f, 54f, 860f, 900f)) { page ->
                Box(Modifier.fillMaxSize()) {
                    if (page == 0 && rows > 0) {
                        Box(Modifier.fillMaxWidth().height(((rows * 310f + 2f) * grid.scale).dp)
                            .clip(RoundedCornerShape((56f * grid.scale).dp)).background(Color(0xff4b4b4b)))
                        Row(Modifier.fillMaxWidth().height((62f * grid.scale).dp), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.drawer_pin), null, Modifier.size((30f * grid.scale).dp))
                            Spacer(Modifier.width((16f * grid.scale).dp))
                            PlayerLabel("Pinned", Modifier.height((44f * grid.scale).dp), 28f * grid.scale)
                        }
                        pinned.forEachIndexed { index, app ->
                            DrawerTile(grid, app, index % 3, index / 3, 66f, { launch(Intent.makeMainActivity(app.component)) }) { options = app }
                        }
                    }
                    val range = drawerRegularRange(page, rows, apps?.size ?: 0)
                    apps?.slice(range)?.forEachIndexed { index, app ->
                        DrawerTile(grid, app, index % 3, index / 3 + if (page == 0) rows else 0,
                            if (rows > 0 && page == 0) 40f else 10f,
                            { launch(Intent.makeMainActivity(app.component)) }) { options = app }
                    }
                }
            }
            if (apps.isNullOrEmpty()) PlayerLabel(if (apps == null) "Loading apps…" else "No launchable apps",
                grid.place(70f, 550f, 850f, 80f), 34f * grid.scale)
            if (pages > 1) Column(grid.place(918f, 180f, 70f, 850f), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(pages) { page ->
                    Box(Modifier.weight(1f, fill = false).heightIn(max = (48f * grid.scale).dp).fillMaxWidth()
                        .semantics { contentDescription = "App page ${page + 1} of $pages"; selected = pager.currentPage == page }
                        .clickable(role = Role.Tab) { scope.launch { pager.animateScrollToPage(page) } }, contentAlignment = Alignment.Center) {
                        Box(Modifier.padding((12f * grid.scale).dp).size(((if (pager.currentPage == page) 22f else 17f) * grid.scale).dp)
                            .clip(CircleShape).background(if (pager.currentPage == page) Color.White else Color.Gray))
                    }
                }
            }
            DrawerAsset(grid, R.drawable.drawer_search_apps, "Search apps", 118f, 988f, 78f, 78f) { search = true }
            DrawerAsset(grid, R.drawable.drawer_android_settings_button, "Android Settings", 368f, 991f, 264f, 76f, pressedIcon = R.drawable.drawer_android_settings_button_pressed) { launch(Intent(Settings.ACTION_SETTINGS)) }
            DrawerAsset(grid, R.drawable.drawer_refresh_apps, if (catalog?.refreshing == true) "Refreshing apps" else "Refresh apps",
                804f, 988f, 78f, 78f, catalog?.refreshing != true) { catalog?.refresh() }
            if (catalog?.refreshing == true) PlayerLabel("Refreshing…", grid.place(710f, 1070f, 240f, 40f), 24f * grid.scale)
            DrawerButton(grid, true, 1f, onClose)
        } else if (options == null) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
            val top = if (keyboardHeight > 0f) 150f else 240f
            val bottom = minOf(1100f, availableHeight - 40f).coerceAtLeast(top + 250f)
            Box(grid.place(70f, top, 860f, bottom - top).clip(RoundedCornerShape((56f * grid.scale).dp)).background(Color(0xff333333)))
            BasicTextField(query, { query = it }, grid.place(110f, top + 30f, 780f, 90f).background(Color(0xff555555), RoundedCornerShape((24f * grid.scale).dp))
                .padding((16f * grid.scale).dp).focusRequester(focus).semantics { contentDescription = "App name" },
                singleLine = true, textStyle = TextStyle(color = Color.White, fontFamily = InterFontFamily, fontSize = (34f * grid.scale).sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White), decorationBox = { field ->
                    if (query.isEmpty()) PlayerLabel("Search apps", Modifier.fillMaxSize(), 34f * grid.scale, alignment = Alignment.CenterStart, color = Color.LightGray)
                    field()
                })
            val results = apps.orEmpty().filter { drawerNameMatches(it.label, query) }.distinctBy { it.component }
            LazyColumn(grid.place(110f, top + 150f, 780f, bottom - top - 190f), state = searchScroll) {
                if (results.isEmpty()) item { PlayerLabel("No apps found", Modifier.fillMaxWidth().height((100f * grid.scale).dp), 34f * grid.scale) }
                items(results, key = { it.component.flattenToString() }) { app ->
                    Row(Modifier.fillMaxWidth().height((110f * grid.scale).dp).combinedClickable(role = Role.Button,
                        onClick = { launch(Intent.makeMainActivity(app.component)) }, onLongClick = { keyboard?.hide(); options = app }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Image(app.icon, null, Modifier.size((80f * grid.scale).dp))
                        Spacer(Modifier.width((30f * grid.scale).dp))
                        PlayerLabel(app.label, Modifier.weight(1f).height((80f * grid.scale).dp), 34f * grid.scale, alignment = Alignment.CenterStart, marquee = true)
                    }
                }
            }
            DrawerPill(grid, "Clear", 90f, top - 110f, 380f) { query = "" }
            DrawerPill(grid, "Close", 530f, top - 110f, 380f, action = ::closeSearch)
        } else {
            val app = options!!
            val isPinned = app.component.flattenToString() in pins
            Box(grid.place(120f, 400f, 760f, 338f).clip(RoundedCornerShape((56f * grid.scale).dp)).background(Color(0xff4b4b4b)))
            Image(app.icon, null, grid.place(189f, 455f, 168f, 168f))
            PlayerLabel(app.label, grid.place(140f, 645f, 266f, 50f), 34f * grid.scale, marquee = true)
            DrawerPill(grid, if (isPinned) "Unpin" else "Pin", 408f, 455f, 404f,
                enabled = catalog?.pinsLoaded == true && (isPinned || pins.size < 6), icon = R.drawable.drawer_pin) {
                catalog?.togglePin(app.component.flattenToString()); options = null; closeSearch()
                scope.launch { pager.scrollToPage(0) }
            }
            DrawerPill(grid, "App info", 408f, 545f, 404f, icon = R.drawable.options_info_icon) {
                launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.component.packageName}")))
            }
            if (!isPinned && pins.size >= 6) PlayerLabel("You can pin up to 6 apps.", grid.place(400f, 650f, 450f, 50f), 25f * grid.scale)
            CollectionsButton("Close", grid, 293f, y = 810f) { options = null }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DrawerTile(grid: PlayerGrid, app: DrawerApp, column: Int, row: Int, top: Float, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.offset(((column * 298f + 1f) * grid.scale).dp, ((row * 310f + top) * grid.scale).dp)
        .size((266f * grid.scale).dp, (245f * grid.scale).dp)
        .combinedClickable(role = Role.Button, onClickLabel = "Open ${app.label}", onLongClickLabel = "Options for ${app.label}",
            onClick = onClick, onLongClick = onLongClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon, null, Modifier.size((168f * grid.scale).dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.height((20f * grid.scale).dp))
        PlayerLabel(app.label, Modifier.fillMaxWidth().height((48f * grid.scale).dp), 34f * grid.scale)
    }
}

@Composable
private fun DrawerAsset(grid: PlayerGrid, icon: Int, label: String, x: Float, y: Float, width: Float, height: Float, enabled: Boolean = true, pressedIcon: Int = icon, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Image(painterResource(if (pressed) pressedIcon else icon), label, grid.place(x, y, width, height).alpha(if (enabled) 1f else .4f)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = action))
}

@Composable
private fun DrawerPill(grid: PlayerGrid, label: String, x: Float, y: Float, width: Float, enabled: Boolean = true, icon: Int? = null, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(grid.place(x, y, width, 79f).clip(RoundedCornerShape(percent = 50))
        .background(if (pressed) Color.White else Color(0xff707070))
        .alpha(if (enabled) 1f else .5f)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = action),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (icon == null) Arrangement.Center else Arrangement.Start) {
        if (icon != null) {
            Spacer(Modifier.width((30f * grid.scale).dp))
            Image(painterResource(icon), null, Modifier.size((30f * grid.scale).dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (pressed) Color.Black else Color.White))
            Spacer(Modifier.width((18f * grid.scale).dp))
        }
        PlayerLabel(label, Modifier.height((60f * grid.scale).dp), 34f * grid.scale, bold = true, color = if (pressed) Color.Black else Color.White)
    }
}

@Composable
internal fun DrawerButton(grid: PlayerGrid, open: Boolean, opacity: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(grid.touchTarget(500f, 1158f, 86f).then(Modifier.semantics { contentDescription = if (open) "Close app drawer" else "App drawer" })
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        val drawable = when {
            open && pressed -> R.drawable.drawer_app_drawer_back_button_pressed
            open -> R.drawable.drawer_app_drawer_back_button
            pressed -> R.drawable.drawer_app_drawer_button_pressed
            else -> R.drawable.drawer_app_drawer_button
        }
        Image(painterResource(drawable), null,
            Modifier.size(((if (open) 54f else 45f) * grid.scale).dp).alpha(opacity))
    }
}

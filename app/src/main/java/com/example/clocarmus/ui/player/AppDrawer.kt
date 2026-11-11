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
import com.example.clocarmus.ClocarmusApplication
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

@Composable
internal fun rememberDrawerApps(): List<DrawerApp>? {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<DrawerApp>?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { revision++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        // System-only package broadcasts; the legacy overload supports API 23.
        context.registerReceiver(receiver, filter)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { context.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(revision) { apps = withContext(Dispatchers.IO) { loadDrawerApps(context) } }
    return apps
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AppDrawer(apps: List<DrawerApp>?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val application = context.applicationContext as ClocarmusApplication
    val pages = drawerPageCount(apps?.size ?: 0)
    val pager = rememberPagerState(initialPage = clampDrawerPage(application.drawerPage, apps?.size ?: 0)) { pages }
    val scope = rememberCoroutineScope()
    // Wait for discovery before clamping the retained session page.
    LaunchedEffect(apps) {
        if (apps != null) pager.scrollToPage(clampDrawerPage(application.drawerPage, apps.size))
    }
    LaunchedEffect(pager, apps != null) {
        if (apps != null) snapshotFlow { pager.settledPage }.collect { application.drawerPage = it }
    }
    fun launch(intent: Intent) {
        try { context.startActivity(intent); onClose() }
        catch (_: RuntimeException) { Toast.makeText(context, "Unable to open this app. It may be unavailable or disabled.", Toast.LENGTH_SHORT).show() }
    }
    BoxWithConstraints(Modifier.fillMaxHeight()) {
        val grid = PlayerGrid(maxWidth.value, maxHeight.value)
        Box(grid.place(322f, 42f, 356f, 101f).clickable(role = Role.Button) { launch(Intent(Settings.ACTION_SETTINGS)) }) {
            Image(painterResource(R.drawable.drawer_android_settings_button), "Android Settings", Modifier.fillMaxSize())
        }
        VerticalPager(pager, Modifier.then(grid.place(70f, 178f, 850f, 870f))) { page ->
            Box(Modifier.fillMaxSize()) {
                apps?.drop(page * 9)?.take(9)?.forEachIndexed { index, app ->
                    val column = index % 3
                    val row = index / 3
                    Column(Modifier.offset((column * 298f * grid.scale).dp, (row * 310f * grid.scale).dp)
                        .size((266f * grid.scale).dp, (255f * grid.scale).dp)
                        .clickable(role = Role.Button, onClickLabel = "Open ${app.label}") {
                            launch(Intent.makeMainActivity(app.component))
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(app.icon, null, Modifier.size((168f * grid.scale).dp), contentScale = ContentScale.Fit)
                        Spacer(Modifier.height((20f * grid.scale).dp))
                        PlayerLabel(app.label, Modifier.fillMaxWidth().height((48f * grid.scale).dp), 34f * grid.scale)
                    }
                }
            }
        }
        if (apps == null || apps.isEmpty()) PlayerLabel(if (apps == null) "Loading apps…" else "No launchable apps",
            grid.place(70f, 550f, 850f, 80f), 34f * grid.scale)
        if (pages > 1) Column(grid.place(918f, 180f, 70f, 850f), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            // Weight the hit areas so every page remains reachable even with a large app list.
            repeat(pages) { page ->
                Box(Modifier.weight(1f, fill = false).heightIn(max = (48f * grid.scale).dp).fillMaxWidth()
                    .semantics { contentDescription = "App page ${page + 1} of $pages"; selected = pager.currentPage == page }
                    .clickable(role = Role.Tab) { scope.launch { pager.animateScrollToPage(page) } }, contentAlignment = Alignment.Center) {
                    Box(Modifier.padding((12f * grid.scale).dp).size(((if (pager.currentPage == page) 22f else 17f) * grid.scale).dp)
                        .clip(CircleShape).background(if (pager.currentPage == page) Color.White else Color.Gray))
                }
            }
        }
        DrawerButton(grid, true, 1f, onClose)
    }
}

@Composable
internal fun DrawerButton(grid: PlayerGrid, open: Boolean, opacity: Float, onClick: () -> Unit) {
    Box(grid.touchTarget(500f, 1158f, 86f).then(Modifier.semantics { contentDescription = if (open) "Close app drawer" else "App drawer" })
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(if (open) R.drawable.drawer_back_button else R.drawable.drawer_app_drawer_button), null,
            Modifier.size(((if (open) 54f else 45f) * grid.scale).dp).alpha(opacity))
    }
}

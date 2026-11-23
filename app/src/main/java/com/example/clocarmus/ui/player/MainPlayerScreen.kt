package com.example.clocarmus.ui.player

import com.example.clocarmus.library.trackFilePath
import com.example.clocarmus.playback.shuffle.ShuffleCollection

import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

import android.content.Context
import android.widget.Toast
import android.media.AudioManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clocarmus.R
import com.example.clocarmus.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A uniformly scaled reference grid, centered within each half of the landscape display. */
internal class PlayerGrid(width: Float, height: Float) {
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

/** Normal transport observes the process playback session; reference previews use local fixtures. */
@Composable
internal fun MainPlayerScreen(referencePreview: Boolean = false, menuPreview: Boolean = false, tracksPreview: Boolean = false, artistsPreview: String? = null, albumsPreview: String? = null, playlistsPreview: String? = null, genresPreview: String? = null, collectionsPreview: String? = null, foldersPreview: String? = null, queuePreview: Boolean = false, settingsPreview: String? = null, initialPlaylists: PlaylistLibrary? = null, onPlaylistsChanged: (PlaylistLibrary) -> Unit = {}, freshStartup: Boolean = false) {
    val context = LocalContext.current
    val fixture = referencePreview || menuPreview || tracksPreview || artistsPreview != null || albumsPreview != null || playlistsPreview != null || genresPreview != null || collectionsPreview != null || foldersPreview != null || settingsPreview != null || queuePreview
    val repository = remember { (context.applicationContext as com.example.clocarmus.ClocarmusApplication).library }
    val librarySnapshot by repository.state.collectAsState()
    LaunchedEffect(fixture) { if (!fixture) repository.refresh() }
    val activity = context as? com.example.clocarmus.MainActivity
    val launcher = activity?.launcher
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var drawerOpening by remember { mutableIntStateOf(0) }
    val drawerApps = if (launcher?.enabled == true) rememberDrawerApps() else null
    LaunchedEffect(launcher?.enabled) { if (launcher?.enabled != true) drawerOpen = false }
    val albumArtPreview = referencePreview && activity?.intent?.getBooleanExtra("album_art_preview", false) == true
    val settingsPreferences = remember { context.getSharedPreferences("player-settings", Context.MODE_PRIVATE) }
    var settings by remember {
        val initial = if (referencePreview) PlayerSettings(sources = if (settingsPreview == "settings-folders") setOf("music") else setOf("music", "oriental", "mix")) else loadPlayerSettings(settingsPreferences)
        mutableStateOf((when {
            albumArtPreview -> initial.selectBackgroundType(BackgroundType.ALBUM_ART)
            settingsPreview == "settings-background-gradient" -> initial.selectBackground(PlayerBackground.GRADIENT_DEFAULT)
            else -> initial
        }).copy(clock = when (settingsPreview) {
            "settings-clock-vertical" -> ClockStyle.VERTICAL
            "settings-clock-lcd" -> ClockStyle.SEGMENTED
            "settings-clock-dotmatrix" -> ClockStyle.DOT_MATRIX
            else -> initial.clock
        }, divider = when (settingsPreview) {
            "settings-divider-dotted" -> DividerStyle.DOTTED
            "settings-divider-squiggly" -> DividerStyle.SQUIGGLY
            "settings-divider-beam" -> DividerStyle.BEAM
            else -> initial.divider
        }))
    }
    val settingsScope = rememberCoroutineScope()
    var settingsBusy by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    val updateSettings: (PlayerSettings) -> Unit = { next ->
        if (!settingsBusy) {
            val previous = settings
            settings = next
            if (!referencePreview) {
                settingsBusy = true
                settingsScope.launch {
                    try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { savePlayerSettings(settingsPreferences, next) } }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (exception: Exception) {
                        settings = previous
                        // A failed commit can still update SharedPreferences' memory cache.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { savePlayerSettings(settingsPreferences, previous) }
                        }
                        settingsError = exception.message ?: "Could not save settings. Please try again."
                    }
                    finally { settingsBusy = false }
                }
            }
        }
    }
    settingsError?.let { message -> androidx.compose.material3.AlertDialog(
        onDismissRequest = { settingsError = null }, title = { androidx.compose.material3.Text("Could not save settings") },
        text = { androidx.compose.material3.Text(message) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { settingsError = null }) { androidx.compose.material3.Text("OK") } }) }
    SideEffect { (context as? com.example.clocarmus.MainActivity)?.setStatusBarVisible(settings.statusBar) }
    val settingsFolderTree = if (fixture) remember(settings.sources, settings.exclusions) { settings.folderTree(demoFolderTree) } else librarySnapshot.folders
    val playlistPreferences = remember { context.getSharedPreferences("demo-playlists", Context.MODE_PRIVATE) }
    var previewPlaylistLibrary by remember { mutableStateOf(if (collectionsPreview != null) collectionsFixture(collectionsPreview) else if (playlistsPreview != null) playlistFixture(playlistsPreview) else initialPlaylists ?: loadPlaylists(playlistPreferences)) }
    var pendingPlaylistLibrary by remember { mutableStateOf<PlaylistLibrary?>(null) }
    val playlistWriteMutex = remember { kotlinx.coroutines.sync.Mutex() }
    val playlistLibrary = if (fixture) previewPlaylistLibrary else pendingPlaylistLibrary ?: librarySnapshot.playlists
    val updatePlaylists: (PlaylistLibrary) -> Unit = { next ->
        if (fixture) previewPlaylistLibrary = next
        else {
            pendingPlaylistLibrary = next
            // Acquire in UI command order; finish accepted writes across activity recreation.
            settingsScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    playlistWriteMutex.withLock {
                        try { repository.editPlaylists { current -> next.copy(imported = current.imported) } }
                        catch (exception: Exception) { settingsError = exception.message ?: "Could not save playlists" }
                        finally { if (pendingPlaylistLibrary == next) pendingPlaylistLibrary = null }
                    }
                }
            }
        }
    }
    val resetPending = remember { !referencePreview && com.example.clocarmus.ui.startup.loadOnboarding(context.getSharedPreferences("onboarding", 0)).resetPending }
    val playlistTracks = if (!fixture) librarySnapshot.tracks else remember { (demoTracks + demoLibraryTracks + genreReferenceTracks + playlistFixtureTracks + demoPlayerTrack + demoFolderTree.entries.mapNotNull { it.track }).distinctBy { it.id } }
    LaunchedEffect(Unit) {
        if (fixture && playlistsPreview == null && collectionsPreview == null && !resetPending && !settingsPreferences.getBoolean("demoLibraryRemoved", false)) {
            var next = playlistLibrary
            playlistTracks.filterNot { it.id.startsWith("playlist:") }.forEachIndexed { i, track -> next = next.imported(track.id, i.toLong()) }
            updatePlaylists(next)
        }
    }
    val recordDemoStart: (TrackMenuEntry) -> Unit = { if (fixture) updatePlaylists(playlistLibrary.playbackStarted(it, System.currentTimeMillis())) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(if (referencePreview) .62f else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(audio, lifecycle, referencePreview) {
        val syncVolume = {
            if (!referencePreview) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { syncVolume() }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { syncVolume(); if (!fixture) repository.refresh() }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        lifecycle.addObserver(lifecycleObserver)
        syncVolume()
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    val playback = remember { if (fixture) null else (context.applicationContext as com.example.clocarmus.ClocarmusApplication).playback }
    var playing by if (fixture) remember { mutableStateOf(referencePreview) } else playback!!.playingState
    var position by if (fixture) remember { mutableStateOf(113f) } else playback!!.positionState
    var shuffle by if (fixture) remember { mutableStateOf(true) } else playback!!.shuffleState
    var repeat by if (fixture) remember { mutableStateOf(0) } else playback!!.repeatState
    var menuOpen by rememberSaveable { mutableStateOf(settingsPreview != null || queuePreview || menuPreview || tracksPreview || artistsPreview != null || albumsPreview != null || playlistsPreview != null || genresPreview != null || foldersPreview != null) }
    var queueOpen by rememberSaveable { mutableStateOf(queuePreview) }
    val queueScroll = rememberLazyListState()
    var tracksOpen by rememberSaveable { mutableStateOf(tracksPreview) }
    var browserStack by rememberSaveable { mutableStateOf(arrayListOf<BrowserDestination>().apply {
        if (settingsPreview != null) add(BrowserDestination("settings"))
        if (foldersPreview != null) add(BrowserDestination("folders", directory = if (foldersPreview == "folder-directory") "music" else null))
        if (artistsPreview != null) {
            add(BrowserDestination("artists", artist = "Ripple"))
            if (artistsPreview != "artists") add(BrowserDestination("detail", artist = "Ripple"))
            if (artistsPreview == "artist-tracks" || artistsPreview == "artist-albums")
                add(BrowserDestination(if (artistsPreview == "artist-tracks") "tracks" else "albums", artist = "Ripple"))
        }
        if (playlistsPreview != null) {
            add(BrowserDestination("playlist-list"))
            val id = when (playlistsPreview) {
                "playlist-favorites", "playlist-favorites-empty" -> FAVORITES
                "playlist-added", "playlist-added-empty" -> RECENT_ADDED
                "playlist-played", "playlist-played-empty" -> RECENT_PLAYED
                "playlist-custom" -> "demo:trip"
                "playlist-custom-empty" -> "demo:empty"
                else -> null
            }
            if (id != null) add(BrowserDestination("playlist-detail", playlistId = id))
        }
        if (genresPreview != null) {
            add(BrowserDestination("genre-list"))
            if (genresPreview != "genres") add(BrowserDestination("genre-options", genre = "alternative rock"))
            if (genresPreview in setOf("genre-tracks", "genre-artists", "genre-albums"))
                add(BrowserDestination(genresPreview, genre = "alternative rock"))
        }
        if (albumsPreview != null) {
            add(BrowserDestination("album-list"))
            if (albumsPreview != "albums") add(BrowserDestination("album-options", album = AlbumIdentity("Saffron", "Artist")))
            if (albumsPreview == "album-tracks") add(BrowserDestination("album-tracks", album = AlbumIdentity("Saffron", "Artist")))
        }
    }) }
    val navigate: (BrowserDestination) -> Unit = { browserStack = ArrayList(browserStack + it) }
    val artistStates = rememberSaveableStateHolder()
    val initialSelectedId = if (foldersPreview == "folder-directory") "folder:alley" else if (genresPreview == "genre-tracks") "genre:track:2" else if (artistsPreview == "artist-tracks") "artist:Ripple:18" else if (albumsPreview == "album-tracks") "album:Saffron:3" else if (playlistsPreview == "playlist-favorites") "playlist:Love" else if (playlistsPreview in setOf("playlist-added", "playlist-custom")) "playlist:Ripple" else demoPlayerTrack.id
    var queue by if (!fixture) playback!!.queueState else remember { mutableStateOf(if (resetPending) TrackQueue() else if (queuePreview) TrackQueue.fromTracks(demoTracks.map { it.id }, "Pebble") else TrackQueue.fromTracks(
        listOf(initialSelectedId) + demoTracks.map { it.id }.filterNot { it == initialSelectedId }, initialSelectedId, shuffle)) }
    val selectedTrackId = queue.current?.trackId
    val selectedTrack = playlistTracks.firstOrNull { it.id == selectedTrackId }?.let { track ->
        // Explicit debug fixture, separate from the embedded-only runtime artwork path.
        if (albumArtPreview && track.id == demoPlayerTrack.id) {
            track.copy(artworkPath = java.io.File(context.filesDir, "reference-album-art.jpg").absolutePath)
        } else track
    }
    val albumArtBackground = rememberAlbumArtBackground(
        settings.backgroundType == BackgroundType.ALBUM_ART, selectedTrackId, selectedTrack?.artworkPath)
    LaunchedEffect(librarySnapshot.version, librarySnapshot.ready, selectedTrackId) {
        if (!fixture && librarySnapshot.ready && selectedTrackId != null) {
            if (selectedTrack == null) { playing = false }
            else if (!selectedTrack.available) playing = false
        }
    }
    val selectedTitle = selectedTrack?.title
    val duration = selectedTrack?.durationSeconds?.takeIf { it > 0 }?.toFloat()
    var seekPreview by remember(queue.currentId, duration) { mutableStateOf<Float?>(null) }
    val displayedPosition = seekPreview?.let { it * (duration ?: 0f) } ?: position
    val seekable = selectedTrack?.available == true && duration != null
    var trackQuery by rememberSaveable { mutableStateOf("") }
    val trackScroll = rememberScrollState()
    val allTracks = if (fixture) demoTracks else playlistTracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    val visibleTracks = remember(trackQuery, allTracks) { allTracks.filter { it.title.contains(trackQuery, ignoreCase = true) } }
    LaunchedEffect(trackQuery) { trackScroll.scrollTo(0) }
    val available: (String) -> Boolean = { id -> playlistTracks.any { it.id == id && it.available } }
    LaunchedEffect(Unit) {
        // A fresh process follows autoplay (off by default); recreation retains transport state.
        if (fixture && freshStartup && !referencePreview) playing = settings.autoplay && selectedTrack?.available == true
    }
    val stepTrack: (Int) -> Unit = { direction ->
        if (!fixture) playback!!.step(direction) else queue = queue.step(direction, available)
        if (playing) queue.current?.trackId?.let { id -> playlistTracks.firstOrNull { it.id == id }?.let(recordDemoStart) }
        if (fixture) position = 0f
    }
    LaunchedEffect(playback?.notice) {
        playback?.notice?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            playback.dismissNotice()
        }
    }
    if (!fixture) playback!!.warning?.let { message ->
        androidx.compose.material3.AlertDialog(onDismissRequest = playback::dismissWarning,
            text = { androidx.compose.material3.Text(message) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = playback::dismissWarning) { androidx.compose.material3.Text("OK") } })
    }
    LaunchedEffect(playing, queue.currentId, duration, repeat, referencePreview) {
        if (fixture && playing && !referencePreview && duration != null) {
            while (true) {
                delay(1000)
                position = (position + 1f).coerceAtMost(duration)
                if (position >= duration) {
                    val completion = queue.complete(repeat, available)
                    queue = completion.queue
                    playing = completion.playing
                    if (playing) {
                        position = 0f
                        playlistTracks.firstOrNull { it.id == queue.current?.trackId }?.let(recordDemoStart)
                    }
                    else break
                }
            }
        }
    }
    var optionsOccurrence by rememberSaveable { mutableStateOf<String?>(null) }
    var optionsTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var infoRequest by rememberSaveable { mutableStateOf<TrackInfoRequest?>(null) }
    var optionsCollections by rememberSaveable { mutableStateOf(false) }
    var optionsCompleted by rememberSaveable { mutableIntStateOf(0) }
    var collectionsTarget by remember { mutableStateOf(if (collectionsPreview != null && collectionsPreview != "collections-member") selectedTrackId else null) }
    var collectionNaming by remember { mutableStateOf(false) }
    var collectionDraft by remember { mutableStateOf("") }
    val collectionsOpen = optionsCollections || (collectionsTarget != null && collectionsTarget == selectedTrackId && !menuOpen)
    val membershipTarget = if (optionsCollections) optionsTarget else collectionsTarget
    val closeCollections: () -> Unit = {
        if (optionsCollections) optionsCollections = false else collectionsTarget = null
        collectionNaming = false; collectionDraft = ""
    }
    LaunchedEffect(selectedTrackId, menuOpen) {
        if (!optionsCollections && (collectionsTarget != selectedTrackId || menuOpen)) {
            collectionsTarget = null; collectionNaming = false; collectionDraft = ""
        }
    }
    BackHandler(enabled = collectionsOpen && optionsTarget == null) {
        if (collectionNaming) { collectionNaming = false; collectionDraft = "" } else closeCollections()
    }
    var dialog by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchOwner by remember { mutableStateOf<PlayerPaneDestination?>(null) }
    var playlistEditing by remember { mutableStateOf(false) }
    var editingOwner by remember { mutableStateOf<PlayerPaneDestination?>(null) }
    val browsingReturn = rememberBrowsingReturnInactivity(menuOpen, suspended = dialog != null || searchOpen || playlistEditing || optionsTarget != null || infoRequest != null) {
        queueOpen = false
        tracksOpen = false
        browserStack = arrayListOf()
        menuOpen = false
    }
    LaunchedEffect(queueOpen, optionsTarget, infoRequest) {
        if (queueOpen && optionsTarget == null && infoRequest == null) browsingReturn.trackSelected()
    }
    LaunchedEffect(trackScroll, browsingReturn) {
        snapshotFlow { tracksOpen && browserStack.isEmpty() && trackScroll.isScrollInProgress }.collect { browsingReturn.setScrolling(it) }
    }
    LaunchedEffect(trackScroll, browsingReturn) {
        snapshotFlow { trackScroll.value }.collect { browsingReturn.interaction() }
    }
    val goBack: () -> Unit = {
        browsingReturn.interaction()
        when {
            queueOpen -> { queueOpen = false; menuOpen = false; browsingReturn.cancel() }
            browserStack.isNotEmpty() -> browserStack = ArrayList(browserStack.dropLast(1))
            tracksOpen -> tracksOpen = false
            else -> { browsingReturn.cancel(); menuOpen = false }
        }
    }
    BackHandler(enabled = menuOpen && optionsTarget == null && infoRequest == null, onBack = goBack)
    val selectTrack: (String, List<TrackMenuEntry>, ShuffleCollection) -> Unit = { id, tracks, source ->
        val playable = playableTrack(tracks, id)
        if (playable != null) {
            if (!fixture) playback!!.selectCollection(tracks.map { it.id }, playable.id, source)
            else {
                queue = TrackQueue.fromTracks(tracks.map { it.id }, playable.id, shuffle)
                position = 0f
                playing = true
            }
            recordDemoStart(playable); browsingReturn.trackSelected()
        } else Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
    }

    val sliderPointers = remember { SliderPointers() }
    val inactivity = rememberMainPlayerInactivity(enabled = !drawerOpen && !settings.disableFade && !menuOpen && dialog == null && !collectionsOpen && infoRequest == null && !referencePreview, pointers = sliderPointers)
    val drawerTimeout = rememberBrowsingReturnInactivity(drawerOpen, suspended = false, pointers = sliderPointers) { drawerOpen = false }
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerTimeout.trackSelected() }
    val controlOpacity = if (dialog == null && !collectionsOpen && infoRequest == null && !referencePreview) inactivity.opacity else 1f

    val closeOptions: () -> Unit = {
        when {
            collectionNaming -> { collectionNaming = false; collectionDraft = "" }
            infoRequest != null -> {
                if (infoRequest?.returnTo == TrackInfoDestination.MAIN_PLAYER) optionsTarget = null
                infoRequest = null
            }
            optionsCollections -> optionsCollections = false
            else -> { optionsTarget = null; optionsOccurrence = null }
        }
    }
    val trackOptionsEntry = TrackOptionsEntry({ id ->
        browsingReturn.cancel()
        optionsOccurrence = null
        optionsTarget = id; optionsCollections = false; infoRequest = null
    }, optionsTarget != null, optionsCompleted)
    val returnHome by rememberUpdatedState<() -> Unit> {
        browsingReturn.cancel()
        drawerOpen = false
        collectionNaming = false
        collectionDraft = ""
        optionsCollections = false
        infoRequest = null
        optionsTarget = null
        optionsOccurrence = null
        queueOpen = false
        collectionsTarget = null
        dialog = null
        searchOpen = false
        playlistEditing = false
        browserStack = arrayListOf()
        tracksOpen = false
        menuOpen = false
    }
    val homeNavigation = remember { PlayerHomeNavigation { returnHome() } }
    val paneDestination = when {
        queueOpen -> PlayerPaneDestination(PlayerPaneRoot.QUEUE)
        browserStack.isNotEmpty() -> PlayerPaneDestination(PlayerPaneRoot.BROWSER, browser = browserStack.last())
        tracksOpen -> PlayerPaneDestination(PlayerPaneRoot.TRACKS)
        menuOpen -> PlayerPaneDestination(PlayerPaneRoot.MENU)
        collectionsOpen && !optionsCollections -> PlayerPaneDestination(PlayerPaneRoot.COLLECTIONS, collectionTrack = membershipTarget)
        else -> PlayerPaneDestination(PlayerPaneRoot.PLAYER)
    }
    val overlayDestination = infoRequest?.let { PlayerOverlayDestination(it.trackId, info = it) }
        ?: optionsTarget?.let { PlayerOverlayDestination(it, collections = optionsCollections, occurrence = optionsOccurrence) }
    val sourceOpacity by animateFloatAsState(if (overlayDestination == null) 1f else 0f,
        tween(PLAYER_PANE_FADE_DURATION_MS), label = "Source behind track panel")
    LaunchedEffect(activity?.homeActivation) {
        if ((activity?.homeActivation ?: 0) > 0) homeNavigation.returnToPlayer()
    }
    var backgroundDropdown by remember { mutableStateOf<BackgroundDropdown?>(null) }
    val backgroundDropdownHost = remember { { next: BackgroundDropdown? -> backgroundDropdown = next } }
    CompositionLocalProvider(LocalTrackOptions provides trackOptionsEntry, LocalPlayerHome provides homeNavigation,
        LocalBackgroundDropdownHost provides backgroundDropdownHost,
        LocalLibraryTracks provides if (fixture) demoLibraryTracks else playlistTracks) {
        Box(Modifier.fillMaxSize().background(
            if (settings.backgroundType == BackgroundType.ALBUM_ART) settings.solidBackground.color else settings.background.color
        ).observePlayerTouches(inactivity, sliderPointers)
            .observeBrowsingTouches(browsingReturn).observeBrowsingTouches(drawerTimeout).onPreviewKeyEvent { browsingReturn.interaction(); drawerTimeout.interaction(); false }) {
            settings.background.gradientAsset?.let { asset ->
                Image(painterResource(asset), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            }
            if (settings.backgroundType == BackgroundType.ALBUM_ART) {
                AlbumArtBackground(albumArtBackground,
                    visible = paneDestination.root in setOf(PlayerPaneRoot.PLAYER, PlayerPaneRoot.COLLECTIONS))
            }
            PlayerPaneLayout(settings.rightHandDrive,
                if (backgroundDropdown != null) Modifier.clearAndSetSemantics {} else Modifier) {
                BoxWithConstraints(Modifier.fillMaxHeight()) {
                    val grid = PlayerGrid(maxWidth.value, maxHeight.value)
                    PlayerSlider(volume, {
                        browsingReturn.interaction()
                        volume = it
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).toInt(), 0)
                    }, grid.place(283f, 61f, 434f, 83f), "Media volume", grid.scale, volume = true, pointers = sliderPointers)
                    Box(Modifier.fillMaxSize().alpha(sourceOpacity)
                        .then(if (overlayDestination != null) Modifier.clearAndSetSemantics {} else Modifier)) {
                        PlayerPaneCrossfade(paneDestination) { displayed ->
                            val reportSearch: (Boolean) -> Unit = { visible ->
                                if (visible) { searchOwner = displayed; searchOpen = true }
                                else if (searchOwner == displayed) { searchOwner = null; searchOpen = false }
                            }
                            val reportEditing: (Boolean) -> Unit = { editing ->
                                if (editing) { editingOwner = displayed; playlistEditing = true }
                                else if (editingOwner == displayed) { editingOwner = null; playlistEditing = false }
                            }
                            Box(Modifier.fillMaxSize()) {
                                if (displayed.root == PlayerPaneRoot.QUEUE) {
                                    QueuePane(grid, queue, playlistTracks, playing, queueScroll, browsingReturn,
                                        interactive = optionsTarget == null && infoRequest == null, onBack = goBack,
                                        onSelect = { id ->
                                            val occurrence = queue.entries.firstOrNull { it.id == id }
                                            if (!fixture) playback!!.selectOccurrence(id)
                                            else if (occurrence != null && available(occurrence.trackId)) {
                                                if (id != queue.currentId || queue.resumeAfterStop) position = 0f
                                                queue = queue.select(id); playing = true
                                                playlistTracks.firstOrNull { it.id == occurrence.trackId }?.let(recordDemoStart)
                                            }
                                        }, onOptions = { entry ->
                                            browsingReturn.cancel(); optionsOccurrence = entry.id; optionsTarget = entry.trackId
                                        }, onReorder = { original, id, index -> queue = queue.reorder(original, id, index) })
                                } else if (displayed.root == PlayerPaneRoot.BROWSER) {
                                    val destination = displayed.browser!!
                                    artistStates.SaveableStateProvider(destination.copy(search = false)) {
                                        val playCollection: (List<TrackMenuEntry>, ShuffleCollection) -> Unit = { tracks, source ->
                                            shuffle = false
                                            val first = tracks.firstOrNull { it.available }
                                            if (!fixture && first != null) playback!!.selectCollection(tracks.map { it.id }, first.id, source)
                                            else {
                                                queue = TrackQueue.fromTracks(tracks.map { it.id }, first?.id)
                                                playing = first != null
                                                position = 0f
                                            }
                                            if (first != null) {
                                                recordDemoStart(first)
                                                browsingReturn.cancel()
                                                browserStack = arrayListOf()
                                                tracksOpen = false
                                                menuOpen = false
                                            }
                                        }
                                        if (destination.page == "settings") {
                                            SettingsPane(grid, settings, updateSettings, goBack, settingsPreview, onLibrariesRemoved = {
                                                playing = false
                                                position = 0f
                                                queue = TrackQueue()
                                                // Stop transient state before awaiting durable reset; startup can replay the journal.
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    com.example.clocarmus.ui.startup.finishPendingLibraryReset(context)
                                                }
                                                settings = settings.copy(sources = emptySet(), exclusions = emptySet())
                                                playing = false
                                                position = 0f
                                                queue = TrackQueue()
                                                updatePlaylists(playlistLibrary.withoutMusic())
                                            })
                                        } else if (destination.page == "folders") {
                                            FoldersPane(grid, destination.directory, foldersPreview, selectedTrackId, playing, browsingReturn,
                                                reportSearch, goBack,
                                                { directory -> browserStack = ArrayList(browserStack.dropLast(1) + BrowserDestination("folders", directory = directory)) },
                                                selectTrack, tree = settingsFolderTree, onSourceLost = { sourceId ->
                                                    if (sourceId != null && selectedTrackId?.let { demoFolderTree.source(it)?.id } == sourceId) playing = false
                                                })
                                        } else if (destination.page.startsWith("genre-")) {
                                            GenresPane(grid, destination, genresPreview, selectedTrackId, playing, browsingReturn,
                                                reportSearch, goBack, navigate, selectTrack, playCollection)
                                        } else if (destination.page.startsWith("playlist-")) {
                                            PlaylistsPane(grid, destination.playlistId, playlistLibrary, playlistTracks, selectedTrackId, playing,
                                                browsingReturn, updatePlaylists, goBack,
                                                { navigate(BrowserDestination("playlist-detail", playlistId = it)) }, selectTrack,
                                                reportEditing)
                                        } else if (destination.page.startsWith("album-")) {
                                            AlbumsPane(grid, destination, albumsPreview, selectedTrackId, playing, browsingReturn,
                                                reportSearch, goBack, navigate, selectTrack, playCollection)
                                        } else {
                                            ArtistsPane(grid, destination.page, destination.artist, artistsPreview, selectedTrackId, playing,
                                                browsingReturn, onSearchVisibilityChanged = reportSearch, onBack = goBack,
                                                onArtist = { navigate(BrowserDestination("detail", artist = it, genre = destination.genre)) },
                                                onPage = { navigate(destination.copy(page = it)) },
                                                onSelectTrack = selectTrack, onPlayAll = playCollection, scope = destination.scope, genre = destination.genre,
                                                onAlbum = { navigate(BrowserDestination("album-options", album = it)) })
                                        }
                                    }
                                } else if (displayed.root == PlayerPaneRoot.TRACKS) {
                                    TrackMenuPane(grid, visibleTracks, selectedTrackId, playing, trackScroll, trackQuery,
                                        onQuery = { trackQuery = it },
                                        onSearchVisibilityChanged = reportSearch,
                                        onSearchEdited = { browsingReturn.cancel() },
                                        onBack = goBack, onSelect = { selectTrack(it, visibleTracks, ShuffleCollection(kind = "all", query = trackQuery)) },
                                        emptyMessage = if (!fixture && librarySnapshot.summary.running) "Scanning music…" else if (!fixture && allTracks.isEmpty()) "Add music folders in Settings" else "No matching tracks")
                                }
                                if (displayed.root == PlayerPaneRoot.MENU) {
                                    MenuPane(grid, onBack = { browsingReturn.cancel(); menuOpen = false }, onDestination = {
                                        browsingReturn.interaction()
                                        if (it == "All tracks") tracksOpen = true
                                        else if (it == "Settings") { browsingReturn.cancel(); navigate(BrowserDestination("settings")) }
                                        else if (it == "Folders") navigate(BrowserDestination("folders"))
                                        else if (it == "Artists") navigate(BrowserDestination("artists"))
                                        else if (it == "Genres") navigate(BrowserDestination("genre-list"))
                                        else if (it == "Albums") navigate(BrowserDestination("album-list"))
                                        else if (it == "Playlists") navigate(BrowserDestination("playlist-list"))
                                        else {
                                            dialog = it
                                        }
                                    })
                                }
                                if (displayed.root == PlayerPaneRoot.PLAYER || displayed.root == PlayerPaneRoot.COLLECTIONS) {
                                    MenuButton(grid, controlOpacity) { closeCollections(); menuOpen = true }
                                }
                            }
                            if (displayed.root == PlayerPaneRoot.PLAYER || displayed.root == PlayerPaneRoot.COLLECTIONS) {
                                AssetButton("Manage playlists", when {
                                    collectionsOpen -> R.drawable.collections_playlist_button_toggle_on
                                    selectedTrackId?.let(playlistLibrary::hasMembership) == true -> R.drawable.collections_track_present_in_playlists
                                    else -> R.drawable.collections_playlist_button_untoggled
                                }, grid, 850f, 106f, if (collectionsOpen) 125f else 104f,
                                    state = if (collectionsOpen) "Expanded" else "Collapsed", opacity = controlOpacity,
                                    enabled = selectedTrack != null) {
                                    if (collectionsOpen) closeCollections() else collectionsTarget = selectedTrackId
                                }
                            }
                            if (displayed.root == PlayerPaneRoot.PLAYER) {
                                val artwork by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, selectedTrack?.artworkPath) {
                                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        selectedTrack?.artworkPath?.let { path ->
                                            android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
                                        }
                                    }
                                }
                                if (artwork != null) Image(artwork!!, "Album artwork", grid.place(273f, 187f, 457f, 457f), contentScale = ContentScale.Fit)
                                else Image(painterResource(R.drawable.player_no_album_art), "No album artwork",
                                    grid.place(273f, 187f, 457f, 457f), contentScale = ContentScale.Fit)
                                AssetButton("Shuffle", if (shuffle) R.drawable.player_shuffle_active else R.drawable.player_shuffle_inactive,
                                    grid, 141f, 230f, 90f, if (shuffle) "On" else "Off", opacity = controlOpacity) {
                                    if (!fixture) playback!!.toggleShuffle() else {
                                        shuffle = !shuffle
                                        val head = queue.entries.take(queue.currentIndex + 1)
                                        val tail = queue.entries.drop(queue.currentIndex + 1)
                                        val scheduled = queue.scheduledIds.mapNotNull { id -> tail.find { it.id == id } }
                                        val rest = tail.filterNot { it.id in queue.scheduledIds }
                                        queue = queue.copy(entries = head + scheduled + if (shuffle) rest.shuffled() else rest)
                                    }
                                }
                                AssetButton("Repeat", when (repeat) {
                                    1 -> R.drawable.player_repeat_one_active
                                    2 -> R.drawable.player_repeat_active
                                    else -> R.drawable.player_repeat_inactive
                                }, grid, 846f, 230f, 78.3f, when (repeat) { 1 -> "Repeat one"; 2 -> "Repeat all"; else -> "Off" }, opacity = controlOpacity) { repeat = (repeat + 1) % 3 }
                                AssetButton("Rewind 10 seconds", R.drawable.player_skip_10sec_backward, grid, 141f, 409f, 90f, opacity = controlOpacity) { position = (position - 10).coerceAtLeast(0f) }
                                AssetButton("Forward 10 seconds", R.drawable.player_skip_10sec_forward, grid, 846f, 409f, 90f, opacity = controlOpacity) { position = (position + 10).coerceAtMost(duration ?: 225f) }
                                AssetButton("Queues", R.drawable.player_queue_button, grid, 141f, 624f, 67.5f,
                                    opacity = controlOpacity) { queueOpen = true; menuOpen = true; browsingReturn.trackSelected() }
                                AssetButton("Track info", R.drawable.player_info_icon, grid, 846f, 612f, 67.5f,
                                    opacity = controlOpacity, enabled = selectedTrackId != null) {
                                    browsingReturn.cancel()
                                    infoRequest = TrackInfoRequest(selectedTrackId!!, TrackInfoDestination.MAIN_PLAYER)
                                }
                                PlayerLabel(selectedTitle ?: if (fixture) "Superman" else "No track selected", grid.place(70f, 670f, 860f, 72f), 54f * grid.scale, bold = true, marquee = true)
                                PlayerLabel(if (selectedTitle == null) if (fixture) "Lazlo Bane" else "Unknown artist" else selectedTrack?.artist ?: "Unknown artist", grid.place(70f, 744f, 860f, 57f), 48f * grid.scale, marquee = true)
                                PlayerLabel(if (selectedTitle == null) if (fixture) "All the Time in the World" else "Unknown album" else selectedTrack?.album ?: "Unknown album", grid.place(60f, 802f, 880f, 57f), 48f * grid.scale, marquee = true)
                                val playerYear = if (selectedTitle == null && fixture) "2002"
                                    else com.example.clocarmus.library.releaseYear(selectedTrack?.year, selectedTrack?.tags?.releaseDate)
                                playerYear?.let { PlayerLabel(it, grid.place(70f, 859f, 860f, 57f), 48f * grid.scale) }
                                PlayerSlider(if (duration != null) position / duration else 0f,
                                    { if (duration != null) position = it * duration },
                                    grid.place(79f, 970f, 841f, 87f), "Track position", grid.scale,
                                    enabled = seekable, gestureKey = queue.currentId to duration, pointers = sliderPointers,
                                    onPreview = { seekPreview = it })
                                PlayerLabel(formatPosition(displayedPosition), grid.place(79f, 925f, 180f, 53f), 43f * grid.scale, alignment = Alignment.CenterStart)
                                PlayerLabel(duration?.let(::formatPosition) ?: "--:--", grid.place(740f, 925f, 180f, 53f), 43f * grid.scale, alignment = Alignment.CenterEnd)
                            }
                            if (displayed.root == PlayerPaneRoot.COLLECTIONS) {
                                TrackCollectionsPopup(grid, playlistLibrary, displayed.collectionTrack!!,
                                    onToggle = { id ->
                                        if (playlistTracks.any { it.id == displayed.collectionTrack }) updatePlaylists(playlistLibrary.toggleMembership(id, displayed.collectionTrack, System.currentTimeMillis()))
                                        else Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                                    }, onCreate = {
                                        if (playlistTracks.any { it.id == displayed.collectionTrack }) { collectionDraft = ""; collectionNaming = true }
                                        else Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                                    }, onClose = closeCollections)
                            }
                        }
                    }
                    if (overlayDestination != null) {
                        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} }.clearAndSetSemantics {})
                    }
                    PlayerPaneCrossfade(overlayDestination) { overlay ->
                        overlay?.info?.let { request ->
                            val record = librarySnapshot.byId[request.trackId]
                            val filePath by produceState<String?>(record?.uri, record?.uri, record?.availability) {
                                value = record?.uri
                                record?.let { value = trackFilePath(context.contentResolver, it.uri) ?: it.uri }
                            }
                            TrackInfoPanel(grid, playlistTracks.firstOrNull { it.id == request.trackId }, closeOptions,
                                filename = record?.filename, filePath = filePath)
                        }
                        if (overlay != null && !overlay.collections && overlay.info == null) TrackOptionsPopup(grid,
                            playlistTracks.firstOrNull { it.id == overlay.trackId }, closeOptions,
                            onCollections = { optionsCollections = true }, onInfo = { infoRequest = TrackInfoRequest(optionsTarget!!, TrackInfoDestination.TRACK_OPTIONS) }, onSchedule = { stop ->
                                val target = playlistTracks.firstOrNull { it.id == optionsTarget && it.available }
                                if (target == null || (optionsOccurrence != null && queue.entries.none { it.id == optionsOccurrence }))
                                    Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                                else {
                                    if (queue.current == null) { playing = false; position = 0f }
                                    queue = optionsOccurrence?.let { queue.scheduleOccurrence(it, stop) } ?: queue.schedule(target.id, stop)
                                    optionsCollections = false; optionsTarget = null; optionsOccurrence = null; optionsCompleted++
                                    Toast.makeText(context, if (stop) "Will stop after this song" else "Will play next", Toast.LENGTH_SHORT).show()
                                }
                            }, fromQueue = overlay.occurrence != null,
                            currentOccurrence = overlay.occurrence != null && overlay.occurrence == queue.currentId,
                            onAdd = {
                                val target = playlistTracks.firstOrNull { it.id == optionsTarget && it.available }
                                if (target != null) {
                                    if (queue.entries.isEmpty()) { playing = false; position = 0f }
                                    queue = queue.append(target.id)
                                    optionsTarget = null; optionsOccurrence = null; optionsCollections = false; optionsCompleted++
                                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                }
                            }, onRemove = {
                                val id = optionsOccurrence
                                if (id != null && queue.entries.any { it.id == id }) {
                                    val wasCurrent = id == queue.currentId
                                    queue = queue.remove(id, available)
                                    if (wasCurrent) {
                                        position = 0f
                                        if (queue.current == null) playing = false
                                        else if (playing) playlistTracks.firstOrNull { it.id == queue.current?.trackId }?.let(recordDemoStart)
                                    }
                                    optionsTarget = null; optionsOccurrence = null; optionsCollections = false; optionsCompleted++
                                    Toast.makeText(context, "Removed from queue", Toast.LENGTH_SHORT).show()
                                }
                            })
                        if (overlay?.collections == true) MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
                            grid, 55f, 20f, 172f, 172f, closeOptions)
                        if (overlay?.collections == true) {
                            TrackCollectionsPopup(grid, playlistLibrary, overlay.trackId,
                                onToggle = { id ->
                                    if (playlistTracks.any { it.id == overlay.trackId }) updatePlaylists(playlistLibrary.toggleMembership(id, overlay.trackId, System.currentTimeMillis()))
                                    else Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                                }, onCreate = {
                                    if (playlistTracks.any { it.id == overlay.trackId }) { collectionDraft = ""; collectionNaming = true }
                                    else Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                                }, onClose = closeCollections)
                        }
                    }
                    AssetButton("Previous track", R.drawable.player_skip_back, grid, 108f, 1136f, 74f, pressedAsset = R.drawable.player_skip_back_pressed) { browsingReturn.interaction(); stepTrack(-1) }
                    AssetButton(if (playing) "Pause" else "Play", if (playing) R.drawable.player_pause else R.drawable.player_play,
                        grid, 508f, 1137f, 123f, pressedAsset = if (playing) R.drawable.player_pause_pressed else R.drawable.player_play_pressed) { browsingReturn.interaction(); if (!fixture) { if (playing) playback!!.pause() else playback!!.play() } else if (playing) playing = false else {
                            val before = queue.currentId
                            val resumed = queue.resume(available)
                            queue = resumed.queue; playing = resumed.playing
                            if (queue.currentId != before) position = 0f
                            if (playing) playlistTracks.firstOrNull { it.id == queue.current?.trackId }?.let(recordDemoStart)
                        } }
                    AssetButton("Next track", R.drawable.player_skip_next, grid, 891f, 1136f, 74f, pressedAsset = R.drawable.player_skip_next_pressed) { browsingReturn.interaction(); stepTrack(1) }
                }
                BoxWithConstraints(Modifier.fillMaxHeight()) {
                    PlayerPaneCrossfade(drawerOpen) { open ->
                        if (open) key(drawerOpening) {
                            AppDrawer(drawerApps, onPopupChanged = drawerTimeout::setSuspended) { drawerOpen = false }
                        }
                        else {
                            ClockPane(referencePreview, Modifier.fillMaxSize(), settings)
                            if (launcher?.enabled == true) DrawerButton(PlayerGrid(maxWidth.value, maxHeight.value), false, controlOpacity) { drawerOpening++; drawerOpen = true }
                        }
                    }
                }
            }
            PlayerDivider(settings.divider, Modifier.align(Alignment.Center))
            backgroundDropdown?.let { BackgroundDropdownOverlay(it) }
        }
        BackHandler(enabled = optionsTarget != null || infoRequest != null, onBack = closeOptions)
    }
    if (collectionsOpen && collectionNaming) PlaylistNamePrompt(playlistLibrary, collectionDraft, { collectionDraft = it },
        onDismiss = { collectionNaming = false; collectionDraft = "" }, onConfirm = {
            val target = membershipTarget
            if (target != null && playlistTracks.any { it.id == target } && playlistLibrary.nameError(collectionDraft) == null) {
                updatePlaylists(playlistLibrary.createForTrack(collectionDraft, target, target, System.currentTimeMillis()))
                collectionNaming = false
                collectionDraft = ""
            } else if (playlistTracks.none { it.id == target }) {
                collectionNaming = false; collectionDraft = ""
                Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
            }
        })
    dialog?.let { title ->
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(title) },
            text = { Text(when (title) {
                else -> "Your music library is empty. $title browsing is not available yet."
            }) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
    }
}

@Composable
private fun MenuButton(grid: PlayerGrid, opacity: Float, onClick: () -> Unit) {
    val buttonSize = 178f * 0.95f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val viewConfiguration = LocalViewConfiguration.current
    val exactTouchBounds = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize = DpSize.Zero
        }
    }
    // Keep the original 48×48 SVG canvas for drawing. Its visible pill occupies
    // x=0..48, y=12.979..34.0575 (including the border stroke).
    Box(grid.place(141f - buttonSize / 2, 106f - buttonSize / 2, buttonSize, buttonSize).alpha(opacity)) {
        Image(painterResource(if (pressed) R.drawable.player_menu_button_pressed else R.drawable.player_menu_button),
            null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        // Restrict only this button's hit area, including the rounded corners.
        // Compose's default minimum touch target would otherwise extend past it.
        CompositionLocalProvider(LocalViewConfiguration provides exactTouchBounds) {
            Box(Modifier.offset(y = (buttonSize * 12.979f / 48f * grid.scale).dp)
                .size((buttonSize * grid.scale).dp, (buttonSize * 21.0785f / 48f * grid.scale).dp)
                .clip(RoundedCornerShape(percent = 50))
                .semantics { contentDescription = "Menu" }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick))
        }
    }
}

@Composable
private fun AssetButton(label: String, @DrawableRes asset: Int, grid: PlayerGrid, cx: Float, cy: Float,
                        size: Float, state: String? = null, opacity: Float = 1f, @DrawableRes pressedAsset: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(grid.touchTarget(cx, cy, size).alpha(opacity * if (enabled) 1f else 0.4f).semantics {
        contentDescription = label
        if (state != null) stateDescription = state
    }.clickable(interactionSource = interaction, indication = if (pressedAsset != null) null else androidx.compose.foundation.LocalIndication.current,
        role = Role.Button, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(if (pressed && pressedAsset != null) pressedAsset else asset), null,
            Modifier.size((size * grid.scale * 0.8f).dp), contentScale = ContentScale.Fit)
    }
}

private fun playerTextStyle(size: Float, bold: Boolean = false) = TextStyle(
    color = Color.White, fontFamily = InterFontFamily,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size.sp, lineHeight = (size * 1.12f).sp, letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

@Composable
internal fun PlayerLabel(text: String, modifier: Modifier, size: Float, alignment: Alignment = Alignment.Center, bold: Boolean = false, color: Color = Color.White, marquee: Boolean = false) {
    Box(if (marquee) modifier.clipToBounds() else modifier, contentAlignment = alignment) {
        // A new title starts at its beginning with a fresh pause. Keep the marquee on
        // Text so it measures natural width inside the label's bounded viewport.
        key(text) {
            Text(text, modifier = if (marquee) Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1500,
                repeatDelayMillis = 1500,
                spacing = MarqueeSpacing((size * 1.5f).dp),
                velocity = (size * 0.8f).dp
            ) else Modifier,
                style = playerTextStyle(size, bold).copy(color = color),
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ClockPane(referencePreview: Boolean, modifier: Modifier, settings: PlayerSettings) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(referencePreview) {
        if (!referencePreview) while (true) { now = Date(); delay(1000) }
    }
    val day = if (referencePreview) "Friday" else SimpleDateFormat("EEEE", Locale.ENGLISH).format(now)
    val time = if (referencePreview) "10:30" else SimpleDateFormat(if (settings.twentyFourHour) "HH:mm" else "h:mm", Locale.ENGLISH).format(now)
    val period = if (settings.twentyFourHour) "" else if (referencePreview) "AM" else SimpleDateFormat("a", Locale.ENGLISH).format(now)
    val date = if (referencePreview) "30-01-2026" else SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(now)
    BoxWithConstraints(modifier) {
        val grid = PlayerGrid(maxWidth.value, maxHeight.value)
        if (settings.clock != ClockStyle.DEFAULT) {
            StyledClock(day, time, period, date, settings.clock, grid)
            return@BoxWithConstraints
        }
        PlayerLabel(day, grid.place(15f, 311f, 900f, 120f), 100f * grid.scale)
        BoxWithConstraints(grid.place(90f, 495f, 850f, 248f), contentAlignment = Alignment.Center) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val mainStyle = playerTextStyle(230f * grid.scale)
            val periodStyle = playerTextStyle(62f * grid.scale, bold = true)
            val gap = ((if (period.isEmpty()) 0f else 24f) * grid.scale).dp
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

private fun formatPosition(position: Float): String {
    val seconds = position.toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

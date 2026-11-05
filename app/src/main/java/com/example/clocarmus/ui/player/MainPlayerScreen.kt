package com.example.clocarmus.ui.player

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
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

/** Visual player shell; transport state is a demo until the playback service is connected. */
@Composable
internal fun MainPlayerScreen(referencePreview: Boolean = false, menuPreview: Boolean = false, tracksPreview: Boolean = false, artistsPreview: String? = null, albumsPreview: String? = null, playlistsPreview: String? = null, genresPreview: String? = null, collectionsPreview: String? = null, foldersPreview: String? = null, initialPlaylists: PlaylistLibrary? = null, onPlaylistsChanged: (PlaylistLibrary) -> Unit = {}, freshStartup: Boolean = false) {
    val context = LocalContext.current
    val playlistPreferences = remember { context.getSharedPreferences("demo-playlists", Context.MODE_PRIVATE) }
    var playlistLibrary by remember { mutableStateOf(if (collectionsPreview != null) collectionsFixture(collectionsPreview) else if (playlistsPreview != null) playlistFixture(playlistsPreview) else initialPlaylists ?: loadPlaylists(playlistPreferences)) }
    val updatePlaylists: (PlaylistLibrary) -> Unit = {
        playlistLibrary = it
        if (playlistsPreview == null && collectionsPreview == null) {
            savePlaylists(playlistPreferences, it)
            onPlaylistsChanged(it)
        }
    }
    val playlistTracks = remember { (demoTracks + demoLibraryTracks + genreReferenceTracks + playlistFixtureTracks + demoPlayerTrack + demoFolderTree.entries.mapNotNull { it.track }).distinctBy { it.id } }
    LaunchedEffect(Unit) {
        if (playlistsPreview == null && collectionsPreview == null) {
            var next = playlistLibrary
            playlistTracks.filterNot { it.id.startsWith("playlist:") }.forEachIndexed { i, track -> next = next.imported(track.id, i.toLong()) }
            updatePlaylists(next)
        }
    }
    val recordDemoStart: (TrackMenuEntry) -> Unit = { updatePlaylists(playlistLibrary.playbackStarted(it, System.currentTimeMillis())) }
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
            if (event == Lifecycle.Event.ON_RESUME) syncVolume()
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        lifecycle.addObserver(lifecycleObserver)
        syncVolume()
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    val sessionPreferences = remember { context.getSharedPreferences("demo-session", Context.MODE_PRIVATE) }
    val fixture = referencePreview || menuPreview || tracksPreview || artistsPreview != null || albumsPreview != null || playlistsPreview != null || genresPreview != null || collectionsPreview != null || foldersPreview != null
    val savedSession = remember { if (fixture) null else loadDemoSession(sessionPreferences) }
    var playing by rememberSaveable { mutableStateOf(referencePreview) }
    LaunchedEffect(Unit) {
        // Even an Android-restored Activity bundle must not autoplay in a new process.
        // Same-session recreation keeps its saved playing state.
        if (freshStartup && !referencePreview) playing = false
    }
    var position by rememberSaveable { mutableFloatStateOf(savedSession?.position ?: 113f) }
    var shuffle by rememberSaveable { mutableStateOf(savedSession?.shuffle ?: true) }
    var repeat by rememberSaveable { mutableIntStateOf(savedSession?.repeat ?: 0) }
    var menuOpen by rememberSaveable { mutableStateOf(menuPreview || tracksPreview || artistsPreview != null || albumsPreview != null || playlistsPreview != null || genresPreview != null || foldersPreview != null) }
    var tracksOpen by rememberSaveable { mutableStateOf(tracksPreview) }
    var browserStack by rememberSaveable { mutableStateOf(arrayListOf<BrowserDestination>().apply {
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
    var queue by rememberSaveable { mutableStateOf(savedSession?.queue ?: TrackQueue.fromTracks(
        listOf(initialSelectedId) + demoTracks.map { it.id }.filterNot { it == initialSelectedId }, initialSelectedId, shuffle)) }
    val selectedTrackId = queue.current?.trackId
    val selectedTrack = playlistTracks.firstOrNull { it.id == selectedTrackId }
    val selectedTitle = selectedTrack?.title
    val duration = selectedTrack?.durationSeconds?.takeIf { it > 0 }?.toFloat()
    var seekPreview by remember(queue.currentId, duration) { mutableStateOf<Float?>(null) }
    val displayedPosition = seekPreview?.let { it * (duration ?: 0f) } ?: position
    val seekable = selectedTrack?.available == true && duration != null
    var trackQuery by rememberSaveable { mutableStateOf("") }
    val trackScroll = rememberScrollState()
    val visibleTracks = remember(trackQuery) { demoTracks.filter { it.title.contains(trackQuery, ignoreCase = true) } }
    LaunchedEffect(trackQuery) { trackScroll.scrollTo(0) }
    val available: (String) -> Boolean = { id -> playlistTracks.any { it.id == id && it.available } }
    val stepTrack: (Int) -> Unit = { direction ->
        queue = queue.step(direction, available)
        if (playing) queue.current?.trackId?.let { id -> playlistTracks.firstOrNull { it.id == id }?.let(recordDemoStart) }
        position = 0f
    }
    LaunchedEffect(queue, position, shuffle, repeat) {
        if (!fixture) saveDemoSession(sessionPreferences, DemoSession(queue, position, shuffle, repeat))
    }
    LaunchedEffect(playing, queue.currentId, duration, repeat, referencePreview) {
        if (playing && !referencePreview && duration != null) {
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
    var optionsTarget by rememberSaveable { mutableStateOf<String?>(null) }
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
    var playlistEditing by remember { mutableStateOf(false) }
    val browsingReturn = rememberBrowsingReturnInactivity(menuOpen, suspended = dialog != null || searchOpen || playlistEditing || optionsTarget != null) {
        tracksOpen = false
        browserStack = arrayListOf()
        menuOpen = false
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
            browserStack.isNotEmpty() -> browserStack = ArrayList(browserStack.dropLast(1))
            tracksOpen -> tracksOpen = false
            else -> { browsingReturn.cancel(); menuOpen = false }
        }
    }
    BackHandler(enabled = menuOpen && optionsTarget == null, onBack = goBack)
    val selectTrack: (String, List<TrackMenuEntry>) -> Unit = { id, tracks ->
        val playable = playableTrack(tracks, id)
        queue = TrackQueue.fromTracks(tracks.map { it.id }, playable?.id, shuffle)
        position = 0f
        playing = playable != null
        if (playable != null) { recordDemoStart(playable); browsingReturn.trackSelected() } else browsingReturn.cancel()
    }

    val sliderPointers = remember { SliderPointers() }
    val inactivity = rememberMainPlayerInactivity(enabled = !menuOpen && dialog == null && !collectionsOpen && !referencePreview, pointers = sliderPointers)
    val controlOpacity = if (dialog == null && !collectionsOpen && !referencePreview) inactivity.opacity else 1f

    val closeOptions: () -> Unit = {
        when {
            collectionNaming -> { collectionNaming = false; collectionDraft = "" }
            optionsCollections -> optionsCollections = false
            else -> optionsTarget = null
        }
    }
    val trackOptionsEntry = TrackOptionsEntry({ id ->
        browsingReturn.cancel()
        optionsTarget = id; optionsCollections = false
    }, optionsTarget != null, optionsCompleted)
    val returnHome by rememberUpdatedState<() -> Unit> {
        browsingReturn.cancel()
        collectionNaming = false
        collectionDraft = ""
        optionsCollections = false
        optionsTarget = null
        collectionsTarget = null
        dialog = null
        searchOpen = false
        playlistEditing = false
        browserStack = arrayListOf()
        tracksOpen = false
        menuOpen = false
    }
    val homeNavigation = remember { PlayerHomeNavigation { returnHome() } }
    CompositionLocalProvider(LocalTrackOptions provides trackOptionsEntry, LocalPlayerHome provides homeNavigation) {
        Box(Modifier.fillMaxSize().background(Color.Black).observePlayerTouches(inactivity, sliderPointers)
            .observeBrowsingTouches(browsingReturn).onPreviewKeyEvent { browsingReturn.interaction(); false }) {
            Row(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val grid = PlayerGrid(maxWidth.value, maxHeight.value)
                    Box(if (optionsTarget != null) Modifier.fillMaxSize().alpha(0f).clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                        if (browserStack.isNotEmpty()) {
                            val destination = browserStack.last()
                            artistStates.SaveableStateProvider(destination.copy(search = false)) {
                                val playCollection: (List<TrackMenuEntry>) -> Unit = { tracks ->
                                    shuffle = false
                                    val first = tracks.firstOrNull { it.available }
                                    queue = TrackQueue.fromTracks(tracks.map { it.id }, first?.id)
                                    playing = first != null
                                    position = 0f
                                    if (first != null) {
                                        recordDemoStart(first)
                                        browsingReturn.cancel()
                                        browserStack = arrayListOf()
                                        tracksOpen = false
                                        menuOpen = false
                                    }
                                }
                                if (destination.page == "folders") {
                                    FoldersPane(grid, destination.directory, foldersPreview, selectedTrackId, playing, browsingReturn,
                                        { searchOpen = it }, goBack,
                                        { directory -> browserStack = ArrayList(browserStack.dropLast(1) + BrowserDestination("folders", directory = directory)) },
                                        selectTrack, onSourceLost = { sourceId ->
                                            if (sourceId != null && selectedTrackId?.let { demoFolderTree.source(it)?.id } == sourceId) playing = false
                                        })
                                } else if (destination.page.startsWith("genre-")) {
                                    GenresPane(grid, destination, genresPreview, selectedTrackId, playing, browsingReturn,
                                        { searchOpen = it }, goBack, navigate, selectTrack, playCollection)
                                } else if (destination.page.startsWith("playlist-")) {
                                    PlaylistsPane(grid, destination.playlistId, playlistLibrary, playlistTracks, selectedTrackId, playing,
                                        browsingReturn, updatePlaylists, goBack,
                                        { navigate(BrowserDestination("playlist-detail", playlistId = it)) }, selectTrack,
                                        { playlistEditing = it })
                                } else if (destination.page.startsWith("album-")) {
                                    AlbumsPane(grid, destination, albumsPreview, selectedTrackId, playing, browsingReturn,
                                        { searchOpen = it }, goBack, navigate, selectTrack, playCollection)
                                } else {
                                    ArtistsPane(grid, destination.page, destination.artist, artistsPreview, selectedTrackId, playing,
                                        browsingReturn, onSearchVisibilityChanged = { searchOpen = it }, onBack = goBack,
                                        onArtist = { navigate(BrowserDestination("detail", artist = it, genre = destination.genre)) },
                                        onPage = { navigate(destination.copy(page = it)) },
                                        onSelectTrack = selectTrack, onPlayAll = playCollection, scope = destination.scope, genre = destination.genre,
                                        onAlbum = { navigate(BrowserDestination("album-options", album = it)) })
                                }
                            }
                        } else if (tracksOpen) {
                            TrackMenuPane(grid, visibleTracks, selectedTrackId, playing, trackScroll, trackQuery,
                                onQuery = { trackQuery = it },
                                onSearchVisibilityChanged = { searchOpen = it },
                                onSearchEdited = { browsingReturn.cancel() },
                                onBack = goBack, onSelect = { selectTrack(it, visibleTracks) })
                        } else if (menuOpen) {
                            MenuPane(grid, onBack = { browsingReturn.cancel(); menuOpen = false }, onDestination = {
                                browsingReturn.interaction()
                                if (it == "All tracks") tracksOpen = true
                                else if (it == "Folders") navigate(BrowserDestination("folders"))
                                else if (it == "Artists") navigate(BrowserDestination("artists"))
                                else if (it == "Genres") navigate(BrowserDestination("genre-list"))
                                else if (it == "Albums") navigate(BrowserDestination("album-list"))
                                else if (it == "Playlists") navigate(BrowserDestination("playlist-list"))
                                else {
                                    // Settings and playlist editing must never inherit a pending return.
                                    if (it == "Settings" || it == "Playlists") browsingReturn.cancel()
                                    dialog = it
                                }
                            })
                        } else {
                            MenuButton(grid, controlOpacity) { closeCollections(); menuOpen = true }
                        }
                    }
                    if (optionsTarget != null) {
                        // Opaque input shield keeps source composition/state while hiding its controls.
                        Box(Modifier.fillMaxSize()
                            .background(Color.Black).pointerInput(Unit) { detectTapGestures {} })
                    }
                    PlayerSlider(volume, {
                        browsingReturn.interaction()
                        volume = it
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).toInt(), 0)
                    }, grid.place(283f, 61f, 434f, 83f), "Media volume", grid.scale, volume = true, pointers = sliderPointers)
                    if (!menuOpen) {
                        AssetButton("Manage playlists", when {
                            collectionsOpen -> R.drawable.collections_playlist_button_toggle_on
                            selectedTrackId?.let(playlistLibrary::hasMembership) == true -> R.drawable.collections_track_present_in_playlists
                            else -> R.drawable.collections_playlist_button_untoggled
                        }, grid, 850f, 106f, if (collectionsOpen) 125f else 104f,
                            state = if (collectionsOpen) "Expanded" else "Collapsed", opacity = controlOpacity,
                            enabled = selectedTrack != null) {
                            if (collectionsOpen) closeCollections() else collectionsTarget = selectedTrackId
                        }
                        if (!collectionsOpen) {
                            Image(painterResource(R.drawable.player_no_album_art), "No album artwork",
                                grid.place(273f, 187f, 457f, 457f), contentScale = ContentScale.Fit)
                            AssetButton("Shuffle", if (shuffle) R.drawable.player_shuffle_active else R.drawable.player_shuffle_inactive,
                                grid, 141f, 291f, 93f, if (shuffle) "On" else "Off", opacity = controlOpacity) {
                                shuffle = !shuffle
                                val head = queue.entries.take(queue.currentIndex + 1)
                                val tail = queue.entries.drop(queue.currentIndex + 1)
                                val scheduled = queue.scheduledIds.mapNotNull { id -> tail.find { it.id == id } }
                                val rest = tail.filterNot { it.id in queue.scheduledIds }
                                queue = queue.copy(entries = head + scheduled + if (shuffle) rest.shuffled() else rest)
                            }
                            AssetButton("Repeat", when (repeat) {
                                1 -> R.drawable.player_repeat_one_active
                                2 -> R.drawable.player_repeat_active
                                else -> R.drawable.player_repeat_inactive
                            }, grid, 850f, 291f, 93f, when (repeat) { 1 -> "Repeat one"; 2 -> "Repeat all"; else -> "Off" }, opacity = controlOpacity) { repeat = (repeat + 1) % 3 }
                            AssetButton("Rewind 10 seconds", R.drawable.player_skip_10sec_backward, grid, 141f, 538f, 93f, opacity = controlOpacity) { position = (position - 10).coerceAtLeast(0f) }
                            AssetButton("Forward 10 seconds", R.drawable.player_skip_10sec_forward, grid, 850f, 538f, 93f, opacity = controlOpacity) { position = (position + 10).coerceAtMost(duration ?: 225f) }
                            PlayerLabel(selectedTitle ?: "Superman", grid.place(70f, 670f, 860f, 72f), 54f * grid.scale, bold = true, marquee = true)
                            PlayerLabel(if (selectedTitle == null) "Lazlo Bane" else selectedTrack?.artist ?: "Unknown artist", grid.place(70f, 744f, 860f, 57f), 48f * grid.scale, marquee = true)
                            PlayerLabel(if (selectedTitle == null) "All the Time in the World" else selectedTrack?.album ?: "Unknown album", grid.place(60f, 802f, 880f, 57f), 48f * grid.scale, marquee = true)
                            PlayerLabel(if (selectedTitle == null) "2002" else selectedTrack?.year?.takeIf { it.isNotBlank() } ?: "Unknown year", grid.place(70f, 859f, 860f, 57f), 48f * grid.scale)
                            PlayerSlider(if (duration != null) position / duration else 0f,
                                { if (duration != null) position = it * duration },
                                grid.place(79f, 944f, 841f, 87f), "Track position", grid.scale,
                                enabled = seekable, gestureKey = queue.currentId to duration, pointers = sliderPointers,
                                onPreview = { seekPreview = it })
                            PlayerLabel(formatPosition(displayedPosition), grid.place(79f, 1029f, 180f, 53f), 43f * grid.scale, alignment = Alignment.CenterStart)
                            PlayerLabel(duration?.let(::formatPosition) ?: "--:--", grid.place(740f, 1029f, 180f, 53f), 43f * grid.scale, alignment = Alignment.CenterEnd)
                        }
                    }
                    if (optionsTarget != null && !optionsCollections) TrackOptionsPopup(grid,
                        playlistTracks.firstOrNull { it.id == optionsTarget }, closeOptions,
                        onCollections = { optionsCollections = true }, onSchedule = { stop ->
                            val target = playlistTracks.firstOrNull { it.id == optionsTarget && it.available }
                            if (target == null) Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                            else {
                                if (queue.current == null) { playing = false; position = 0f }
                                queue = queue.schedule(target.id, stop)
                                optionsCollections = false; optionsTarget = null; optionsCompleted++
                                Toast.makeText(context, if (stop) "Will stop after this song" else "Will play next", Toast.LENGTH_SHORT).show()
                            }
                        })
                    if (optionsCollections) MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
                        grid, 55f, 20f, 172f, 172f, closeOptions)
                    if (collectionsOpen) TrackCollectionsPopup(grid, playlistLibrary, membershipTarget!!,
                        onToggle = { id ->
                            val target = membershipTarget
                            if (playlistTracks.any { it.id == target }) updatePlaylists(playlistLibrary.toggleMembership(id, target, System.currentTimeMillis()))
                            else Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                        }, onCreate = {
                            if (playlistTracks.any { it.id == membershipTarget }) { collectionDraft = ""; collectionNaming = true }
                            else Toast.makeText(context, "Track unavailable", Toast.LENGTH_SHORT).show()
                        }, onClose = closeCollections)
                    AssetButton("Previous track", R.drawable.player_skip_back, grid, 238f, 1136f, if (menuOpen) 74f else 93f, pressedAsset = R.drawable.player_skip_back_pressed) { browsingReturn.interaction(); stepTrack(-1) }
                    AssetButton(if (playing) "Pause" else "Play", if (playing) R.drawable.player_pause else R.drawable.player_play,
                        grid, 508f, 1137f, if (menuOpen) 123f else 110f, pressedAsset = if (playing) R.drawable.player_pause_pressed else R.drawable.player_play_pressed) { browsingReturn.interaction(); playing = !playing; if (playing) selectedTrack?.let(recordDemoStart) }
                    AssetButton("Next track", R.drawable.player_skip_next, grid, 778f, 1136f, if (menuOpen) 74f else 93f, pressedAsset = R.drawable.player_skip_next_pressed) { browsingReturn.interaction(); stepTrack(1) }
                }
                ClockPane(referencePreview, Modifier.weight(1f).fillMaxHeight())
            }
            Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxHeight().background(Color(0xffdddddd)))
        }
        BackHandler(enabled = optionsTarget != null, onBack = closeOptions)
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
                "Settings" -> "Settings are not available yet."
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
    Box(grid.touchTarget(cx, cy, size).alpha(opacity).semantics {
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

private fun formatPosition(position: Float): String {
    val seconds = position.toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

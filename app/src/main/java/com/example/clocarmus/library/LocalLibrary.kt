package com.example.clocarmus.library

import android.content.*
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.example.clocarmus.ui.player.*
import com.example.clocarmus.ui.startup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Process-owned, single scanner. All database/provider work runs on IO.
 * Requests arriving during traversal remain queued for a follow-up pass.
 */
internal class LocalLibrary(private val context: Context, private val documents: DocumentAccess = AndroidDocumentAccess(context)) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = LibraryDatabase(context)
    private val mutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val requestLock = Any()
    private val dirtySources = mutableSetOf<String>()
    private var allRequested = false
    private var scanJob: Job? = null
    private var generation = 0L
    private var directoryCache = emptyList<LibraryDirectory>()
    private var configuration = OnboardingState()
    private val mutable = MutableStateFlow(LibrarySnapshot())
    val state = mutable.asStateFlow()
    private val observers = mutableListOf<ContentObserver>()
    private val metadataPrefs = context.getSharedPreferences("library-metadata", 0)
    private val metadataRefreshIds = mutableSetOf<String>()
    private val prefs = context.getSharedPreferences("onboarding", 0)
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("folders", "exclusions", "resetPending")) scope.launch {
            mutex.withLock {
                val next = loadOnboarding(prefs)
                if (configuration.folders != next.folders || configuration.exclusions != next.exclusions || next.resetPending) {
                    generation++
                    scanJob?.cancel()
                }
            }
            refresh()
        }
    }

    private val storageReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, intent: Intent?) { refresh() } }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED); addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT); addAction(Intent.ACTION_MEDIA_REMOVED); addDataScheme("file")
        }
        context.registerReceiver(storageReceiver, filter)
        scope.launch {
            try {
                mutex.withLock {
                    configuration = loadOnboarding(prefs)
                    directoryCache = db.directories()
                    val records = db.records()
                    if (metadataPrefs.getInt("readerVersion", 0) < 2) {
                        metadataRefreshIds.addAll(records.map { it.id })
                        if (metadataRefreshIds.isEmpty()) metadataPrefs.edit().putInt("readerVersion", 2).commit()
                    }
                    val playlists = db.playlists() ?: loadPlaylists(context.getSharedPreferences("demo-playlists", 0))
                        .withoutMusic() // Preserve custom names; demo IDs never become real identities.
                    publish(records, playlists, db.version(), ready = true)
                }
                refresh()
                for (signal in requests) {
                    val wanted = synchronized(requestLock) {
                        val next = if (allRequested) emptySet() else dirtySources.toSet()
                        dirtySources.clear(); allRequested = false
                        next
                    }
                    scanJob = launch { scan(wanted) }
                    scanJob?.join()
                }
            } catch (exception: Exception) {
                mutable.value = mutable.value.copy(summary = ScanSummary(failures = listOf(exception.message ?: "Library could not open"), incomplete = true), ready = true)
            }
        }
    }
    fun refresh(source: String? = null) {
        synchronized(requestLock) {
            if (source == null) allRequested = true else dirtySources.add(source)
            requests.trySend(Unit)
        }
    }
    fun cancel() {
        synchronized(requestLock) {
            dirtySources.clear(); allRequested = false
            requests.tryReceive()
            scanJob?.cancel()
        }
    }
    suspend fun close() = withContext(Dispatchers.IO) {
        scope.cancel()
        mutex.withLock {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            context.unregisterReceiver(storageReceiver)
            observers.forEach { runCatching { context.contentResolver.unregisterContentObserver(it) } }
            db.close()
        }
    }

    suspend fun editPlaylists(edit: (PlaylistLibrary) -> PlaylistLibrary) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = mutable.value
            val playlists = edit(snapshot.playlists).retaining(snapshot.records.map { it.id }.toSet())
            val version = db.commit(emptyList(), emptyList(), playlists)
            publish(snapshot.records, playlists, version)
        }
    }
    suspend fun reset() = withContext(Dispatchers.IO) {
        mutex.withLock {
            generation++
            scanJob?.cancel()
            val snapshot = mutable.value
            val playlists = (if (snapshot.ready) snapshot.playlists else db.playlists()
                ?: loadPlaylists(context.getSharedPreferences("demo-playlists", 0))).withoutMusic()
            directoryCache = emptyList()
            val version = db.commit(emptyList(), db.records().map { it.id }, playlists, directoryCache)
            configuration = loadOnboarding(prefs)
            publish(emptyList(), playlists, version)
            mutable.value = mutable.value.copy(summary = ScanSummary())
        }
    }
    suspend fun removeMissing(previewIds: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = mutable.value
            val removed = snapshot.records.filter { it.id in previewIds && it.availability == Availability.MISSING && stillMissing(it) }.map { it.id }.toSet()
            val retained = snapshot.records.filterNot { it.id in removed }
            val playlists = snapshot.playlists.retaining(retained.map { it.id }.toSet())
            val version = db.commit(emptyList(), removed, playlists)
            publish(retained, playlists, version)
        }
    }
    /** Recheck provider evidence at confirmation time; permission failures are not absence. */
    private fun stillMissing(record: LibraryRecord): Boolean {
        if (mutable.value.summary.running) return false
        val config = loadOnboarding(prefs)
        return record.paths.isNotEmpty() && record.paths.all { path ->
            if (config.folders.none { it.uri == path.source } || path.directories.any { it in config.exclusions }) false
            else try {
                val tree = Uri.parse(path.source)
                var parent = DocumentsContract.getTreeDocumentId(tree)
                val ids = path.directories.map { DocumentsContract.getDocumentId(Uri.parse(it)) } + DocumentsContract.getDocumentId(Uri.parse(record.uri))
                var absent = false
                for (id in ids) {
                    var found = false
                    documents.children(tree, parent).use { cursor ->
                        if (cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) return false
                        while (cursor.moveToNext()) if (cursor.getString(0) == id) found = true
                    }
                    if (!found) { absent = true; break }
                    parent = id
                }
                absent
            } catch (_: Exception) { false }
        }
    }
    private fun publish(records: List<LibraryRecord>, playlists: PlaylistLibrary, version: Long, ready: Boolean = mutable.value.ready) {
        val entries = linkedMapOf<String, FolderEntry>()
        directoryCache.forEach { entries[it.id] = FolderEntry(it.id, it.name, it.parent, available = it.available) }
        configuration.folders.forEach { source ->
            val covered = records.filter { record -> record.paths.any { it.source == source.uri } }
            val accessible = directoryCache.firstOrNull { it.id == source.uri }?.available ?: (covered.any { it.availability == Availability.AVAILABLE } || covered.isEmpty())
            entries[source.uri] = FolderEntry(source.uri, source.name, available = accessible)
        }
        records.forEach { record ->
            record.paths.forEachIndexed { index, path ->
                var parent = path.source
                path.directories.forEachIndexed { i, id ->
                    if (id !in entries) entries[id] = FolderEntry(id, path.names[i], parent, available = record.availability == Availability.AVAILABLE)
                    parent = id
                }
                // Overlapping sources share one track ID; folder rows use occurrence IDs.
                val rowId = "${record.id}:$index"
                entries[rowId] = FolderEntry(rowId, record.filename, parent,
                    record.track.copy(id = record.id, available = record.availability == Availability.AVAILABLE),
                    available = record.availability == Availability.AVAILABLE)
            }
        }
        mutable.value = mutable.value.copy(records = records, playlists = playlists, folders = FolderTree(entries.values.toList()), version = version, ready = ready)
    }
    private suspend fun scan(wanted: Set<String>) {
        var summary = ScanSummary(running = true)
        val startedGeneration: Long
        val config = loadOnboarding(prefs)
        val configChanged = configuration.folders != config.folders || configuration.exclusions != config.exclusions
        mutex.withLock {
            if (config.resetPending) return
            configuration = config
            generation++
            startedGeneration = generation
            val before = mutable.value
            val retained = before.records.mapNotNull { record ->
                val paths = record.paths.filter { path -> config.folders.any { it.uri == path.source } && path.directories.none { it in config.exclusions } }
                record.copy(paths = paths).takeIf { paths.isNotEmpty() }
            }
            val playlists = before.playlists.retaining(retained.map { it.id }.toSet())
            val removed = before.records.map { it.id }.toSet() - retained.map { it.id }.toSet()
            val byDirectory = directoryCache.associateBy { it.id }
            directoryCache = directoryCache.filter { directory ->
                var current: LibraryDirectory? = directory
                var allowed = config.folders.any { it.uri == directory.source }
                val visited = mutableSetOf<String>()
                while (current != null && visited.add(current.id)) {
                    if (current.id in config.exclusions) allowed = false
                    current = byDirectory[current.parent]
                }
                allowed
            }
            val version = db.commit(retained, removed, playlists, directoryCache)
            publish(retained, playlists, version)
            mutable.value = mutable.value.copy(summary = summary)
        }
        context.contentResolver.let { resolver -> observers.forEach { runCatching { resolver.unregisterContentObserver(it) } }; observers.clear() }
        config.folders.forEach { source ->
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { refresh(source.uri) }
            }
            runCatching { context.contentResolver.registerContentObserver(Uri.parse(source.uri), true, observer); observers.add(observer) }
        }
        val original = mutable.value.records
        val originalsByLocation = original.associateBy { it.location }
        val pending = mutableListOf<LibraryRecord>()
        val seen = mutableMapOf<String, MutableSet<String>>()
        val directoriesSeen = mutableSetOf<String>()
        val completed = mutableSetOf<String>()
        val failedSources = mutableMapOf<String, Availability>()
        val requested = config.folders.filter { configChanged || wanted.isEmpty() || it.uri in wanted }
        suspend fun save(record: LibraryRecord) {
            mutex.withLock {
                check(generation == startedGeneration) { "Scan invalidated" }
                currentCoroutineContext().ensureActive()
                val before = mutable.value
                val previous = before.records.firstOrNull { it.id == record.id }
                summary = when {
                    previous == null -> summary.copy(added = summary.added + 1)
                    previous.track != record.track || previous.fingerprint != record.fingerprint || previous.location != record.location -> summary.copy(updated = summary.updated + 1)
                    else -> summary
                }
                val merged = record.copy(paths = (previous?.paths.orEmpty().filterNot { old -> record.paths.any { it.source == old.source } } + record.paths).distinct())
                val records = before.records.filterNot { it.id == merged.id } + merged
                val playlists = before.playlists.imported(merged.id, merged.importedAt)
                val version = db.commit(listOf(merged), emptyList(), playlists)
                publish(records, playlists, version)
                mutable.value = mutable.value.copy(summary = summary)
            }
        }
        try {
            for (source in requested) {
                val tree = Uri.parse(source.uri)
                val sourceSeen = seen.getOrPut(source.uri) { mutableSetOf() }
                var complete = true
                val visited = mutableSetOf<String>()
                suspend fun walk(id: String, directories: List<String>, names: List<String>) {
                    currentCoroutineContext().ensureActive()
                    if (!visited.add(id)) { complete = false; return }
                    val cursor = documents.children(tree, id)
                    val directoryId = directories.lastOrNull() ?: source.uri
                    directoriesSeen.add(directoryId)
                    cursor.use {
                    mutex.withLock {
                        currentCoroutineContext().ensureActive()
                        check(generation == startedGeneration) { "Scan invalidated" }
                        val directory = LibraryDirectory(source.uri, directoryId, names.lastOrNull() ?: source.name,
                            if (directories.isEmpty()) null else directories.dropLast(1).lastOrNull() ?: source.uri)
                        directoryCache = directoryCache.filterNot { it.id == directoryId } + directory
                        val before = mutable.value
                        val version = db.commit(emptyList(), emptyList(), before.playlists, directoryCache)
                        publish(before.records, before.playlists, version)
                    }
                        while (it.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val docId = it.getString(0)
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                            val name = it.getString(1) ?: docId
                            val mime = it.getString(2) ?: ""
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                if (uri.toString() !in config.exclusions) walk(docId, directories + uri.toString(), names + name)
                            } else if (audioCandidate(name, mime) || documentLocation(tree, docId) in originalsByLocation) {
                                val location = documentLocation(tree, docId)
                                sourceSeen.add(location)
                                val path = LibraryPath(source.uri, directories, names)
                                val known = mutable.value.records.firstOrNull { record -> record.location == location }
                                val document = AudioDocument(uri, location, name, mime, if (it.isNull(3)) -1 else it.getLong(3), if (it.isNull(4)) 0 else it.getLong(4))
                                try {
                                    if (known != null && known.id !in metadataRefreshIds && document.size >= 0 && document.modified > 0 && known.size == document.size && known.modified == document.modified && known.availability != Availability.UNREADABLE) {
                                        save(known.copy(uri = uri.toString(), filename = name, paths = listOf(path), availability = Availability.AVAILABLE))
                                    } else {
                                        val trackId = known?.id ?: "local:${UUID.randomUUID()}"
                                        val (metadata, fingerprint) = documents.audio(document, trackId)
                                        val record = LibraryRecord(trackId, location, uri.toString(), name, document.size, document.modified,
                                            fingerprint, metadata, known?.importedAt ?: System.currentTimeMillis(), listOf(path))
                                        if (known == null && original.any { old -> old.fingerprint == fingerprint }) {
                                            val pendingIndex = pending.indexOfFirst { candidate -> candidate.location == location }
                                            if (pendingIndex >= 0) pending[pendingIndex] = pending[pendingIndex].copy(paths = (pending[pendingIndex].paths + path).distinct()) else pending.add(record)
                                        } else save(record)
                                        metadataRefreshIds.remove(trackId)
                                    }
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (exception: Exception) {
                                    summary = summary.copy(failures = summary.failures + "$name: ${exception.message ?: "Unreadable audio"}")
                                    if (known != null) save(known.copy(paths = listOf(path), availability = Availability.UNREADABLE))
                                }
                            }
                        }
                        if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) complete = false
                    }
                }
                try {
                    walk(DocumentsContract.getTreeDocumentId(tree), emptyList(), emptyList())
                    if (complete) completed.add(source.uri) else summary = summary.copy(incomplete = true)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (exception: Exception) {
                    failedSources[source.uri] = if (exception is SecurityException) Availability.ACCESS_LOST else Availability.DISCONNECTED
                    summary = summary.copy(incomplete = true, failures = summary.failures + "${source.name}: ${exception.message ?: "Source unavailable"}")
                }
            }
            // Absence is evidence only when every source covering this physical copy completed.
            val missing = original.filter { record -> record.paths.isNotEmpty() && record.paths.all { path ->
                path.source in completed && record.location !in seen[path.source].orEmpty()
            } }.map { it.id }.toSet()
            val matches = relocationMatches(original, pending, missing)
            pending.forEach { record ->
                val old = matches[record.id]?.let { id -> original.first { it.id == id } }
                save(if (old == null) record else record.copy(id = old.id, track = record.track.copy(id = old.id), importedAt = old.importedAt))
            }
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                if (generation != startedGeneration) return
                val before = mutable.value
                val reconciled = before.records.map { record ->
                    val observed = record.paths.any { record.location in seen[it.source].orEmpty() }
                    val allMissing = record.id in missing && record.location == originalsByLocation[record.location]?.location
                    when {
                        observed -> record.copy(paths = record.paths.filter { it.source !in completed || record.location in seen[it.source].orEmpty() })
                        allMissing -> { if (record.availability != Availability.MISSING) summary = summary.copy(missing = summary.missing + 1); record.copy(availability = Availability.MISSING) }
                        record.paths.all { it.source in failedSources } -> record.copy(availability = failedSources[record.paths.first().source]!!)
                        else -> record
                    }
                }
                directoryCache = directoryCache.filter { it.source !in completed || it.id in directoriesSeen }
                    .map { if (it.source in failedSources) it.copy(available = false) else it }
                val version = db.commit(reconciled, emptyList(), before.playlists, directoryCache)
                publish(reconciled, before.playlists, version)
            }
        } catch (cancelled: CancellationException) { summary = summary.copy(incomplete = true) }
        catch (exception: Exception) { summary = summary.copy(incomplete = true, failures = summary.failures + (exception.message ?: "Scan failed")) }
        finally {
            withContext(NonCancellable) { mutex.withLock {
                if (generation == startedGeneration) {
                    metadataRefreshIds.retainAll(mutable.value.records.map { it.id }.toSet())
                    if (metadataRefreshIds.isEmpty()) metadataPrefs.edit().putInt("readerVersion", 2).commit()
                    mutable.value = mutable.value.copy(summary = summary.copy(running = false))
                }
            } }
        }
    }
}

package com.example.clocarmus.ui.startup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WelcomeFlow(initial: OnboardingState, preview: String? = null, onComplete: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("onboarding", 0) }
    // Startup already warmed this preferences file on IO. Re-read its cached
    // values here so Activity recreation also retains folders added during setup.
    var state by remember { mutableStateOf(if (preview == null) loadOnboarding(prefs) else initial) }
    var pageName by rememberSaveable { mutableStateOf(when (preview) {
        "warning" -> "Warning"; "permission" -> "Permission"; "folders" -> "Folders"; "loading" -> "Loading"; else -> "Welcome"
    }) }
    val page = WelcomePage.valueOf(pageName)
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun save(next: OnboardingState) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { saveOnboarding(prefs, next) }
                state = next
            } catch (exception: Exception) { error = exception.message ?: "Could not save setup. Please try again." }
            finally { busy = false }
        }
    }
    fun finish() { if (!busy) { dialog = null; pageName = "Loading" } }
    LaunchedEffect(page) {
        if (page == WelcomePage.Loading) {
            // Start the three-second animation once the loading screen has drawn.
            // Completion does not depend on selecting any folders.
            withFrameNanos { }
            delay(3_000)
            try {
                if (preview == null) {
                    withContext(Dispatchers.IO) { saveOnboarding(prefs, state.copy(completed = true)) }
                }
                onComplete()
            } catch (exception: Exception) {
                pageName = "Folders"
                error = exception.message ?: "Could not save setup. Please try again."
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pageName = "Folders" else dialog = "permission-denied"
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val folder = withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val id = DocumentsContract.getTreeDocumentId(uri)
                    val doc = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                    val name = context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: id.substringAfterLast('/').substringAfter(':').ifBlank { "Music" }
                    OnboardingFolder(uri.toString(), name, "${if (id.startsWith("primary:")) "Internal storage" else "USB storage"} - $name")
                }
                val next = state.add(folder)
                withContext(Dispatchers.IO) { saveOnboarding(prefs, next) }
                state = next
            } catch (_: Exception) { error = "Could not access this folder. Please select it again." }
            finally { busy = false }
        }
    }
    BackHandler(page != WelcomePage.Loading) {
        if (dialog != null) dialog = null
        else pageName = when (page) {
            WelcomePage.Warning -> "Welcome"; WelcomePage.Permission -> "Warning"; WelcomePage.Folders -> "Permission"
            else -> { dialog = "skip"; "Welcome" }
        }
    }
    val shownFolders = if (preview == "folders") listOf(OnboardingFolder("preview", "My Music", "USB storage - My Music")) else state.folders
    WelcomeScreen(page, shownFolders,
        onNext = {
            if (!busy) when (page) {
                WelcomePage.Welcome -> pageName = "Warning"
                WelcomePage.Warning -> pageName = "Permission"
                WelcomePage.Folders -> if (state.folders.isEmpty()) dialog = "empty" else finish()
                else -> Unit
            }
        },
        onPermission = {
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) pageName = "Folders"
            else permissionLauncher.launch(permission)
        },
        onAdd = { if (!busy) try { folderLauncher.launch(null) } catch (_: ActivityNotFoundException) {
            error = "No system folder picker is available on this device. You can continue without adding folders."
        } },
        onRemove = { uri -> if (!busy) save(state.remove(uri)) },
        onExclusions = { if (!busy) dialog = "exclusions" })
    if (dialog == "exclusions") ExclusionsDialog(state, onDismiss = { dialog = null }, onChange = { save(it) })
    else if (dialog != null) AlertDialog(onDismissRequest = { dialog = null },
        title = { Text(when (dialog) { "permission-denied" -> "Storage access denied"; "skip" -> "Skip setup?"; else -> "Continue without folders?" }) },
        text = { Text(if (dialog == "permission-denied") "Allow music access in Android settings, or continue to select individual folders using the system picker."
            else "You can use the player now. Music discovery and playback still use the demo library in this build.") },
        confirmButton = { TextButton(onClick = {
            if (dialog == "permission-denied") { dialog = null; pageName = "Folders" } else finish()
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = {
            if (dialog == "permission-denied") {
                dialog = null
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            } else dialog = null
        }) { Text(if (dialog == "permission-denied") "Open settings" else "Cancel") } })
    if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Setup could not finish") },
        text = { Text(error!!) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
}

/** Browse only granted trees; all provider queries run on IO, never during composition. */
@Composable
private fun ExclusionsDialog(state: OnboardingState, onDismiss: () -> Unit, onChange: (OnboardingState) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    var directory by remember { mutableStateOf<Uri?>(null) }
    var parents by remember { mutableStateOf(emptyList<Uri>()) }
    var children by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(directory) {
        val current = directory ?: return@LaunchedEffect
        loading = true; failed = false; children = emptyList()
        try {
            children = withContext(Dispatchers.IO) {
                val id = if (current.pathSegments.contains("document")) DocumentsContract.getDocumentId(current)
                    else DocumentsContract.getTreeDocumentId(current)
                val query = DocumentsContract.buildChildDocumentsUriUsingTree(current, id)
                resolver.query(query, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
                            add(DocumentsContract.buildDocumentUriUsingTree(current, cursor.getString(0)).toString() to cursor.getString(1))
                    }.sortedBy { it.second.lowercase() }
                } ?: error("Folder unavailable")
            }
        } catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Exclusions") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (directory == null) {
                Text("Choose a source, then tap a checkbox to exclude a subfolder.")
                if (state.folders.isEmpty()) Text("Add a music folder first.")
                state.folders.forEach { folder -> TextButton(onClick = { directory = Uri.parse(folder.uri) }) { Text(folder.name) } }
            } else {
                TextButton(onClick = { directory = parents.lastOrNull(); parents = parents.dropLast(1) }) { Text("Back") }
                if (loading) Text("Loading folders…")
                else if (failed) Text("Folder unavailable. Check that storage is connected and access is granted.")
                else if (children.isEmpty()) Text("No subfolders")
                children.forEach { (uri, name) ->
                    androidx.compose.foundation.layout.Row {
                        androidx.compose.material3.Checkbox(checked = uri in state.exclusions, onCheckedChange = { excluded ->
                            onChange(state.copy(exclusions = if (excluded) state.exclusions + uri else state.exclusions - uri))
                        })
                        TextButton(onClick = { parents = parents + directory!!; directory = Uri.parse(uri) }) { Text(name) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

package com.example.clocarmus.ui.startup

import android.Manifest
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
import kotlinx.coroutines.withContext

@Composable
internal fun WelcomeFlow(initial: OnboardingState, preview: String? = null, onComplete: (OnboardingState, Boolean) -> Unit) {
    val context = LocalContext.current
    val sources = rememberSourceManagement(initial, preview != null)
    val state = sources.state
    val busy = sources.busy
    fun save(next: OnboardingState) = sources.save(next)
    var pageName by rememberSaveable { mutableStateOf(when (preview) {
        "warning" -> "Warning"; "permission" -> "Permission"; "folders" -> "Folders"; "loading" -> "Loading"; else -> state.step.name
    }) }
    val page = WelcomePage.valueOf(pageName)
    var skipped by rememberSaveable { mutableStateOf(state.initialSelectionSkipped) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun navigate(page: WelcomePage) {
        if (!busy) sources.save(state.advancingTo(page)) { pageName = page.name }
    }
    fun finish() {
        if (!busy) {
            dialog = null
            sources.save(state.copy(initialSelectionSkipped = skipped).advancingTo(WelcomePage.Loading)) {
                pageName = WelcomePage.Loading.name
            }
        }
    }
    LaunchedEffect(page) {
        if (page == WelcomePage.Loading) {
            // Start the three-second animation once the loading screen has drawn.
            // Completion does not depend on selecting any folders.
            withFrameNanos { }
            if (preview == null) (context.applicationContext as com.example.clocarmus.ClocarmusApplication).library.refresh()
            delay(3_000)
            try {
                if (preview == null) {
                    withContext(Dispatchers.IO) { saveOnboarding(context.getSharedPreferences("onboarding", 0), state.copy(completed = true)) }
                }
                onComplete(state.copy(completed = true), skipped)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (exception: Exception) {
                pageName = WelcomePage.Folders.name
                error = exception.message ?: "Could not save setup. Please try again."
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) navigate(WelcomePage.Folders) else dialog = "permission-denied"
    }
    BackHandler {
        if (page == WelcomePage.Loading || busy) return@BackHandler
        if (dialog != null) dialog = null
        else navigate(when (page) {
            WelcomePage.Warning -> WelcomePage.Welcome; WelcomePage.Permission -> WelcomePage.Warning; WelcomePage.Folders -> WelcomePage.Permission
            else -> { dialog = "skip"; WelcomePage.Welcome }
        })
    }
    val shownFolders = if (preview == "folders") listOf(OnboardingFolder("preview", "My Music", "USB storage - My Music")) else state.folders
    WelcomeScreen(page, shownFolders,
        onNext = {
            if (!busy) when (page) {
                WelcomePage.Welcome -> navigate(WelcomePage.Warning)
                WelcomePage.Warning -> navigate(WelcomePage.Permission)
                WelcomePage.Folders -> if (state.folders.isEmpty()) dialog = "empty" else finish()
                else -> Unit
            }
        },
        onPermission = {
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) navigate(WelcomePage.Folders)
            else permissionLauncher.launch(permission)
        },
        onAdd = { sources.add() },
        onRemove = { uri -> if (!busy) save(state.remove(uri)) },
        onExclusions = { if (!busy) dialog = "exclusions" })
    if (dialog == "exclusions") ExclusionsDialog(state, onDismiss = { dialog = null }, onChange = { save(it) })
    else if (dialog != null) AlertDialog(onDismissRequest = { dialog = null },
        title = { Text(when (dialog) { "permission-denied" -> "Storage access denied"; "skip" -> "Skip setup?"; else -> "Continue without folders?" }) },
        text = { Text(if (dialog == "permission-denied") "Allow music access in Android settings, or continue to select individual folders using the system picker."
            else "You can add music folders later in Settings.") },
        confirmButton = { TextButton(onClick = {
            if (dialog == "permission-denied") { dialog = null; navigate(WelcomePage.Folders) } else { skipped = dialog == "skip"; finish() }
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = {
            if (dialog == "permission-denied") {
                dialog = null
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            } else dialog = null
        }) { Text(if (dialog == "permission-denied") "Open settings" else "Cancel") } })
    SourceManagementError(sources)
    if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Setup could not finish") },
        text = { Text(error!!) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
}

/** Browse only granted trees; all provider queries run on IO, never during composition. */
@Composable
internal fun ExclusionsDialog(state: OnboardingState, onDismiss: () -> Unit, onChange: (OnboardingState) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    var directory by remember { mutableStateOf<Uri?>(null) }
    var parents by remember { mutableStateOf(emptyList<Uri>()) }
    var children by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var pendingExclusion by remember { mutableStateOf<String?>(null) }
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
                            if (excluded) pendingExclusion = uri else onChange(state.copy(exclusions = state.exclusions - uri))
                        })
                        TextButton(onClick = { parents = parents + directory!!; directory = Uri.parse(uri) }) { Text(name) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
    pendingExclusion?.let { uri -> AlertDialog(onDismissRequest = { pendingExclusion = null },
        title = { Text("Exclude folder?") }, text = { Text("Tracks in this folder will be removed from the library, playlists, Favorites and history unless another selected source includes them. Music files are kept.") },
        confirmButton = { TextButton(onClick = { onChange(state.copy(exclusions = state.exclusions + uri)); pendingExclusion = null }) { Text("Exclude") } },
        dismissButton = { TextButton(onClick = { pendingExclusion = null }) { Text("Cancel") } }) }
}

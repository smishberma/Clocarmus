package com.example.clocarmus.ui.startup

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SourceManagement(initial: OnboardingState) {
    var state by mutableStateOf(initial)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    lateinit var save: (OnboardingState, suspend () -> Unit) -> Unit
    lateinit var add: () -> Unit
    fun save(next: OnboardingState) = save(next) {}
}

@Composable
internal fun rememberSourceManagement(initial: OnboardingState, preview: Boolean = false): SourceManagement {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("onboarding", 0) }
    val model = remember { SourceManagement(if (preview) initial else loadOnboarding(prefs)) }
    val scope = rememberCoroutineScope()
    model.save = { next, done ->
        if (!model.busy) {
            model.busy = true
            scope.launch {
                // Finish an accepted durable change even if navigation disposes this screen.
                withContext(kotlinx.coroutines.NonCancellable) {
                    try {
                        if (!preview) withContext(Dispatchers.IO) { saveOnboarding(prefs, next) }
                        model.state = next
                        done()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (exception: Exception) { model.error = exception.message ?: "Could not save folders. Please try again." }
                    finally { model.busy = false }
                }
            }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            model.busy = true
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
                val next = model.state.add(folder)
                withContext(Dispatchers.IO) { saveOnboarding(prefs, next) }
                model.state = next
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { model.error = "Could not access this folder. Please select it again." }
            finally { model.busy = false }
        }
    }
    model.add = {
        if (!model.busy) try { folderLauncher.launch(null) }
        catch (_: android.content.ActivityNotFoundException) { model.error = "No system folder picker is available on this device." }
    }
    return model
}

@Composable
internal fun SourceManagementError(model: SourceManagement) {
    model.error?.let { message -> AlertDialog(onDismissRequest = { model.error = null },
        title = { Text("Could not save folders") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { model.error = null }) { Text("OK") } }) }
}

package com.example.clocarmus.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.clocarmus.ClocarmusApplication
import kotlinx.coroutines.launch

@Composable
internal fun LibraryScanDialog(onDismiss: () -> Unit) {
    val repository = (LocalContext.current.applicationContext as ClocarmusApplication).library
    val snapshot by repository.state.collectAsState()
    val summary = snapshot.summary
    val scope = rememberCoroutineScope()
    var cleanup by remember { mutableStateOf<Set<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (summary.running) "Scanning music…" else "Library scan") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("${snapshot.records.size} tracks indexed\n${summary.added} added · ${summary.updated} updated\n${summary.missing} confirmed missing · ${summary.failures.size} failures")
            if (summary.incomplete) Text("Scan incomplete or cancelled. Unvisited files are kept.")
            summary.failures.forEach { Text(it) }
            error?.let { Text(it) }
            TextButton(onClick = { repository.refresh() }) { Text("Rescan all sources") }
            if (summary.running) TextButton(onClick = { repository.cancel() }) { Text("Cancel scan") }
            val eligible = snapshot.records.filter { it.availability == Availability.MISSING }.map { it.id }.toSet()
            TextButton(enabled = eligible.isNotEmpty() && !summary.running, onClick = { cleanup = eligible }) { Text("Remove unavailable tracks (${eligible.size})") }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
    cleanup?.let { ids -> AlertDialog(onDismissRequest = { cleanup = null },
        title = { Text("Remove ${ids.size} missing tracks?") },
        text = { Text("Remove these confirmed-missing records and their playlist, Favorites and history references. Music files are kept. Disconnected and inaccessible files are excluded.") },
        confirmButton = { TextButton(onClick = { scope.launch {
            try { repository.removeMissing(ids); cleanup = null }
            catch (exception: Exception) { error = exception.message ?: "Could not remove records"; cleanup = null }
        } }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { cleanup = null }) { Text("Cancel") } }) }
}

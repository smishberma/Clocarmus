package com.example.clocarmus.ui.player

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/** One validation and naming interaction for overview creation, rename, and player creation. */
@Composable
internal fun PlaylistNamePrompt(
    library: PlaylistLibrary, draft: String, onDraft: (String) -> Unit,
    onDismiss: () -> Unit, onConfirm: () -> Unit, renamingId: String? = null
) {
    val error = library.nameError(draft, renamingId)
    val verb = if (renamingId == null) "Create" else "Rename"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("$verb playlist") },
        text = { OutlinedTextField(draft, onDraft, label = { Text("Playlist name") }, singleLine = true,
            isError = error != null && draft.isNotEmpty(), supportingText = { if (error != null) Text(error) }) },
        confirmButton = { TextButton(enabled = error == null, onClick = onConfirm) { Text(verb) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

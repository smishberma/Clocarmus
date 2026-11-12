package com.example.clocarmus.library

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.example.clocarmus.ui.player.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

internal data class AudioDocument(val uri: Uri, val location: String, val name: String, val mime: String, val size: Long, val modified: Long)
internal val audioExtensions = setOf("mp3", "flac", "wav", "ogg", "opus", "m4a", "aac", "amr", "3gp", "mp4")
internal fun audioCandidate(name: String, mime: String) = mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in audioExtensions
internal fun documentLocation(uri: Uri, id: String) = "${uri.authority}:$id"

/** Full-file SHA-256 is deliberately off the UI thread; cached when reliable attributes agree. */
internal suspend fun readAudio(context: Context, document: AudioDocument, id: String): Pair<TrackMenuEntry, String> {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, document.uri)
        fun tag(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
        check(tag(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") { "No supported audio stream" }
        val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        // DATE may be the container creation timestamp (for example an M4A conversion).
        val date = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)
        val number = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
        val hash = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(document.uri)?.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        } ?: error("Could not read audio")
        val fingerprint = hash.digest().joinToString("") { "%02x".format(it) }
        val artwork = runCatching { retriever.embeddedPicture?.let { data ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 768) sample *= 2
            BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })?.let { bitmap ->
                val dir = File(context.filesDir, "artwork").apply { mkdirs() }
                val file = File(dir, "$fingerprint.jpg")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bitmap.recycle()
                file.absolutePath
            }
        }
        }.getOrNull()
        return TrackMenuEntry(
            title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: document.name,
            artist = artist ?: "Unknown artist", album = album ?: "Unknown album",
            year = date?.let { Regex("\\d{4}").find(it)?.value }, id = id,
            albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            discNumber = tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.substringBefore('/')?.toIntOrNull(),
            trackNumber = number?.substringBefore('/')?.toIntOrNull(),
            durationSeconds = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { (it / 1000).toInt() },
            genres = tag(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { listOf(it) }.orEmpty(),
            tags = TrackTags(artists = listOfNotNull(artist), album = album, releaseDate = date,
                trackNumber = number, composer = tag(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                unreadFields = setOf("Comment", "Description", "Copyright", "Publisher") +
                    if (number?.contains('/') == true) emptySet() else setOf("Total album tracks")), artworkPath = artwork
        ) to fingerprint
    } finally { retriever.release() }
}

internal interface DocumentAccess {
    fun children(tree: Uri, id: String): android.database.Cursor
    suspend fun audio(document: AudioDocument, id: String): Pair<TrackMenuEntry, String>
}
internal class AndroidDocumentAccess(private val context: Context) : DocumentAccess {
    override fun children(tree: Uri, id: String): android.database.Cursor =
        context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null) ?: error("Folder unavailable")
    override suspend fun audio(document: AudioDocument, id: String) = readAudio(context, document, id)
}

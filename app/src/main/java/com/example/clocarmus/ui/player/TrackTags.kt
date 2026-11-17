package com.example.clocarmus.ui.player

import java.io.Serializable

/** Saved file tags, kept separately from normalized browser labels and library fallbacks.
 * null on TrackMenuEntry means no metadata snapshot; an empty snapshot means known absent tags.
 * The library retains this snapshot even while the source is disconnected.
 */
internal data class TrackTags(
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val releaseDate: String? = null,
    val comment: String? = null,
    val description: String? = null,
    val totalAlbumTracks: String? = null,
    val trackNumber: String? = null,
    val composer: String? = null,
    val copyright: String? = null,
    val publisher: String? = null,
    val unreadFields: Set<String> = emptySet()
) : Serializable {
    fun fields(): List<Pair<String, String?>> {
        val compound = trackNumber?.split('/', limit = 2).orEmpty()
        val date = releaseDate?.takeIf { it.isNotBlank() }
        val year = date?.let { Regex("^\\s*(\\d{4})(?:$|[-/.T\\s])").find(it)?.groupValues?.get(1) ?: it }
        return listOf(
            "Artist(s)" to artists.filterNot { it.isBlank() }.joinToString(", "),
            "Album" to album, "Year" to year, "Comment" to comment,
            "Description" to description,
            "Total album tracks" to (totalAlbumTracks?.takeIf { it.isNotBlank() } ?: compound.getOrNull(1)),
            "Track number" to compound.firstOrNull(), "Composer" to composer,
            "Copyright" to copyright, "Publisher" to publisher
        ).map { (label, value) -> label to value?.takeIf { it.isNotBlank() } }
    }
}

internal data class TrackInfoContent(val fields: List<Pair<String, String?>>, val notice: String?)
/** File type comes from the cached provider filename, never the song title or document URI. */
internal fun trackFileType(filename: String?): String? {
    val name = filename?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val extension = name.substringAfterLast('.', "").takeIf { it.isNotBlank() && name.substringBeforeLast('.').isNotBlank() }
        ?.uppercase(java.util.Locale.ROOT) ?: return null
    return if (extension == "M4A") "M4A (AAC)" else extension
}

internal fun trackInfoContent(track: TrackMenuEntry?, filename: String? = null, filePath: String? = null): TrackInfoContent {
    val fileType = trackFileType(filename)
    val fileFields = listOf("File type" to fileType, "File path" to filePath?.takeIf { it.isNotBlank() })
    return when {
        track == null || (!track.available && track.tags == null) ->
            TrackInfoContent(if (track != null) fileFields.filter { it.second != null } else emptyList(),
                "File unavailable — no saved tags available")
        else -> TrackInfoContent(fileFields + (track.tags ?: TrackTags()).fields().map { (label, value) ->
            label to if (label in track.tags?.unreadFields.orEmpty() && value == null) "Not read" else value
        }, if (!track.available) "File unavailable — showing saved tags" else null)
    }
}

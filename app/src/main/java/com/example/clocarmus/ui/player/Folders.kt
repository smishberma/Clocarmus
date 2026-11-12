package com.example.clocarmus.ui.player

/** Source-scoped demo tree. A source root has no physical parent outside its grant. */
internal data class FolderEntry(
    val id: String, val name: String, val parent: String? = null,
    val track: TrackMenuEntry? = null, val available: Boolean = true,
    val directory: Boolean = track == null
)

/** Compare digit runs by magnitude without overflowing, retaining a deterministic leading-zero tie. */
internal fun naturalNameCompare(a: String, b: String): Int {
    val left = Regex("[0-9]+|[^0-9]+").findAll(a).map { it.value }.toList()
    val right = Regex("[0-9]+|[^0-9]+").findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]; val y = right[i]
        val result = if (x[0] in '0'..'9' && y[0] in '0'..'9') {
            val nx = x.trimStart('0').ifEmpty { "0" }; val ny = y.trimStart('0').ifEmpty { "0" }
            nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
        } else x.compareTo(y, ignoreCase = true)
        if (result != 0) return result
    }
    return left.size.compareTo(right.size)
}

internal class FolderTree(val entries: List<FolderEntry>) {
    private val byId = entries.associateBy { it.id }
    fun get(id: String?): FolderEntry? = byId[id]
    fun source(id: String): FolderEntry? {
        var entry = get(id) ?: return null
        val seen = mutableSetOf<String>()
        while (entry.parent != null) {
            if (!seen.add(entry.id)) return null
            entry = get(entry.parent) ?: return null
        }
        return entry.takeIf { it.directory }
    }
    fun accessible(entry: FolderEntry): Boolean = entry.available && source(entry.id)?.available == true
    private fun relativePath(entry: FolderEntry, directory: String?): String {
        val parts = mutableListOf(entry.name)
        var parent = get(entry.parent)
        while (parent != null && parent.id != directory) {
            parts.add(parent.name); parent = get(parent.parent)
        }
        return parts.asReversed().joinToString("/")
    }
    fun path(entry: FolderEntry, directory: String?): String = relativePath(entry, directory)
    private fun beneath(entry: FolderEntry, directory: String?): Boolean {
        if (directory == null) return source(entry.id) != null
        var parent = entry.parent
        val seen = mutableSetOf<String>()
        while (parent != null && seen.add(parent)) {
            if (parent == directory) return true
            parent = get(parent)?.parent
        }
        return false
    }
    fun contents(directory: String?, query: String = ""): List<FolderEntry> = entries.filter {
        val eligible = it.directory || (it.track != null && (it.track.id.startsWith("local:") || it.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in
            setOf("mp3", "flac", "wav", "ogg", "opus", "m4a", "aac", "amr", "3gp", "mp4")))
        val validDirectory = directory == null || get(directory)?.let { parent -> parent.directory && accessible(parent) } == true
        eligible && validDirectory && if (query.isBlank()) it.parent == directory && (directory != null || it.directory)
        else beneath(it, directory) && accessible(it) && it.name.contains(query, true)
    }.sortedWith { a, b ->
        when {
            a.directory != b.directory -> if (a.directory) -1 else 1
            else -> naturalNameCompare(a.name, b.name).takeIf { it != 0 }
                ?: naturalNameCompare(relativePath(a, directory), relativePath(b, directory)).takeIf { it != 0 }
                ?: a.id.compareTo(b.id)
        }
    }
    fun queue(directory: String?, query: String = ""): List<TrackMenuEntry> = contents(directory, query)
        .mapNotNull { entry -> entry.track?.copy(available = entry.track.available && accessible(entry)) }.distinctBy { it.id }
}

internal val demoFolderTree = FolderTree(buildList {
    add(FolderEntry("music", "Music 01"))
    add(FolderEntry("oriental", "Music oriental"))
    add(FolderEntry("mix", "Mix"))
    add(FolderEntry("subfolder", "subfolder", "music"))
    fun audio(id: String, name: String, parent: String, title: String) {
        add(FolderEntry(id, name, parent, TrackMenuEntry(title, "Artist", "Album", "2026", id = id)))
    }
    audio("folder:abc", "01 - abc.flac", "music", "Abc")
    audio("folder:alley", "02 - alley.flac", "music", "Alley")
    audio("folder:banter", "03 - banter.mp3", "music", "Banter")
    audio("folder:nested", "02 - alley.flac", "subfolder", "Nested Alley")
    (1..18).forEach { index -> audio("folder:collection:$index", "$index - collection song.mp3", "long-folder", "Collection song $index") }
    audio("folder:2", "2-song.mp3", "mix", "Second song")
    audio("folder:10", "10-song.mp3", "mix", "Tenth song")
    add(FolderEntry("empty", "Empty folder", "oriental"))
    add(FolderEntry("long-folder", "A very long directory name that continues beyond the available folder label viewport", "oriental"))
})

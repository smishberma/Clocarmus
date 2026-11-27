package com.example.clocarmus.library

import java.io.InputStream

private const val MAX_ID3_BYTES = 16 * 1024 * 1024
private const val MAX_DATE_BYTES = 1024

/** Read only a leading ID3v2.3/v2.4 tag, never audio data or a provider filesystem path.
 * Allocation and reads are bounded even for corrupt tags. Compressed/encrypted frames
 * are ignored. The caller owns the stream and supplies its cancellation check.
 */
internal fun readId3Date(stream: InputStream, checkActive: () -> Unit = {}): String? {
    val header = ByteArray(10)
    if (!stream.readId3Bytes(header, checkActive) || String(header, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
    val version = header[3].toInt()
    if (version !in 3..4 || header[4].unsigned() == 255) return null
    val flags = header[5].unsigned()
    if (flags and (if (version == 3) 0x1f else 0x0f) != 0) return null
    val size = header.id3Size(6, synchsafe = true) ?: return null
    if (size !in 10..MAX_ID3_BYTES) return null
    val body = ByteArray(size)
    if (!stream.readId3Bytes(body, checkActive)) return null
    val end = if (version == 3 && flags and 0x80 != 0) body.removeUnsynchronisation(checkActive) else size
    var offset = 0
    if (flags and 0x40 != 0) {
        val extendedSize = body.id3Size(0, synchsafe = version == 4) ?: return null
        if (extendedSize < 6 || extendedSize > end - (if (version == 3) 4 else 0)) return null
        offset = extendedSize + if (version == 3) 4 else 0
    }
    var recording: String? = null
    var legacyYear: String? = null
    var release: String? = null
    while (end - offset >= 10) {
        checkActive()
        if ((offset until offset + 4).any { body[it].unsigned() !in 65..90 && body[it].unsigned() !in 48..57 }) break
        val id = String(body, offset, 4, Charsets.ISO_8859_1)
        val frameSize = body.id3Size(offset + 4, synchsafe = version == 4) ?: break
        val frameFlags = body[offset + 9].unsigned()
        offset += 10
        if (frameSize <= 0 || frameSize > end - offset) break
        val unsupported = if (version == 3) frameFlags and 0xdf != 0 else frameFlags and 0xbc != 0
        if (!unsupported && frameSize <= MAX_DATE_BYTES && id in setOf("TDRC", "TDRL", "TYER")) {
            val bytes = body.copyOfRange(offset, offset + frameSize)
            val length = if (version == 4 && (flags and 0x80 != 0 || frameFlags and 0x02 != 0)) {
                bytes.removeUnsynchronisation(checkActive)
            } else bytes.size
            var start = if (frameFlags and (if (version == 3) 0x20 else 0x40) != 0) 1 else 0
            if (version == 4 && frameFlags and 0x01 != 0) {
                if (start + 4 > length || bytes.id3Size(start, synchsafe = true) == null) {
                    offset += frameSize
                    continue
                }
                start += 4
            }
            val date = decodeId3Date(bytes, start, length)
            when (id) {
                "TDRC" -> if (recording == null) recording = date
                "TYER" -> if (legacyYear == null) legacyYear = date
                "TDRL" -> if (release == null) release = date
            }
        }
        offset += frameSize
    }
    // TDRC replaced TYER in v2.4. Never use encoding, tagging or file timestamps.
    return recording ?: legacyYear ?: release
}

private fun decodeId3Date(bytes: ByteArray, start: Int, end: Int): String? {
    if (start >= end) return null
    val charset = when (bytes[start].toInt()) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> return null
    }
    return String(bytes, start + 1, end - start - 1, charset).split('\u0000')
        .map { it.trim() }.firstOrNull { id3Timestamp.matches(it) }
}

private val id3Timestamp = Regex("[0-9]{4}(?:-[0-9]{2}(?:-[0-9]{2}(?:T[0-9]{2}(?::[0-9]{2}(?::[0-9]{2})?)?)?)?)?")

private fun Byte.unsigned() = toInt() and 0xff

private fun ByteArray.id3Size(offset: Int, synchsafe: Boolean): Int? {
    if (offset < 0 || size - offset < 4) return null
    var value = 0L
    for (index in offset until offset + 4) {
        val byte = this[index].unsigned()
        if (synchsafe && byte >= 128) return null
        value = (value shl if (synchsafe) 7 else 8) or byte.toLong()
    }
    return value.takeIf { it <= Int.MAX_VALUE }?.toInt()
}

private fun InputStream.readId3Bytes(bytes: ByteArray, checkActive: () -> Unit): Boolean {
    var offset = 0
    while (offset < bytes.size) {
        checkActive()
        val count = read(bytes, offset, minOf(64 * 1024, bytes.size - offset))
        if (count < 0) return false
        if (count == 0) {
            val byte = read()
            if (byte < 0) return false
            bytes[offset++] = byte.toByte()
        } else offset += count
    }
    return true
}

/** Decode in place to avoid a second tag-sized allocation. */
private fun ByteArray.removeUnsynchronisation(checkActive: () -> Unit): Int {
    var source = 0
    var target = 0
    while (source < size) {
        if (source % (64 * 1024) == 0) checkActive()
        val byte = this[source++]
        this[target++] = byte
        if (byte.unsigned() == 255 && source < size && this[source] == 0.toByte()) source++
    }
    return target
}

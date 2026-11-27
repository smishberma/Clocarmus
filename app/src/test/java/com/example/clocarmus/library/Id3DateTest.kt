package com.example.clocarmus.library

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class Id3DateTest {
    @Test fun recordingDateWinsRegardlessOfFrameOrder() {
        val recording = frame("TDRC", text("2007"))
        val release = frame("TDRL", text("2008-08-21"))
        assertEquals("2007", read(tag(recording + release)))
        assertEquals("2007", read(tag(release + recording)))
        assertEquals("2007", read(tag(recording + frame("TDRL", text("2007-08-21")))))
    }

    @Test fun releaseDateFillsAbsentOrInvalidRecordingDate() {
        val release = frame("TDRL", text("2007-08-21"))
        for (invalid in listOf("", "unknown", "12345", "copyright 2001")) {
            assertEquals("2007-08-21", read(tag(frame("TDRC", text(invalid)) + release)))
        }
        assertEquals("2007-08-21", read(tag(release)))
    }

    @Test fun allTextEncodingsAndNullSeparatedValuesAreSupported() {
        for (encoding in 0..3) {
            assertEquals("2007-08-21T12:30:00", read(tag(frame("TDRC", text("2007-08-21T12:30:00\u0000", encoding)))))
        }
        assertEquals("2007", read(tag(frame("TDRC", text("\u00002007\u00002008")))))
        assertNull(read(tag(frame("TDRC", byteArrayOf(4) + "2007".toByteArray()))))
    }

    @Test fun legacyYearAndVersionSpecificFrameSizesAreSupported() {
        for (version in 3..4) {
            val unrelated = frame("APIC", ByteArray(300), version)
            assertEquals("1999", read(tag(unrelated + frame("TYER", text("1999", 1), version), version)))
        }
    }

    @Test fun extendedHeadersAreSkippedUsingTheirVersionSpecificSizes() {
        val v3Extended = size(6, false) + ByteArray(6)
        val v4Extended = size(6) + byteArrayOf(1, 0)
        assertEquals("2007", read(tag(v3Extended + frame("TYER", text("2007"), 3), 3, 0x40)))
        assertEquals("2007", read(tag(v4Extended + frame("TDRC", text("2007")), 4, 0x40)))
        assertNull(read(tag(size(1) + frame("TDRC", text("2007")), 4, 0x40)))
    }

    @Test fun tagAndFrameUnsynchronisationPreserveUtf16DatesAndFrameBoundaries() {
        val payload = text("2007", 1)
        val v3Body = frame("APIC", byteArrayOf(-1, -32, -1, 0), 3) + frame("TYER", payload, 3)
        assertEquals("2007", read(tag(unsynchronise(v3Body), 3, 0x80)))
        for (tagFlags in listOf(0, 0x80)) {
            assertEquals("2007", read(tag(frame("TDRC", unsynchronise(payload), flags = 0x02), flags = tagFlags)))
        }
    }

    @Test fun groupingAndDataLengthIndicatorsAreSkipped() {
        val date = text("2007")
        assertEquals("2007", read(tag(frame("TDRC", byteArrayOf(7) + size(date.size) + date, flags = 0x41))))
        assertEquals("2007", read(tag(frame("TYER", byteArrayOf(7) + date, 3, 0x20), 3)))
        assertNull(read(tag(frame("TDRC", byteArrayOf(0), flags = 0x01))))
    }

    @Test fun compressedEncryptedAndUnknownFramesCannotSupplyDates() {
        for (flag in listOf(0x04, 0x08, 0x80)) {
            assertEquals("2008", read(tag(frame("TDRC", text("2007"), flags = flag) + frame("TDRL", text("2008")))))
        }
        for (flag in listOf(0x40, 0x80)) {
            assertNull(read(tag(frame("TYER", text("2007"), 3, flag), 3)))
        }
        assertNull(read(tag(frame("TDEN", text("2007")) + frame("TDTG", text("2008")))))
    }

    @Test fun corruptTruncatedAndUnsupportedTagsReturnNoDate() {
        val valid = tag(frame("TDRC", text("2007")))
        for (length in 0 until valid.size) assertNull(read(valid.copyOf(length)))
        assertNull(read(valid.copyOf().apply { this[3] = 5 }))
        assertNull(read(valid.copyOf().apply { this[6] = -1 }))
        assertNull(read(valid.copyOf().apply { this[14] = -1 }))
        assertNull(read(tag("TDRC".toByteArray() + size(Int.MAX_VALUE, false) + byteArrayOf(0, 0), 3)))
        assertNull(read("not an ID3 audio file".toByteArray()))
    }

    @Test fun oversizedTagsAreRejectedBeforeReadingBodyAndLargeDateFramesAreSkipped() {
        val headerOnly = ByteArrayInputStream("ID3".toByteArray() + byteArrayOf(4, 0, 0) + size(16 * 1024 * 1024 + 1))
        val guarded = object : InputStream() {
            override fun read(): Int = error("Should only read the header")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                check(headerOnly.available() > 0)
                return headerOnly.read(bytes, offset, length)
            }
        }
        assertNull(readId3Date(guarded))
        assertEquals("2008", read(tag(frame("TDRC", text("2007" + " ".repeat(1024))) + frame("TDRL", text("2008")))))
    }

    @Test fun shortReadsWorkAndAudioBytesRemainUnread() {
        val bytes = tag(frame("TDRC", text("2007"))) + byteArrayOf(42)
        val stream = object : ByteArrayInputStream(bytes) {
            override fun read(bytes: ByteArray, offset: Int, length: Int) = super.read(bytes, offset, minOf(3, length))
        }
        assertEquals("2007", readId3Date(stream))
        assertEquals(42, stream.read())
    }

    @Test(expected = CancellationException::class)
    fun cancellationEscapesTheParser() {
        var checks = 0
        readId3Date(ByteArrayInputStream(tag(frame("APIC", ByteArray(200000))))) {
            if (++checks == 3) throw CancellationException()
        }
    }

    private fun read(bytes: ByteArray) = readId3Date(ByteArrayInputStream(bytes))
    private fun text(value: String, encoding: Int = 3): ByteArray {
        val charset = listOf(Charsets.ISO_8859_1, Charsets.UTF_16, Charsets.UTF_16BE, Charsets.UTF_8)[encoding]
        return byteArrayOf(encoding.toByte()) + value.toByteArray(charset)
    }
    private fun size(value: Int, synchsafe: Boolean = true): ByteArray =
        (3 downTo 0).map { ((value ushr (it * if (synchsafe) 7 else 8)) and if (synchsafe) 127 else 255).toByte() }.toByteArray()
    private fun frame(id: String, bytes: ByteArray, version: Int = 4, flags: Int = 0) =
        id.toByteArray() + size(bytes.size, version == 4) + byteArrayOf(0, flags.toByte()) + bytes
    private fun tag(body: ByteArray, version: Int = 4, flags: Int = 0) =
        "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, flags.toByte()) + size(body.size) + body
    private fun unsynchronise(bytes: ByteArray): ByteArray = bytes.flatMap {
        if (it == (-1).toByte()) listOf(it, 0.toByte()) else listOf(it)
    }.toByteArray()
}

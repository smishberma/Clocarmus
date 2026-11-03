package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test

class FoldersTest {
    @Test fun directQueueExcludesDescendantsAndSearchSnapshotsOnlyMatchingAudio() {
        val tree = demoFolderTree
        assertEquals(listOf("folder:abc", "folder:alley", "folder:banter"), tree.queue("music").map { it.id })
        assertEquals(listOf("folder:alley", "folder:nested"), tree.queue("music", "alley").map { it.id })
        assertTrue(tree.queue("music", "subfolder").isEmpty())
        val snapshot = tree.queue("music")
        tree.queue("music", "banter")
        assertEquals(3, snapshot.size)
        assertEquals("Alley", snapshot[1].title)
    }

    @Test fun rootAndParentsStayWithinSelectedSourcesAndEmptyDirectoriesRemainVisible() {
        val tree = demoFolderTree
        assertEquals(listOf("mix", "music", "oriental"), tree.contents(null).map { it.id })
        assertNull(tree.get("music")!!.parent)
        assertEquals("music", tree.source("folder:nested")!!.id)
        assertEquals("subfolder", tree.get("folder:nested")!!.parent)
        assertTrue(tree.contents("empty").isEmpty())
        assertTrue(tree.contents("oriental").any { it.id == "empty" })
        assertTrue(tree.contents("music", "song").isEmpty())
    }

    @Test fun naturalOrderHandlesLargeNumbersCaseAndStableDuplicatePaths() {
        assertTrue(naturalNameCompare("2-song.mp3", "10-song.mp3") < 0)
        assertTrue(naturalNameCompare("99999999999999999999.mp3", "100000000000000000000.mp3") < 0)
        assertEquals(0, naturalNameCompare("ALLEY", "alley"))
        assertEquals(listOf("folder:2", "folder:10"), demoFolderTree.queue("mix").map { it.id })
        val results = demoFolderTree.contents(null, "alley")
        assertEquals(listOf("Music 01/02 - alley.flac", "Music 01/subfolder/02 - alley.flac"),
            results.map { demoFolderTree.path(it, null) })
    }

    @Test fun recursiveSearchIncludesFoldersAndFilenamesButNeverMetadata() {
        assertEquals(listOf("subfolder"), demoFolderTree.contents("music", "SUBFOLDER").map { it.id })
        assertTrue(demoFolderTree.contents(null, "Artist").isEmpty())
        assertEquals(4, demoFolderTree.contents("music", ".").size)
    }

    @Test fun unsupportedFilesAndRootAudioNeverAppearInDirectoryResults() {
        val tree = FolderTree(demoFolderTree.entries + listOf(
            FolderEntry("readme", "readme.txt", "music", directory = false),
            FolderEntry("fake-audio", "image.png", "music", TrackMenuEntry("Image", "", "")),
            FolderEntry("root-audio", "outside.mp3", track = TrackMenuEntry("Outside", "", ""))))
        assertEquals(4, tree.contents("music").size)
        assertTrue(tree.contents(null).all { it.directory })
        assertTrue(tree.contents(null, "image").isEmpty())
        assertTrue(tree.contents("missing").isEmpty())
    }

    @Test fun disconnectedSourceStaysAtRootAndUnavailableQueueUsesFiniteSkipping() {
        val tree = FolderTree(demoFolderTree.entries.map { if (it.id == "music") it.copy(available = false) else it })
        assertTrue(tree.contents(null).any { it.id == "music" && !tree.accessible(it) })
        assertTrue(tree.contents(null, "alley").isEmpty())
        assertNull(playableTrack(tree.queue("music"), "folder:alley"))
        assertTrue(tree.queue("music").isEmpty())
    }
}

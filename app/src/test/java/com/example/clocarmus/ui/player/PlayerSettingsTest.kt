package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlayerSettingsTest {
    @Test fun removingSourceRemovesItsDescendantsOnly() {
        val tree = PlayerSettings(sources = setOf("mix")).folderTree(demoFolderTree)
        assertEquals(listOf("mix"), tree.contents(null).map { it.id })
        assertNull(tree.get("music"))
        assertNull(tree.get("folder:alley"))
        assertEquals(2, tree.queue("mix").size)
    }

    @Test fun exclusionsRemoveWholeSubtreeAndCanBeRestored() {
        val settings = PlayerSettings(exclusions = setOf("subfolder"))
        val tree = settings.folderTree(demoFolderTree)
        assertNotNull(tree.get("music"))
        assertNull(tree.get("subfolder"))
        assertNull(tree.get("folder:nested"))
        assertNotNull(tree.get("folder:alley"))
        assertNotNull(settings.copy(exclusions = emptySet()).folderTree(demoFolderTree).get("folder:nested"))
    }

    @Test fun resetSourcesLeavesEmptyTree() {
        assertTrue(PlayerSettings(sources = emptySet()).folderTree(demoFolderTree).entries.isEmpty())
    }
}

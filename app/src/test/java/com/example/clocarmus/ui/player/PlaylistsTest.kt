package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlaylistsTest {
    @Test fun namesAreTrimmedValidatedAndSortedWithoutChangingIdentity() {
        var library = PlaylistLibrary().create("  Zebra  ").create("alpha")
        assertEquals(listOf("alpha", "Zebra"), library.sortedCustom.map { it.name })
        assertNotNull(library.nameError("  "))
        listOf("FAVORITES", " recently ADDED ", "Recently played", "ALPHA").forEach { assertNotNull(library.nameError(it)) }
        val id = library.custom.first().id
        library = library.add(id, "one", 1).rename(id, "  Aardvark ")
        assertEquals(id, library.sortedCustom.first().id)
        assertEquals(listOf("one"), library.trackIds(id))
        assertNull(library.nameError("aardvark", id))
        assertEquals(library, library.rename(id, "favorites"))
    }
    @Test fun membershipIsUniqueAppendOnlyAndScoped() {
        var library = PlaylistLibrary().create("Trip").create("Other")
        val id = library.custom.first().id
        val other = library.custom.last().id
        library = library.add(id, "a", 1).add(id, "b", 2).add(id, "a", 3).add(other, "a", 4)
        assertEquals(listOf("a", "b"), library.trackIds(id))
        library = library.remove(id, "a")
        assertEquals(listOf("b"), library.trackIds(id))
        assertEquals(listOf("a"), library.trackIds(other))
    }
    @Test fun reorderAndDeletionLeaveQueueSnapshotIntact() {
        var library = PlaylistLibrary().create("Trip")
        val id = library.custom.first().id
        listOf("a", "b", "c").forEach { library = library.add(id, it, 1) }
        val queue = library.trackIds(id).toList()
        library = library.reorder(id, "c", 0)
        assertEquals(listOf("c", "a", "b"), library.trackIds(id))
        library = library.reorder(id, "c", 100)
        assertEquals(queue, library.trackIds(id))
        assertEquals(library, library.reorder(id, "missing", 0))
        library = library.delete(id)
        assertTrue(library.custom.isEmpty())
        assertEquals(listOf("a", "b", "c"), queue)
    }
    @Test fun favoritesUseOriginalMembershipTimeUntilRemovedAndAddedAgain() {
        var library = PlaylistLibrary().add(FAVORITES, "a", 1).add(FAVORITES, "b", 2).add(FAVORITES, "a", 3)
        assertEquals(listOf("b", "a"), library.trackIds(FAVORITES))
        library = library.remove(FAVORITES, "a").add(FAVORITES, "a", 4)
        assertEquals(listOf("a", "b"), library.trackIds(FAVORITES))
        assertEquals(library, library.delete(FAVORITES).rename(FAVORITES, "Changed"))
    }
    @Test fun playerCreationValidatesTargetAndAddsOnlyToTheNewPlaylist() {
        val original = PlaylistLibrary().create("Existing").add(FAVORITES, "selected", 1)
        assertEquals(original, original.createForTrack("New", "selected", "advanced", 2))
        assertEquals(original, original.createForTrack("New", "selected", null, 2))
        listOf(" ", "favorites", "EXISTING").forEach {
            assertEquals(original, original.createForTrack(it, "selected", "selected", 2))
        }
        val created = original.createForTrack("  New  ", "selected", "selected", 2)
        assertEquals("New", created.custom.last().name)
        assertEquals(listOf("selected"), created.custom.last().tracks)
        assertTrue(created.custom.first().tracks.isEmpty())
        assertEquals(original.favorites, created.favorites)
        assertTrue(original.create("Overview").custom.last().tracks.isEmpty())
    }
    @Test fun playerMembershipIndicatorExcludesRecentListsAndClearsWithLastMembership() {
        var library = PlaylistLibrary().imported("selected", 1)
            .playbackStarted(TrackMenuEntry("Selected", "Artist", "Album", id = "selected"), 1).create("Trip")
        val id = library.custom.single().id
        assertFalse(library.hasMembership("selected"))
        library = library.toggleMembership(FAVORITES, "selected", 2)
        assertTrue(library.hasMembership("selected"))
        library = library.toggleMembership(id, "selected", 3).toggleMembership(FAVORITES, "selected", 4)
        assertTrue(library.hasMembership("selected"))
        assertEquals(listOf("selected"), library.trackIds(id))
        library = library.toggleMembership(id, "selected", 5)
        assertFalse(library.hasMembership("selected"))
        assertEquals(listOf("selected"), library.trackIds(RECENT_PLAYED))
    }
    @Test fun recentlyAddedBoundsDisplayAndPreservesEvictedImportTimesOnReconnect() {
        var library = PlaylistLibrary()
        repeat(51) { library = library.imported("$it", it.toLong()) }
        assertEquals(50, library.trackIds(RECENT_ADDED).size)
        assertEquals("50", library.trackIds(RECENT_ADDED).first())
        assertFalse("0" in library.trackIds(RECENT_ADDED))
        assertEquals(library, library.imported("0", 1000))
        assertEquals(library, library.imported("50", 1000))
    }
    @Test fun startsSharingATimestampKeepNewestEventFirst() {
        val library = PlaylistLibrary().playbackStarted(TrackMenuEntry("a", "a", "b"), 1)
            .playbackStarted(TrackMenuEntry("b", "a", "b"), 1)
        assertEquals(listOf("b", "a"), library.trackIds(RECENT_PLAYED))
    }
    @Test fun recentlyPlayedBoundsDistinctStartsAndSkipsUnavailableTracks() {
        var library = PlaylistLibrary()
        repeat(51) { library = library.playbackStarted(TrackMenuEntry("$it", "a", "b"), it.toLong()) }
        assertEquals(50, library.trackIds(RECENT_PLAYED).size)
        library = library.playbackStarted(TrackMenuEntry("10", "a", "b"), 100)
        assertEquals("10", library.trackIds(RECENT_PLAYED).first())
        assertEquals(50, library.trackIds(RECENT_PLAYED).distinct().size)
        assertEquals(library, library.playbackStarted(TrackMenuEntry("missing", "a", "b", available = false), 200))
        val queue = library.trackIds(RECENT_PLAYED).toList()
        library = library.playbackStarted(TrackMenuEntry("11", "a", "b"), 101)
        assertEquals("10", queue.first())
        assertEquals("11", library.trackIds(RECENT_PLAYED).first())
    }
}

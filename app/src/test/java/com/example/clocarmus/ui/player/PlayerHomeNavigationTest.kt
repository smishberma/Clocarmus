package com.example.clocarmus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerHomeNavigationTest {
    @Test fun cancelsUnfinishedWorkBeforeSavingNavigationState() {
        var draft = "Unsubmitted name"
        var editing = true
        val home = PlayerHomeNavigation {
            assertEquals("", draft)
            assertEquals(false, editing)
        }
        home.registerCancellation { draft = ""; editing = false }
        home.returnToPlayer()
    }

    @Test fun disposedPanesDoNotReceiveCancellation() {
        val events = mutableListOf<String>()
        val home = PlayerHomeNavigation { events += "home" }
        val unregister = home.registerCancellation { events += "disposed" }
        home.registerCancellation { events += "active" }
        unregister()
        home.returnToPlayer()
        assertEquals(listOf("active", "home"), events)
    }
}

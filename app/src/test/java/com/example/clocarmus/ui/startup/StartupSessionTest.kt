package com.example.clocarmus.ui.startup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupSessionTest {
    @Test fun readyImmediatelyWithoutBrandingDelayAndInitializeOnlyOnce() = runTest {
        var reads = 0
        val session = StartupSession(backgroundScope, "fallback") { reads++; "saved" }
        assertTrue(session.claimBranding())
        runCurrent()
        assertEquals("saved", session.ready.value?.value)
        assertFalse(session.ready.value!!.failed)
        repeat(3) { assertFalse(session.claimBranding()) } // Resume/recreate in this process.
        assertEquals(1, reads)
    }

    @Test fun slowEssentialReadWaitsButDoesNotReclaimBrandingOnRecreation() = runTest {
        val read = CompletableDeferred<String>()
        val session = StartupSession(backgroundScope, "fallback") { read.await() }
        assertTrue(session.claimBranding())
        runCurrent()
        assertNull(session.ready.value)
        assertFalse(session.claimBranding())
        read.complete("saved")
        runCurrent()
        assertEquals("saved", session.ready.value?.value)
    }

    @Test fun recoverableFailureReachesDestinationWithDefaults() = runTest {
        val session = StartupSession(backgroundScope, "fallback") { throw java.io.IOException("Storage unavailable") }
        runCurrent()
        assertEquals("fallback", session.ready.value?.value)
        assertTrue(session.ready.value!!.failed)
    }

    @Test fun cancelledInitializationDoesNotBecomeAReadFailure() = runTest {
        val session = StartupSession(backgroundScope, "fallback") { awaitCancellation() }
        runCurrent()
        backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        runCurrent()
        assertNull(session.ready.value)
    }

    @Test fun latestLocalChangesSurviveActivityRecreationAndNewProcessHasFreshGate() = runTest {
        val session = StartupSession(backgroundScope, "fallback") { "saved" }
        runCurrent()
        session.claimBranding()
        session.update("edited")
        assertFalse(session.claimBranding())
        assertEquals("edited", session.ready.value?.value)
        val newProcess = StartupSession(backgroundScope, "fallback") { "edited" }
        assertTrue(newProcess.claimBranding())
        runCurrent()
        assertEquals("edited", newProcess.ready.value?.value)
    }
}

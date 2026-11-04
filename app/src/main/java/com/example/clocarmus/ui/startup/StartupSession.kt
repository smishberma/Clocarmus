package com.example.clocarmus.ui.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Process-owned initialization and a one-use branding gate; independent of Activity/Compose. */
internal class StartupSession<T>(scope: CoroutineScope, fallback: T, initialize: suspend () -> T) {
    data class Ready<T>(val value: T, val failed: Boolean = false)
    private val mutableReady = MutableStateFlow<Ready<T>?>(null)
    val ready = mutableReady.asStateFlow()
    private var brandingClaimed = false

    init {
        scope.launch {
            mutableReady.value = try {
                Ready(initialize())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A local read failure must not leave branding up indefinitely.
                Ready(fallback, failed = true)
            }
        }
    }

    @Synchronized fun claimBranding(): Boolean {
        if (brandingClaimed) return false
        brandingClaimed = true
        return true
    }

    fun update(value: T) {
        mutableReady.value = Ready(value, failed = mutableReady.value?.failed == true)
    }
}

package com.example.clocarmus.ui.player

import java.io.Serializable

internal enum class TrackInfoDestination { MAIN_PLAYER, TRACK_OPTIONS }

/** Snapshot the identity and explicit return destination independently of playback. */
internal data class TrackInfoRequest(
    val trackId: String,
    val returnTo: TrackInfoDestination
) : Serializable

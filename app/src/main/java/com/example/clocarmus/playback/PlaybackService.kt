package com.example.clocarmus.playback

import android.app.*
import android.content.*
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import com.example.clocarmus.ClocarmusApplication
import com.example.clocarmus.MainActivity
import com.example.clocarmus.R

/** Non-exported host; system media buttons arrive through the active MediaSession token. */
class PlaybackService : Service(), PlaybackHost {
    private lateinit var controller: PlaybackController
    private lateinit var session: MediaSession
    private var promoted = false
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { controller.pause() }
    }
    override fun onCreate() {
        super.onCreate()
        controller = (application as ClocarmusApplication).playback
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("playback", "Music playback", NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "Clocarmus").apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setSessionActivity(PendingIntent.getActivity(this@PlaybackService, 0,
                Intent(this@PlaybackService, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = this@PlaybackService.controller.play()
                override fun onPause() = this@PlaybackService.controller.pause()
                override fun onStop() = this@PlaybackService.controller.pause()
                override fun onSkipToNext() = this@PlaybackService.controller.step(1)
                override fun onSkipToPrevious() = this@PlaybackService.controller.step(-1)
                override fun onSeekTo(pos: Long) { this@PlaybackService.controller.positionState.value = (pos.coerceAtLeast(0) / 1000f) }
            })
            isActive = true
        }
        registerReceiver(noisy, IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        publish() // Promote before requesting audio focus on target 35.
        controller.hostReady(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "play" -> controller.play()
            "pause" -> controller.pause()
            "next" -> controller.step(1)
            "previous" -> controller.step(-1)
        }
        publish()
        return START_NOT_STICKY // Process recovery restores paused; never invent a Play command.
    }
    // Media-session notifications are exempt from POST_NOTIFICATIONS on API 33+.
    @android.annotation.SuppressLint("NotificationPermission")
    override fun publish() {
        if (!::session.isInitialized) return
        val playing = this@PlaybackService.controller.playingState.value
        val queue = this@PlaybackService.controller.queueState.value
        val track = (application as ClocarmusApplication).library.state.value.tracks.firstOrNull { it.id == queue.current?.trackId }
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, track?.title ?: "Clocarmus")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track?.artist.orEmpty())
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (track?.durationSeconds?.toLong() ?: 0) * 1000).build())
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (controller.positionState.value * 1000).toLong(), if (playing) 1f else 0f).build())
        fun action(command: String) = PendingIntent.getService(this, command.hashCode(), Intent(this, PlaybackService::class.java).setAction(command), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "playback") else Notification.Builder(this)
        val notification = builder.setSmallIcon(R.drawable.ic_notification_playback).setContentTitle(track?.title ?: "Clocarmus")
            .setContentText(track?.artist).setContentIntent(launch).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true).setOngoing(playing)
            .addAction(Notification.Action.Builder(R.drawable.player_skip_back, "Previous", action("previous")).build())
            .addAction(Notification.Action.Builder(if (playing) R.drawable.player_pause else R.drawable.player_play, if (playing) "Pause" else "Play", action(if (playing) "pause" else "play")).build())
            .addAction(Notification.Action.Builder(R.drawable.player_skip_next, "Next", action("next")).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
        if (playing || !promoted) { startForeground(42, notification); promoted = true }
        else getSystemService(NotificationManager::class.java).notify(42, notification)
        if (!playing) {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH) else stopForeground(false)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        unregisterReceiver(noisy)
        controller.hostDestroyed()
        session.release()
        super.onDestroy()
    }
}

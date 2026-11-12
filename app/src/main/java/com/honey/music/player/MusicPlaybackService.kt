package com.honey.music.player

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Playback service entry point.
 *
 * The player engine will be connected here as the playback layer is completed.
 * Keep the service contract valid for Android while the app is being assembled.
 */
class MusicPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}


package com.honey.music.player

import android.app.Service
import android.content.Intent
import android.os.IBinder

class MusicPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): START_STICKY {
        return START_STICKY
    }
}

package com.honey.music.player

import android.media.audiofx.Equalizer

class AudioEffectsController {
    private var equalizer: Equalizer? = null
    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId > 0) try { equalizer = Equalizer(0, audioSessionId).apply { enabled = true } } catch (_: Exception) { }
    }
    fun applyPreset(name: String) {
        val eq = equalizer ?: return
        try {
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val profiles = mapOf(
                "Normal" to floatArrayOf(0f,0f,0f,0f,0f), "Bass" to floatArrayOf(5f,4f,1f,0f,-1f),
                "Pop" to floatArrayOf(-1f,2f,4f,2f,-1f), "Rock" to floatArrayOf(4f,2f,-1f,3f,5f),
                "Classical" to floatArrayOf(3f,1f,-2f,2f,3f), "Jazz" to floatArrayOf(3f,1f,-1f,2f,4f))
            val profile = profiles[name] ?: profiles.getValue("Normal")
            for (i in 0 until bands) {
                val v = profile[(i * profile.size / bands).coerceIn(0, profile.lastIndex)]
                eq.setBandLevel(i.toShort(), (v * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            eq.enabled = true
        } catch (_: Exception) { }
    }
    fun release() { try { equalizer?.release() } catch (_: Exception) { }; equalizer = null }
}
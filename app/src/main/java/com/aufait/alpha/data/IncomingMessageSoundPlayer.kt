package com.aufait.alpha.data

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.SystemClock

class IncomingMessageSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var lastPlayAtMs: Long = 0L

    fun play() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayAtMs < MIN_INTERVAL_MS) return
        lastPlayAtMs = now

        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return
            val ringtone = RingtoneManager.getRingtone(appContext, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 700L
    }
}

package com.aufait.alpha.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aufait.alpha.MainActivity
import com.aufait.alpha.R
import java.util.concurrent.atomic.AtomicInteger

class BackgroundMessageNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val ids = AtomicInteger(1000)

    init {
        createChannelIfNeeded()
    }

    fun notifyIncomingMessage(fromPeer: String, body: String) {
        runCatching {
            val safeTitle = fromPeer.trim().ifBlank { appContext.getString(R.string.app_name) }.take(48)
            val safeBody = body.trim().ifBlank { appContext.getString(R.string.notification_new_message) }.replace('\n', ' ').take(180)
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setContentIntent(buildOpenAppIntent())
                .build()

            NotificationManagerCompat.from(appContext)
                .notify(ids.incrementAndGet(), notification)
        }
    }

    private fun buildOpenAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Messages Aufait",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications des nouveaux messages"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            enableVibration(true)
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "aufait_messages"
    }
}

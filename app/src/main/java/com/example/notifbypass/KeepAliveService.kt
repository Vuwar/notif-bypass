package com.example.notifbypass

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * A low-importance foreground service whose only job is to keep the app process
 * resident so MagicOS is far less likely to hibernate the NotificationListener.
 *
 * It does no real work — the persistent (silent, minimal) notification is the
 * price Android charges for staying alive in the background.
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "notifbypass_keepalive"
        private const val NOTIFICATION_ID = 1001

        /** Safe to call repeatedly; starting an already-running service is a no-op. */
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // START_STICKY: ask the system to recreate us if it kills the service.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotifBypass active")
            .setContentText("Listening for priority alerts")
            .setSmallIcon(R.drawable.ic_stat_bypass)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Keep-alive",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps NotifBypass running in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

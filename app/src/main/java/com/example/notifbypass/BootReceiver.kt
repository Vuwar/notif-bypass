package com.example.notifbypass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the keep-alive foreground service after the device reboots, so the
 * listener stays resilient without the user having to open the app manually.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                KeepAliveService.start(context)
            }
        }
    }
}

package com.example.notifbypass

import android.app.Notification
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MyNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifBypass"

        // Apps we care about
        private val TARGET_PACKAGES = setOf(
            "com.whatsapp",
            "com.instagram.android",
            "com.zhiliaoapp.musically" // TikTok
        )

        // The person whose notifications trigger the bypass.
        // Matching is case-insensitive and uses "contains" so partial titles work.
        private const val SPECIFIC_PERSON_NAME = "SpecificPersonName"

        // Unique pattern: 0ms delay -> 300ms vibrate -> 150ms pause -> 300ms vibrate
        private val VIBRATION_PATTERN = longArrayOf(0, 300, 150, 300)

        // Amplitudes matching the pattern (0 = off segment, 255 = full strength)
        private val VIBRATION_AMPLITUDES = intArrayOf(0, 255, 0, 255)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected.")
        // Make sure the keep-alive foreground service is running whenever the
        // listener binds (e.g. after the system rebinds us).
        KeepAliveService.start(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // 1. Filter by target apps
        if (packageName !in TARGET_PACKAGES) return

        // 2. Extract notification text fields
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        // For group/conversation notifications, the sender can appear in the
        // conversation title too — check all to be safe.
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString().orEmpty()

        val haystack = "$title $text $convTitle"

        // 3. Match the specific person (case-insensitive)
        if (haystack.contains(SPECIFIC_PERSON_NAME, ignoreCase = true)) {
            Log.d(TAG, "Match from $packageName — title='$title'. Triggering bypass vibration.")
            triggerBypassVibration()

            // Optional: also play a distinct sound through silent mode.
            // playEmergencySound()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for this use-case.
    }

    /**
     * Fires a custom haptic pattern that bypasses Silent / DND by tagging the
     * vibration as a NOTIFICATION_RINGTONE / SONIFICATION usage.
     *
     * USAGE_NOTIFICATION_RINGTONE is treated by the framework as a "ringer"
     * channel, which is exempt from the touch/haptic-feedback suppression that
     * normally silences vibrations in DND/Silent.
     */
    private fun triggerBypassVibration() {
        val vibrator = getSystemVibrator() ?: return

        // AudioAttributes that signal "this is a ringtone-class alert".
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        try {
            // minSdk is 26, so VibrationEffect is always available here.
            val effect = VibrationEffect.createWaveform(
                VIBRATION_PATTERN,
                VIBRATION_AMPLITUDES,
                -1 // -1 = do not repeat
            )
            vibrator.vibrate(effect, audioAttributes)
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    /**
     * Resolves the Vibrator across API levels.
     * Android 12+ (API 31): use VibratorManager.
     * Older: use the legacy VIBRATOR_SERVICE.
     */
    private fun getSystemVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * OPTIONAL / SCALABLE:
     * Plays a distinct sound that pushes through Silent mode by routing it to the
     * ALARM stream (alarms are exempt from Silent and most DND configurations).
     *
     * Replace the placeholder at res/raw/emergency_tone with a real audio file
     * (e.g. emergency_tone.mp3), then uncomment the call in onNotificationPosted().
     */
    fun playEmergencySound() {
        try {
            val mediaPlayer = MediaPlayer()
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            mediaPlayer.setAudioAttributes(audioAttributes)

            // Resolve res/raw/emergency_tone.* at runtime so the project still
            // compiles even when no such file exists yet. Returns 0 if missing.
            val resId = resources.getIdentifier("emergency_tone", "raw", packageName)
            if (resId == 0) {
                Log.w(TAG, "No res/raw/emergency_tone file found — skipping sound.")
                return
            }

            val afd = resources.openRawResourceFd(resId) ?: return
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Emergency sound failed", e)
        }
    }
}

package com.example.notifbypass

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Optional, DND-proof audio fallback. Plays the device's default ALARM sound
 * (alarms bypass Silent/DND) for a few seconds, then stops itself. Uses no bundled
 * audio file — it borrows whatever alarm tone the phone already has.
 *
 * Off by default; toggled via MatchConfig.getPlaySound().
 */
object SoundAlert {

    private const val TAG = "NotifBypass"
    private const val PLAY_MS = 4000L

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null

    fun play(ctx: Context) {
        stop() // never stack two tones
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return

            val r = RingtoneManager.getRingtone(ctx, uri) ?: return
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone = r
            r.play()
            handler.postDelayed({ stop() }, PLAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Alarm sound failed", e)
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        try {
            ringtone?.takeIf { it.isPlaying }?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Alarm sound stop failed", e)
        }
        ringtone = null
    }
}

package com.example.notifbypass

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Shared vibration playback. Used by both the listener (real alerts) and the
 * settings screen (Test buttons), so the two can never drift apart.
 *
 * All vibrations are tagged as ALARM-class so they bypass Silent AND DND haptic
 * suppression. (Ringtone/notification-class vibrations are tied to the ringer, so
 * they're silenced in full Silent mode — alarms are exempt.) Best-effort; OEM skins
 * may still interfere.
 */
object Haptics {

    private const val TAG = "NotifBypass"

    /** Resolves the system Vibrator across API levels. */
    fun getVibrator(ctx: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Amplitude per segment: 0 for OFF (even) indices, [onAmp] for ON (odd) indices. */
    fun amplitudesFor(timings: LongArray, onAmp: Int): IntArray =
        IntArray(timings.size) { if (it % 2 == 1) onAmp else 0 }

    /**
     * Plays [timings] with the given on-segment amplitude.
     * [repeatIndex] = -1 plays once; 0 loops the whole pattern until [cancel].
     */
    fun play(ctx: Context, timings: LongArray, onAmp: Int, repeatIndex: Int) {
        val vibrator = getVibrator(ctx) ?: return
        try {
            // Only send amplitudes if the motor supports them; otherwise the
            // on/off-only overload (every non-zero timing = ON) is the safe path.
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudesFor(timings, onAmp), repeatIndex)
            } else {
                VibrationEffect.createWaveform(timings, repeatIndex)
            }
            vibrator.vibrate(effect, bypassAttributes())
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    /** Stops any ongoing vibration started via [play]. */
    fun cancel(ctx: Context) {
        getVibrator(ctx)?.cancel()
    }

    /** ALARM usage → exempt from Silent and DND haptic suppression. */
    private fun bypassAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
}

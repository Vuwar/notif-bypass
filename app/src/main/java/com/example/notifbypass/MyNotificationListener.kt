package com.example.notifbypass

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MyNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifBypass"

        // Apps + per-app sender names AND the selected vibration patterns live in
        // MatchConfig / VibrationPatterns (editable at runtime from the settings UI).

        // On-segment amplitude by urgency (0..255).
        private const val AMP_GENTLE = 160   // Normal mode
        private const val AMP_FULL = 255     // Silent / DND

        // In a quiet mode, replay the one-shot text buzz this many times so it can't be missed.
        private const val TEXT_REPEATS_QUIET = 3
        private const val TEXT_REPEAT_GAP_MS = 250L

        // Safety cap: stop ringing after this long even if the call notification lingers
        // (e.g. a build that keeps an "ongoing call" notification after answering).
        private const val CALL_MAX_RING_MS = 30_000L
    }

    /** Notification key of the call currently being rung, so we can stop it when it ends. */
    private var activeCallKey: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopCallRunnable = Runnable { stopCallVibration() }

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
        if (packageName !in MatchConfig.targetPackages) return

        // 2. Extract notification text fields
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        // For group/conversation notifications, the sender can appear in the
        // conversation title too — check all to be safe.
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString().orEmpty()

        val haystack = "$title $text $convTitle"

        // 3. Match any configured alias for this app (case-insensitive, per-app)
        val aliases = MatchConfig.getAliases(this, packageName)
        if (aliases.isEmpty()) return
        if (aliases.none { haystack.contains(it, ignoreCase = true) }) return

        // 4. Call vs text → distinct haptics.
        val isCall = sbn.notification.category == Notification.CATEGORY_CALL
        if (isCall) {
            Log.d(TAG, "Call match from $packageName — title='$title'. Ringing.")
            startCallVibration(sbn.key)
        } else {
            Log.d(TAG, "Text match from $packageName — title='$title'. Buzzing.")
            triggerTextVibration()
        }

        // Optional: also play a distinct sound through silent mode.
        // playEmergencySound()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // When the ringing call's notification disappears (answered / declined /
        // missed), stop the repeating ring vibration.
        if (sbn.key != null && sbn.key == activeCallKey) {
            Log.d(TAG, "Call ended — stopping ring vibration.")
            stopCallVibration()
        }
    }

    /**
     * One-shot text buzz. Mode-aware: in Silent/DND it plays at full strength and
     * repeats so it can't be missed; in Normal mode it's a single gentle tap.
     */
    private fun triggerTextVibration() {
        val quiet = isQuietMode()
        val onAmp = if (quiet) AMP_FULL else AMP_GENTLE
        val repeats = if (quiet) TEXT_REPEATS_QUIET else 1

        val base = VibrationPatterns.text(MatchConfig.getTextPatternId(this)).timings
        val timings = repeatPattern(base, repeats, TEXT_REPEAT_GAP_MS)
        Haptics.play(this, timings, onAmp, -1) // -1 = play once
    }

    /**
     * Continuous ring for an incoming call. Loops the ring pattern until
     * [onNotificationRemoved] cancels it. Always full strength — it's a call.
     */
    private fun startCallVibration(key: String?) {
        // Already ringing for this exact call (notifications re-post as they update).
        if (key != null && key == activeCallKey) return
        activeCallKey = key

        // repeat = 0 → loop the whole pattern from the start until cancelled.
        val base = VibrationPatterns.call(MatchConfig.getCallPatternId(this)).timings
        Haptics.play(this, base, AMP_FULL, 0)

        // Safety net in case the call notification never gets removed.
        handler.removeCallbacks(stopCallRunnable)
        handler.postDelayed(stopCallRunnable, CALL_MAX_RING_MS)
    }

    /** Stops the looping call ring and clears call state. */
    private fun stopCallVibration() {
        handler.removeCallbacks(stopCallRunnable)
        activeCallKey = null
        Haptics.cancel(this)
    }

    /**
     * Concatenates [base] (a leading-0 waveform) [times] times, separated by [gapMs].
     * Each block has an even length so the ON-is-odd-index parity is preserved.
     */
    private fun repeatPattern(base: LongArray, times: Int, gapMs: Long): LongArray {
        if (times <= 1) return base
        val tail = base.copyOf().also { it[0] = gapMs } // subsequent blocks start with the gap
        val result = ArrayList<Long>(base.size * times)
        base.forEach { result.add(it) }
        repeat(times - 1) { tail.forEach { result.add(it) } }
        return result.toLongArray()
    }

    /** True when the phone is in DND or any non-normal ringer (Silent/Vibrate). */
    private fun isQuietMode(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val dndOn = nm != null &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerQuiet = am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL

        return dndOn || ringerQuiet
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

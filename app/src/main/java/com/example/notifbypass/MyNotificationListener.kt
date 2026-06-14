package com.example.notifbypass

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
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

        // In a quiet mode the text buzz repeats (count is user-configurable via
        // MatchConfig.getQuietRepeats), separated by this gap, so it can't be missed.
        private const val TEXT_REPEAT_GAP_MS = 250L

        // Safety cap: stop ringing after this long even if the call notification lingers
        // (e.g. a build that keeps an "ongoing call" notification after answering).
        private const val CALL_MAX_RING_MS = 30_000L
    }

    /** Notification key of the call currently being rung, so we can stop it when it ends. */
    private var activeCallKey: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopCallRunnable = Runnable { stopCallVibration() }

    /**
     * Last message signature we alerted for, per notification key. Lets us tell a
     * genuinely new message apart from a re-post/update of one we already buzzed for
     * (which Android also delivers via onNotificationPosted).
     */
    private val lastTextSignature = HashMap<String, String>()

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

        // 2. Ignore the bundle/summary notification. It aggregates EVERY chat, so it
        //    still contains our person while her message is unread — which would make
        //    us (wrongly) buzz whenever anyone else messages, and also double up with
        //    the per-chat notification.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // 3. Extract notification text fields
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString().orEmpty()

        // 4. Match a configured alias (whole-word, per-app; sender-only unless body
        //    matching is enabled).
        if (!MatchConfig.matches(this, packageName, title, text, convTitle)) return

        val label = MatchConfig.APPS.firstOrNull { it.pkg == packageName }?.label ?: packageName
        val isCall = sbn.notification.category == Notification.CATEGORY_CALL

        if (isCall) {
            // Re-posts of the same ringing call shouldn't re-trigger.
            if (sbn.key == activeCallKey) return
            Log.d(TAG, "Call match from $packageName. Ringing.")
            startCallVibration(sbn.key)
            MatchConfig.recordAlert(this, "$label call")
        } else {
            // 5. De-dupe: only alert on a genuinely NEW message, not on a re-post /
            //    update of one we already buzzed for (e.g. triggered by other chats).
            val signature = "${sbn.notification.`when`}|$title|$text"
            if (lastTextSignature[sbn.key] == signature) {
                Log.d(TAG, "Re-post of an already-alerted message ($title) — skipping.")
                return
            }
            lastTextSignature[sbn.key] = signature

            Log.d(TAG, "Text match from $packageName — title='$title'. Buzzing.")
            triggerTextVibration()
            MatchConfig.recordAlert(this, "$label text")
        }

        if (MatchConfig.getPlaySound(this)) SoundAlert.play(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Reading/dismissing the chat clears its signature, so the next message buzzes.
        lastTextSignature.remove(sbn.key)

        // When the ringing call's notification disappears (answered / declined /
        // missed), stop the repeating ring vibration.
        if (sbn.key == activeCallKey) {
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
        val repeats = if (quiet) MatchConfig.getQuietRepeats(this) else 1

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
}

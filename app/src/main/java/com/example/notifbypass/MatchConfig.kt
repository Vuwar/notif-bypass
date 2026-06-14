package com.example.notifbypass

import android.content.Context

/**
 * Single source of truth for which apps we watch and which sender names/handles
 * trigger the bypass alert for each one.
 *
 * Names are stored per-package in SharedPreferences as a single comma-separated
 * string, so they can be edited at runtime from [MainActivity] without
 * recompiling. The listener reads them live on every notification.
 */
object MatchConfig {

    private const val PREFS = "notifbypass_prefs"
    private fun keyFor(pkg: String) = "aliases_$pkg"

    /** A watched app: its package, a human label for the UI, and out-of-the-box defaults. */
    data class AppTarget(
        val pkg: String,
        val label: String,
        val defaultAliases: String
    )

    /**
     * The apps we watch. The same person appears under different names per app,
     * so each gets its own alias list (case-insensitive "contains" matching).
     */
    val APPS = listOf(
        AppTarget("com.whatsapp", "WhatsApp", "İlahə"),
        AppTarget("com.instagram.android", "Instagram", "Amirova, @ilheexs"),
        AppTarget("com.zhiliaoapp.musically", "TikTok", "ilo, @ilheexs")
    )

    /** Packages we should inspect at all. */
    val targetPackages: Set<String> = APPS.map { it.pkg }.toSet()

    /**
     * Raw, user-facing string for a package: the saved value if it has ever been
     * saved (even ""), otherwise the package's default. Used to prefill the UI.
     */
    fun getRaw(ctx: Context, pkg: String): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Saving "" deliberately disables an app, so only fall back to the default
        // when the key is absent (null).
        return prefs.getString(keyFor(pkg), null)
            ?: APPS.firstOrNull { it.pkg == pkg }?.defaultAliases
            ?: ""
    }

    /** Persist the raw alias string for a package. */
    fun setRaw(ctx: Context, pkg: String, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(keyFor(pkg), value)
            .apply()
    }

    /** Parsed alias list for matching: trimmed, blanks removed. */
    fun getAliases(ctx: Context, pkg: String): List<String> =
        getRaw(ctx, pkg)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Matching ---

    private const val KEY_MATCH_BODY = "match_body"

    /** Whether the message body is searched too (needed for group chats). Default on. */
    fun getMatchBody(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_MATCH_BODY, true)

    fun setMatchBody(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_MATCH_BODY, enabled).apply()

    /**
     * True if any configured alias for [pkg] appears (as a whole word) in the
     * notification's sender fields — and, when [getMatchBody] is on, its body text.
     * Whole-word matching stops short aliases (e.g. "ilo") matching inside other
     * words like "kilo" or "philosophy".
     */
    fun matches(
        ctx: Context,
        pkg: String,
        title: String,
        text: String,
        convTitle: String
    ): Boolean {
        val aliases = getAliases(ctx, pkg)
        if (aliases.isEmpty()) return false

        val haystack = buildString {
            append(title).append(' ').append(convTitle)
            if (getMatchBody(ctx)) append(' ').append(text)
        }
        return aliases.any { containsWholeWord(haystack, it) }
    }

    /**
     * Case-insensitive "contains", but the alias must not be flanked by letters/
     * digits on any alphanumeric edge. Uses locale-invariant lowercasing so the two
     * sides fold identically (avoids the Turkish dotted/dotless-i pitfall).
     */
    private fun containsWholeWord(haystack: String, alias: String): Boolean {
        if (alias.isEmpty()) return false
        val h = haystack.lowercase()
        val a = alias.lowercase()

        var idx = h.indexOf(a)
        while (idx >= 0) {
            val before = if (idx > 0) h[idx - 1] else ' '
            val after = if (idx + a.length < h.length) h[idx + a.length] else ' '
            val leftOk = !a.first().isLetterOrDigit() || !before.isLetterOrDigit()
            val rightOk = !a.last().isLetterOrDigit() || !after.isLetterOrDigit()
            if (leftOk && rightOk) return true
            idx = h.indexOf(a, idx + 1)
        }
        return false
    }

    // --- Last alert (proof of life) ---

    private const val KEY_LAST_ALERT_AT = "last_alert_at"
    private const val KEY_LAST_ALERT_DESC = "last_alert_desc"

    /** Record that an alert just fired, e.g. desc = "WhatsApp call". */
    fun recordAlert(ctx: Context, desc: String) =
        prefs(ctx).edit()
            .putLong(KEY_LAST_ALERT_AT, System.currentTimeMillis())
            .putString(KEY_LAST_ALERT_DESC, desc)
            .apply()

    /** Epoch millis of the last alert, or 0 if none. */
    fun getLastAlertAt(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_ALERT_AT, 0L)

    fun getLastAlertDesc(ctx: Context): String? =
        prefs(ctx).getString(KEY_LAST_ALERT_DESC, null)

    // --- Optional alarm sound fallback ---

    private const val KEY_PLAY_SOUND = "play_sound"

    /** Whether to also play the system alarm sound on a match. Default off. */
    fun getPlaySound(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_PLAY_SOUND, false)

    fun setPlaySound(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_PLAY_SOUND, enabled).apply()

    // --- Selected vibration patterns (ids from VibrationPatterns) ---

    private const val KEY_TEXT_PATTERN = "pattern_text"
    private const val KEY_CALL_PATTERN = "pattern_call"

    fun getTextPatternId(ctx: Context): String =
        prefs(ctx).getString(KEY_TEXT_PATTERN, null) ?: VibrationPatterns.TEXT.first().id

    fun setTextPatternId(ctx: Context, id: String) =
        prefs(ctx).edit().putString(KEY_TEXT_PATTERN, id).apply()

    fun getCallPatternId(ctx: Context): String =
        prefs(ctx).getString(KEY_CALL_PATTERN, null) ?: VibrationPatterns.CALL.first().id

    fun setCallPatternId(ctx: Context, id: String) =
        prefs(ctx).edit().putString(KEY_CALL_PATTERN, id).apply()

    // --- How many times a text buzz repeats in Silent/DND ("quiet" mode) ---

    private const val KEY_QUIET_REPEATS = "quiet_repeats"
    const val QUIET_REPEATS_MIN = 1
    const val QUIET_REPEATS_MAX = 5
    private const val QUIET_REPEATS_DEFAULT = 1

    /** Repeat count for the text buzz when the phone is Silent/DND. Clamped to 1..5. */
    fun getQuietRepeats(ctx: Context): Int =
        prefs(ctx).getInt(KEY_QUIET_REPEATS, QUIET_REPEATS_DEFAULT)
            .coerceIn(QUIET_REPEATS_MIN, QUIET_REPEATS_MAX)

    fun setQuietRepeats(ctx: Context, count: Int) =
        prefs(ctx).edit()
            .putInt(KEY_QUIET_REPEATS, count.coerceIn(QUIET_REPEATS_MIN, QUIET_REPEATS_MAX))
            .apply()
}

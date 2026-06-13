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

    // --- Selected vibration patterns (ids from VibrationPatterns) ---

    private const val KEY_TEXT_PATTERN = "pattern_text"
    private const val KEY_CALL_PATTERN = "pattern_call"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getTextPatternId(ctx: Context): String =
        prefs(ctx).getString(KEY_TEXT_PATTERN, null) ?: VibrationPatterns.TEXT.first().id

    fun setTextPatternId(ctx: Context, id: String) =
        prefs(ctx).edit().putString(KEY_TEXT_PATTERN, id).apply()

    fun getCallPatternId(ctx: Context): String =
        prefs(ctx).getString(KEY_CALL_PATTERN, null) ?: VibrationPatterns.CALL.first().id

    fun setCallPatternId(ctx: Context, id: String) =
        prefs(ctx).edit().putString(KEY_CALL_PATTERN, id).apply()
}

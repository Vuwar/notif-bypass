package com.example.notifbypass

/**
 * A named vibration rhythm. [timings] is in waveform format (index 0 = initial OFF
 * delay; odd indices are the ON buzzes — see [Haptics.amplitudesFor]). [loops] is
 * informational: call patterns loop until the call ends, text patterns play once.
 */
class VibePattern(
    val id: String,
    val label: String,
    val timings: LongArray,
    val loops: Boolean
)

/**
 * The selectable vibration menus, shared by the settings UI and the listener.
 * The first entry in each list is the default.
 *
 * NOTE: text patterns must have an even length so they tile cleanly when repeated
 * in quiet mode (keeps ON on odd indices). Call patterns may be any length — they
 * loop via the waveform repeat index, not by concatenation.
 */
object VibrationPatterns {

    val TEXT = listOf(
        VibePattern("heartbeat", "Heartbeat (lub-dub)", longArrayOf(0, 150, 100, 400), false),
        VibePattern("double", "Double-buzz", longArrayOf(0, 300, 150, 300), false),
        VibePattern("triple", "Triple-tap", longArrayOf(0, 120, 90, 120, 90, 120), false),
        VibePattern("long", "Long pulse", longArrayOf(0, 700), false)
    )

    val CALL = listOf(
        VibePattern("ring", "Phone ring", longArrayOf(0, 1000, 500, 1000, 500), true),
        VibePattern(
            "sos", "SOS (Morse)",
            longArrayOf(0, 200, 100, 200, 100, 200, 300, 500, 200, 500, 200, 500, 300), true
        ),
        VibePattern("rapid", "Rapid pulse", longArrayOf(0, 400, 200), true),
        VibePattern("gallop", "Gallop", longArrayOf(0, 150, 80, 150, 300), true)
    )

    /** Look up a text pattern by id, falling back to the default. */
    fun text(id: String?): VibePattern = TEXT.firstOrNull { it.id == id } ?: TEXT.first()

    /** Look up a call pattern by id, falling back to the default. */
    fun call(id: String?): VibePattern = CALL.firstOrNull { it.id == id } ?: CALL.first()
}

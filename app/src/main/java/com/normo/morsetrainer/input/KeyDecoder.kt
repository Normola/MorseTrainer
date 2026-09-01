package com.normo.morsetrainer.input

import com.normo.morsetrainer.core.MorseCode
import com.normo.morsetrainer.core.Timing
import kotlin.math.roundToLong

/** A character the decoder has committed to. */
data class DecodedChar(
    val code: String,
    /** Null when the code matches nothing in the alphabet. */
    val char: Char?,
)

/**
 * Turns key-down/key-up timings into characters.
 *
 * Hold length decides dit versus dah; the silence *after* a release decides whether the
 * character is finished and whether a word ended. Callers drive [poll] on a timer,
 * because both of those decisions are made by the passage of time, not by an event.
 *
 * With [adaptive] on, the unit length tracks how fast the operator is actually keying,
 * which matters far more than the configured speed for anyone still finding their rhythm.
 */
class KeyDecoder(
    timing: Timing,
    var adaptive: Boolean = true,
) {

    /** Estimated dit length in ms. */
    var unitMs: Double = timing.ditMs.toDouble()
        private set

    private var pressStartMs: Long = 0
    private var lastReleaseMs: Long = 0
    private var down = false
    private var wordPending = false

    /** Elements accumulated for the character in progress, e.g. "-.". */
    var buffer: String = ""
        private set

    val isDown: Boolean get() = down

    /** Configured speed, used directly when [adaptive] is off and as the starting guess. */
    var timing: Timing = timing
        set(value) {
            field = value
            if (!adaptive) unitMs = value.ditMs.toDouble()
        }

    fun reset() {
        buffer = ""
        down = false
        wordPending = false
        lastReleaseMs = 0
        unitMs = timing.ditMs.toDouble()
    }

    fun press(nowMs: Long) {
        if (down) return
        down = true
        pressStartMs = nowMs
    }

    /** Returns the element that was keyed, as a "." or "-", or null if the press was ignored. */
    fun release(nowMs: Long): String? {
        if (!down) return null
        down = false

        val heldMs = (nowMs - pressStartMs).coerceAtLeast(1)
        val unit = effectiveUnit()
        val symbol = if (heldMs < unit * DIT_DAH_BOUNDARY) "." else "-"

        if (adaptive) {
            val impliedUnit = if (symbol == ".") heldMs.toDouble() else heldMs / 3.0
            unitMs = (unitMs * (1 - ADAPT_RATE) + impliedUnit * ADAPT_RATE)
                .coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)
        }

        buffer += symbol
        lastReleaseMs = nowMs
        wordPending = true
        return symbol
    }

    /**
     * Call regularly while the key is up.
     *
     * Emits a character once the gap passes the character threshold, then a space once
     * it passes the word threshold. Returns null when nothing has been decided yet.
     */
    fun poll(nowMs: Long): DecodedChar? {
        if (down || lastReleaseMs == 0L) return null

        val gap = nowMs - lastReleaseMs
        val unit = effectiveUnit()

        if (buffer.isNotEmpty() && gap >= unit * CHAR_GAP_UNITS) {
            val code = buffer
            buffer = ""
            return DecodedChar(code, MorseCode.charFor(code))
        }

        if (buffer.isEmpty() && wordPending && gap >= unit * WORD_GAP_UNITS) {
            wordPending = false
            return DecodedChar(" ", ' ')
        }

        return null
    }

    /** Fraction of the way to the character-gap deadline, for a progress indicator. */
    fun charGapProgress(nowMs: Long): Float {
        if (down || buffer.isEmpty() || lastReleaseMs == 0L) return 0f
        val gap = (nowMs - lastReleaseMs).toDouble()
        return (gap / (effectiveUnit() * CHAR_GAP_UNITS)).coerceIn(0.0, 1.0).toFloat()
    }

    /** Speed implied by the current unit estimate. */
    fun estimatedWpm(): Int = (1200.0 / effectiveUnit()).roundToLong().toInt().coerceIn(1, 99)

    private fun effectiveUnit(): Double =
        if (adaptive) unitMs else timing.ditMs.toDouble()

    private companion object {
        /** A hold shorter than two units is a dit. */
        const val DIT_DAH_BOUNDARY = 2.0

        /** Textbook is 3; a little under that is more forgiving of a hesitant fist. */
        const val CHAR_GAP_UNITS = 2.6

        /** Textbook is 7. */
        const val WORD_GAP_UNITS = 6.0

        const val ADAPT_RATE = 0.25
        const val MIN_UNIT_MS = 25.0
        const val MAX_UNIT_MS = 500.0
    }
}

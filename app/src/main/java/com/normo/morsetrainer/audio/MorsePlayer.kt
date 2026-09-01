package com.normo.morsetrainer.audio

import com.normo.morsetrainer.core.Element
import com.normo.morsetrainer.core.MorseCode
import com.normo.morsetrainer.core.Timing
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** What the player is doing right now, for the UI to follow along with. */
data class PlayState(
    val playing: Boolean = false,
    /** Index into the text being sent, or -1. */
    val charIndex: Int = -1,
    /** The character currently being sent. */
    val char: Char? = null,
    /** Elements of the current character sent so far, e.g. "-." while sending N. */
    val partialCode: String = "",
    val keyDown: Boolean = false,
)

/**
 * Sends characters through the sidetone engine and the optional torch/vibration sinks.
 *
 * Timing is scheduled against a fixed start instant rather than by sleeping for each
 * gap in turn, so a slow frame or a delayed wake-up does not accumulate into audible
 * drift over a long message.
 */
class MorsePlayer(
    private val tone: SideToneEngine,
    private val outputs: OutputSinks,
) {

    /** Monotonic schedule anchored at construction. */
    private class Schedule {
        private val startNs = System.nanoTime()
        private var cursorMs = 0L

        suspend fun advance(ms: Long) {
            if (ms <= 0) return
            cursorMs += ms
            val remainingMs = (startNs + cursorMs * 1_000_000L - System.nanoTime()) / 1_000_000L
            if (remainingMs > 0) delay(remainingMs)
        }
    }

    /**
     * Sends [text], reporting progress through [onState].
     *
     * Cancelling the calling coroutine stops the tone and releases the torch.
     */
    suspend fun play(
        text: String,
        timing: Timing,
        onState: (PlayState) -> Unit = {},
    ) {
        val message = MorseCode.sanitize(text)
        if (message.isEmpty()) {
            onState(PlayState())
            return
        }

        val schedule = Schedule()
        try {
            message.forEachIndexed { index, ch ->
                coroutineContext.ensureActive()

                if (ch == ' ') {
                    onState(PlayState(playing = true, charIndex = index, char = ' '))
                    // A character gap is already added on either side of this space, so
                    // only the remainder of the word gap is owed here.
                    schedule.advance((timing.wordGapMs - timing.charGapMs * 2).coerceAtLeast(0))
                } else {
                    sendChar(ch, timing, schedule) { partial, keyDown ->
                        onState(
                            PlayState(
                                playing = true,
                                charIndex = index,
                                char = ch,
                                partialCode = partial,
                                keyDown = keyDown,
                            ),
                        )
                    }
                }

                if (index != message.lastIndex) schedule.advance(timing.charGapMs)
            }
        } finally {
            tone.key(false)
            outputs.keyUp()
            onState(PlayState())
        }
    }

    /** Sends a single character and leaves no trailing gap. */
    suspend fun playChar(
        ch: Char,
        timing: Timing,
        onState: (PlayState) -> Unit = {},
    ) {
        val schedule = Schedule()
        try {
            sendChar(ch, timing, schedule) { partial, keyDown ->
                onState(
                    PlayState(
                        playing = true,
                        charIndex = 0,
                        char = ch,
                        partialCode = partial,
                        keyDown = keyDown,
                    ),
                )
            }
        } finally {
            tone.key(false)
            outputs.keyUp()
            onState(PlayState())
        }
    }

    private suspend fun sendChar(
        ch: Char,
        timing: Timing,
        schedule: Schedule,
        onProgress: (partial: String, keyDown: Boolean) -> Unit,
    ) {
        val code = MorseCode.codeFor(ch) ?: return

        code.forEachIndexed { i, symbol ->
            coroutineContext.ensureActive()

            val element = if (symbol == '.') Element.DIT else Element.DAH
            val durationMs = timing.durationMs(element)
            val partial = code.substring(0, i + 1)

            onProgress(partial, true)
            tone.key(true)
            outputs.keyDown(durationMs)
            schedule.advance(durationMs)

            tone.key(false)
            outputs.keyUp()
            onProgress(partial, false)

            if (i != code.lastIndex) schedule.advance(timing.elementGapMs)
        }
    }
}

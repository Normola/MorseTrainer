package com.normo.morsetrainer.core

import kotlin.math.roundToLong

/** A single keyed element of a Morse character. */
enum class Element(val units: Int) {
    DIT(1),
    DAH(3),
}

/**
 * The ITU Morse alphabet, plus lookups in both directions.
 *
 * The trainer card only carries the 26 letters; digits and punctuation are here so
 * the keyer and the encoder can handle real text.
 */
object MorseCode {

    val LETTERS: Map<Char, String> = linkedMapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
    )

    val DIGITS: Map<Char, String> = linkedMapOf(
        '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
        '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.", '0' to "-----",
    )

    val PUNCTUATION: Map<Char, String> = linkedMapOf(
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.",
        '!' to "-.-.--", '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-",
        '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
        '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.",
        '$' to "...-..-", '@' to ".--.-.",
    )

    val ALL: Map<Char, String> = LETTERS + DIGITS + PUNCTUATION

    private val byCode: Map<String, Char> = ALL.entries.associate { it.value to it.key }

    fun codeFor(c: Char): String? = ALL[c.uppercaseChar()]

    fun charFor(code: String): Char? = byCode[code]

    /** True if some longer character begins with [prefix] — i.e. the tree keeps going. */
    fun hasContinuation(prefix: String): Boolean =
        byCode.keys.any { it.length > prefix.length && it.startsWith(prefix) }

    fun elements(code: String): List<Element> =
        code.map { if (it == '.') Element.DIT else Element.DAH }

    /** Strips characters with no Morse representation, collapsing runs of whitespace. */
    fun sanitize(text: String): String = buildString {
        var lastWasSpace = true
        for (raw in text) {
            val c = raw.uppercaseChar()
            when {
                c.isWhitespace() -> {
                    if (!lastWasSpace) {
                        append(' ')
                        lastWasSpace = true
                    }
                }

                ALL.containsKey(c) -> {
                    append(c)
                    lastWasSpace = false
                }
            }
        }
    }.trim()
}

/**
 * Element and gap durations.
 *
 * [charWpm] is the speed individual characters are sent at; [effectiveWpm] is the
 * overall speed. When the two differ, the extra time is pushed into the character
 * and word gaps (Farnsworth spacing) so characters still *sound* fast while the
 * text as a whole arrives slowly enough to copy.
 */
data class Timing(val charWpm: Int = 18, val effectiveWpm: Int = 12) {

    private val c: Int = charWpm.coerceIn(5, 60)
    private val s: Int = effectiveWpm.coerceIn(5, c)

    /** PARIS standard: one unit is 1200 ms / wpm. */
    val ditMs: Long = (1200.0 / c).roundToLong()
    val dahMs: Long = ditMs * 3
    val elementGapMs: Long = ditMs

    /** ARRL Farnsworth: total padding, in seconds, spread over one PARIS word. */
    private val padSeconds: Double = ((60.0 * c) - (37.2 * s)) / (c * s)

    val charGapMs: Long =
        if (s >= c) ditMs * 3 else (padSeconds * 3.0 / 19.0 * 1000.0).roundToLong()

    val wordGapMs: Long =
        if (s >= c) ditMs * 7 else (padSeconds * 7.0 / 19.0 * 1000.0).roundToLong()

    fun durationMs(e: Element): Long = if (e == Element.DIT) ditMs else dahMs
}

package com.normo.morsetrainer.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/** Right/wrong tally for one character. */
data class CharStat(val correct: Int = 0, val wrong: Int = 0) {
    val attempts: Int get() = correct + wrong
    val accuracy: Float get() = if (attempts == 0) 0f else correct.toFloat() / attempts
}

/**
 * Per-character quiz history, persisted as a compact "A:12/14,B:3/5" string.
 *
 * The trainer uses this to weight its picks towards characters that are going badly,
 * which is the whole point of keeping it.
 */
class StatsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("morse_stats", Context.MODE_PRIVATE)

    private val stats = mutableStateMapOf<Char, CharStat>()

    init {
        prefs.getString(KEY_STATS, null)?.let(::parseInto)
    }

    fun stat(c: Char): CharStat = stats[c.uppercaseChar()] ?: CharStat()

    fun snapshot(): Map<Char, CharStat> = stats.toMap()

    fun record(c: Char, correct: Boolean) {
        val key = c.uppercaseChar()
        val current = stats[key] ?: CharStat()
        stats[key] = if (correct) {
            current.copy(correct = current.correct + 1)
        } else {
            current.copy(wrong = current.wrong + 1)
        }
        persist()
    }

    fun clear() {
        stats.clear()
        prefs.edit().remove(KEY_STATS).apply()
    }

    /**
     * Selection weight for [c]: characters with a poor or unknown record come up more often.
     */
    fun weight(c: Char): Float {
        val s = stat(c)
        if (s.attempts == 0) return 3f
        // 1.0 at perfect accuracy, up to 5.0 at zero.
        return 1f + (1f - s.accuracy) * 4f
    }

    private fun parseInto(raw: String) {
        raw.split(',')
            .filter { it.isNotBlank() }
            .forEach { entry ->
                val char = entry.getOrNull(0) ?: return@forEach
                val counts = entry.substringAfter(':', "").split('/')
                if (counts.size != 2) return@forEach
                val correct = counts[0].toIntOrNull() ?: return@forEach
                val attempts = counts[1].toIntOrNull() ?: return@forEach
                stats[char] = CharStat(correct, (attempts - correct).coerceAtLeast(0))
            }
    }

    private fun persist() {
        val encoded = stats.entries.joinToString(",") { (c, s) ->
            "$c:${s.correct}/${s.attempts}"
        }
        prefs.edit().putString(KEY_STATS, encoded).apply()
    }

    private companion object {
        const val KEY_STATS = "char_stats"
    }
}

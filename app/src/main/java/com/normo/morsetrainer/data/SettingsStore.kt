package com.normo.morsetrainer.data

import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.normo.morsetrainer.core.LearningOrder
import com.normo.morsetrainer.core.Timing

enum class KeyMode(val label: String) {
    STRAIGHT("Straight key"),
    PADDLES("Dit / dah paddles"),
}

/**
 * User settings, exposed as Compose state and written straight through to prefs.
 *
 * Each property clamps and persists in its own setter, so there is exactly one way to
 * change a setting and no way to store a value the rest of the app would reject.
 *
 * Small enough that SharedPreferences is the right tool — there is no async load to
 * sequence and no schema to migrate.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("morse_settings", Context.MODE_PRIVATE)

    private val charWpmState = mutableIntStateOf(prefs.getInt(KEY_CHAR_WPM, 18))
    private val effectiveWpmState = mutableIntStateOf(prefs.getInt(KEY_EFF_WPM, 12))
    private val toneHzState = mutableIntStateOf(prefs.getInt(KEY_TONE_HZ, 600))
    private val volumeState = mutableFloatStateOf(prefs.getFloat(KEY_VOLUME, 0.6f))
    private val adaptiveKeyingState = mutableStateOf(prefs.getBoolean(KEY_ADAPTIVE, true))
    private val keyModeState = mutableStateOf(
        enumOrDefault(prefs.getString(KEY_KEY_MODE, null), KeyMode.STRAIGHT),
    )
    private val torchOutputState = mutableStateOf(prefs.getBoolean(KEY_TORCH, false))
    private val vibrationOutputState = mutableStateOf(prefs.getBoolean(KEY_VIBRATE, false))
    private val learningOrderState = mutableStateOf(
        enumOrDefault(prefs.getString(KEY_ORDER, null), LearningOrder.Order.KOCH),
    )
    private val kochLevelState = mutableIntStateOf(prefs.getInt(KEY_KOCH_LEVEL, 2))

    /** Speed individual characters are sent at. */
    var charWpm: Int
        get() = charWpmState.intValue
        set(value) {
            val clamped = value.coerceIn(MIN_WPM, MAX_WPM)
            charWpmState.intValue = clamped
            prefs.edit().putInt(KEY_CHAR_WPM, clamped).apply()
            // Overall speed can never outrun character speed.
            if (effectiveWpm > clamped) effectiveWpm = clamped
        }

    /** Overall speed; below [charWpm] this becomes Farnsworth spacing. */
    var effectiveWpm: Int
        get() = effectiveWpmState.intValue
        set(value) {
            val clamped = value.coerceIn(MIN_WPM, charWpm)
            effectiveWpmState.intValue = clamped
            prefs.edit().putInt(KEY_EFF_WPM, clamped).apply()
        }

    var toneHz: Int
        get() = toneHzState.intValue
        set(value) {
            val clamped = value.coerceIn(MIN_TONE_HZ, MAX_TONE_HZ)
            toneHzState.intValue = clamped
            prefs.edit().putInt(KEY_TONE_HZ, clamped).apply()
        }

    var volume: Float
        get() = volumeState.floatValue
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            volumeState.floatValue = clamped
            prefs.edit().putFloat(KEY_VOLUME, clamped).apply()
        }

    var adaptiveKeying: Boolean
        get() = adaptiveKeyingState.value
        set(value) {
            adaptiveKeyingState.value = value
            prefs.edit().putBoolean(KEY_ADAPTIVE, value).apply()
        }

    var keyMode: KeyMode
        get() = keyModeState.value
        set(value) {
            keyModeState.value = value
            prefs.edit().putString(KEY_KEY_MODE, value.name).apply()
        }

    var torchOutput: Boolean
        get() = torchOutputState.value
        set(value) {
            torchOutputState.value = value
            prefs.edit().putBoolean(KEY_TORCH, value).apply()
        }

    var vibrationOutput: Boolean
        get() = vibrationOutputState.value
        set(value) {
            vibrationOutputState.value = value
            prefs.edit().putBoolean(KEY_VIBRATE, value).apply()
        }

    var learningOrder: LearningOrder.Order
        get() = learningOrderState.value
        set(value) {
            learningOrderState.value = value
            prefs.edit().putString(KEY_ORDER, value.name).apply()
        }

    var kochLevel: Int
        get() = kochLevelState.intValue
        set(value) {
            val clamped = value.coerceIn(MIN_KOCH_LEVEL, MAX_KOCH_LEVEL)
            kochLevelState.intValue = clamped
            prefs.edit().putInt(KEY_KOCH_LEVEL, clamped).apply()
        }

    val timing: Timing get() = Timing(charWpm, effectiveWpm.coerceAtMost(charWpm))

    private companion object {
        const val MIN_WPM = 5
        const val MAX_WPM = 40
        const val MIN_TONE_HZ = 300
        const val MAX_TONE_HZ = 1200
        const val MIN_KOCH_LEVEL = 2
        const val MAX_KOCH_LEVEL = 26

        const val KEY_CHAR_WPM = "char_wpm"
        const val KEY_EFF_WPM = "eff_wpm"
        const val KEY_TONE_HZ = "tone_hz"
        const val KEY_VOLUME = "volume"
        const val KEY_ADAPTIVE = "adaptive"
        const val KEY_KEY_MODE = "key_mode"
        const val KEY_TORCH = "torch"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_ORDER = "order"
        const val KEY_KOCH_LEVEL = "koch_level"

        /** Tolerates a stored name that no longer exists in the enum. */
        inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    }
}

package com.normo.morsetrainer.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.normo.morsetrainer.core.LearningOrder
import com.normo.morsetrainer.core.Timing

enum class KeyMode(val label: String) {
    STRAIGHT("Straight key"),
    PADDLES("Dit / dah paddles"),
}

/**
 * User settings, exposed as Compose state and written straight through to prefs.
 *
 * Small enough that SharedPreferences is the right tool — there is no async load to
 * sequence and no schema to migrate.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("morse_settings", Context.MODE_PRIVATE)

    var charWpm by mutableIntStateOf(prefs.getInt(KEY_CHAR_WPM, 18))
    var effectiveWpm by mutableIntStateOf(prefs.getInt(KEY_EFF_WPM, 12))
    var toneHz by mutableIntStateOf(prefs.getInt(KEY_TONE_HZ, 600))
    var volume by mutableFloatStateOf(prefs.getFloat(KEY_VOLUME, 0.6f))
    var adaptiveKeying by mutableStateOf(prefs.getBoolean(KEY_ADAPTIVE, true))
    var keyMode by mutableStateOf(
        runCatching { KeyMode.valueOf(prefs.getString(KEY_KEY_MODE, null) ?: "") }
            .getOrDefault(KeyMode.STRAIGHT),
    )
    var torchOutput by mutableStateOf(prefs.getBoolean(KEY_TORCH, false))
    var vibrationOutput by mutableStateOf(prefs.getBoolean(KEY_VIBRATE, false))
    var learningOrder by mutableStateOf(
        runCatching { LearningOrder.Order.valueOf(prefs.getString(KEY_ORDER, null) ?: "") }
            .getOrDefault(LearningOrder.Order.KOCH),
    )
    var kochLevel by mutableIntStateOf(prefs.getInt(KEY_KOCH_LEVEL, 2))

    /** Effective speed can never exceed character speed. */
    val timing: Timing get() = Timing(charWpm, effectiveWpm.coerceAtMost(charWpm))

    fun setCharWpm(value: Int) {
        charWpm = value.coerceIn(5, 40)
        if (effectiveWpm > charWpm) setEffectiveWpm(charWpm)
        prefs.edit().putInt(KEY_CHAR_WPM, charWpm).apply()
    }

    fun setEffectiveWpm(value: Int) {
        effectiveWpm = value.coerceIn(5, charWpm)
        prefs.edit().putInt(KEY_EFF_WPM, effectiveWpm).apply()
    }

    fun setToneHz(value: Int) {
        toneHz = value.coerceIn(300, 1200)
        prefs.edit().putInt(KEY_TONE_HZ, toneHz).apply()
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_VOLUME, volume).apply()
    }

    fun setAdaptiveKeying(value: Boolean) {
        adaptiveKeying = value
        prefs.edit().putBoolean(KEY_ADAPTIVE, value).apply()
    }

    fun setKeyMode(value: KeyMode) {
        keyMode = value
        prefs.edit().putString(KEY_KEY_MODE, value.name).apply()
    }

    fun setTorchOutput(value: Boolean) {
        torchOutput = value
        prefs.edit().putBoolean(KEY_TORCH, value).apply()
    }

    fun setVibrationOutput(value: Boolean) {
        vibrationOutput = value
        prefs.edit().putBoolean(KEY_VIBRATE, value).apply()
    }

    fun setLearningOrder(value: LearningOrder.Order) {
        learningOrder = value
        prefs.edit().putString(KEY_ORDER, value.name).apply()
    }

    fun setKochLevel(value: Int) {
        kochLevel = value.coerceIn(2, 26)
        prefs.edit().putInt(KEY_KOCH_LEVEL, kochLevel).apply()
    }

    private companion object {
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
    }
}

package com.normo.morsetrainer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.normo.morsetrainer.audio.MorsePlayer
import com.normo.morsetrainer.audio.OutputSinks
import com.normo.morsetrainer.audio.PlayState
import com.normo.morsetrainer.audio.SideToneEngine
import com.normo.morsetrainer.data.SettingsStore
import com.normo.morsetrainer.data.StatsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the audio engine and the stores.
 *
 * The engine is a real OS resource with a thread behind it, so it lives here rather
 * than in composition — it must survive rotation and be torn down exactly once.
 */
class MorseViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SettingsStore(app)
    val stats = StatsStore(app)

    val tone = SideToneEngine()
    val outputs = OutputSinks(app)
    private val player = MorsePlayer(tone, outputs)

    var playState by mutableStateOf(PlayState())
        private set

    private var playJob: Job? = null

    init {
        tone.start()
        applyAudioSettings()
    }

    /** Push the current tone/output preferences into the engine and sinks. */
    fun applyAudioSettings() {
        tone.frequencyHz = settings.toneHz.toFloat()
        tone.volume = settings.volume
        outputs.torchEnabled = settings.torchOutput && outputs.hasTorch
        outputs.vibrationEnabled = settings.vibrationOutput && outputs.hasVibrator
    }

    /** Sends [text], replacing anything already in flight. */
    fun play(text: String, onFinished: () -> Unit = {}) {
        stop()
        playJob = viewModelScope.launch {
            player.play(text, settings.timing) { playState = it }
            onFinished()
        }
    }

    fun playChar(ch: Char, onFinished: () -> Unit = {}) {
        stop()
        playJob = viewModelScope.launch {
            player.playChar(ch, settings.timing) { playState = it }
            onFinished()
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        tone.key(false)
        outputs.keyUp()
        playState = PlayState()
    }

    override fun onCleared() {
        stop()
        outputs.release()
        tone.stop()
        super.onCleared()
    }
}

package com.normo.morsetrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A continuously running sine oscillator that can be keyed on and off.
 *
 * A single [AudioTrack] stays open for the life of the engine and is fed silence when
 * the key is up. Keeping the track running — and the oscillator phase continuous —
 * avoids the pop you get from starting and stopping a track per element, and the gain
 * is ramped through a raised cosine so each edge is soft rather than a hard click.
 */
class SideToneEngine(
    private val sampleRate: Int = 44_100,
) {

    @Volatile
    var frequencyHz: Float = 600f

    /** 0f..1f */
    @Volatile
    var volume: Float = 0.6f

    /** Edge ramp length. ~5 ms is the usual "soft but still crisp" compromise. */
    @Volatile
    var rampMs: Float = 5f

    private val keyed = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    val isKeyed: Boolean get() = keyed.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return

        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = max(minBytes, FRAMES_PER_WRITE * 2 * 4)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        track = audioTrack
        audioTrack.play()

        thread = Thread({ renderLoop(audioTrack) }, "morse-sidetone").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        keyed.set(false)
        thread?.join(500)
        thread = null
        track?.runCatching {
            pause()
            flush()
            release()
        }
        track = null
    }

    /** Key down starts the tone, key up ramps it out. Safe to call from any thread. */
    fun key(down: Boolean) {
        keyed.set(down)
    }

    private fun renderLoop(audioTrack: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val buffer = ShortArray(FRAMES_PER_WRITE)
        var phase = 0.0
        // Linear position through the ramp, 0 = fully off, 1 = fully on.
        var ramp = 0.0

        while (running.get()) {
            val target = if (keyed.get()) 1.0 else 0.0
            val rampStep = 1.0 / max(1.0, sampleRate * (rampMs / 1000.0))
            val phaseInc = 2.0 * PI * frequencyHz / sampleRate
            val amplitude = volume.coerceIn(0f, 1f) * 0.85 * Short.MAX_VALUE

            for (i in buffer.indices) {
                ramp = when {
                    ramp < target -> min(target, ramp + rampStep)
                    ramp > target -> max(target, ramp - rampStep)
                    else -> ramp
                }

                if (ramp <= 0.0) {
                    buffer[i] = 0
                    // Reset phase while silent so every element starts at a zero crossing.
                    phase = 0.0
                    continue
                }

                // Raised cosine on the linear ramp: no discontinuity in the envelope slope.
                val envelope = 0.5 - 0.5 * cos(PI * ramp)
                buffer[i] = (sin(phase) * envelope * amplitude).toInt().toShort()

                phase += phaseInc
                if (phase >= TWO_PI) phase -= TWO_PI
            }

            // Blocking write; this is what paces the loop.
            val written = audioTrack.write(buffer, 0, buffer.size)
            if (written < 0) break
        }

        audioTrack.runCatching { stop() }
    }

    private companion object {
        const val FRAMES_PER_WRITE = 256
        const val TWO_PI = 2.0 * PI
    }
}

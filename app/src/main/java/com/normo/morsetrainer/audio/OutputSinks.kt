package com.normo.morsetrainer.audio

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build

/**
 * The non-audio ways a keyed element can be rendered: the camera flash and the vibrator.
 *
 * Both are optional and best-effort — a device with no torch, or a vibrator that
 * refuses a request, must not take the sending loop down with it.
 */
class OutputSinks(context: Context) {

    private val appContext = context.applicationContext

    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Id of the first camera that actually has a flash unit, if any. */
    private val torchCameraId: String? by lazy {
        runCatching {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    val hasTorch: Boolean get() = torchCameraId != null

    val hasVibrator: Boolean get() = vibrator?.hasVibrator() == true

    @Volatile
    var torchEnabled: Boolean = false

    @Volatile
    var vibrationEnabled: Boolean = false

    private var torchOn = false

    /** Turn the enabled sinks on for the duration of one element. */
    fun keyDown(durationMs: Long) {
        if (torchEnabled) setTorch(true)
        if (vibrationEnabled && durationMs > 0) {
            runCatching {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    ),
                )
            }
        }
    }

    fun keyUp() {
        if (torchOn) setTorch(false)
    }

    /** Drop everything immediately — used when playback is cancelled. */
    fun release() {
        setTorch(false)
        runCatching { vibrator?.cancel() }
    }

    private fun setTorch(on: Boolean) {
        val id = torchCameraId ?: return
        runCatching { cameraManager?.setTorchMode(id, on) }
            .onSuccess { torchOn = on }
    }
}

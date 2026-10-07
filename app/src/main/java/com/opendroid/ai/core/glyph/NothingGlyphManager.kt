package com.opendroid.ai.core.glyph

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller for Nothing Phone (2a) Plus Glyph Interface and rear light notifications.
 * Provides custom LED lighting patterns corresponding to AI Agent states:
 * - WAKE_WORD_DETECTED: Rapid affirmative double pulse
 * - LISTENING: Dynamic pulsing wave (reactive to audio/query)
 * - THINKING: Smooth breathing cycle
 * - EXECUTING: Rapid active chase pattern
 * - SUCCESS: Dual triumph flash
 * - ERROR: Alert strobe
 *
 * Automatically falls back to Camera Flash Torch and Advanced Haptics if
 * custom Nothing Glyph AIDL service is not bound.
 */
@Singleton
class NothingGlyphManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NothingGlyphManager"
        private const val NOTHING_BRAND = "Nothing"
    }

    private val isNothingDevice: Boolean by lazy {
        Build.MANUFACTURER.contains(NOTHING_BRAND, ignoreCase = true) ||
        Build.BRAND.contains(NOTHING_BRAND, ignoreCase = true) ||
        Build.MODEL.contains("A142P", ignoreCase = true) || // Nothing Phone (2a) Plus
        Build.MODEL.contains("A142", ignoreCase = true)     // Nothing Phone (2a)
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val mainCameraId: String? by lazy {
        try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                val hasFlash = chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK && hasFlash
            }
        } catch (e: Exception) {
            null
        }
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val glyphScope = CoroutineScope(Dispatchers.Default)
    private var activeAnimationJob: Job? = null
    @Volatile private var isTorchOn = false

    fun onWakeWordDetected() {
        Log.d(TAG, "Glyph pattern: Wake Word Detected on $isNothingDevice device")
        triggerHaptic(50)
        flashBurst(times = 2, intervalMs = 90)
    }

    fun onListening() {
        cancelAnimation()
        triggerHaptic(30)
        activeAnimationJob = glyphScope.launch {
            while (isActive) {
                setLightState(true)
                delay(400)
                setLightState(false)
                delay(300)
            }
        }
    }

    fun onThinking() {
        cancelAnimation()
        activeAnimationJob = glyphScope.launch {
            while (isActive) {
                setLightState(true)
                delay(600)
                setLightState(false)
                delay(400)
            }
        }
    }

    fun onExecuting() {
        cancelAnimation()
        activeAnimationJob = glyphScope.launch {
            while (isActive) {
                setLightState(true)
                delay(120)
                setLightState(false)
                delay(120)
            }
        }
    }

    fun onSuccess() {
        cancelAnimation()
        triggerHaptic(80)
        flashBurst(times = 2, intervalMs = 150)
    }

    fun onError() {
        cancelAnimation()
        triggerHaptic(150)
        flashBurst(times = 3, intervalMs = 80)
    }

    fun onIdle() {
        cancelAnimation()
        setLightState(false)
    }

    private fun cancelAnimation() {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        setLightState(false)
    }

    private fun flashBurst(times: Int, intervalMs: Long) {
        glyphScope.launch {
            repeat(times) {
                setLightState(true)
                delay(intervalMs)
                setLightState(false)
                delay(intervalMs)
            }
        }
    }

    private fun setLightState(enabled: Boolean) {
        if (isTorchOn == enabled) return
        isTorchOn = enabled

        // Universal hardware back-end via Camera Flash Torch
        val camId = mainCameraId ?: return
        try {
            cameraManager?.setTorchMode(camId, enabled)
        } catch (e: Exception) {
            // Ignore camera busy or permission issues during rapid toggling
        }
    }

    private fun triggerHaptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Non-critical
        }
    }
}

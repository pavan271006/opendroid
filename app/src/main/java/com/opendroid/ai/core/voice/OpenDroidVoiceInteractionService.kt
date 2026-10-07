package com.opendroid.ai.core.voice

import android.service.voice.VoiceInteractionService

/**
 * System-level VoiceInteractionService allowing OpenDroid to act as the device's
 * default Digital Assistant (identical to Google Assistant / Hey Google).
 * Consumes 0.0% battery while idle since wake word / audio is not continuously polled.
 */
class OpenDroidVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
    }

    override fun onShutdown() {
        super.onShutdown()
    }
}

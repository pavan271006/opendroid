package com.opendroid.ai.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.opendroid.ai.MainActivity
import com.opendroid.ai.core.service.OpenDroidService

/**
 * VoiceInteractionSession invoked when the user:
 * 1. Long-presses the Power Button (configured as Assistant on Nothing OS / Android)
 * 2. Swipes up from the bottom corner of the screen
 * 3. Taps the Assistant icon on the lock screen
 *
 * This provides 0.0% battery drain in standby: the microphone is only activated
 * upon explicit hardware / gesture trigger!
 */
class OpenDroidVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d("VoiceInteraction", "OpenDroid invoked via System Assistant gesture / Power button")

        try {
            // Trigger voice recording and glyph lighting in the foreground service
            val serviceIntent = Intent(context, OpenDroidService::class.java).apply {
                action = OpenDroidService.ACTION_TRIGGER_RECORD
            }
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e("VoiceInteraction", "Failed to startForegroundService on assist", e)
        }

        try {
            // Bring Google Assistant style bottom sheet overlay to front over lock screen
            val uiIntent = Intent(context, com.opendroid.ai.ui.overlay.VoiceOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("EXTRA_VOICE_TRIGGERED", true)
            }
            context.startActivity(uiIntent)
        } catch (e: Exception) {
            Log.e("VoiceInteraction", "Failed to start VoiceOverlayActivity on assist", e)
        }

        hide()
    }
}

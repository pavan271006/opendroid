package com.opendroid.ai.core.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.opendroid.ai.MainActivity

/**
 * Quick Settings Tile allowing instant one-tap access to OpenDroid voice assistant
 * directly from the Nothing OS notification / quick settings panel with 0% standby battery drain.
 */
@RequiresApi(Build.VERSION_CODES.N)
class OpenDroidTileService : TileService() {

    override fun onClick() {
        super.onClick()

        // Trigger recording in foreground service
        try {
            val serviceIntent = Intent(this, OpenDroidService::class.java).apply {
                action = OpenDroidService.ACTION_TRIGGER_RECORD
            }
            startForegroundService(serviceIntent)
        } catch (e: Exception) {}

        // Launch Google Assistant style UI overlay
        try {
            val mainIntent = Intent(this, com.opendroid.ai.ui.overlay.VoiceOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("EXTRA_VOICE_TRIGGERED", true)
            }
            startActivityAndCollapse(mainIntent)
        } catch (e: Exception) {}
    }
}

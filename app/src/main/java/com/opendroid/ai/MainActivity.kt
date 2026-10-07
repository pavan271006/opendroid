package com.opendroid.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.opendroid.ai.core.service.OpenDroidService
import com.opendroid.ai.data.repository.SettingsRepository
import com.opendroid.ai.ui.OpenDroidNavigation
import com.opendroid.ai.ui.theme.AppTheme
import com.opendroid.ai.ui.theme.OpenDroidTheme
import com.opendroid.ai.ui.theme.enableOpenDroidEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableOpenDroidEdgeToEdge(isDarkTheme = true)
        super.onCreate(savedInstanceState)

        // Enable display over lock screen and turn display on when voice assistant activates
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request Ignore Battery Optimizations for persistent background & sleep listening
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            // Ignore if restricted
        }

        // Start foreground assistant service only if RECORD_AUDIO permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            OpenDroidService.start(this)
        }

        // Handle instant invocation via Assistant button, power button, or voice intent
        checkAndHandleAssistIntent(intent)

        setContent {
            val config by settingsRepository.llmConfig.collectAsState(
                initial = com.opendroid.ai.data.models.LLMConfig()
            )

            OpenDroidTheme(isDarkTheme = config.isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppTheme.colors.background
                ) {
                    OpenDroidNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAndHandleAssistIntent(intent)
    }

    private fun checkAndHandleAssistIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val isAssist = intent.action == android.content.Intent.ACTION_ASSIST ||
                       intent.action == "android.intent.action.VOICE_ASSIST" ||
                       intent.getBooleanExtra("EXTRA_VOICE_TRIGGERED", false)
        if (isAssist) {
            val triggerIntent = android.content.Intent(this, OpenDroidService::class.java).apply {
                action = OpenDroidService.ACTION_TRIGGER_RECORD
            }
            try {
                startForegroundService(triggerIntent)
            } catch (e: Exception) {
                // Ignore if background restriction
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}


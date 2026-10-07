package com.opendroid.ai.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opendroid.ai.core.agent.AgentLoop
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.glyph.NothingGlyphManager
import com.opendroid.ai.core.voice.SpeechRecognitionEngine
import com.opendroid.ai.core.voice.TextToSpeechEngine
import com.opendroid.ai.core.voice.VoiceApprovalIntent
import com.opendroid.ai.core.voice.VoiceApprovalParser
import com.opendroid.ai.core.voice.WakeWordDetector
import com.opendroid.ai.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OpenDroidService : Service() {

    @Inject
    lateinit var agentLoop: AgentLoop

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var mcpServer: McpServer

    @Inject
    lateinit var glyphManager: NothingGlyphManager

    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var speechRecognitionEngine: SpeechRecognitionEngine
    private lateinit var textToSpeechEngine: TextToSpeechEngine

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var showFloatingButton = false
    @Volatile private var pendingApprovalListen = false

    companion object {
        const val ACTION_TRIGGER_RECORD = "com.opendroid.ai.action.TRIGGER_RECORD"
        private const val CHANNEL_ID = "opendroid_channel"
        private const val NOTIFICATION_ID = 2024
        
        fun start(context: Context) {
            val intent = Intent(context, OpenDroidService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OpenDroidService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Acquire PARTIAL_WAKE_LOCK to ensure background CPU remains active even when screen sleeps
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OpenDroid::AssistantServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.e("OpenDroidService", "Failed to acquire PARTIAL_WAKE_LOCK", e)
        }

        // Initialize engines
        wakeWordDetector = WakeWordDetector(this)
        speechRecognitionEngine = SpeechRecognitionEngine(this)
        textToSpeechEngine = TextToSpeechEngine(this, settingsRepository)

        // Bind Agent Loop TTS
        agentLoop.onSpeakCallback = { text ->
            textToSpeechEngine.speak(text)
        }

        // Set TTS completion listener to transition back to Idle
        textToSpeechEngine.onCompletionListener = {
            if (agentLoop.agentState.value is AgentState.Speaking) {
                agentLoop.setAgentState(AgentState.Idle)
            }
            if (pendingApprovalListen) {
                pendingApprovalListen = false
                startListeningForApproval()
            }
        }

        // Start Foreground Notification
        createNotificationChannel()
        startForegroundCompat()
        mcpServer.start()

        // Hook Glyph Manager to AI Agent Lifecycle states
        serviceScope.launch {
            agentLoop.agentState.collectLatest { state ->
                when (state) {
                    is AgentState.Listening -> glyphManager.onListening()
                    is AgentState.Thinking -> glyphManager.onThinking()
                    is AgentState.ExecutingPlan -> glyphManager.onExecuting()
                    is AgentState.Speaking -> glyphManager.onSuccess()
                    is AgentState.Idle -> glyphManager.onIdle()
                    is AgentState.Error -> glyphManager.onError()
                    else -> glyphManager.onIdle()
                }
            }
        }

        // Keep Wake Word detection running 24/7, including when screen is off or locked
        startWakeWordDetection()

        // Monitor floating button config without killing wake word
        serviceScope.launch {
            settingsRepository.llmConfig.collectLatest { config ->
                showFloatingButton = config.showFloatingButton
            }
        }

        // Hands-free plan approval: speak the prompt once per proposed plan
        serviceScope.launch {
            var promptedPlanId: String? = null
            agentLoop.agentState.collectLatest { state ->
                if (state is AgentState.PlanProposed && state.plan.planId != promptedPlanId) {
                    promptedPlanId = state.plan.planId
                    pendingApprovalListen = true
                    textToSpeechEngine.speak(
                        "I've planned: ${state.plan.goal}, ${state.plan.estimatedSteps} steps. " +
                        "Say approve to run, or cancel."
                    )
                }
            }
        }
    }

    private fun startForegroundCompat() {
        val notification = createNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Starting a microphone-type FGS without RECORD_AUDIO granted throws a
            // SecurityException on Android 14+, and starting one from BOOT_COMPLETED is
            // prohibited on Android 15 even with the permission. Fall back to specialUse
            // until the microphone is actually usable.
            val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val type = if (micGranted) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                androidx.core.app.ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            } catch (e: SecurityException) {
                androidx.core.app.ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // FOREGROUND_SERVICE_TYPE_MICROPHONE is an API 30 constant; API 29 devices
            // fall through to plain startForeground, which uses the manifest-declared types.
            androidx.core.app.ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startWakeWordDetection() {
        wakeWordDetector.startListening {
            // Wake word detected ("Siri" / "Hey Jarvis" / "OpenDroid")
            Log.d("OpenDroidService", "Wake word triggered! Activating phone & screen...")
            glyphManager.onWakeWordDetected()
            wakeScreenUp()
            bringAssistantToFront()
            textToSpeechEngine.speak("Yes, I'm listening.")
            startListeningForQuery()
        }
    }

    private fun wakeScreenUp() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "OpenDroid::ScreenWakeUp"
            )
            screenWakeLock.acquire(4000)
        } catch (e: Exception) {
            Log.e("OpenDroidService", "Failed to acquire screen wake lock", e)
        }
    }

    private fun bringAssistantToFront() {
        try {
            val intent = Intent(this, com.opendroid.ai.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("EXTRA_VOICE_TRIGGERED", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("OpenDroidService", "Failed to bring MainActivity to front", e)
        }
    }

    private fun startListeningForQuery() {
        // Temporarily pause wake word to avoid hearing itself
        wakeWordDetector.stopListening()

        // Set agent state to Listening
        agentLoop.setAgentState(AgentState.Listening)

        // Start speech recognizer for query input
        speechRecognitionEngine.startListening(
            onResult = { query ->
                agentLoop.processQuery(query, this)
                // Resume wake word detection for continuous hands-free interaction
                startWakeWordDetection()
            },
            onError = { _ ->
                agentLoop.setAgentState(AgentState.Idle)
                glyphManager.onIdle()
                // Resume wake word detection immediately
                startWakeWordDetection()
            }
        )
    }

    /**
     * One-shot reply capture after the spoken approval prompt. Unrecognized
     * speech (or an error) is a deliberate no-op: the plan stays in
     * PlanProposed with the visual modal still on screen. Never a grant path -
     * nothing spoken may widen the allowlist.
     */
    private fun startListeningForApproval() {
        wakeWordDetector.stopListening()
        speechRecognitionEngine.startListening(
            onResult = { utterance ->
                when (VoiceApprovalParser.parse(utterance)) {
                    VoiceApprovalIntent.APPROVE -> agentLoop.approveProposedPlan(this)
                    VoiceApprovalIntent.REJECT -> agentLoop.rejectProposedPlan()
                    VoiceApprovalIntent.NONE -> Unit
                }
                startWakeWordDetection()
            },
            onError = { _ ->
                startWakeWordDetection()
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_RECORD) {
            startListeningForQuery()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mcpServer.stop()
        glyphManager.onIdle()
        wakeWordDetector.destroy()
        speechRecognitionEngine.destroy()
        textToSpeechEngine.destroy()
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OpenDroid Agent Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps OpenDroid background agent alive"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenDroid Active")
            .setContentText("Listening for wake word 'OpenDroid'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
}

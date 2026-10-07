package com.opendroid.ai.ui.overlay

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.opendroid.ai.core.agent.AgentLoop
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.glyph.NothingGlyphManager
import com.opendroid.ai.core.voice.SpeechRecognitionEngine
import com.opendroid.ai.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Translucent bottom-sheet overlay activity modeled after Google Assistant / Gemini.
 * Launches directly over whatever app the user is in (or over lock screen) without full-screen jarring switches.
 */
@AndroidEntryPoint
class VoiceOverlayActivity : ComponentActivity() {

    @Inject lateinit var agentLoop: AgentLoop
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphManager: NothingGlyphManager

    private var speechEngine: SpeechRecognitionEngine? = null
    private var liveSpeechText by mutableStateOf("")
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen and display wake configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        glyphManager.onListening()

        // Check for direct text query from intent (e.g. from Google Assistant Routine or shortcut)
        val passedQuery = intent?.getStringExtra("EXTRA_QUERY")
            ?: intent?.getStringExtra(android.content.Intent.EXTRA_TEXT)

        if (!passedQuery.isNullOrBlank()) {
            liveSpeechText = passedQuery
            agentLoop.processQuery(passedQuery, this)
        } else {
            startListening()
        }

        // Auto-dismiss smoothly when execution finishes and state returns to Idle
        activityScope.launch {
            var hasStarted = false
            agentLoop.agentState.collectLatest { state ->
                when (state) {
                    is AgentState.Listening, is AgentState.Thinking, is AgentState.ExecutingPlan, is AgentState.Speaking -> {
                        hasStarted = true
                    }
                    is AgentState.Idle -> {
                        if (hasStarted) {
                            handler.postDelayed({
                                if (!isFinishing) finish()
                            }, 1800L)
                        }
                    }
                    else -> Unit
                }
            }
        }

        setContent {
            val agentState by agentLoop.agentState.collectAsState()

            VoiceOverlayScreen(
                agentState = agentState,
                liveSpeechText = liveSpeechText,
                onDismiss = { finish() },
                onQuickActionClick = { query ->
                    liveSpeechText = query
                    agentLoop.processQuery(query, this@VoiceOverlayActivity)
                }
            )
        }
    }

    private fun startListening() {
        speechEngine = SpeechRecognitionEngine(this)
        speechEngine?.startListening(
            onResult = { finalQuery ->
                liveSpeechText = finalQuery
                glyphManager.onThinking()
                agentLoop.processQuery(finalQuery, this)
            },
            onPartialResult = { partialQuery ->
                liveSpeechText = partialQuery
            },
            onError = { _ ->
                handler.postDelayed({
                    if (!isFinishing) finish()
                }, 2000L)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        speechEngine?.stopListening()
        speechEngine = null
        glyphManager.onIdle()
    }
}

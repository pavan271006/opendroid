package com.opendroid.ai.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.core.agent.AgentState

/**
 * Google Assistant / Gemini style bottom-sheet voice overlay.
 * Renders over whatever app is currently open with ambient glowing lightbar,
 * real-time speech transcription, dynamic cognitive state badges, and zero lag.
 */
@Composable
fun VoiceOverlayScreen(
    agentState: AgentState,
    liveSpeechText: String,
    onDismiss: () -> Unit,
    onQuickActionClick: (String) -> Unit
) {
    // Ambient glowing gradient animation across top border
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientOffset"
    )

    // Pulsing mic orb scale animation during listening
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000)) // Translucent scrim over current app
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Bottom Sheet Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume click inside sheet
                ),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xF5141416)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Ambient Light Bar (Google Assistant 4-color / Nothing OS Glow)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4285F4), // Google Blue
                                    Color(0xFFEA4335), // Google Red
                                    Color(0xFFFBBC05), // Google Yellow
                                    Color(0xFF34A853)  // Google Green
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header with Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = when (agentState) {
                                is AgentState.Listening -> Color(0xFF34A853)
                                is AgentState.Thinking -> Color(0xFFFBBC05)
                                is AgentState.ExecutingPlan -> Color(0xFF4285F4)
                                else -> Color.White
                            }
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OpenDroid Jarvis",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE0E0E0)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Center Cognitive State & Voice Visualizer
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            when (agentState) {
                                is AgentState.Listening -> Color(0x334285F4)
                                is AgentState.Thinking -> Color(0x33FBBC05)
                                is AgentState.ExecutingPlan -> Color(0x3334A853)
                                else -> Color(0x22FFFFFF)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (agentState) {
                        is AgentState.Listening -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Listening",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier
                                    .size(32.dp)
                                    .scale(pulseScale)
                            )
                        }
                        is AgentState.Thinking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFFFBBC05),
                                strokeWidth = 3.dp
                            )
                        }
                        is AgentState.ExecutingPlan -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF34A853),
                                strokeWidth = 3.dp
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Done",
                                tint = Color(0xFF34A853),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Transcription / Status Text
                val displayText = when {
                    liveSpeechText.isNotBlank() -> liveSpeechText
                    agentState is AgentState.Listening -> "Listening... Speak your command"
                    agentState is AgentState.Thinking -> "Thinking with Groq (120ms)..."
                    agentState is AgentState.ExecutingPlan -> (agentState as AgentState.ExecutingPlan).currentStepDesc
                    agentState is AgentState.Speaking -> (agentState as AgentState.Speaking).text
                    agentState is AgentState.Error -> (agentState as AgentState.Error).message
                    else -> "Hi! How can I help you?"
                }

                Text(
                    text = displayText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Quick Action Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickChip(label = "WhatsApp Mom", onClick = { onQuickActionClick("Send message to Mom on WhatsApp I will be in 5 min") })
                    QuickChip(label = "Torch On", onClick = { onQuickActionClick("Turn on flashlight") })
                    QuickChip(label = "What's on Screen", onClick = { onQuickActionClick("What is on my screen right now?") })
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF242428),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFFD0D0D4),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

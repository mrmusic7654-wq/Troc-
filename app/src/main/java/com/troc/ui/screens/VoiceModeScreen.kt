package com.troc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troc.domain.model.VoiceStatus
import com.troc.ui.components.VoiceOrb
import com.troc.ui.theme.*
import com.troc.viewmodel.VoiceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceModeScreen(
    onClose: () -> Unit,
    viewModel: VoiceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startVoiceLoop()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(DarkBackground, Color(0xFF1A1033)),
                    radius = 1200f
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Voice Mode", color = DarkText) },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.stopListening()
                            onClose()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mini transcript
                    Card(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.8f))
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(12.dp),
                            reverseLayout = true
                        ) {
                            items(state.miniTranscript.reversed()) { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall, color = DarkMuted, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleMute() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (state.isMuted) ErrorRed.copy(alpha = 0.2f) else DarkSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (state.isMuted) ErrorRed else DarkText
                            )
                        }

                        // Central action button
                        FilledIconButton(
                            onClick = {
                                if (state.status is VoiceStatus.Idle || state.status is VoiceStatus.Error) {
                                    viewModel.startVoiceLoop()
                                } else {
                                    viewModel.stopListening()
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryPurple)
                        ) {
                            Icon(
                                imageVector = when (state.status) {
                                    is VoiceStatus.Listening -> Icons.Default.Stop
                                    is VoiceStatus.Speaking -> Icons.Default.Stop
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = "Action",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(DarkSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = DarkText)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when (state.status) {
                            is VoiceStatus.Idle -> "Tap mic to speak"
                            is VoiceStatus.Listening -> "Listening…"
                            is VoiceStatus.Transcribing -> "Transcribing…"
                            is VoiceStatus.Thinking -> "Thinking…"
                            is VoiceStatus.Speaking -> "Speaking…"
                            is VoiceStatus.Error -> (state.status as VoiceStatus.Error).message
                        },
                        color = DarkMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VoiceOrb(
                    status = state.status,
                    amplitude = state.amplitude,
                    modifier = Modifier.size(260.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (state.transcript.isNotEmpty() && state.status is VoiceStatus.Transcribing) {
                    Text(
                        text = "\"${state.transcript}\"",
                        color = DarkText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                if (state.assistantResponse.isNotEmpty() && state.status is VoiceStatus.Thinking) {
                    Text(
                        text = state.assistantResponse.take(200),
                        color = DarkMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

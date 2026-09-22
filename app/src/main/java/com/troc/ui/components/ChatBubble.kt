package com.troc.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.troc.domain.model.ChatMessage
import com.troc.domain.model.MessageRole
import com.troc.ui.theme.PrimaryGradientEnd
import com.troc.ui.theme.PrimaryPurple

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = when {
            isUser -> Alignment.CenterEnd
            isTool -> Alignment.CenterStart
            else -> Alignment.CenterStart
        }
    ) {
        when {
            isUser -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PrimaryPurple, PrimaryGradientEnd),
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(300f, 100f)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .widthIn(max = 300.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f
                        ),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
            isTool -> {
                // Tool result - more compact, refined
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔧", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message.content.take(500),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            else -> {
                // Assistant bubble: refined, with subtle surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 24.dp)
                ) {
                    MarkdownMessage(
                        text = message.content,
                        isStreaming = message.isStreaming
                    )
                }
            }
        }
    }
}

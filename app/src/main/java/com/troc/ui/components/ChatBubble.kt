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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryPurple, PrimaryGradientEnd),
                            // 135 deg
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(max = 300.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        } else {
            // Assistant bubble: transparent, no border, ChatGPT style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 32.dp)
            ) {
                MarkdownMessage(
                    text = message.content,
                    isStreaming = message.isStreaming
                )
            }
        }
    }
}

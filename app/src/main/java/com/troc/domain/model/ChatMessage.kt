package com.troc.domain.model

import java.util.UUID

enum class MessageRole { USER, ASSISTANT, TOOL, SYSTEM }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCall> = emptyList(),
    val isStreaming: Boolean = false,
    val chatId: String = ""
)

data class ChatInfo(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val hasSandbox: Boolean = false,
    val hasVoice: Boolean = false,
    val messageCount: Int = 0
)

enum class ChatMode { CHAT, AGENT }

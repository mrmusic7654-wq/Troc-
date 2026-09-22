package com.example.troc.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class Role { USER, ASSISTANT, TOOL, SYSTEM }

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Populated for [Role.TOOL] messages: e.g. "run_code (javascript)". */
    val toolLabel: String? = null,
    val isError: Boolean = false,
    val durationMs: Long? = null
)

@Serializable
data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isAgentMode: Boolean = false
)

@Serializable
data class SandboxSessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String? = null,
    val tool: String,
    val language: String? = null,
    val input: String,
    val output: String,
    val success: Boolean,
    val durationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AccentColor(val hex: Long, val label: String) {
    VIOLET(0xFF8B5CF6, "Violet"),
    CYAN(0xFF06B6D4, "Cyan"),
    EMERALD(0xFF10B981, "Emerald"),
    ROSE(0xFFF43F5E, "Rose"),
    AMBER(0xFFF59E0B, "Amber"),
    BLUE(0xFF3B82F6, "Blue")
}

data class AppSettings(
    val apiKey: String = "",
    val model: String = "mistral-small-latest",
    val customModel: String = "",
    val endpoint: String = DEFAULT_ENDPOINT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1.0f,
    val accent: AccentColor = AccentColor.VIOLET,
    val sandboxEnabled: Boolean = true,
    val maxExecutionSeconds: Int = 30,
    val allowedTools: Set<String> = SandboxTool.entries.map { it.name }.toSet(),
    val onboardingDone: Boolean = false
) {
    val resolvedModel: String get() = customModel.ifBlank { model }

    fun isToolAllowed(tool: SandboxTool): Boolean = tool.name in allowedTools

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.mistral.ai"
    }
}

data class FileAttachment(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    /** Text content for text-like files, null for binary files. */
    val content: String? = null,
    val uri: String? = null
)

data class ModelInfo(val id: String)

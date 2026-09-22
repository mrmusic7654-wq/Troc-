package com.troc.domain.model

sealed class VoiceStatus {
    object Idle : VoiceStatus()
    object Listening : VoiceStatus()
    object Transcribing : VoiceStatus()
    object Thinking : VoiceStatus()
    object Speaking : VoiceStatus()
    data class Error(val message: String) : VoiceStatus()
}

data class VoiceState(
    val status: VoiceStatus = VoiceStatus.Idle,
    val amplitude: Float = 0f, // 0..1
    val transcript: String = "",
    val lastAssistantMessage: String = "",
    val isMuted: Boolean = false,
    val isBargeInEnabled: Boolean = true
)

enum class SttModel(val id: String) {
    WHISPER_LARGE_V3("whisper-large-v3"),
    WHISPER_TURBO("whisper-large-v3-turbo")
}

enum class TtsVoice(val id: String, val displayName: String) {
    ARISTA("Arista-PlayAI", "Arista"),
    ATLAS("Atlas-PlayAI", "Atlas"),
    BASIL("Basil-PlayAI", "Basil"),
    CELESTE("Celeste-PlayAI", "Celeste"),
    FRITZ("Fritz-PlayAI", "Fritz"),
    QUINN("Quinn-PlayAI", "Quinn"),
    ALLOY("Aaliyah-PlayAI", "Aaliyah")
}

data class VoiceSettings(
    val sttModel: SttModel = SttModel.WHISPER_LARGE_V3,
    val ttsVoice: TtsVoice = TtsVoice.ARISTA,
    val speechSpeed: Float = 1.0f,
    val autoSend: Boolean = true,
    val autoPlay: Boolean = true,
    val inputLanguage: String = "auto"
)

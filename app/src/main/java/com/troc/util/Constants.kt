package com.troc.util

object Constants {
    const val MISTRAL_BASE_URL = "https://api.mistral.ai/"
    const val GROQ_BASE_URL = "https://api.groq.com/openai/"
    const val DEFAULT_MISTRAL_MODEL = "mistral-small-latest"
    const val DEFAULT_GROQ_STT_MODEL = "whisper-large-v3"
    const val DEFAULT_GROQ_TTS_MODEL = "playai-tts"
    const val DEFAULT_GROQ_TTS_VOICE = "Arista-PlayAI"
    const val MAX_TOOL_ITERATIONS = 6
    const val MAX_ATTACHMENT_SIZE_MB = 10
    const val SAMPLE_RATE = 16000
    const val TTS_CHUNK_LIMIT = 10240
    const val VAD_SILENCE_MS = 1000L
    const val BARGE_IN_THRESHOLD = 0.15f
}

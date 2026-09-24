package com.troc.data.repository

import com.troc.data.api.GroqApi
import com.troc.util.Constants
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRepository @Inject constructor(
    private val groqApi: GroqApi,
    private val apiKeyRepository: ApiKeyRepository
) {
    suspend fun transcribe(
        wavFile: File,
        model: String = Constants.DEFAULT_GROQ_STT_MODEL,
        language: String? = null,
        baseUrl: String = Constants.GROQ_BASE_URL
    ): Result<String> {
        val key = apiKeyRepository.getGroqKeySync() ?: return Result.failure(Exception("Missing Groq API key"))
        return groqApi.transcribeAudio(baseUrl, key, wavFile, model, language)
    }

    suspend fun synthesize(
        text: String,
        voice: String,
        model: String = Constants.DEFAULT_GROQ_TTS_MODEL,
        speed: Float = 1.0f,
        baseUrl: String = Constants.GROQ_BASE_URL
    ): Result<ByteArray> {
        val key = apiKeyRepository.getGroqKeySync() ?: return Result.failure(Exception("Missing Groq API key"))
        // Chunk long text at sentence boundaries
        if (text.length <= Constants.TTS_CHUNK_LIMIT) {
            return groqApi.synthesizeSpeech(baseUrl, key, text, voice, model, speed)
        } else {
            // For simplicity, return first chunk failure handling outside; but we implement chunking in use case
            return groqApi.synthesizeSpeech(baseUrl, key, text.take(Constants.TTS_CHUNK_LIMIT), voice, model, speed)
        }
    }

    fun chunkTextForTts(text: String, limit: Int = Constants.TTS_CHUNK_LIMIT): List<String> {
        if (text.length <= limit) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (sentence in sentences) {
            if (current.length + sentence.length + 1 > limit) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current = StringBuilder()
                }
                // If single sentence too long, split hard
                if (sentence.length > limit) {
                    sentence.chunked(limit).forEach { chunks.add(it) }
                } else {
                    current.append(sentence).append(" ")
                }
            } else {
                current.append(sentence).append(" ")
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }
}

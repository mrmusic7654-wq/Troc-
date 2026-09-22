package com.example.troc.domain.model

data class ApiMessage(val role: String, val content: String)

data class ChatRequestSpec(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val messages: List<ApiMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null
)

class TrocApiException(val code: Int, message: String) : Exception(message)

object ApiErrors {
    fun friendly(code: Int?, raw: String?): String {
        val detail = raw?.take(300)?.trim().orEmpty().ifBlank { "No details returned" }
        return when (code) {
            401 -> "Invalid or missing API key (401). Check it in Settings."
            429 -> "Rate limit reached (429). Wait a moment and try again."
            in 500..599 -> "Mistral server error ($code). Try again shortly."
            else -> "Mistral API error${code?.let { " ($it)" } ?: ""}: $detail"
        }
    }
}

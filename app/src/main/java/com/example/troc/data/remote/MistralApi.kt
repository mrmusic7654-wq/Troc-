package com.example.troc.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

@Serializable
data class ApiMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ApiMessageDto>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false
)

@Serializable
data class ChatCompletionResponseDto(
    val id: String? = null,
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null
) {
    val firstText: String get() = choices.firstOrNull()?.message?.content.orEmpty()
}

@Serializable
data class ChoiceDto(
    val index: Int = 0,
    val message: ApiMessageDto? = null,
    val delta: DeltaDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class DeltaDto(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class ModelsResponseDto(
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String,
    @SerialName("max_context_length") val maxContextLength: Int? = null,
    val capabilities: List<String> = emptyList()
)

/**
 * Retrofit surface for the non-streaming Mistral endpoints.
 * All routes take an absolute [Url] so the custom-endpoint setting works
 * without rebuilding the client.
 */
interface MistralApi {

    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: ChatCompletionRequestDto
    ): ChatCompletionResponseDto

    @GET
    suspend fun listModels(
        @Url url: String,
        @Header("Authorization") auth: String
    ): ModelsResponseDto
}

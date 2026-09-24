package com.troc.data.api

import com.google.gson.annotations.SerializedName

data class MistralChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<MistralMessage>,
    @SerializedName("temperature") val temperature: Float = 0.7f,
    @SerializedName("stream") val stream: Boolean = true,
    @SerializedName("max_tokens") val maxTokens: Int? = null
)

data class MistralMessage(
    @SerializedName("role") val role: String, // system, user, assistant, tool
    @SerializedName("content") val content: String,
    @SerializedName("name") val name: String? = null
)

data class MistralStreamChunk(
    @SerializedName("id") val id: String?,
    @SerializedName("choices") val choices: List<StreamChoice>?,
    @SerializedName("usage") val usage: MistralUsage? = null
)

data class StreamChoice(
    @SerializedName("delta") val delta: Delta?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class Delta(
    @SerializedName("content") val content: String?,
    @SerializedName("role") val role: String?
)

data class MistralUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)

data class MistralNonStreamResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("choices") val choices: List<NonStreamChoice>?,
    @SerializedName("usage") val usage: MistralUsage?
)

data class NonStreamChoice(
    @SerializedName("message") val message: MistralMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class MistralModelsResponse(
    @SerializedName("data") val data: List<ModelInfo>?
)

data class ModelInfo(
    @SerializedName("id") val id: String
)

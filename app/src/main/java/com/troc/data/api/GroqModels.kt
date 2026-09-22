package com.troc.data.api

import com.google.gson.annotations.SerializedName

data class GroqTranscriptionResponse(
    @SerializedName("text") val text: String
)

data class GroqTtsRequest(
    @SerializedName("model") val model: String = "playai-tts",
    @SerializedName("input") val input: String,
    @SerializedName("voice") val voice: String,
    @SerializedName("response_format") val responseFormat: String = "wav",
    @SerializedName("speed") val speed: Float = 1.0f
)

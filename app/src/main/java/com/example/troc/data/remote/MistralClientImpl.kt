package com.example.troc.data.remote

import com.example.troc.domain.model.ApiErrors
import com.example.troc.domain.model.ApiMessage
import com.example.troc.domain.model.ChatRequestSpec
import com.example.troc.domain.model.ModelInfo
import com.example.troc.domain.model.StreamEvent
import com.example.troc.domain.model.TrocApiException
import com.example.troc.domain.repository.MistralClient
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import retrofit2.HttpException
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mistral API client: token streaming via OkHttp Server-Sent Events,
 * non-streaming completions and model listing via Retrofit.
 */
@Singleton
class MistralClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val api: MistralApi
) : MistralClient {

    private fun endpoint(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + path

    private fun auth(apiKey: String): String = "Bearer ${apiKey.trim()}"

    // ------------------------------------------------------------------ streaming

    override fun stream(spec: ChatRequestSpec): Flow<StreamEvent> = callbackFlow {
        val url = endpoint(spec.baseUrl, "/v1/chat/completions")
        val body = json
            .encodeToString(
                ChatCompletionRequestDto(
                    model = spec.model,
                    messages = spec.messages.map { ApiMessage(it.role, it.content) }.map {
                        ApiMessageDto(it.role, it.content)
                    },
                    temperature = spec.temperature,
                    maxTokens = spec.maxTokens,
                    stream = true
                )
            )
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth(spec.apiKey))
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == DONE_MARKER) {
                        trySend(StreamEvent.Done)
                        close()
                        return
                    }
                    runCatching { json.decodeFromString<ChatCompletionResponseDto>(data) }
                        .onSuccess { chunk ->
                            val choice = chunk.choices.firstOrNull()
                            val token = choice?.delta?.content
                            if (!token.isNullOrEmpty()) trySend(StreamEvent.Token(token))
                            if (choice?.finishReason != null) {
                                trySend(StreamEvent.Done)
                                close()
                            }
                        }
                        // Malformed keep-alive chunks are ignored.
                }

                override fun onClosed(eventSource: EventSource) {
                    trySend(StreamEvent.Done)
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val code = response?.code
                    val rawError = response?.body?.string()?.take(500)
                    val message = when {
                        t is java.io.IOException && response == null ->
                            "Network error: check your connection or custom endpoint."
                        rawError != null -> ApiErrors.friendly(code, rawError)
                        else -> ApiErrors.friendly(code, t?.message)
                    }
                    trySend(StreamEvent.Error(message, code))
                    close()
                }
            }
        )

        awaitClose { eventSource.cancel() }
    }

    // ------------------------------------------------------------------ non-streaming

    override suspend fun complete(spec: ChatRequestSpec): String = withContext(Dispatchers.IO) {
        try {
            api.chatCompletions(
                url = endpoint(spec.baseUrl, "/v1/chat/completions"),
                auth = auth(spec.apiKey),
                request = ChatCompletionRequestDto(
                    model = spec.model,
                    messages = spec.messages.map { ApiMessageDto(it.role, it.content) },
                    temperature = spec.temperature,
                    maxTokens = spec.maxTokens,
                    stream = false
                )
            ).firstText
        } catch (e: HttpException) {
            throw TrocApiException(e.code(), ApiErrors.friendly(e.code(), e.response()?.errorBody()?.string()))
        }
    }

    override suspend fun listModels(baseUrl: String, apiKey: String): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            try {
                api.listModels(
                    url = endpoint(baseUrl, "/v1/models"),
                    auth = auth(apiKey)
                ).data.map { ModelInfo(it.id) }
            } catch (e: HttpException) {
                throw TrocApiException(e.code(), ApiErrors.friendly(e.code(), e.response()?.errorBody()?.string()))
            }
        }

    private companion object {
        const val DONE_MARKER = "[DONE]"
    }
}

package com.troc.data.api

import com.google.gson.Gson
import com.troc.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MistralApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    sealed class StreamEvent {
        data class Content(val text: String) : StreamEvent()
        data class Usage(val usage: MistralUsage) : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }

    fun streamChatCompletion(
        baseUrl: String = Constants.MISTRAL_BASE_URL,
        request: MistralChatRequest
    ): Flow<String> = callbackFlow {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/chat/completions" else "$baseUrl/v1/chat/completions"
        val json = gson.toJson(request)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val call = okHttpClient.newCall(httpRequest)
        var response: Response? = null
        try {
            response = call.execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                close(IOException("Mistral API error ${response.code}: $errorBody"))
                return@callbackFlow
            }

            val source = response.body?.source()
            if (source == null) {
                close(IOException("Empty response body from Mistral API"))
                return@callbackFlow
            }

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.startsWith("data:")) {
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        break
                    }
                    if (data.isNotEmpty()) {
                        try {
                            val chunk = gson.fromJson(data, MistralStreamChunk::class.java)
                            val content = chunk.choices?.firstOrNull()?.delta?.content
                            if (!content.isNullOrEmpty()) {
                                trySend(content)
                            }
                        } catch (e: Exception) {
                            // ignore malformed chunks
                        }
                    }
                }
            }
            close()
        } catch (e: Exception) {
            close(e)
        } finally {
            response?.close()
        }

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    fun streamChatCompletionWithUsage(
        baseUrl: String = Constants.MISTRAL_BASE_URL,
        request: MistralChatRequest
    ): Flow<StreamEvent> = callbackFlow {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/chat/completions" else "$baseUrl/v1/chat/completions"
        val json = gson.toJson(request)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val call = okHttpClient.newCall(httpRequest)
        var response: Response? = null
        try {
            response = call.execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                close(IOException("Mistral API error ${response.code}: $errorBody"))
                return@callbackFlow
            }

            val source = response.body?.source()
            if (source == null) {
                close(IOException("Empty response body from Mistral API"))
                return@callbackFlow
            }

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.startsWith("data:")) {
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        break
                    }
                    if (data.isNotEmpty()) {
                        try {
                            val chunk = gson.fromJson(data, MistralStreamChunk::class.java)
                            val content = chunk.choices?.firstOrNull()?.delta?.content
                            if (!content.isNullOrEmpty()) {
                                trySend(StreamEvent.Content(content))
                            }
                            chunk.usage?.let { usage ->
                                trySend(StreamEvent.Usage(usage))
                            }
                        } catch (e: Exception) {
                            // ignore malformed chunk
                        }
                    }
                }
            }
            close()
        } catch (e: Exception) {
            close(e)
        } finally {
            response?.close()
        }

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    suspend fun chatCompletionNonStream(
        baseUrl: String = Constants.MISTRAL_BASE_URL,
        request: MistralChatRequest
    ): Result<Pair<String, MistralUsage?>> = withContext(Dispatchers.IO) {
        try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/chat/completions" else "$baseUrl/v1/chat/completions"
            val nonStreamRequest = request.copy(stream = false)
            val json = gson.toJson(nonStreamRequest)
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val httpRequest = Request.Builder().url(url).post(body).build()

            val response = okHttpClient.newCall(httpRequest).execute()
            val bodyStr = response.body?.string() ?: ""
            val code = response.code
            response.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Mistral error $code: $bodyStr"))
            }
            val parsed = gson.fromJson(bodyStr, MistralNonStreamResponse::class.java)
            val content = parsed.choices?.firstOrNull()?.message?.content ?: ""
            Result.success(content to parsed.usage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateKey(baseUrl: String = Constants.MISTRAL_BASE_URL, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").removePrefix("bearer ").trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanKey.isBlank() || cleanKey.length < 5) return@withContext false
        try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/models" else "$baseUrl/v1/models"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanKey")
                .get()
                .build()

            val validationClient = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val resp = validationClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string() ?: ""
            resp.close()

            when (code) {
                200 -> true
                401, 403 -> false
                429 -> true // Rate limited means the key is authenticated and valid
                else -> {
                    val isAuthError = body.contains("unauthorized", ignoreCase = true) ||
                            (body.contains("invalid", ignoreCase = true) && body.contains("key", ignoreCase = true)) ||
                            body.contains("authentication", ignoreCase = true)
                    !isAuthError
                }
            }
        } catch (e: Exception) {
            // If connection failed or timeout, check if basic format looks like an API key
            cleanKey.length >= 10
        }
    }

    suspend fun getModels(baseUrl: String = Constants.MISTRAL_BASE_URL, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").removePrefix("bearer ").trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanKey.isBlank()) return@withContext emptyList()
        try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/models" else "$baseUrl/v1/models"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanKey")
                .get()
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return@withContext emptyList()
            }
            val body = resp.body?.string() ?: ""
            resp.close()
            val parsed = gson.fromJson(body, MistralModelsResponse::class.java)
            parsed.data?.map { it.id } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

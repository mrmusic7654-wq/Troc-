package com.troc.data.api

import com.google.gson.Gson
import com.troc.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface MistralApiService {
    // Retrofit would be used for non-streaming, but streaming is custom OkHttp
}

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
        val body = json.toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val call = okHttpClient.newCall(httpRequest)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            close(e)
            return@callbackFlow
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            close(IOException("Mistral API error ${response.code}: $errorBody"))
            return@callbackFlow
        }

        val source = response.body?.source()
        if (source == null) {
            close(IOException("Empty response body"))
            return@callbackFlow
        }

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, MistralStreamChunk::class.java)
                        val content = chunk.choices?.firstOrNull()?.delta?.content
                        if (!content.isNullOrEmpty()) {
                            trySend(content)
                        }
                        // Usage may appear in chunk
                        chunk.usage?.let {
                            // Send usage as special marker - we will parse in repository
                            // For backward compat, we ignore here, usage handled in streamWithUsage
                        }
                    } catch (e: Exception) {
                        // ignore malformed chunk
                    }
                }
            }
            close()
        } catch (e: Exception) {
            close(e)
        } finally {
            response.close()
        }

        awaitClose { call.cancel() }
    }

    fun streamChatCompletionWithUsage(
        baseUrl: String = Constants.MISTRAL_BASE_URL,
        request: MistralChatRequest
    ): Flow<StreamEvent> = callbackFlow {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/chat/completions" else "$baseUrl/v1/chat/completions"
        val json = gson.toJson(request)
        val body = json.toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val call = okHttpClient.newCall(httpRequest)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            close(e)
            return@callbackFlow
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            close(IOException("Mistral API error ${response.code}: $errorBody"))
            return@callbackFlow
        }

        val source = response.body?.source()
        if (source == null) {
            close(IOException("Empty response body"))
            return@callbackFlow
        }

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
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
                        // ignore
                    }
                }
            }
            close()
        } catch (e: Exception) {
            close(e)
        } finally {
            response.close()
        }

        awaitClose { call.cancel() }
    }

    suspend fun chatCompletionNonStream(
        baseUrl: String = Constants.MISTRAL_BASE_URL,
        request: MistralChatRequest
    ): Result<Pair<String, MistralUsage?>> {
        return try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/chat/completions" else "$baseUrl/v1/chat/completions"
            val nonStreamRequest = request.copy(stream = false)
            val json = gson.toJson(nonStreamRequest)
            val body = json.toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder().url(url).post(body).build()

            val response = okHttpClient.newCall(httpRequest).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                response.close()
                return Result.failure(Exception("Mistral error ${response.code}: $bodyStr"))
            }
            response.close()
            val parsed = gson.fromJson(bodyStr, MistralNonStreamResponse::class.java)
            val content = parsed.choices?.firstOrNull()?.message?.content ?: ""
            Result.success(content to parsed.usage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateKey(baseUrl: String = Constants.MISTRAL_BASE_URL, apiKey: String): Boolean {
        if (apiKey.isBlank() || apiKey.length < 10) return false
        return try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/models" else "$baseUrl/v1/models"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            // Use fresh client without auth interceptor for validation to avoid interference
            val validationClient = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val resp = validationClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string() ?: ""
            resp.close()
            when (code) {
                200 -> true
                401, 403 -> false
                429 -> true // Rate limited = valid key
                else -> {
                    val isAuthError = body.contains("unauthorized", ignoreCase = true) ||
                            (body.contains("invalid", ignoreCase = true) && body.contains("api", ignoreCase = true)) ||
                            body.contains("authentication", ignoreCase = true)
                    if (isAuthError) false else true
                }
            }
        } catch (e: Exception) {
            // Network error - assume valid to avoid false negative
            true
        }
    }

    suspend fun getModels(baseUrl: String, apiKey: String): List<String> {
        return try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/models" else "$baseUrl/v1/models"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return emptyList()
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

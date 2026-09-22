package com.troc.data.api

import com.google.gson.Gson
import com.troc.util.Constants
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun transcribeAudio(
        baseUrl: String = Constants.GROQ_BASE_URL,
        apiKey: String,
        wavFile: File,
        model: String = Constants.DEFAULT_GROQ_STT_MODEL,
        language: String? = null
    ): Result<String> {
        return try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}audio/transcriptions" else "$baseUrl/audio/transcriptions"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", wavFile.name, wavFile.asRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", model)
                .addFormDataPart("temperature", "0")
                .apply {
                    if (language != null && language != "auto") {
                        addFormDataPart("language", language)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                return Result.failure(Exception("STT failed ${response.code}: $bodyStr"))
            }

            val parsed = gson.fromJson(bodyStr, GroqTranscriptionResponse::class.java)
            Result.success(parsed.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun synthesizeSpeech(
        baseUrl: String = Constants.GROQ_BASE_URL,
        apiKey: String,
        text: String,
        voice: String,
        model: String = Constants.DEFAULT_GROQ_TTS_MODEL,
        speed: Float = 1.0f
    ): Result<ByteArray> {
        return try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}audio/speech" else "$baseUrl/audio/speech"
            val reqObj = GroqTtsRequest(
                model = model,
                input = text,
                voice = voice,
                responseFormat = "wav",
                speed = speed
            )
            val json = gson.toJson(reqObj)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                response.close()
                return Result.failure(Exception("TTS failed ${response.code}: $err"))
            }
            val bytes = response.body?.bytes() ?: byteArrayOf()
            response.close()
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateGroqKey(baseUrl: String, apiKey: String): Boolean {
        // Use models endpoint for lightweight validation - more reliable than TTS
        return try {
            // Try Groq models endpoint first
            val modelsUrl = if (baseUrl.endsWith("/")) "${baseUrl}models" else "$baseUrl/models"
            val request = Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val resp = okHttpClient.newCall(request).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            // 200 = valid, 401/403 = invalid, other codes may still mean valid key but other error
            when (resp.code) {
                200 -> true
                401, 403 -> false
                else -> {
                    // For other errors, check if body contains auth error
                    val isAuthError = body.contains("authentication", ignoreCase = true) || 
                                      body.contains("invalid", ignoreCase = true) ||
                                      body.contains("unauthorized", ignoreCase = true)
                    !isAuthError // If not auth error, assume valid (could be rate limit, etc)
                }
            }
        } catch (e: Exception) {
            // Network error - don't mark as invalid, assume valid to avoid false negatives
            // Let actual usage determine validity
            true
        }
    }
}

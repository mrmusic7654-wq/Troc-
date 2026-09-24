package com.troc.data.api

import com.google.gson.Gson
import com.troc.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private fun buildEndpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        // If baseUrl already has /v1 and path starts with v1/, avoid duplicate
        return if (base.endsWith("/v1") && cleanPath.startsWith("v1/")) {
            "$base/${cleanPath.removePrefix("v1/")}"
        } else if (!base.endsWith("/v1") && !cleanPath.startsWith("v1/")) {
            "$base/v1/$cleanPath"
        } else {
            "$base/$cleanPath"
        }
    }

    suspend fun transcribeAudio(
        baseUrl: String = Constants.GROQ_BASE_URL,
        apiKey: String,
        wavFile: File,
        model: String = Constants.DEFAULT_GROQ_STT_MODEL,
        language: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").removePrefix("bearer ").trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Groq API key is missing"))
        }
        try {
            val url = buildEndpoint(baseUrl, "audio/transcriptions")
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
                .header("Authorization", "Bearer $cleanKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            val code = response.code
            response.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("STT failed $code: $bodyStr"))
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
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").removePrefix("bearer ").trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Groq API key is missing"))
        }
        try {
            val url = buildEndpoint(baseUrl, "audio/speech")
            val reqObj = GroqTtsRequest(
                model = model,
                input = text,
                voice = voice,
                responseFormat = "wav",
                speed = speed
            )
            val json = gson.toJson(reqObj)
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanKey")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                response.close()
                return@withContext Result.failure(Exception("TTS failed $code: $err"))
            }
            val bytes = response.body?.bytes() ?: byteArrayOf()
            response.close()
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateGroqKey(baseUrl: String = Constants.GROQ_BASE_URL, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removePrefix("Bearer ").removePrefix("bearer ").trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleanKey.isBlank() || cleanKey.length < 5) return@withContext false
        try {
            val modelsUrl = buildEndpoint(baseUrl, "models")
            val request = Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer $cleanKey")
                .get()
                .build()
            val validationClient = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val resp = validationClient.newCall(request).execute()
            val code = resp.code
            val body = resp.body?.string() ?: ""
            resp.close()
            when (code) {
                200 -> true
                401, 403 -> false
                429 -> true
                else -> {
                    val isAuthError = body.contains("unauthorized", ignoreCase = true) ||
                            (body.contains("invalid", ignoreCase = true) && body.contains("key", ignoreCase = true))
                    !isAuthError
                }
            }
        } catch (e: Exception) {
            cleanKey.length >= 10
        }
    }
}

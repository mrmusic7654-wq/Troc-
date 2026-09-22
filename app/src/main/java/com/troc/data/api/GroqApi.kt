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
        // Try a tiny TTS request
        return try {
            val url = if (baseUrl.endsWith("/")) "${baseUrl}audio/speech" else "$baseUrl/audio/speech"
            val reqObj = GroqTtsRequest(
                model = Constants.DEFAULT_GROQ_TTS_MODEL,
                input = "Hi",
                voice = Constants.DEFAULT_GROQ_TTS_VOICE
            )
            val json = gson.toJson(reqObj)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            val resp = okHttpClient.newCall(request).execute()
            val success = resp.isSuccessful || resp.code == 400 // 400 means key ok but bad request? treat as valid? We'll check 401
            val is401 = resp.code == 401 || resp.code == 403
            resp.close()
            !is401
        } catch (e: Exception) {
            false
        }
    }
}

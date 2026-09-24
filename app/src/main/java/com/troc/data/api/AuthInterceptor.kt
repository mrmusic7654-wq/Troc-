package com.troc.data.api

import com.troc.data.repository.ApiKeyRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class MistralAuthInterceptor(
    private val apiKeyRepository: ApiKeyRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = try {
            runBlocking { apiKeyRepository.getMistralKeySync() }
        } catch (e: Exception) {
            null
        }
        val cleanKey = key?.trim()
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")

        val newRequest = if (!cleanKey.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $cleanKey")
                .header("Content-Type", "application/json")
                .build()
        } else {
            original
        }
        return chain.proceed(newRequest)
    }
}

class GroqAuthInterceptor(
    private val apiKeyRepository: ApiKeyRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = try {
            runBlocking { apiKeyRepository.getGroqKeySync() }
        } catch (e: Exception) {
            null
        }
        val cleanKey = key?.trim()
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")

        val newRequest = if (!cleanKey.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $cleanKey")
                .build()
        } else {
            original
        }
        return chain.proceed(newRequest)
    }
}

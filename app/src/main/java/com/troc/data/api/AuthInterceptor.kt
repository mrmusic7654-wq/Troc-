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
        val key = runBlocking { apiKeyRepository.getMistralKeySync() }
        val newRequest = if (key != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $key")
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
        val key = runBlocking { apiKeyRepository.getGroqKeySync() }
        val newRequest = if (key != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $key")
                .build()
        } else {
            original
        }
        return chain.proceed(newRequest)
    }
}

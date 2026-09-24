package com.troc.di

import com.google.gson.Gson
import com.troc.data.api.GroqApi
import com.troc.data.api.MistralApi
import com.troc.data.repository.ApiKeyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class UnifiedAuthInterceptor(
    private val apiKeyRepository: ApiKeyRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // If Authorization already present (e.g., validation with explicit key), don't overwrite
        if (original.header("Authorization") != null) {
            return chain.proceed(original)
        }
        val url = original.url.toString()
        val key = try {
            runBlocking {
                when {
                    url.contains("groq.com", ignoreCase = true) -> apiKeyRepository.getGroqKeySync()
                    else -> apiKeyRepository.getMistralKeySync()
                }
            }
        } catch (e: Exception) {
            null
        }

        val cleanKey = key?.trim()
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")

        val builder = original.newBuilder()
        if (!cleanKey.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $cleanKey")
        }
        if (original.header("Content-Type") == null && original.body != null) {
            builder.header("Content-Type", "application/json")
        }

        return chain.proceed(builder.build())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        apiKeyRepository: ApiKeyRepository
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(UnifiedAuthInterceptor(apiKeyRepository))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    fun createNoAuthClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideMistralApi(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): MistralApi {
        return MistralApi(okHttpClient, gson)
    }

    @Provides
    @Singleton
    fun provideGroqApi(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): GroqApi {
        return GroqApi(okHttpClient, gson)
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.mistral.ai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}

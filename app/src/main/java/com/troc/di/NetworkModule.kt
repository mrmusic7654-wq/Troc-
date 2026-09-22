package com.troc.di

import com.google.gson.Gson
import com.troc.data.api.GroqApi
import com.troc.data.api.MistralApi
import com.troc.data.repository.ApiKeyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

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
        val key = runBlocking {
            try {
                when {
                    url.contains("mistral.ai") -> apiKeyRepository.getMistralKeySync()
                    url.contains("groq.com") -> apiKeyRepository.getGroqKeySync()
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
        val newRequest = if (!key.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $key")
                .build()
        } else {
            original
        }
        return chain.proceed(newRequest)
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

    // No Hilt binding for no-auth client to avoid DuplicateBindings
    // Validation creates its own client internally
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

package com.example.troc.di

import android.content.Context
import com.example.troc.BuildConfig
import com.example.troc.data.remote.MistralApi
import com.example.troc.data.remote.MistralClientImpl
import com.example.troc.data.repository.ChatHistoryRepositoryImpl
import com.example.troc.data.repository.FileRepositoryImpl
import com.example.troc.data.repository.SettingsRepositoryImpl
import com.example.troc.data.sandbox.SandboxManager
import com.example.troc.domain.repository.ChatHistoryRepository
import com.example.troc.domain.repository.FileRepository
import com.example.troc.domain.repository.MistralClient
import com.example.troc.domain.repository.SandboxGateway
import com.example.troc.domain.repository.SettingsRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://troc.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideMistralApi(retrofit: Retrofit): MistralApi =
        retrofit.create(MistralApi::class.java)

    @Provides
    @Singleton
    fun provideAppContext(@ApplicationContext context: Context): Context = context
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindChatHistoryRepository(impl: ChatHistoryRepositoryImpl): ChatHistoryRepository

    @Binds
    abstract fun bindMistralClient(impl: MistralClientImpl): MistralClient

    @Binds
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    abstract fun bindSandboxGateway(manager: SandboxManager): SandboxGateway
}

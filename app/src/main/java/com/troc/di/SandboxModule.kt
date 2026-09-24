package com.troc.di

import android.content.Context
import com.troc.data.sandbox.CodeSandbox
import com.troc.data.sandbox.DataSandbox
import com.troc.data.sandbox.FileSandbox
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SandboxModule {

    @Provides
    @Singleton
    fun provideCodeSandbox(@ApplicationContext context: Context): CodeSandbox = CodeSandbox(context)

    @Provides
    @Singleton
    fun provideDataSandbox(): DataSandbox = DataSandbox()

    @Provides
    @Singleton
    fun provideFileSandbox(@ApplicationContext context: Context): FileSandbox = FileSandbox(context)
}

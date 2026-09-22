package com.troc.di

import android.content.Context
import androidx.room.Room
import com.troc.data.db.AppDatabase
import com.troc.data.db.ChatDao
import com.troc.data.db.MessageDao
import com.troc.data.db.SandboxDao
import com.troc.data.db.WorkflowDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "troc.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideSandboxDao(db: AppDatabase): SandboxDao = db.sandboxDao()

    @Provides
    fun provideWorkflowDao(db: AppDatabase): WorkflowDao = db.workflowDao()

    @Provides
    fun provideUsageDao(db: AppDatabase) = db.usageDao()
}

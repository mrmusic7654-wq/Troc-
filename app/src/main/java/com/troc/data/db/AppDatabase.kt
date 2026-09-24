package com.troc.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        SandboxSessionEntity::class,
        SavedWorkflowEntity::class,
        UsageEntity::class,
        UsageHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun sandboxDao(): SandboxDao
    abstract fun workflowDao(): WorkflowDao
    abstract fun usageDao(): UsageDao
}

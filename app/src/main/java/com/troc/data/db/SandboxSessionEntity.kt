package com.troc.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sandbox_sessions")
data class SandboxSessionEntity(
    @PrimaryKey val id: String,
    val chatId: String?,
    val toolType: String,
    val language: String?,
    val input: String,
    val output: String?,
    val error: String?,
    val executionTimeMs: Long?,
    val createdAt: Long
)

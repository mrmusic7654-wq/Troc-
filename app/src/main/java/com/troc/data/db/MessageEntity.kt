package com.troc.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chatId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String, // USER, ASSISTANT, TOOL, SYSTEM
    val content: String,
    val timestamp: Long,
    val toolType: String? = null,
    val toolLanguage: String? = null,
    val toolInput: String? = null,
    val toolOutput: String? = null,
    val toolError: String? = null,
    val executionTimeMs: Long? = null
)

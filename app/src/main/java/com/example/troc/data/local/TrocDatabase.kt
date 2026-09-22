package com.example.troc.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val messagesJson: String,
    val timestamp: Long,
    val isAgentMode: Boolean,
    val messageCount: Int
)

@Dao
interface ChatDao {
    @Upsert
    suspend fun upsert(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getById(id: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE title LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<ChatEntity>>

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chats")
    suspend fun deleteAll()
}

@Entity(tableName = "sandbox_sessions")
data class SandboxSessionEntity(
    @PrimaryKey val id: String,
    val chatId: String?,
    val tool: String,
    val language: String?,
    val input: String,
    val output: String,
    val success: Boolean,
    val durationMs: Long,
    val timestamp: Long
)

@Dao
interface SandboxSessionDao {
    @Upsert
    suspend fun upsert(session: SandboxSessionEntity)

    @Query("SELECT * FROM sandbox_sessions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SandboxSessionEntity>>

    @Query("SELECT * FROM sandbox_sessions WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun observeByChat(chatId: String): Flow<List<SandboxSessionEntity>>

    @Query("SELECT * FROM sandbox_sessions WHERE tool LIKE '%' || :query || '%' OR input LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<SandboxSessionEntity>>

    @Query("DELETE FROM sandbox_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sandbox_sessions")
    suspend fun deleteAll()
}

@Database(
    entities = [ChatEntity::class, SandboxSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TrocDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun sandboxSessionDao(): SandboxSessionDao
}

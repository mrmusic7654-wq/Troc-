package com.troc.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChat(id: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChat(id: String)

    @Query("DELETE FROM chats")
    suspend fun clearAll()

    @Query("UPDATE chats SET updatedAt = :updatedAt, hasSandbox = :hasSandbox, hasVoice = :hasVoice WHERE id = :id")
    suspend fun updateMeta(id: String, updatedAt: Long, hasSandbox: Boolean, hasVoice: Boolean)

    @Query("SELECT * FROM chats WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchChats(query: String): Flow<List<ChatEntity>>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessages(chatId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChatMessages(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Dao
interface SandboxDao {
    @Query("SELECT * FROM sandbox_sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<SandboxSessionEntity>>

    @Query("SELECT * FROM sandbox_sessions WHERE chatId = :chatId ORDER BY createdAt DESC")
    fun observeSessionsForChat(chatId: String): Flow<List<SandboxSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SandboxSessionEntity)

    @Query("DELETE FROM sandbox_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM sandbox_sessions")
    suspend fun clearAll()
}

@Dao
interface WorkflowDao {
    @Query("SELECT * FROM workflows ORDER BY createdAt DESC")
    fun observeWorkflows(): Flow<List<SavedWorkflowEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: SavedWorkflowEntity)

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteWorkflow(id: String)

    @Query("SELECT * FROM workflows WHERE id = :id")
    suspend fun getWorkflow(id: String): SavedWorkflowEntity?
}

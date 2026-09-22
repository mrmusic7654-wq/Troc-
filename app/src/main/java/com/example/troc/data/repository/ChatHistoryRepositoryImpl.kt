package com.example.troc.data.repository

import com.example.troc.data.local.ChatDao
import com.example.troc.data.local.ChatEntity
import com.example.troc.data.local.SandboxSessionDao
import com.example.troc.data.local.SandboxSessionEntity
import com.example.troc.domain.model.Chat
import com.example.troc.domain.model.ChatMessage
import com.example.troc.domain.model.SandboxSessionRecord
import com.example.troc.domain.repository.ChatHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val sessionDao: SandboxSessionDao,
    private val json: Json
) : ChatHistoryRepository {

    private val messagesSerializer = ListSerializer(ChatMessage.serializer())

    override fun observeChats(): Flow<List<Chat>> =
        chatDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSessions(): Flow<List<SandboxSessionRecord>> =
        sessionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getChat(id: String): Chat? = chatDao.getById(id)?.toDomain()

    override suspend fun saveChat(chat: Chat) {
        chatDao.upsert(chat.toEntity())
    }

    override suspend fun deleteChat(id: String) = chatDao.deleteById(id)

    override suspend fun clearChats() = chatDao.deleteAll()

    override suspend fun saveSession(session: SandboxSessionRecord) =
        sessionDao.upsert(session.toEntity())

    override suspend fun deleteSession(id: String) = sessionDao.deleteById(id)

    override suspend fun clearSessions() = sessionDao.deleteAll()

    // ------------------------------------------------------------------ mappers

    private fun ChatEntity.toDomain(): Chat = Chat(
        id = id,
        title = title,
        messages = runCatching { json.decodeFromString(messagesSerializer, messagesJson) }
            .getOrDefault(emptyList()),
        timestamp = timestamp,
        isAgentMode = isAgentMode
    )

    private fun Chat.toEntity(): ChatEntity = ChatEntity(
        id = id,
        title = title,
        messagesJson = json.encodeToString(messagesSerializer, messages),
        timestamp = timestamp,
        isAgentMode = isAgentMode,
        messageCount = messages.size
    )

    private fun SandboxSessionEntity.toDomain(): SandboxSessionRecord = SandboxSessionRecord(
        id = id,
        chatId = chatId,
        tool = tool,
        language = language,
        input = input,
        output = output,
        success = success,
        durationMs = durationMs,
        timestamp = timestamp
    )

    private fun SandboxSessionRecord.toEntity(): SandboxSessionEntity = SandboxSessionEntity(
        id = id,
        chatId = chatId,
        tool = tool,
        language = language,
        input = input,
        output = output,
        success = success,
        durationMs = durationMs,
        timestamp = timestamp
    )
}

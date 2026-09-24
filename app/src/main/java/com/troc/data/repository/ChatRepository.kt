package com.troc.data.repository

import com.troc.data.db.ChatDao
import com.troc.data.db.ChatEntity
import com.troc.data.db.MessageDao
import com.troc.data.db.MessageEntity
import com.troc.domain.model.ChatInfo
import com.troc.domain.model.ChatMessage
import com.troc.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) {
    fun observeChats(): Flow<List<ChatInfo>> {
        return chatDao.observeChats().map { list ->
            list.map { entity ->
                ChatInfo(
                    id = entity.id,
                    title = entity.title,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    hasSandbox = entity.hasSandbox,
                    hasVoice = entity.hasVoice
                )
            }
        }
    }

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> {
        return messageDao.observeMessages(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMessages(chatId: String): List<ChatMessage> {
        return messageDao.getMessages(chatId).map { it.toDomain() }
    }

    suspend fun createChat(title: String = "New Chat"): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatDao.insertChat(ChatEntity(id = id, title = title, createdAt = now, updatedAt = now))
        return id
    }

    suspend fun saveMessage(chatId: String, message: ChatMessage) {
        // Ensure chat exists
        var chat = chatDao.getChat(chatId)
        if (chat == null) {
            val now = System.currentTimeMillis()
            chat = ChatEntity(id = chatId, title = message.content.take(50), createdAt = now, updatedAt = now)
            chatDao.insertChat(chat)
        }
        // If first user message, update title
        val existingMessages = messageDao.getMessages(chatId)
        if (existingMessages.isEmpty() && message.role == MessageRole.USER) {
            val updated = chat.copy(title = message.content.take(60), updatedAt = System.currentTimeMillis())
            chatDao.insertChat(updated)
        } else {
            chatDao.insertChat(chat.copy(updatedAt = System.currentTimeMillis()))
        }

        messageDao.insertMessage(message.toEntity(chatId))
    }

    suspend fun deleteChat(chatId: String) {
        chatDao.deleteChat(chatId)
        messageDao.clearChatMessages(chatId)
    }

    suspend fun clearAllChats() {
        chatDao.clearAll()
        messageDao.clearAll()
    }

    fun searchChats(query: String): Flow<List<ChatInfo>> {
        return chatDao.searchChats(query).map { list ->
            list.map { ChatInfo(it.id, it.title, it.createdAt, it.updatedAt, it.hasSandbox, it.hasVoice) }
        }
    }

    suspend fun updateChatMeta(chatId: String, hasSandbox: Boolean, hasVoice: Boolean) {
        val chat = chatDao.getChat(chatId) ?: return
        chatDao.updateMeta(chatId, System.currentTimeMillis(), hasSandbox || chat.hasSandbox, hasVoice || chat.hasVoice)
    }

    private fun MessageEntity.toDomain(): ChatMessage {
        val role = try { MessageRole.valueOf(role) } catch (e: Exception) { MessageRole.USER }
        return ChatMessage(
            id = id,
            role = role,
            content = content,
            timestamp = timestamp,
            chatId = chatId
        )
    }

    private fun ChatMessage.toEntity(chatId: String): MessageEntity {
        return MessageEntity(
            id = id,
            chatId = chatId,
            role = role.name,
            content = content,
            timestamp = timestamp
        )
    }
}

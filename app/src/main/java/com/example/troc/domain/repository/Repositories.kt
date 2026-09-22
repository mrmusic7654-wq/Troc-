package com.example.troc.domain.repository

import com.example.troc.domain.model.AppSettings
import com.example.troc.domain.model.Chat
import com.example.troc.domain.model.ChatRequestSpec
import com.example.troc.domain.model.FileAction
import com.example.troc.domain.model.FileAttachment
import com.example.troc.domain.model.ModelInfo
import com.example.troc.domain.model.SandboxRequest
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.SandboxSessionRecord
import com.example.troc.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

interface ChatHistoryRepository {
    fun observeChats(): Flow<List<Chat>>
    fun observeSessions(): Flow<List<SandboxSessionRecord>>
    suspend fun getChat(id: String): Chat?
    suspend fun saveChat(chat: Chat)
    suspend fun deleteChat(id: String)
    suspend fun clearChats()
    suspend fun saveSession(session: SandboxSessionRecord)
    suspend fun deleteSession(id: String)
    suspend fun clearSessions()
}

interface MistralClient {
    fun stream(spec: ChatRequestSpec): Flow<StreamEvent>
    suspend fun complete(spec: ChatRequestSpec): String
    suspend fun listModels(baseUrl: String, apiKey: String): List<ModelInfo>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun clearAll()
}

interface FileRepository {
    /** Reads a picked document into an attachable [FileAttachment] (text content when possible). */
    suspend fun readAttachment(uriString: String): FileAttachment?

    /** Runs a sandbox file action against a content [android.net.Uri]. */
    suspend fun inspectFile(uriString: String, action: FileAction, pattern: String? = null, replacement: String = ""): SandboxResult

    /** Runs a sandbox file action against raw in-memory text content. */
    suspend fun inspectContent(fileName: String, content: String, action: FileAction, pattern: String? = null, replacement: String = ""): SandboxResult
}

/** Domain-facing contract for the sandboxed execution environment. */
interface SandboxGateway {
    suspend fun execute(request: SandboxRequest): SandboxResult
    fun cancelActive()
}

interface WorkflowRunner {
    suspend fun execute(
        steps: List<com.example.troc.domain.model.WorkflowStep>,
        onStepResult: suspend (index: Int, result: SandboxResult) -> Unit = { _, _ -> }
    ): List<SandboxResult>
}

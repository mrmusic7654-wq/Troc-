package com.example.troc.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.troc.domain.agent.AgentPrompts
import com.example.troc.domain.agent.AgentToolCall
import com.example.troc.domain.agent.AgentToolCallParser
import com.example.troc.domain.model.ApiErrors
import com.example.troc.domain.model.ApiMessage
import com.example.troc.domain.model.AppSettings
import com.example.troc.domain.model.Chat
import com.example.troc.domain.model.ChatMessage
import com.example.troc.domain.model.ChatRequestSpec
import com.example.troc.domain.model.FileAction
import com.example.troc.domain.model.FileAttachment
import com.example.troc.domain.model.Role
import com.example.troc.domain.model.SandboxLanguage
import com.example.troc.domain.model.SandboxRequest
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.SandboxTool
import com.example.troc.domain.model.StreamEvent
import com.example.troc.domain.model.WorkflowStep
import com.example.troc.domain.repository.ChatHistoryRepository
import com.example.troc.domain.repository.FileRepository
import com.example.troc.domain.repository.SettingsRepository
import com.example.troc.domain.repository.SandboxGateway
import com.example.troc.domain.usecase.RunWorkflowUseCase
import com.example.troc.domain.usecase.StreamChatUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isAgentMode: Boolean = false,
    val input: String = "",
    val error: String? = null,
    val attachment: FileAttachment? = null,
    val workflowSteps: List<WorkflowStep> = emptyList(),
    val isExecutingWorkflow: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatHistory: ChatHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val streamChat: StreamChatUseCase,
    private val sandbox: SandboxGateway,
    private val parser: AgentToolCallParser,
    private val runWorkflow: RunWorkflowUseCase,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private var chatId: String? = null
    private var chatTitle: String = ""
    private var streamJob: Job? = null
    private var fileContext: FileAttachment? = null

    // ------------------------------------------------------------------ lifecycle

    fun loadChat(id: String?) {
        if (id == null || id == chatId) return
        viewModelScope.launch {
            val chat = chatHistory.getChat(id) ?: return@launch
            chatId = chat.id
            chatTitle = chat.title
            fileContext = null
            _state.update {
                ChatState(messages = chat.messages, isAgentMode = chat.isAgentMode)
            }
        }
    }

    fun newChat() {
        streamJob?.cancel()
        chatId = null
        chatTitle = ""
        fileContext = null
        _state.update { ChatState(isAgentMode = it.isAgentMode) }
    }

    fun setInput(value: String) = _state.update { it.copy(input = value) }

    fun setAgentMode(enabled: Boolean) {
        _state.update { it.copy(isAgentMode = enabled) }
        persist()
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    // ------------------------------------------------------------------ attachments

    fun attachFile(uriString: String) {
        viewModelScope.launch {
            val attachment = fileRepository.readAttachment(uriString)
            if (attachment == null) {
                _state.update { it.copy(error = "Could not read that file.") }
            } else {
                fileContext = attachment
                _state.update { it.copy(attachment = attachment) }
            }
        }
    }

    fun clearAttachment() {
        fileContext = null
        _state.update { it.copy(attachment = null) }
    }

    // ------------------------------------------------------------------ chat + agent loop

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _state.value.isLoading) return
        streamJob = viewModelScope.launch {
            val current = settingsRepository.settings.first()
            if (current.apiKey.isBlank()) {
                _state.update { it.copy(error = "Add your Mistral API key in Settings first.") }
                return@launch
            }
            val attachment = _state.value.attachment
            val content = if (attachment?.content != null) {
                buildString {
                    append(text)
                    append("\n\n📎 Attached file: ")
                    append(attachment.name)
                    append(" (")
                    append(com.example.troc.util.FileUtils.humanBytes(attachment.sizeBytes))
                    append(")\n```\n")
                    append(attachment.content.take(8_000))
                    append("\n```")
                }
            } else {
                text
            }
            val userMessage = ChatMessage(role = Role.USER, content = content)
            _state.update {
                it.copy(
                    messages = it.messages + userMessage,
                    input = "",
                    attachment = null,
                    isLoading = true,
                    error = null
                )
            }
            ensureChatCreated(userMessage)
            runConversation(current)
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        sandbox.cancelActive()
        _state.update { it.copy(isLoading = false) }
        persist()
    }

    private suspend fun runConversation(current: AppSettings) {
        var iteration = 0
        try {
            while (true) {
                val assistant = ChatMessage(role = Role.ASSISTANT, content = "")
                appendMessage(assistant)
                var streamError: String? = null
                streamChat(
                    ChatRequestSpec(
                        baseUrl = current.endpoint,
                        apiKey = current.apiKey,
                        model = current.resolvedModel,
                        messages = buildApiMessages(current)
                    )
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Token -> updateLastAssistant { it + event.text }
                        is StreamEvent.Done -> Unit
                        is StreamEvent.Error -> streamError = event.message
                    }
                }
                if (streamError != null) {
                    removeEmptyTrailingAssistant()
                    _state.update { it.copy(isLoading = false, error = streamError) }
                    persist()
                    return
                }
                persist()

                if (!_state.value.isAgentMode) break
                val content = _state.value.messages.lastOrNull()?.content.orEmpty()
                val calls = parser.parse(content)
                if (calls.isEmpty() || iteration >= MAX_AGENT_ITERATIONS) break

                val toolMessages = calls.map { call -> executeToolCall(call) }
                val resultsText = toolMessages.joinToString("\n\n") { toolMessage ->
                    "<tool_result tool=\"${toolMessage.toolLabel}\" status=\"${if (toolMessage.isError) "error" else "success"}\">\n" +
                        toolMessage.content.take(4_000) + "\n</tool_result>"
                }
                appendMessage(
                    ChatMessage(
                        role = Role.SYSTEM,
                        content = "Tool results:\n$resultsText\n\nContinue the task. " +
                            "If it is complete, give the final answer without tool tags."
                    )
                )
                iteration++
            }
        } catch (e: CancellationException) {
            // Stopped by the user or VM teardown.
            _state.update { it.copy(isLoading = false) }
            return
        } catch (e: Exception) {
            removeEmptyTrailingAssistant()
            _state.update { it.copy(isLoading = false, error = friendly(e)) }
        }
        _state.update { it.copy(isLoading = false) }
        persist()
    }

    private suspend fun executeToolCall(call: AgentToolCall): ChatMessage {
        val settings = settingsRepository.settings.first()
        val started = System.currentTimeMillis()
        val request: SandboxRequest = when (call) {
            is AgentToolCall.RunCode -> SandboxRequest(
                tool = SandboxTool.CODE,
                language = languageFromName(call.language),
                code = call.code,
                timeoutMs = settings.maxExecutionSeconds * 1000L
            )
            is AgentToolCall.AnalyzeData -> {
                val data = call.content.ifBlank { fileContext?.content.orEmpty() }
                val format = if (call.format.equals("json", true) || data.trimStart().startsWith("{") || data.trimStart().startsWith("[")) {
                    "json"
                } else {
                    "csv"
                }
                SandboxRequest(
                    tool = SandboxTool.DATA,
                    data = data,
                    dataFormat = format,
                    operation = "summarize",
                    timeoutMs = settings.maxExecutionSeconds * 1000L
                )
            }
            is AgentToolCall.ProcessFile -> SandboxRequest(
                tool = SandboxTool.FILE,
                fileAction = fileActionFromName(call.action),
                fileName = fileContext?.name,
                uri = fileContext?.uri,
                data = fileContext?.content,
                regexPattern = call.argument.ifBlank { null },
                timeoutMs = settings.maxExecutionSeconds * 1000L
            )
        }

        val result = if (!settings.sandboxEnabled) {
            SandboxResult.Failure("The sandbox is disabled in Settings.", 0)
        } else {
            withTimeoutOrNull(settings.maxExecutionSeconds * 1000L + 5_000L) { sandbox.execute(request) }
                ?: SandboxResult.Failure("Sandbox execution timed out.", settings.maxExecutionSeconds * 1000L)
        }
        val message = ChatMessage(
            role = Role.TOOL,
            content = com.example.troc.util.FileUtils.truncate(result.text(), 20_000),
            toolLabel = call.label,
            isError = !result.isSuccessful,
            durationMs = System.currentTimeMillis() - started
        )
        appendMessage(message)
        persist()
        return message
    }

    // ------------------------------------------------------------------ workflow panel

    fun addWorkflowStep() = _state.update {
        it.copy(workflowSteps = it.workflowSteps + WorkflowStep())
    }

    fun removeWorkflowStep(id: String) = _state.update {
        it.copy(workflowSteps = it.workflowSteps.filterNot { step -> step.id == id })
    }

    fun updateWorkflowStep(step: WorkflowStep) = _state.update {
        it.copy(workflowSteps = it.workflowSteps.map { s -> if (s.id == step.id) step else s })
    }

    fun executeWorkflow() {
        val steps = _state.value.workflowSteps
        if (steps.isEmpty() || _state.value.isExecutingWorkflow) return
        _state.update { it.copy(isExecutingWorkflow = true) }
        viewModelScope.launch {
            val current = settingsRepository.settings.first()
            if (current.apiKey.isBlank()) {
                _state.update { it.copy(error = "Add your Mistral API key in Settings first.", isExecutingWorkflow = false) }
                return@launch
            }
            ensureChatCreated(
                ChatMessage(role = Role.USER, content = "▶️ Executing a ${steps.size}-step workflow")
            )
            try {
                runWorkflow(steps) { _, result ->
                    appendMessage(
                        ChatMessage(
                            role = Role.TOOL,
                            content = com.example.troc.util.FileUtils.truncate(result.text(), 20_000),
                            toolLabel = "workflow step",
                            isError = !result.isSuccessful,
                            durationMs = result.durationMs
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = friendly(e)) }
            }
            _state.update { it.copy(isExecutingWorkflow = false) }
            persist()
        }
    }

    // ------------------------------------------------------------------ persistence helpers

    private fun ensureChatCreated(firstMessage: ChatMessage) {
        if (chatId == null) {
            chatId = java.util.UUID.randomUUID().toString()
            chatTitle = firstMessage.content.replace("\n", " ").take(42).trim()
        }
    }

    private suspend fun persist() {
        val id = chatId ?: return
        val state = _state.value
        if (state.messages.isEmpty()) return
        chatHistory.saveChat(
            Chat(
                id = id,
                title = chatTitle.ifBlank { "New chat" },
                messages = state.messages,
                timestamp = System.currentTimeMillis(),
                isAgentMode = state.isAgentMode
            )
        )
    }

    private fun appendMessage(message: ChatMessage) = _state.update {
        it.copy(messages = it.messages + message)
    }

    private fun updateLastAssistant(transform: (String) -> String) = _state.update { state ->
        val messages = state.messages.toMutableList()
        for (i in messages.indices.reversed()) {
            if (messages[i].role == Role.ASSISTANT) {
                messages[i] = messages[i].copy(content = transform(messages[i].content))
                break
            }
        }
        state.copy(messages = messages)
    }

    private fun removeEmptyTrailingAssistant() = _state.update { state ->
        if (state.messages.lastOrNull()?.let { it.role == Role.ASSISTANT && it.content.isBlank() } == true) {
            state.copy(messages = state.messages.dropLast(1))
        } else {
            state
        }
    }

    /** Maps local history to API messages: system prompts first, tool chatter folded in. */
    private fun buildApiMessages(settings: AppSettings): List<ApiMessage> {
        val systemPrompt = if (_state.value.isAgentMode) AgentPrompts.AGENT_SYSTEM_PROMPT else AgentPrompts.CHAT_SYSTEM_PROMPT
        val history = _state.value.messages
            .filter { it.role != Role.TOOL }
            .filterNot { it.role == Role.ASSISTANT && it.content.isBlank() }
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { ApiMessage(role = if (it.role == Role.SYSTEM) "user" else it.role.name.lowercase(Locale.US), content = it.content) }
        return listOf(ApiMessage("system", systemPrompt)) + history
    }

    private fun friendly(e: Exception): String = when (e) {
        is com.example.troc.domain.model.TrocApiException -> e.message ?: "API error"
        is IOException -> "Network error: check your connection and try again."
        else -> "Something went wrong: ${e.message ?: e::class.simpleName}"
    }

    private fun languageFromName(name: String): SandboxLanguage = when (name.lowercase(Locale.US)) {
        "kotlin", "kt" -> SandboxLanguage.KOTLIN
        "python", "py" -> SandboxLanguage.PYTHON
        else -> SandboxLanguage.JAVASCRIPT
    }

    private fun fileActionFromName(name: String): FileAction = when (name.lowercase(Locale.US)) {
        "metadata", "meta" -> FileAction.METADATA
        "list_zip", "listzip", "unzip_list" -> FileAction.LIST_ZIP
        "extract_zip", "extract", "unzip" -> FileAction.EXTRACT_ZIP
        "regex_replace", "replace" -> FileAction.REGEX_REPLACE
        else -> FileAction.READ_TEXT
    }

    private companion object {
        const val MAX_AGENT_ITERATIONS = 4
        const val MAX_HISTORY_MESSAGES = 30
    }
}

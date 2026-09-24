package com.troc.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ApiKeyRepository
import com.troc.data.repository.ChatRepository
import com.troc.data.repository.SandboxRepository
import com.troc.data.repository.UsageRepository
import com.troc.domain.model.*
import com.troc.domain.usecase.ExecuteSandbox
import com.troc.domain.usecase.RunAgentLoop
import com.troc.domain.usecase.SendMessage
import com.troc.domain.usecase.WebSearch
import com.troc.util.Constants
import com.troc.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isAgentMode: Boolean = false,
    val isWebSearchMode: Boolean = false,
    val activeToolCalls: List<ToolCall> = emptyList(),
    val error: String? = null,
    val currentChatId: String? = null,
    val inputText: String = "",
    val attachedFileContent: String? = null,
    val attachedFileName: String? = null,
    val isRecording: Boolean = false,
    val amplitude: Float = 0f,
    val workflowSteps: List<WorkflowStep> = emptyList(),
    // Model selector
    val selectedModel: String = "mistral-small-latest",
    val availableModels: List<String> = listOf(
        "mistral-small-latest",
        "mistral-medium-latest",
        "mistral-large-latest",
        "codestral-latest",
        "open-mistral-nemo",
        "ministral-3b-latest",
        "ministral-8b-latest"
    ),
    val isModelDropdownExpanded: Boolean = false,
    // Web search
    val webSearchResults: String? = null,
    val isWebSearching: Boolean = false,
    val hasApiKey: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val sendMessage: SendMessage,
    private val runAgentLoop: RunAgentLoop,
    private val executeSandbox: ExecuteSandbox,
    private val sandboxRepository: SandboxRepository,
    private val settingsDataStore: SettingsDataStore,
    private val usageRepository: UsageRepository,
    private val webSearch: WebSearch,
    private val mistralApi: com.troc.data.api.MistralApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null
    private var currentChatId: String? = null

    init {
        viewModelScope.launch {
            settingsDataStore.mistralModelFlow.collect { model ->
                _uiState.update { it.copy(selectedModel = model.ifBlank { "mistral-small-latest" }) }
            }
        }
        viewModelScope.launch {
            apiKeyRepository.mistralKeyState.collect { state ->
                val hasKey = state is ApiKeyState.UserProvided || state is ApiKeyState.Default
                _uiState.update { it.copy(hasApiKey = hasKey) }
            }
        }
        // Load available models from API if key is present
        viewModelScope.launch {
            try {
                val key = apiKeyRepository.getMistralKeySync()
                if (!key.isNullOrBlank()) {
                    val models = mistralApi.getModels(Constants.MISTRAL_BASE_URL, key)
                    if (models.isNotEmpty()) {
                        _uiState.update { it.copy(availableModels = models) }
                    }
                }
            } catch (e: Exception) {
                // keep defaults
            }
        }
        // Create initial chat
        viewModelScope.launch {
            val id = chatRepository.createChat("New Chat")
            currentChatId = id
            _uiState.update { it.copy(currentChatId = id) }
            observeMessages(id)
        }
    }

    private fun observeMessages(chatId: String) {
        viewModelScope.launch {
            chatRepository.observeMessages(chatId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun setChatId(chatId: String) {
        currentChatId = chatId
        _uiState.update { it.copy(currentChatId = chatId) }
        observeMessages(chatId)
    }

    fun newChat() {
        viewModelScope.launch {
            val id = chatRepository.createChat("New Chat")
            currentChatId = id
            _uiState.update { it.copy(currentChatId = id, messages = emptyList(), activeToolCalls = emptyList()) }
            observeMessages(id)
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleAgentMode() {
        _uiState.update { it.copy(isAgentMode = !it.isAgentMode) }
    }

    fun setAgentMode(enabled: Boolean) {
        _uiState.update { it.copy(isAgentMode = enabled) }
    }

    fun attachFile(uri: Uri) {
        viewModelScope.launch {
            val content = FileUtils.readTextFromUri(context, uri)
            if (content != null) {
                if (content.length > 10 * 1024 * 1024) {
                    _uiState.update { it.copy(error = "File too large (max 10MB)") }
                    return@launch
                }
                _uiState.update { it.copy(attachedFileContent = content, attachedFileName = uri.lastPathSegment ?: "file") }
            } else {
                _uiState.update { it.copy(error = "Failed to read file") }
            }
        }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(attachedFileContent = null, attachedFileName = null) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() && _uiState.value.attachedFileContent == null) return
        if (_uiState.value.isLoading) return

        val chatId = currentChatId ?: return

        viewModelScope.launch {
            val key = apiKeyRepository.getMistralKeySync()
            if (key.isNullOrBlank()) {
                _uiState.update { it.copy(error = "No Mistral API key found. Please add your key in Settings.") }
                return@launch
            }

            val fullText = if (_uiState.value.attachedFileContent != null) {
                "$text\n\n[Attached ${_uiState.value.attachedFileName}]:\n${_uiState.value.attachedFileContent?.take(5000)}"
            } else text

            val userMessage = ChatMessage(role = MessageRole.USER, content = fullText, chatId = chatId)
            chatRepository.saveMessage(chatId, userMessage)
            _uiState.update { it.copy(inputText = "", attachedFileContent = null, attachedFileName = null, isLoading = true, error = null, webSearchResults = null) }

            val history = chatRepository.getMessages(chatId)
            val isWebSearch = _uiState.value.isWebSearchMode
            var webSearchContext: String? = null

            // If web search mode, perform search first
            if (isWebSearch) {
                _uiState.update { it.copy(isWebSearching = true) }
                try {
                    val searchResult = webSearch(fullText, 5)
                    if (searchResult.isSuccess) {
                        webSearchContext = searchResult.getOrNull()
                        _uiState.update { it.copy(webSearchResults = webSearchContext, isWebSearching = false) }
                        val toolCall = ToolCall(
                            tool = ToolType.CUSTOM,
                            input = "Web search: $fullText",
                            output = webSearchContext?.take(1000) ?: "No results",
                            isRunning = false
                        )
                        _uiState.update { it.copy(activeToolCalls = it.activeToolCalls + toolCall) }
                    } else {
                        _uiState.update { it.copy(isWebSearching = false, error = "Web search failed: ${searchResult.exceptionOrNull()?.message}") }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isWebSearching = false, error = "Web search error: ${e.message}") }
                }
            }

            if (_uiState.value.isAgentMode) {
                // Autonomous Agent Loop
                val agentMessages = history
                runAgentLoop.run(agentMessages) { toolCall ->
                    _uiState.update { state -> state.copy(activeToolCalls = state.activeToolCalls + toolCall) }
                    val result = when (toolCall.tool) {
                        ToolType.CODE -> executeSandbox.executeCode(toolCall.language ?: "python", toolCall.input)
                        ToolType.DATA -> {
                            val stats = executeSandbox.executeData(toolCall.input)
                            SandboxResult.Success(stats.toString(), 0)
                        }
                        ToolType.FILE -> executeSandbox.executeFile("metadata", toolCall.input)
                        ToolType.CUSTOM -> {
                            if (toolCall.input.contains("web_search", ignoreCase = true) || toolCall.tool == ToolType.CUSTOM) {
                                try {
                                    val query = if (toolCall.input.contains("\"input\"")) {
                                        Regex(""""input"\s*:\s*"([^"]+)"""").find(toolCall.input)?.groupValues?.get(1) ?: toolCall.input
                                    } else toolCall.input
                                    val searchRes = webSearch(query, 5)
                                    if (searchRes.isSuccess) {
                                        SandboxResult.Success(searchRes.getOrNull() ?: "No results", 0)
                                    } else {
                                        SandboxResult.Error("Web search failed: ${searchRes.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    SandboxResult.Error("Web search error: ${e.message}")
                                }
                            } else {
                                SandboxResult.Error("Unknown custom tool")
                            }
                        }
                        else -> SandboxResult.Error("Unknown tool")
                    }
                    val completed = toolCall.copy(
                        output = when (result) {
                            is SandboxResult.Success -> result.output
                            is SandboxResult.Error -> result.message
                            is SandboxResult.Timeout -> "Timeout"
                        },
                        isRunning = false
                    )
                    _uiState.update { state ->
                        state.copy(activeToolCalls = state.activeToolCalls.map { if (it.id == toolCall.id) completed else it })
                    }
                    result
                }.collect { event ->
                    when (event.type) {
                        RunAgentLoop.EventType.FINAL_ANSWER -> {
                            val existing = chatRepository.getMessages(chatId).lastOrNull { it.role == MessageRole.ASSISTANT && it.isStreaming }
                            if (existing != null) {
                                val updated = existing.copy(content = event.content, isStreaming = true)
                                chatRepository.saveMessage(chatId, updated)
                            } else {
                                val assistantMsg = ChatMessage(role = MessageRole.ASSISTANT, content = event.content, isStreaming = true, chatId = chatId)
                                chatRepository.saveMessage(chatId, assistantMsg)
                            }
                        }
                        RunAgentLoop.EventType.TOOL_CALL_RESULT -> {
                            event.toolCall?.let { tc ->
                                val toolMsg = ChatMessage(role = MessageRole.TOOL, content = tc.output ?: "", chatId = chatId)
                                chatRepository.saveMessage(chatId, toolMsg)
                            }
                        }
                        RunAgentLoop.EventType.ERROR -> {
                            _uiState.update { it.copy(error = event.content) }
                        }
                        else -> {}
                    }
                }
                val last = chatRepository.getMessages(chatId).lastOrNull { it.role == MessageRole.ASSISTANT }
                if (last != null) {
                    chatRepository.saveMessage(chatId, last.copy(isStreaming = false))
                }
                _uiState.update { it.copy(isLoading = false) }

            } else {
                // Normal chat streaming
                streamingJob = viewModelScope.launch {
                    try {
                        var assistantMessage = ChatMessage(role = MessageRole.ASSISTANT, content = "", isStreaming = true, chatId = chatId)
                        chatRepository.saveMessage(chatId, assistantMessage)

                        val flow = sendMessage(history, isAgentMode = false, isWebSearchMode = isWebSearch, webSearchContext = webSearchContext)
                        val sb = StringBuilder()
                        flow.collect { chunk ->
                            sb.append(chunk)
                            assistantMessage = assistantMessage.copy(content = sb.toString(), isStreaming = true)
                            chatRepository.saveMessage(chatId, assistantMessage)
                        }
                        assistantMessage = assistantMessage.copy(isStreaming = false)
                        chatRepository.saveMessage(chatId, assistantMessage)
                        _uiState.update { it.copy(isLoading = false) }
                    } catch (e: Exception) {
                        val msg = e.message ?: "Unknown error"
                        val isAuthError = msg.contains("401") || msg.contains("403") ||
                                msg.contains("unauthorized", ignoreCase = true) ||
                                (msg.contains("API key", ignoreCase = true) && !msg.contains("429"))
                        val isRateLimit = msg.contains("429") || msg.contains("rate limit", ignoreCase = true)
                        val isNetworkError = msg.contains("Unable to resolve host", ignoreCase = true) ||
                                msg.contains("timeout", ignoreCase = true) ||
                                msg.contains("Failed to connect", ignoreCase = true)

                        val friendlyError = when {
                            isAuthError -> "API key issue: Please check your Mistral API key in Settings."
                            isRateLimit -> "Rate limit reached. Please wait a moment and try again."
                            isNetworkError -> "Network connection failed. Please check your internet."
                            else -> "Error: ${msg.take(200)}"
                        }

                        _uiState.update { it.copy(error = friendlyError, isLoading = false) }

                        val msgs = chatRepository.getMessages(chatId)
                        val last = msgs.lastOrNull { it.role == MessageRole.ASSISTANT && it.isStreaming }
                        if (last != null && last.content.isEmpty()) {
                            chatRepository.saveMessage(chatId, last.copy(content = friendlyError, isStreaming = false))
                        }
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
        viewModelScope.launch {
            val chatId = currentChatId ?: return@launch
            val msgs = chatRepository.getMessages(chatId)
            val last = msgs.lastOrNull { it.role == MessageRole.ASSISTANT && it.isStreaming }
            if (last != null) {
                chatRepository.saveMessage(chatId, last.copy(isStreaming = false))
            }
        }
    }

    fun setRecordingState(isRecording: Boolean, amplitude: Float = 0f) {
        _uiState.update { it.copy(isRecording = isRecording, amplitude = amplitude) }
    }

    fun insertTranscribedText(text: String) {
        _uiState.update { it.copy(inputText = if (it.inputText.isBlank()) text else "${it.inputText} $text") }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun toggleModelDropdown() {
        _uiState.update { it.copy(isModelDropdownExpanded = !it.isModelDropdownExpanded) }
    }

    fun setModelDropdownExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isModelDropdownExpanded = expanded) }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model, isModelDropdownExpanded = false) }
        viewModelScope.launch {
            settingsDataStore.setMistralModel(model)
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            try {
                val key = apiKeyRepository.getMistralKeySync()
                if (!key.isNullOrBlank()) {
                    val models = mistralApi.getModels(Constants.MISTRAL_BASE_URL, key)
                    if (models.isNotEmpty()) {
                        _uiState.update { it.copy(availableModels = models) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to refresh models: ${e.message}") }
            }
        }
    }

    fun toggleWebSearchMode() {
        _uiState.update { it.copy(isWebSearchMode = !it.isWebSearchMode) }
    }

    fun setWebSearchMode(enabled: Boolean) {
        _uiState.update { it.copy(isWebSearchMode = enabled) }
    }
}

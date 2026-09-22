package com.troc.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ApiKeyRepository
import com.troc.data.repository.ChatRepository
import com.troc.data.repository.SandboxRepository
import com.troc.domain.model.*
import com.troc.domain.usecase.ExecuteSandbox
import com.troc.domain.usecase.RunAgentLoop
import com.troc.domain.usecase.SendMessage
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
    // Usage tracking
    val apiUsage: ApiUsage? = null,
    val isUsageLoading: Boolean = false,
    // Web search
    val webSearchResults: String? = null,
    val isWebSearching: Boolean = false
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
    private val usageRepository: com.troc.data.repository.UsageRepository,
    private val webSearch: com.troc.domain.usecase.WebSearch,
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
                _uiState.update { it.copy(selectedModel = model) }
            }
        }
        viewModelScope.launch {
            usageRepository.observeUsage().collect { usage ->
                _uiState.update { it.copy(apiUsage = usage) }
            }
        }
        // Load available models from API
        viewModelScope.launch {
            try {
                val key = apiKeyRepository.getMistralKeySync()
                if (key != null) {
                    val models = mistralApi.getModels(com.troc.util.Constants.MISTRAL_BASE_URL, key)
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

    fun addWorkflowStep() {
        val step = WorkflowStep(
            tool = ToolType.CODE,
            language = "python",
            inputTemplate = "",
            outputVarName = "output_${UUID.randomUUID().toString().take(4)}"
        )
        _uiState.update { it.copy(workflowSteps = it.workflowSteps + step) }
    }

    fun updateWorkflowStep(index: Int, step: WorkflowStep) {
        val list = _uiState.value.workflowSteps.toMutableList()
        if (index in list.indices) {
            list[index] = step
            _uiState.update { it.copy(workflowSteps = list) }
        }
    }

    fun removeWorkflowStep(index: Int) {
        val list = _uiState.value.workflowSteps.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _uiState.update { it.copy(workflowSteps = list) }
        }
    }

    fun executeWorkflow() {
        val steps = _uiState.value.workflowSteps
        if (steps.isEmpty()) return
        val workflow = Workflow(name = "Chat Workflow", steps = steps)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val variables = mutableMapOf<String, String>()
            for (step in steps) {
                var input = step.inputTemplate
                variables.forEach { (k, v) -> input = input.replace("{{${k}}}", v) }
                val toolCall = ToolCall(tool = step.tool, language = step.language, input = input, isRunning = true)
                _uiState.update { it.copy(activeToolCalls = it.activeToolCalls + toolCall) }

                val result = when (step.tool) {
                    ToolType.CODE -> executeSandbox.executeCode(step.language, input)
                    ToolType.DATA -> {
                        val stats = executeSandbox.executeData(input)
                        SandboxResult.Success("Data stats: $stats", 0)
                    }
                    ToolType.FILE -> executeSandbox.executeFile("metadata", input)
                    else -> SandboxResult.Error("Unknown tool")
                }

                val output = when (result) {
                    is SandboxResult.Success -> result.output
                    is SandboxResult.Error -> "Error: ${result.message}"
                    is SandboxResult.Timeout -> "Timeout"
                }
                variables[step.outputVarName] = output

                val completed = toolCall.copy(output = output, isRunning = false, executionTimeMs = (result as? SandboxResult.Success)?.executionTimeMs ?: 0)
                _uiState.update { state ->
                    state.copy(activeToolCalls = state.activeToolCalls.map { if (it.id == toolCall.id) completed else it })
                }

                // Add to chat as tool message
                val chatId = currentChatId ?: continue
                val toolMessage = ChatMessage(role = MessageRole.TOOL, content = "Workflow step ${step.outputVarName}: $output", chatId = chatId)
                chatRepository.saveMessage(chatId, toolMessage)
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() && _uiState.value.attachedFileContent == null) return
        if (_uiState.value.isLoading) return

        val chatId = currentChatId ?: return
        val fullText = if (_uiState.value.attachedFileContent != null) {
            "$text\n\n[Attached ${_uiState.value.attachedFileName}]:\n${_uiState.value.attachedFileContent?.take(5000)}"
        } else text

        viewModelScope.launch {
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
                        // Add tool card for web search
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
                // Agent loop with web search support
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
                            // Handle web_search tool
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
                // Normal chat streaming with optional web search context
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
                        if (msg.contains("401") || msg.contains("Invalid API key")) {
                            apiKeyRepository.markMistralInvalid()
                            _uiState.update { it.copy(error = "Invalid Mistral API key — update it in Settings", isLoading = false) }
                        } else if (msg.contains("429")) {
                            _uiState.update { it.copy(error = "Rate limited, retrying…", isLoading = false) }
                        } else {
                            _uiState.update { it.copy(error = "Error: $msg", isLoading = false) }
                        }
                        val msgs = chatRepository.getMessages(chatId)
                        val last = msgs.lastOrNull { it.role == MessageRole.ASSISTANT && it.isStreaming }
                        if (last != null && last.content.isEmpty()) {
                            chatRepository.saveMessage(chatId, last.copy(content = "Failed to get response: $msg", isStreaming = false))
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
        _uiState.update { it.copy(inputText = it.inputText + text) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Model selector methods
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
                if (key != null) {
                    val models = mistralApi.getModels(com.troc.util.Constants.MISTRAL_BASE_URL, key)
                    if (models.isNotEmpty()) {
                        _uiState.update { it.copy(availableModels = models) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to refresh models: ${e.message}") }
            }
        }
    }

    // Web search mode
    fun toggleWebSearchMode() {
        _uiState.update { it.copy(isWebSearchMode = !it.isWebSearchMode) }
    }

    fun setWebSearchMode(enabled: Boolean) {
        _uiState.update { it.copy(isWebSearchMode = enabled) }
    }

    // Usage
    fun clearUsage() {
        viewModelScope.launch {
            usageRepository.clearAll()
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUsageLoading = true) }
            try {
                val usage = usageRepository.getUsage()
                _uiState.update { it.copy(apiUsage = usage, isUsageLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUsageLoading = false, error = "Failed to load usage: ${e.message}") }
            }
        }
    }
}

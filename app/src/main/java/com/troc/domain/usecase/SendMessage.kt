package com.troc.domain.usecase

import com.troc.data.api.MistralApi
import com.troc.data.api.MistralChatRequest
import com.troc.data.api.MistralMessage
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ApiKeyRepository
import com.troc.data.repository.UsageRepository
import com.troc.domain.model.ChatMessage
import com.troc.domain.model.MessageRole
import com.troc.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class SendMessage @Inject constructor(
    private val mistralApi: MistralApi,
    private val apiKeyRepository: ApiKeyRepository,
    private val settingsDataStore: SettingsDataStore,
    private val usageRepository: UsageRepository
) {
    suspend operator fun invoke(
        messages: List<ChatMessage>,
        isAgentMode: Boolean = false,
        isWebSearchMode: Boolean = false,
        webSearchContext: String? = null
    ): Flow<String> {
        val apiKey = apiKeyRepository.getMistralKeySync()
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException("Mistral API key not configured. Please add your API key in Settings.")
        }

        val model = settingsDataStore.mistralModelFlow.first().ifBlank { Constants.DEFAULT_MISTRAL_MODEL }
        val temperature = settingsDataStore.temperatureFlow.first()
        val customEndpoint = settingsDataStore.customEndpointFlow.first()
        val baseUrl = if (customEndpoint.isNotBlank()) customEndpoint else Constants.MISTRAL_BASE_URL

        val apiMessages = mutableListOf<MistralMessage>()

        when {
            isWebSearchMode -> {
                val searchContextPart = if (!webSearchContext.isNullOrBlank()) {
                    "\n\nWeb Search Results:\n$webSearchContext"
                } else ""
                apiMessages.add(
                    MistralMessage(
                        role = "system",
                        content = """
                            You are Troc, a smart and helpful AI assistant with real-time web search capabilities.
                            Use the provided search results to deliver accurate, up-to-date, and well-structured answers.
                            Cite relevant sources and URLs when available. Format with clean Markdown.
                            $searchContextPart
                        """.trimIndent()
                    )
                )
            }
            isAgentMode -> {
                apiMessages.add(
                    MistralMessage(
                        role = "system",
                        content = """
                            You are Troc, an autonomous AI agent with sandboxed code execution and data processing superpowers.

                            Capabilities:
                            - Code Execution: Run Python, JavaScript, Kotlin in secure sandbox.
                              Format: ```json
                              {"tool": "code", "language": "python", "input": "print('hello')"}
                              ```
                            - Data Analysis: Analyze CSV/JSON data.
                              Format: ```json
                              {"tool": "data", "input": "calculate summary statistics"}
                              ```
                            - File Operations:
                              Format: ```json
                              {"tool": "file", "input": "create report.txt"}
                              ```

                            Be proactive, concise, and helpful. Format your responses with clean Markdown.
                        """.trimIndent()
                    )
                )
            }
            else -> {
                apiMessages.add(
                    MistralMessage(
                        role = "system",
                        content = "You are Troc, a helpful, thoughtful, and capable AI assistant. Be concise, direct, and use clean markdown."
                    )
                )
            }
        }

        messages.forEach { msg ->
            val roleStr = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.TOOL -> "tool"
                MessageRole.SYSTEM -> "system"
            }
            apiMessages.add(MistralMessage(role = roleStr, content = msg.content))
        }

        val request = MistralChatRequest(
            model = model,
            messages = apiMessages,
            temperature = temperature,
            stream = true
        )

        val promptText = apiMessages.joinToString(" ") { it.content }
        val estimatedPromptTokens = usageRepository.estimateTokens(promptText)

        return flow {
            var fullResponse = StringBuilder()

            try {
                mistralApi.streamChatCompletionWithUsage(baseUrl, request).collect { event ->
                    when (event) {
                        is MistralApi.StreamEvent.Content -> {
                            fullResponse.append(event.text)
                            emit(event.text)
                        }
                        is MistralApi.StreamEvent.Usage -> {
                            try {
                                usageRepository.trackUsage(
                                    model = model,
                                    promptTokens = event.usage.promptTokens.takeIf { it > 0 } ?: estimatedPromptTokens,
                                    completionTokens = event.usage.completionTokens.takeIf { it > 0 } ?: (fullResponse.length / 4),
                                    isWebSearch = isWebSearchMode
                                )
                            } catch (e: Exception) {
                                // ignore tracking errors
                            }
                        }
                        else -> {}
                    }
                }

                if (fullResponse.isNotEmpty()) {
                    try {
                        val completionTokens = usageRepository.estimateTokens(fullResponse.toString())
                        usageRepository.trackUsage(
                            model = model,
                            promptTokens = estimatedPromptTokens,
                            completionTokens = completionTokens,
                            isWebSearch = isWebSearchMode
                        )
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun invokeNonStream(
        messages: List<ChatMessage>,
        isWebSearchMode: Boolean = false,
        webSearchContext: String? = null
    ): Result<Pair<String, com.troc.data.api.MistralUsage?>> {
        val apiKey = apiKeyRepository.getMistralKeySync()
        if (apiKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Mistral API key not configured."))
        }
        val model = settingsDataStore.mistralModelFlow.first().ifBlank { Constants.DEFAULT_MISTRAL_MODEL }
        val temperature = settingsDataStore.temperatureFlow.first()
        val customEndpoint = settingsDataStore.customEndpointFlow.first()
        val baseUrl = if (customEndpoint.isNotBlank()) customEndpoint else Constants.MISTRAL_BASE_URL

        val apiMessages = mutableListOf<MistralMessage>()
        if (isWebSearchMode) {
            apiMessages.add(MistralMessage(role = "system", content = "You are Troc with web search. Context: ${webSearchContext ?: ""}"))
        } else {
            apiMessages.add(MistralMessage(role = "system", content = "You are Troc, a helpful AI assistant."))
        }

        messages.forEach { msg ->
            val roleStr = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.TOOL -> "tool"
                MessageRole.SYSTEM -> "system"
            }
            apiMessages.add(MistralMessage(role = roleStr, content = msg.content))
        }

        val request = MistralChatRequest(
            model = model,
            messages = apiMessages,
            temperature = temperature,
            stream = false
        )

        return mistralApi.chatCompletionNonStream(baseUrl, request)
    }
}

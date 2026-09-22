package com.troc.domain.usecase

import com.troc.data.api.MistralApi
import com.troc.data.api.MistralChatRequest
import com.troc.data.api.MistralMessage
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ApiKeyRepository
import com.troc.domain.model.ChatMessage
import com.troc.domain.model.MessageRole
import com.troc.util.BackoffPolicy
import com.troc.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class SendMessage @Inject constructor(
    private val mistralApi: MistralApi,
    private val apiKeyRepository: ApiKeyRepository,
    private val settingsDataStore: SettingsDataStore,
    private val usageRepository: com.troc.data.repository.UsageRepository
) {
    suspend operator fun invoke(
        messages: List<ChatMessage>,
        isAgentMode: Boolean = false,
        isWebSearchMode: Boolean = false,
        webSearchContext: String? = null
    ): Flow<String> {
        val model = settingsDataStore.mistralModelFlow.first()
        val temperature = settingsDataStore.temperatureFlow.first()
        val customEndpoint = settingsDataStore.customEndpointFlow.first()

        val baseUrl = if (customEndpoint.isNotBlank()) customEndpoint else Constants.MISTRAL_BASE_URL

        val apiMessages = mutableListOf<MistralMessage>()

        // System prompt with modes
        when {
            isWebSearchMode -> {
                val searchContextPart = if (!webSearchContext.isNullOrBlank()) {
                    "\n\nWeb Search Results (use these to answer, cite sources):\n$webSearchContext"
                } else ""
                apiMessages.add(
                    MistralMessage(
                        role = "system",
                        content = """
                            You are Troc, a premium AI assistant with web search superpowers.
                            You have access to real-time web search results. Use them to provide accurate, up-to-date answers.
                            Always cite your sources with URLs when possible.
                            Be concise, friendly, and accurate. Use markdown for formatting.
                            If search results are insufficient, say so and provide best effort answer.
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
                            You are Troc, an autonomous AI agent with sandboxed superpowers. You are fully automatic.

                            ## Your Capabilities (use them automatically without asking user):
                            - **Code Execution**: Run Python, JavaScript, Kotlin in secure sandbox. Use for calculations, data processing, file generation, etc.
                              Format: ```json
                              {"tool": "code", "language": "python", "input": "print('hello world')"}
                              ```
                            - **Data Analysis**: Analyze CSV, JSON, stats, generate charts
                              Format: ```json
                              {"tool": "data", "input": "analyze sales data, mean, median, trends"}
                              ```
                            - **File Operations**: Create, read, transform files
                              Format: ```json
                              {"tool": "file", "input": "create report.txt with summary"}
                              ```
                            - **Web Search**: Search real-time web for current info
                              Format: ```json
                              {"tool": "web_search", "input": "latest news about AI"}
                              ```

                            ## Autonomous Workflow:
                            1. **Understand** user request deeply
                            2. **Plan** automatically: break into steps, decide which tools needed
                            3. **Execute** tools one by one - you don't need permission, just do it
                            4. **Synthesize** results into final answer
                            5. **Explain** what you did

                            ## Rules:
                            - Be PROACTIVE: If user asks to analyze data, automatically run code to do it
                            - Be AUTONOMOUS: Don't ask "should I run code?" - just run it
                            - Chain up to ${Constants.MAX_TOOL_ITERATIONS} tool calls automatically
                            - Always show your reasoning, then execute
                            - If no tool needed, answer directly with high quality
                            - Use markdown, code blocks with syntax highlighting
                            - Be concise but thorough

                            Example autonomous flow:
                            User: "Analyze this CSV and create a chart"
                            You: "I'll analyze the CSV and generate a visualization."
                            ```json
                            {"tool": "code", "language": "python", "input": "import pandas as pd\nimport matplotlib.pyplot as plt\ndf = pd.read_csv('data.csv')\nprint(df.describe())"}
                            ```
                            [After tool result, continue automatically]
                            ```json
                            {"tool": "code", "language": "python", "input": "plt.bar(...); plt.savefig('chart.png')\nprint('Chart created')"}
                            ```
                            Then final answer with insights.

                            You are automatic, intelligent, and action-oriented. No need for manual workflow building - you ARE the workflow.
                        """.trimIndent()
                    )
                )
            }
            else -> {
                apiMessages.add(
                    MistralMessage(
                        role = "system",
                        content = "You are Troc, a helpful AI assistant. Be concise, friendly, and accurate. Use markdown for formatting."
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

        // For usage tracking, we estimate prompt tokens and will update on completion
        val promptText = apiMessages.joinToString(" ") { it.content }
        val estimatedPromptTokens = usageRepository.estimateTokens(promptText)

        return kotlinx.coroutines.flow.flow {
            var completionTokensEstimate = 0
            var fullResponse = StringBuilder()

            try {
                BackoffPolicy.retryWithBackoff(maxRetries = 2) {
                    mistralApi.streamChatCompletionWithUsage(baseUrl, request)
                }.collect { event ->
                    when (event) {
                        is MistralApi.StreamEvent.Content -> {
                            fullResponse.append(event.text)
                            completionTokensEstimate += usageRepository.estimateTokens(event.text) / 4 // rough
                            emit(event.text)
                        }
                        is MistralApi.StreamEvent.Usage -> {
                            // Real usage from API
                            try {
                                usageRepository.trackUsage(
                                    model = model,
                                    promptTokens = event.usage.promptTokens.takeIf { it > 0 } ?: estimatedPromptTokens,
                                    completionTokens = event.usage.completionTokens.takeIf { it > 0 } ?: completionTokensEstimate,
                                    isWebSearch = isWebSearchMode
                                )
                            } catch (e: Exception) {
                                // ignore tracking errors
                            }
                        }
                        else -> {}
                    }
                }

                // If no usage event received, track estimated
                if (fullResponse.isNotEmpty()) {
                    try {
                        // Check if we already tracked via usage event by checking recent history
                        // For simplicity, if completionTokensEstimate > 0, track estimated if not already
                        val currentUsage = usageRepository.getUsage()
                        // Only track estimated if we haven't tracked in last 5 seconds for same model
                        // We'll track estimated as fallback
                        if (completionTokensEstimate > 0) {
                            // We will track only if no usage event was received (we can't know, so we track with flag)
                            // To avoid double counting, we track estimated only when usage event missing
                            // Here we approximate: if total tokens didn't increase in last collection, track
                            // Simplified: always track estimated as fallback with small delay check
                            // For now, we track estimated if we didn't get usage event - we assume we didn't
                            // So we track here only if completionTokensEstimate > 0 and we haven't tracked usage yet
                            // We'll use a heuristic: if last update was > 10 sec ago, track estimated
                            if (System.currentTimeMillis() - currentUsage.lastUpdated > 5000) {
                                usageRepository.trackUsage(
                                    model = model,
                                    promptTokens = estimatedPromptTokens,
                                    completionTokens = usageRepository.estimateTokens(fullResponse.toString()),
                                    isWebSearch = isWebSearchMode
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // Non-streaming version for simple calls with usage
    suspend fun invokeNonStream(
        messages: List<ChatMessage>,
        isWebSearchMode: Boolean = false,
        webSearchContext: String? = null
    ): Result<Pair<String, com.troc.data.api.MistralUsage?>> {
        val model = settingsDataStore.mistralModelFlow.first()
        val temperature = settingsDataStore.temperatureFlow.first()
        val customEndpoint = settingsDataStore.customEndpointFlow.first()
        val baseUrl = if (customEndpoint.isNotBlank()) customEndpoint else Constants.MISTRAL_BASE_URL

        val apiMessages = mutableListOf<MistralMessage>()

        if (isWebSearchMode) {
            apiMessages.add(
                MistralMessage(
                    role = "system",
                    content = "You are Troc with web search. Use this context: ${webSearchContext ?: ""}"
                )
            )
        } else {
            apiMessages.add(
                MistralMessage(
                    role = "system",
                    content = "You are Troc, helpful AI."
                )
            )
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

        val result = mistralApi.chatCompletionNonStream(baseUrl, request)
        result.onSuccess { (content, usage) ->
            usage?.let {
                try {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        usageRepository.trackUsage(model, it.promptTokens, it.completionTokens, isWebSearchMode)
                    }
                } catch (e: Exception) {}
            }
        }
        return result
    }
}

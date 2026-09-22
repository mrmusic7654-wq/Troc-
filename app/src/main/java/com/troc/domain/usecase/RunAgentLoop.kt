package com.troc.domain.usecase

import com.troc.data.repository.SandboxRepository
import com.troc.domain.model.*
import com.troc.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class RunAgentLoop @Inject constructor(
    private val sendMessage: SendMessage,
    private val sandboxRepository: SandboxRepository
) {
    data class AgentEvent(
        val type: EventType,
        val content: String = "",
        val toolCall: ToolCall? = null
    )

    enum class EventType { THINKING, TOOL_CALL_START, TOOL_CALL_RESULT, FINAL_ANSWER, ERROR }

    fun run(
        initialMessages: List<ChatMessage>,
        onToolCall: (suspend (ToolCall) -> SandboxResult)? = null
    ): Flow<AgentEvent> = flow {
        var messages = initialMessages.toMutableList()
        var iterations = 0
        var consecutiveToolExecutions = 0

        while (iterations < Constants.MAX_TOOL_ITERATIONS) {
            iterations++
            emit(AgentEvent(EventType.THINKING, if (iterations == 1) "Agent is analyzing your request..." else "Agent is planning next step... (${iterations}/${Constants.MAX_TOOL_ITERATIONS})"))

            val responseBuilder = StringBuilder()
            try {
                sendMessage(messages, isAgentMode = true).collect { chunk ->
                    responseBuilder.append(chunk)
                    // Stream final answer as it comes, but don't emit tool JSON as final
                    val partial = responseBuilder.toString()
                    if (!ToolParser.hasToolCall(partial)) {
                        emit(AgentEvent(EventType.FINAL_ANSWER, partial))
                    }
                }
            } catch (e: Exception) {
                emit(AgentEvent(EventType.ERROR, "Agent error: ${e.message}"))
                break
            }

            val fullResponse = responseBuilder.toString()
            val toolCalls = ToolParser.extractToolCalls(fullResponse)

            if (toolCalls.isEmpty()) {
                // No more tools, final answer - ensure we emit it
                if (fullResponse.isNotBlank()) {
                    emit(AgentEvent(EventType.FINAL_ANSWER, fullResponse))
                }
                break
            }

            // Automatic execution: execute ALL tool calls found, sequentially
            // This makes agent fully autonomous
            for (parsed in toolCalls.take(3)) { // Max 3 tools per iteration to avoid overload
                val toolType = when (parsed.tool.lowercase()) {
                    "code" -> ToolType.CODE
                    "data" -> ToolType.DATA
                    "file" -> ToolType.FILE
                    "web_search", "search" -> ToolType.CUSTOM
                    else -> ToolType.CUSTOM
                }

                val toolCall = ToolCall(
                    tool = toolType,
                    language = parsed.language ?: if (toolType == ToolType.CODE) "python" else null,
                    input = parsed.input,
                    isRunning = true
                )

                emit(AgentEvent(EventType.TOOL_CALL_START, toolCall = toolCall))

                val result = if (onToolCall != null) {
                    onToolCall(toolCall)
                } else {
                    try {
                        when (toolType) {
                            ToolType.CODE -> sandboxRepository.executeCode(parsed.language ?: "python", parsed.input)
                            ToolType.DATA -> {
                                val stats = sandboxRepository.analyzeData(parsed.input)
                                SandboxResult.Success("Data analysis complete: $stats", 0)
                            }
                            ToolType.FILE -> sandboxRepository.executeFileOperation("read", parsed.input)
                            ToolType.CUSTOM -> {
                                // Web search
                                if (parsed.tool.contains("search", ignoreCase = true)) {
                                    // This will be handled by HomeViewModel's onToolCall for web search
                                    SandboxResult.Success("Web search requested: ${parsed.input} - will be executed", 0)
                                } else {
                                    SandboxResult.Error("Unknown custom tool: ${parsed.tool}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        SandboxResult.Error("Tool execution failed: ${e.message}")
                    }
                }

                val output = when (result) {
                    is SandboxResult.Success -> result.output
                    is SandboxResult.Error -> "Error: ${result.message}\nTool: ${parsed.tool}\nInput: ${parsed.input.take(200)}"
                    is SandboxResult.Timeout -> "Error: Execution timed out after ${Constants.MAX_TOOL_ITERATIONS}s"
                }

                val completedToolCall = toolCall.copy(
                    output = output,
                    isRunning = false,
                    executionTimeMs = when (result) {
                        is SandboxResult.Success -> result.executionTimeMs
                        is SandboxResult.Error -> result.executionTimeMs
                        else -> 0
                    },
                    error = (result as? SandboxResult.Error)?.message
                )

                emit(AgentEvent(EventType.TOOL_CALL_RESULT, toolCall = completedToolCall))
                consecutiveToolExecutions++

                // Feed back to LLM immediately for next iteration planning
                // Keep history concise - only last 2 tool results to avoid context overflow
                if (messages.size > 20) {
                    // Trim old tool messages, keep system + last 10
                    val systemMsg = messages.firstOrNull { it.role == MessageRole.SYSTEM }
                    val recent = messages.takeLast(10)
                    messages = (listOfNotNull(systemMsg) + recent).toMutableList()
                }

                messages.add(ChatMessage(role = MessageRole.ASSISTANT, content = "Executed ${parsed.tool}: ${parsed.input.take(100)}"))
                messages.add(
                    ChatMessage(
                        role = MessageRole.TOOL,
                        content = """Tool ${parsed.tool} result: ${output.take(3000).replace("\"", "'")}"""
                    )
                )
            }

            // If we executed tools, continue loop for next planning step
            // If no progress (same tool repeated), break to avoid infinite loop
            if (consecutiveToolExecutions > Constants.MAX_TOOL_ITERATIONS * 2) {
                emit(AgentEvent(EventType.FINAL_ANSWER, "Agent completed $consecutiveToolExecutions tool executions. Synthesizing final answer..."))
                break
            }
        }

        if (iterations >= Constants.MAX_TOOL_ITERATIONS) {
            emit(AgentEvent(EventType.THINKING, "Max iterations reached, generating final summary..."))
            // One final call without tools to synthesize
            val finalBuilder = StringBuilder()
            try {
                // Add system instruction to summarize
                val summaryMessages = messages.toMutableList()
                summaryMessages.add(ChatMessage(role = MessageRole.SYSTEM, content = "You have completed ${iterations} iterations with tool executions. Now provide a comprehensive final answer summarizing what you did, the results, and any insights. Be concise but thorough."))
                sendMessage(summaryMessages, isAgentMode = false).collect { chunk ->
                    finalBuilder.append(chunk)
                    emit(AgentEvent(EventType.FINAL_ANSWER, finalBuilder.toString()))
                }
            } catch (e: Exception) {
                emit(AgentEvent(EventType.ERROR, "Final synthesis failed: ${e.message}"))
                // Emit last response as fallback
                if (finalBuilder.isNotEmpty()) {
                    emit(AgentEvent(EventType.FINAL_ANSWER, finalBuilder.toString()))
                }
            }
        }
    }
}

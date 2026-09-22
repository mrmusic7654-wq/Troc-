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

        while (iterations < Constants.MAX_TOOL_ITERATIONS) {
            iterations++
            emit(AgentEvent(EventType.THINKING, "Thinking..."))

            val responseBuilder = StringBuilder()
            try {
                sendMessage(messages, isAgentMode = true).collect { chunk ->
                    responseBuilder.append(chunk)
                    emit(AgentEvent(EventType.FINAL_ANSWER, responseBuilder.toString()))
                }
            } catch (e: Exception) {
                emit(AgentEvent(EventType.ERROR, "Agent error: ${e.message}"))
                break
            }

            val fullResponse = responseBuilder.toString()
            val toolCalls = ToolParser.extractToolCalls(fullResponse)

            if (toolCalls.isEmpty()) {
                // No more tools, final answer
                emit(AgentEvent(EventType.FINAL_ANSWER, fullResponse))
                break
            }

            // Execute first tool call (sequentially for now)
            val parsed = toolCalls.first()
            val toolType = when (parsed.tool.lowercase()) {
                "code" -> ToolType.CODE
                "data" -> ToolType.DATA
                "file" -> ToolType.FILE
                else -> ToolType.CUSTOM
            }

            val toolCall = ToolCall(
                tool = toolType,
                language = parsed.language,
                input = parsed.input,
                isRunning = true
            )

            emit(AgentEvent(EventType.TOOL_CALL_START, toolCall = toolCall))

            val result = if (onToolCall != null) {
                onToolCall(toolCall)
            } else {
                when (toolType) {
                    ToolType.CODE -> sandboxRepository.executeCode(parsed.language ?: "python", parsed.input)
                    ToolType.DATA -> {
                        val stats = sandboxRepository.analyzeData(parsed.input)
                        SandboxResult.Success("Data stats: $stats", 0)
                    }
                    ToolType.FILE -> sandboxRepository.executeFileOperation("metadata", parsed.input)
                    else -> SandboxResult.Error("Unknown tool")
                }
            }

            val output = when (result) {
                is SandboxResult.Success -> result.output
                is SandboxResult.Error -> "Error: ${result.message}"
                is SandboxResult.Timeout -> "Error: Execution timed out"
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

            // Feed back to LLM
            messages.add(ChatMessage(role = MessageRole.ASSISTANT, content = fullResponse))
            messages.add(
                ChatMessage(
                    role = MessageRole.TOOL,
                    content = """{"tool":"${parsed.tool}","output":${'"'}${output.replace("\"", "\\\"").take(4000)}${'"'}}"""
                )
            )
        }

        if (iterations >= Constants.MAX_TOOL_ITERATIONS) {
            emit(AgentEvent(EventType.FINAL_ANSWER, "Reached max tool iterations. Forcing final answer..."))
            // One final call without tools
            val finalBuilder = StringBuilder()
            try {
                sendMessage(messages, isAgentMode = false).collect { chunk ->
                    finalBuilder.append(chunk)
                    emit(AgentEvent(EventType.FINAL_ANSWER, finalBuilder.toString()))
                }
            } catch (e: Exception) {
                emit(AgentEvent(EventType.ERROR, e.message ?: "Final call failed"))
            }
        }
    }
}

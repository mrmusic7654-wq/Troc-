package com.troc

import com.troc.data.repository.SandboxRepository
import com.troc.domain.model.ChatMessage
import com.troc.domain.model.MessageRole
import com.troc.domain.model.SandboxResult
import com.troc.domain.usecase.RunAgentLoop
import com.troc.domain.usecase.SendMessage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RunAgentLoopTest {

    private lateinit var sendMessage: SendMessage
    private lateinit var sandboxRepository: SandboxRepository
    private lateinit var runAgentLoop: RunAgentLoop

    @Before
    fun setup() {
        sendMessage = mockk()
        sandboxRepository = mockk(relaxed = true)
        runAgentLoop = RunAgentLoop(sendMessage, sandboxRepository)
    }

    @Test
    fun `agent loop without tool calls returns final answer`() = runTest {
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Hello"))
        coEvery { sendMessage.invoke(any(), true) } returns flowOf("Hi there!")

        val events = runAgentLoop.run(messages).toList()
        assertTrue(events.any { it.type == RunAgentLoop.EventType.FINAL_ANSWER })
    }

    @Test
    fun `agent loop with tool call executes sandbox`() = runTest {
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Run code"))
        val toolResponse = """Here's code: ```json {"tool":"code","language":"python","input":"print('hi')"} ```"""

        coEvery { sendMessage.invoke(any(), true) } returns flowOf(toolResponse) andThen flowOf("Done")
        coEvery { sandboxRepository.executeCode(any(), any(), any()) } returns SandboxResult.Success("hi", 100)

        val events = runAgentLoop.run(messages).toList()
        assertTrue(events.any { it.type == RunAgentLoop.EventType.TOOL_CALL_START })
        assertTrue(events.any { it.type == RunAgentLoop.EventType.TOOL_CALL_RESULT })
    }
}

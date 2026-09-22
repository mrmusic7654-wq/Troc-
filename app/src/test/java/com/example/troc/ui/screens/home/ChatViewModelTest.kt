package com.example.troc.ui.screens.home

import com.example.troc.domain.agent.AgentToolCallParser
import com.example.troc.domain.model.AppSettings
import com.example.troc.domain.model.Chat
import com.example.troc.domain.model.ChatMessage
import com.example.troc.domain.model.ChatRequestSpec
import com.example.troc.domain.model.FileAttachment
import com.example.troc.domain.model.FileAction
import com.example.troc.domain.model.ModelInfo
import com.example.troc.domain.model.Role
import com.example.troc.domain.model.SandboxRequest
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.SandboxSessionRecord
import com.example.troc.domain.model.StreamEvent
import com.example.troc.domain.model.WorkflowStep
import com.example.troc.domain.repository.ChatHistoryRepository
import com.example.troc.domain.repository.FileRepository
import com.example.troc.domain.repository.MistralClient
import com.example.troc.domain.repository.SandboxGateway
import com.example.troc.domain.repository.SettingsRepository
import com.example.troc.domain.repository.WorkflowRunner
import com.example.troc.domain.usecase.RunWorkflowUseCase
import com.example.troc.domain.usecase.StreamChatUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var historyRepo: FakeHistoryRepository
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var mistralFake: FakeMistralClient
    private lateinit var sandboxFake: FakeSandboxGateway
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        historyRepo = FakeHistoryRepository()
        settingsRepo = FakeSettingsRepository()
        mistralFake = FakeMistralClient()
        sandboxFake = FakeSandboxGateway()
        viewModel = ChatViewModel(
            chatHistory = historyRepo,
            settingsRepository = settingsRepo,
            streamChat = StreamChatUseCase(mistralFake),
            sandbox = sandboxFake,
            parser = AgentToolCallParser(),
            runWorkflow = RunWorkflowUseCase(FakeWorkflowRunner()),
            fileRepository = FakeFileRepository()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `send appends user message and streamed assistant reply`() = runTest {
        mistralFake.responses += listOf(flowOf(StreamEvent.Token("Hel"), StreamEvent.Token("lo"), StreamEvent.Done))
        viewModel.send("hi")
        advanceUntilIdle()

        val roles = viewModel.state.value.messages.map { it.role }
        assertEquals(listOf(Role.USER, Role.ASSISTANT), roles)
        assertEquals("Hello", viewModel.state.value.messages[1].content)
        assertEquals(false, viewModel.state.value.isLoading)
        // Chat was persisted.
        assertEquals(1, historyRepo.saved.size)
    }

    @Test
    fun `agent mode executes tool call and loops until final answer`() = runTest {
        viewModel.setAgentMode(true)
        mistralFake.responses += listOf(
            flowOf(
                StreamEvent.Token("Plan: compute <run_code language=\"javascript\">1+1</run_code>"),
                StreamEvent.Done
            ),
            flow {
                emit(StreamEvent.Token("Answer: 2"))
                emit(StreamEvent.Done)
            }
        )
        viewModel.send("what is 1+1")
        advanceUntilIdle()

        val messages = viewModel.state.value.messages
        val toolMessages = messages.filter { it.role == Role.TOOL }
        assertEquals(1, toolMessages.size)
        assertTrue(toolMessages[0].content.contains("2"))
        assertEquals("Answer: 2", messages.last().content)
        assertEquals(1, sandboxFake.requests.size)
    }

    @Test
    fun `missing api key surfaces a friendly error`() = runTest {
        settingsRepo.flow.value = AppSettings(apiKey = "")
        viewModel.send("hello")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error!!.contains("API key"))
        assertTrue(viewModel.state.value.messages.isEmpty())
    }

    @Test
    fun `stream error is surfaced and empty assistant removed`() = runTest {
        mistralFake.responses += listOf(flowOf(StreamEvent.Error("Invalid or missing API key (401).", 401)))
        viewModel.send("hi")
        advanceUntilIdle()
        assertEquals("Invalid or missing API key (401).", viewModel.state.value.error)
        assertTrue(viewModel.state.value.messages.none { it.role == Role.ASSISTANT })
    }
}

// ------------------------------------------------------------------ fakes

@OptIn(ExperimentalCoroutinesApi::class)
private class FakeMistralClient : MistralClient {
    val responses = mutableListOf<Flow<StreamEvent>>()
    private var index = 0

    override fun stream(spec: ChatRequestSpec): Flow<StreamEvent> =
        responses.getOrElse(index++) { flowOf(StreamEvent.Error("no scripted response")) }

    override suspend fun complete(spec: ChatRequestSpec): String = ""

    override suspend fun listModels(baseUrl: String, apiKey: String): List<ModelInfo> =
        listOf(ModelInfo("mistral-small-latest"))
}

private class FakeSandboxGateway : SandboxGateway {
    val requests = mutableListOf<SandboxRequest>()

    override suspend fun execute(request: SandboxRequest): SandboxResult {
        requests += request
        return SandboxResult.Success("2", 12L)
    }

    override fun cancelActive() = Unit
}

private class FakeSettingsRepository : SettingsRepository {
    val flow = MutableStateFlow(AppSettings(apiKey = "test-key"))
    override val settings: Flow<AppSettings> = flow

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        flow.value = transform(flow.value)
    }

    override suspend fun clearAll() {
        flow.value = AppSettings()
    }
}

private class FakeHistoryRepository : ChatHistoryRepository {
    val saved = mutableListOf<Chat>()

    override fun observeChats(): Flow<List<Chat>> = flowOf(saved.toList())
    override fun observeSessions(): Flow<List<SandboxSessionRecord>> = flowOf(emptyList())
    override suspend fun getChat(id: String): Chat? = saved.find { it.id == id }
    override suspend fun saveChat(chat: Chat) {
        saved.removeAll { it.id == chat.id }
        saved += chat
    }
    override suspend fun deleteChat(id: String) {
        saved.removeAll { it.id == id }
    }
    override suspend fun clearChats() = saved.clear()
    override suspend fun saveSession(session: SandboxSessionRecord) = Unit
    override suspend fun deleteSession(id: String) = Unit
    override suspend fun clearSessions() = Unit
}

private class FakeFileRepository : FileRepository {
    override suspend fun readAttachment(uriString: String): FileAttachment? =
        FileAttachment("data.csv", 10, "text/csv", content = "a,b\n1,2")

    override suspend fun inspectFile(uriString: String, action: FileAction, pattern: String?, replacement: String): SandboxResult =
        SandboxResult.Success("ok", 1)

    override suspend fun inspectContent(fileName: String, content: String, action: FileAction, pattern: String?, replacement: String): SandboxResult =
        SandboxResult.Success("ok", 1)
}

private class FakeWorkflowRunner : WorkflowRunner {
    override suspend fun execute(
        steps: List<WorkflowStep>,
        onStepResult: suspend (Int, SandboxResult) -> Unit
    ): List<SandboxResult> = emptyList()
}

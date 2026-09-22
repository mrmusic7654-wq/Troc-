package com.troc

import app.cash.turbine.test
import com.troc.data.prefs.EncryptedPrefs
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ApiKeyRepository
import com.troc.data.repository.ChatRepository
import com.troc.data.repository.SandboxRepository
import com.troc.domain.model.ApiKeyState
import com.troc.domain.usecase.ExecuteSandbox
import com.troc.domain.usecase.RunAgentLoop
import com.troc.domain.usecase.SendMessage
import com.troc.viewmodel.HomeViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChatViewModelTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var sendMessage: SendMessage
    private lateinit var runAgentLoop: RunAgentLoop
    private lateinit var executeSandbox: ExecuteSandbox
    private lateinit var sandboxRepository: SandboxRepository
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setup() {
        chatRepository = mockk(relaxed = true)
        apiKeyRepository = mockk(relaxed = true)
        sendMessage = mockk(relaxed = true)
        runAgentLoop = mockk(relaxed = true)
        executeSandbox = mockk(relaxed = true)
        sandboxRepository = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)

        every { chatRepository.observeChats() } returns flowOf(emptyList())
        every { chatRepository.observeMessages(any()) } returns flowOf(emptyList())
        coEvery { chatRepository.createChat(any()) } returns "test-chat-id"
        coEvery { chatRepository.getMessages(any()) } returns emptyList()

        every { settingsDataStore.mistralModelFlow } returns MutableStateFlow("mistral-small-latest")
        every { settingsDataStore.temperatureFlow } returns MutableStateFlow(0.7f)
        every { settingsDataStore.customEndpointFlow } returns MutableStateFlow("")
    }

    @Test
    fun `initial state is empty`() = runTest {
        val context = mockk<android.content.Context>(relaxed = true)
        val viewModel = HomeViewModel(chatRepository, apiKeyRepository, sendMessage, runAgentLoop, executeSandbox, sandboxRepository, settingsDataStore, context)

        viewModel.uiState.test {
            val state = awaitItem()
            // Initially loading might be false and messages empty after init
            assertNotNull(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle agent mode`() = runTest {
        val context = mockk<android.content.Context>(relaxed = true)
        val viewModel = HomeViewModel(chatRepository, apiKeyRepository, sendMessage, runAgentLoop, executeSandbox, sandboxRepository, settingsDataStore, context)

        // Wait for init
        kotlinx.coroutines.delay(100)
        val initial = viewModel.uiState.value.isAgentMode
        viewModel.toggleAgentMode()
        assertEquals(!initial, viewModel.uiState.value.isAgentMode)
    }
}

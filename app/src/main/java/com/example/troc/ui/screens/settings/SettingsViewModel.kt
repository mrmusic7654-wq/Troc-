package com.example.troc.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.troc.domain.model.AccentColor
import com.example.troc.domain.model.AppSettings
import com.example.troc.domain.model.SandboxTool
import com.example.troc.domain.model.ThemeMode
import com.example.troc.domain.repository.ChatHistoryRepository
import com.example.troc.domain.repository.SettingsRepository
import com.example.troc.domain.usecase.ListModelsUseCase
import com.example.troc.util.ShareUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionTestState(
    val testing: Boolean = false,
    val result: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: ChatHistoryRepository,
    private val listModels: ListModelsUseCase
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _connectionTest = MutableStateFlow(ConnectionTestState())
    val connectionTest: StateFlow<ConnectionTestState> = _connectionTest.asStateFlow()

    fun setApiKey(value: String) = updateSetting { it.copy(apiKey = value.trim()) }
    fun setModel(value: String) = updateSetting { it.copy(model = value, customModel = "") }
    fun setCustomModel(value: String) = updateSetting { it.copy(customModel = value.trim()) }
    fun setEndpoint(value: String) = updateSetting { it.copy(endpoint = value.trim()) }
    fun setThemeMode(value: ThemeMode) = updateSetting { it.copy(themeMode = value) }
    fun setFontScale(value: Float) = updateSetting { it.copy(fontScale = value) }
    fun setAccent(value: AccentColor) = updateSetting { it.copy(accent = value) }
    fun setSandboxEnabled(value: Boolean) = updateSetting { it.copy(sandboxEnabled = value) }
    fun setMaxExecutionSeconds(value: Int) = updateSetting { it.copy(maxExecutionSeconds = value) }

    fun toggleTool(tool: SandboxTool) = updateSetting { current ->
        val allowed = current.allowedTools.toMutableSet()
        if (tool.name in allowed) allowed.remove(tool.name) else allowed.add(tool.name)
        current.copy(allowedTools = allowed)
    }

    fun testConnection() {
        val current = settings.value
        if (current.apiKey.isBlank()) {
            _connectionTest.value = ConnectionTestState(result = "Enter an API key first.", success = false)
            return
        }
        _connectionTest.value = ConnectionTestState(testing = true)
        viewModelScope.launch {
            listModels(current.endpoint, current.apiKey).fold(
                onSuccess = { models ->
                    _connectionTest.value = ConnectionTestState(
                        testing = false,
                        success = true,
                        result = "✓ Connected — ${models.size} models available."
                    )
                },
                onFailure = { error ->
                    _connectionTest.value = ConnectionTestState(
                        testing = false,
                        success = false,
                        result = error.message ?: "Connection failed."
                    )
                }
            )
        }
    }

    /** Zips chats (Markdown + JSON) and sandbox sessions, then opens the share sheet. */
    fun exportAllData() {
        viewModelScope.launch {
            val chats = historyRepository.observeChats().first()
            val sessions = historyRepository.observeSessions().first()
            if (chats.isEmpty() && sessions.isEmpty()) return@launch
            runCatching {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                ShareUtils.exportBackup(appContext, chats, sessions, json)
            }.onSuccess { file ->
                ShareUtils.shareFile(appContext, file, "application/zip")
            }
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            historyRepository.clearChats()
            historyRepository.clearSessions()
            settingsRepository.clearAll()
            onDone()
        }
    }

    private fun updateSetting(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}

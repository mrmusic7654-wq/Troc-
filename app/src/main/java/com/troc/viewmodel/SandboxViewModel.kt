package com.troc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.SandboxRepository
import com.troc.domain.model.*
import com.troc.domain.usecase.ExecuteSandbox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SandboxUiState(
    val selectedTab: Int = 0, // 0 code, 1 data, 2 files, 3 custom
    val code: String = "print('Hello from Troc sandbox!')",
    val language: String = "python",
    val output: String = "",
    val isRunning: Boolean = false,
    val executionTimeMs: Long? = null,
    val timeoutSec: Int = 15,
    val dataContent: String = "",
    val dataStats: DataStats? = null,
    val fileOperation: String = "word_count",
    val fileInput: String = "",
    val fileOutput: String = "",
    val workflows: List<Workflow> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SandboxViewModel @Inject constructor(
    private val sandboxRepository: SandboxRepository,
    private val executeSandbox: ExecuteSandbox,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SandboxUiState())
    val uiState: StateFlow<SandboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.sandboxTimeoutFlow.collect { timeout ->
                _uiState.update { it.copy(timeoutSec = timeout) }
            }
        }
        viewModelScope.launch {
            sandboxRepository.observeWorkflows().collect { entities ->
                val workflows = entities.mapNotNull { entity ->
                    try {
                        // parsing handled in repo getWorkflow, but we need quick parse
                        sandboxRepository.getWorkflow(entity.id)
                    } catch (e: Exception) { null }
                }
                _uiState.update { it.copy(workflows = workflows) }
            }
        }
    }

    fun setTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateCode(code: String) {
        _uiState.update { it.copy(code = code) }
    }

    fun setLanguage(lang: String) {
        _uiState.update { it.copy(language = lang) }
    }

    fun setTimeout(timeout: Int) {
        _uiState.update { it.copy(timeoutSec = timeout) }
    }

    fun runCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, output = "", error = null) }
            val result = executeSandbox.executeCode(_uiState.value.language, _uiState.value.code, _uiState.value.timeoutSec)
            when (result) {
                is SandboxResult.Success -> _uiState.update { it.copy(isRunning = false, output = result.output, executionTimeMs = result.executionTimeMs) }
                is SandboxResult.Error -> _uiState.update { it.copy(isRunning = false, error = result.message, executionTimeMs = result.executionTimeMs) }
                is SandboxResult.Timeout -> _uiState.update { it.copy(isRunning = false, error = "Execution timed out after ${_uiState.value.timeoutSec}s") }
            }
        }
    }

    fun stopCode() {
        _uiState.update { it.copy(isRunning = false) }
    }

    fun updateDataContent(content: String) {
        _uiState.update { it.copy(dataContent = content) }
    }

    fun analyzeData() {
        viewModelScope.launch {
            try {
                val stats = executeSandbox.executeData(_uiState.value.dataContent)
                _uiState.update { it.copy(dataStats = stats) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun setFileOperation(op: String) {
        _uiState.update { it.copy(fileOperation = op) }
    }

    fun updateFileInput(input: String) {
        _uiState.update { it.copy(fileInput = input) }
    }

    fun runFileOperation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }
            val result = executeSandbox.executeFile(_uiState.value.fileOperation, _uiState.value.fileInput)
            when (result) {
                is SandboxResult.Success -> _uiState.update { it.copy(isRunning = false, fileOutput = result.output, executionTimeMs = result.executionTimeMs) }
                is SandboxResult.Error -> _uiState.update { it.copy(isRunning = false, error = result.message) }
                else -> _uiState.update { it.copy(isRunning = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                code = "print('Hello from Troc sandbox!')",
                output = "",
                fileOutput = "",
                dataStats = null,
                error = null,
                executionTimeMs = null
            )
        }
    }
}

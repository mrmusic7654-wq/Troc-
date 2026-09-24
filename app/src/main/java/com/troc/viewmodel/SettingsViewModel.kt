package com.troc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troc.data.api.GroqApi
import com.troc.data.api.MistralApi
import com.troc.data.prefs.EncryptedPrefs
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ApiKeyRepository
import com.troc.data.repository.ChatRepository
import com.troc.data.repository.SandboxRepository
import com.troc.data.repository.UsageRepository
import com.troc.domain.model.ApiKeyState
import com.troc.domain.model.ApiUsage
import com.troc.domain.model.UsageHistoryItem
import com.troc.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val mistralKeyState: ApiKeyState = ApiKeyState.Missing,
    val groqKeyState: ApiKeyState = ApiKeyState.Missing,
    val mistralInput: String = "",
    val groqInput: String = "",
    val isTestingMistral: Boolean = false,
    val isTestingGroq: Boolean = false,
    val mistralValid: Boolean? = null,
    val groqValid: Boolean? = null,
    val mistralModel: String = Constants.DEFAULT_MISTRAL_MODEL,
    val temperature: Float = 0.7f,
    val customEndpoint: String = "",
    val voiceEnabled: Boolean = true,
    val autoSend: Boolean = true,
    val autoPlay: Boolean = true,
    val sttModel: String = Constants.DEFAULT_GROQ_STT_MODEL,
    val ttsVoice: String = Constants.DEFAULT_GROQ_TTS_VOICE,
    val speechSpeed: Float = 1.0f,
    val inputLanguage: String = "auto",
    val sandboxEnabled: Boolean = true,
    val sandboxTimeout: Int = 15,
    val theme: String = "system",
    val fontSize: String = "M",
    val accent: String = "purple",
    val apiUsage: ApiUsage? = null,
    val usageHistory: List<UsageHistoryItem> = emptyList(),
    val saveSuccessMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
    private val settingsDataStore: SettingsDataStore,
    private val mistralApi: MistralApi,
    private val groqApi: GroqApi,
    private val chatRepository: ChatRepository,
    private val sandboxRepository: SandboxRepository,
    private val encryptedPrefs: EncryptedPrefs,
    private val usageRepository: UsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            apiKeyRepository.mistralKeyState.collect { state ->
                _uiState.update { it.copy(mistralKeyState = state) }
            }
        }
        viewModelScope.launch {
            apiKeyRepository.groqKeyState.collect { state ->
                _uiState.update { it.copy(groqKeyState = state) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.mistralModelFlow.collect { v -> _uiState.update { it.copy(mistralModel = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.temperatureFlow.collect { v -> _uiState.update { it.copy(temperature = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.customEndpointFlow.collect { v -> _uiState.update { it.copy(customEndpoint = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.voiceEnabledFlow.collect { v -> _uiState.update { it.copy(voiceEnabled = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.autoSendFlow.collect { v -> _uiState.update { it.copy(autoSend = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.autoPlayFlow.collect { v -> _uiState.update { it.copy(autoPlay = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.sttModelFlow.collect { v -> _uiState.update { it.copy(sttModel = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.ttsVoiceFlow.collect { v -> _uiState.update { it.copy(ttsVoice = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.speechSpeedFlow.collect { v -> _uiState.update { it.copy(speechSpeed = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.inputLanguageFlow.collect { v -> _uiState.update { it.copy(inputLanguage = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.sandboxEnabledFlow.collect { v -> _uiState.update { it.copy(sandboxEnabled = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.sandboxTimeoutFlow.collect { v -> _uiState.update { it.copy(sandboxTimeout = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.themeFlow.collect { v -> _uiState.update { it.copy(theme = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.fontSizeFlow.collect { v -> _uiState.update { it.copy(fontSize = v) } }
        }
        viewModelScope.launch {
            settingsDataStore.accentFlow.collect { v -> _uiState.update { it.copy(accent = v) } }
        }
        viewModelScope.launch {
            usageRepository.observeUsage().collect { usage ->
                _uiState.update { it.copy(apiUsage = usage) }
            }
        }
        viewModelScope.launch {
            usageRepository.observeHistory().collect { history ->
                _uiState.update { it.copy(usageHistory = history) }
            }
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            val usage = usageRepository.getUsage()
            _uiState.update { it.copy(apiUsage = usage) }
        }
    }

    fun clearUsage() {
        viewModelScope.launch {
            usageRepository.clearAll()
        }
    }

    fun updateMistralInput(text: String) {
        _uiState.update { it.copy(mistralInput = text, mistralValid = null) }
    }

    fun updateGroqInput(text: String) {
        _uiState.update { it.copy(groqInput = text, groqValid = null) }
    }

    fun saveMistralKey() {
        val key = _uiState.value.mistralInput.trim()
        if (key.isNotEmpty()) {
            apiKeyRepository.saveMistralKey(key)
            _uiState.update { it.copy(mistralInput = "", saveSuccessMessage = "Mistral key saved successfully!") }
            testMistralKey()
        }
    }

    fun saveGroqKey() {
        val key = _uiState.value.groqInput.trim()
        if (key.isNotEmpty()) {
            apiKeyRepository.saveGroqKey(key)
            _uiState.update { it.copy(groqInput = "", saveSuccessMessage = "Groq key saved successfully!") }
            testGroqKey()
        }
    }

    fun clearSaveSuccessMessage() {
        _uiState.update { it.copy(saveSuccessMessage = null) }
    }

    fun testMistralKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingMistral = true, mistralValid = null) }
            val key = if (_uiState.value.mistralInput.isNotBlank()) {
                _uiState.value.mistralInput.trim()
            } else {
                apiKeyRepository.getMistralKeySync() ?: ""
            }
            if (key.isBlank()) {
                _uiState.update { it.copy(isTestingMistral = false, mistralValid = false) }
                return@launch
            }
            val endpoint = _uiState.value.customEndpoint.ifEmpty { Constants.MISTRAL_BASE_URL }
            val valid = mistralApi.validateKey(endpoint, key)
            _uiState.update { it.copy(isTestingMistral = false, mistralValid = valid) }
            if (valid && _uiState.value.mistralInput.isNotBlank()) {
                apiKeyRepository.saveMistralKey(key)
            }
        }
    }

    fun testGroqKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingGroq = true, groqValid = null) }
            val key = if (_uiState.value.groqInput.isNotBlank()) {
                _uiState.value.groqInput.trim()
            } else {
                apiKeyRepository.getGroqKeySync() ?: ""
            }
            if (key.isBlank()) {
                _uiState.update { it.copy(isTestingGroq = false, groqValid = false) }
                return@launch
            }
            val endpoint = Constants.GROQ_BASE_URL
            val valid = groqApi.validateGroqKey(endpoint, key)
            _uiState.update { it.copy(isTestingGroq = false, groqValid = valid) }
            if (valid && _uiState.value.groqInput.isNotBlank()) {
                apiKeyRepository.saveGroqKey(key)
            }
        }
    }

    fun setMistralModel(model: String) {
        viewModelScope.launch { settingsDataStore.setMistralModel(model) }
    }

    fun setTemperature(temp: Float) {
        viewModelScope.launch { settingsDataStore.setTemperature(temp) }
    }

    fun setCustomEndpoint(endpoint: String) {
        viewModelScope.launch { settingsDataStore.setCustomEndpoint(endpoint) }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setVoiceEnabled(enabled) }
    }

    fun setAutoSend(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoSend(enabled) }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoPlay(enabled) }
    }

    fun setSttModel(model: String) {
        viewModelScope.launch { settingsDataStore.setSttModel(model) }
    }

    fun setTtsVoice(voice: String) {
        viewModelScope.launch { settingsDataStore.setTtsVoice(voice) }
    }

    fun setSpeechSpeed(speed: Float) {
        viewModelScope.launch { settingsDataStore.setSpeechSpeed(speed) }
    }

    fun setInputLanguage(lang: String) {
        viewModelScope.launch { settingsDataStore.setInputLanguage(lang) }
    }

    fun setSandboxEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSandboxEnabled(enabled) }
    }

    fun setSandboxTimeout(timeout: Int) {
        viewModelScope.launch { settingsDataStore.setSandboxTimeout(timeout) }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { settingsDataStore.setTheme(theme) }
    }

    fun setFontSize(size: String) {
        viewModelScope.launch { settingsDataStore.setFontSize(size) }
    }

    fun setAccent(accent: String) {
        viewModelScope.launch { settingsDataStore.setAccent(accent) }
    }

    fun clearAllChats() {
        viewModelScope.launch {
            chatRepository.clearAllChats()
        }
    }

    fun clearSandboxSessions() {
        viewModelScope.launch {
            sandboxRepository.clearAllSessions()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            chatRepository.clearAllChats()
            sandboxRepository.clearAllSessions()
            usageRepository.clearAll()
            apiKeyRepository.clearAllKeys()
            encryptedPrefs.clearAll()
            settingsDataStore.clearAll()
        }
    }
}

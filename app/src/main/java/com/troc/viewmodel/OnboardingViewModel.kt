package com.troc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troc.data.api.GroqApi
import com.troc.data.api.MistralApi
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ApiKeyRepository
import com.troc.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val mistralKey: String = "",
    val groqKey: String = "",
    val isTestingMistral: Boolean = false,
    val isTestingGroq: Boolean = false,
    val mistralValid: Boolean? = null,
    val groqValid: Boolean? = null,
    val hasAudioPermission: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
    private val settingsDataStore: SettingsDataStore,
    private val mistralApi: MistralApi,
    private val groqApi: GroqApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(3)) }
    }

    fun prevStep() {
        _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    fun updateMistralKey(key: String) {
        _uiState.update { it.copy(mistralKey = key) }
    }

    fun updateGroqKey(key: String) {
        _uiState.update { it.copy(groqKey = key) }
    }

    fun setAudioPermission(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
    }

    fun testMistral() {
        viewModelScope.launch {
            if (_uiState.value.mistralKey.isBlank() || _uiState.value.mistralKey.length < 10) {
                _uiState.update { it.copy(mistralValid = false) }
                return@launch
            }
            _uiState.update { it.copy(isTestingMistral = true, mistralValid = null) }
            val valid = mistralApi.validateKey(Constants.MISTRAL_BASE_URL, _uiState.value.mistralKey)
            // Additional check: if validation says invalid, try getModels as fallback
            val finalValid = if (!valid) {
                try {
                    val models = mistralApi.getModels(Constants.MISTRAL_BASE_URL, _uiState.value.mistralKey)
                    models.isNotEmpty()
                } catch (e: Exception) {
                    valid
                }
            } else valid
            _uiState.update { it.copy(isTestingMistral = false, mistralValid = finalValid) }
        }
    }

    fun testGroq() {
        viewModelScope.launch {
            if (_uiState.value.groqKey.isBlank() || _uiState.value.groqKey.length < 10) {
                _uiState.update { it.copy(groqValid = false) }
                return@launch
            }
            _uiState.update { it.copy(isTestingGroq = true, groqValid = null) }
            val valid = groqApi.validateGroqKey(Constants.GROQ_BASE_URL, _uiState.value.groqKey)
            _uiState.update { it.copy(isTestingGroq = false, groqValid = valid) }
        }
    }

    fun saveKeysAndFinish() {
        viewModelScope.launch {
            if (_uiState.value.mistralKey.isNotBlank()) {
                apiKeyRepository.saveMistralKey(_uiState.value.mistralKey)
            }
            if (_uiState.value.groqKey.isNotBlank()) {
                apiKeyRepository.saveGroqKey(_uiState.value.groqKey)
            }
            settingsDataStore.setOnboardingDone(true)
        }
    }

    fun skipGroq() {
        // just proceed
        nextStep()
    }
}

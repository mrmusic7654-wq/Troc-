package com.example.troc.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.troc.domain.model.AppSettings
import com.example.troc.domain.repository.SettingsRepository
import com.example.troc.domain.usecase.ListModelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingKeyState(
    val testing: Boolean = false,
    val tested: Boolean = false,
    val success: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val listModels: ListModelsUseCase
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _keyState = MutableStateFlow(OnboardingKeyState())
    val keyState: StateFlow<OnboardingKeyState> = _keyState.asStateFlow()

    fun setApiKey(value: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(apiKey = value.trim()) }
            _keyState.value = OnboardingKeyState()
        }
    }

    fun testConnection() {
        val key = settings.value.apiKey
        if (key.isBlank()) {
            _keyState.value = OnboardingKeyState(message = "Paste your API key first.")
            return
        }
        _keyState.value = OnboardingKeyState(testing = true)
        viewModelScope.launch {
            listModels(AppSettings.DEFAULT_ENDPOINT, key).fold(
                onSuccess = { models ->
                    _keyState.value = OnboardingKeyState(
                        tested = true,
                        success = true,
                        message = "✓ Key works — ${models.size} models available"
                    )
                },
                onFailure = { error ->
                    _keyState.value = OnboardingKeyState(
                        tested = true,
                        success = false,
                        message = error.message ?: "Connection failed."
                    )
                }
            )
        }
    }

    fun complete() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(onboardingDone = true) }
        }
    }
}

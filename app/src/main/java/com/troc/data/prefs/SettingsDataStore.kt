package com.troc.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.troc.domain.model.SttModel
import com.troc.domain.model.TtsVoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "troc_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    object Keys {
        val THEME = stringPreferencesKey("theme") // system, light, dark
        val FONT_SIZE = stringPreferencesKey("font_size") // S, M, L
        val ACCENT = stringPreferencesKey("accent") // purple, cyan, emerald
        val MISTRAL_MODEL = stringPreferencesKey("mistral_model")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val AUTO_SEND = booleanPreferencesKey("auto_send")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val STT_MODEL = stringPreferencesKey("stt_model")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val INPUT_LANGUAGE = stringPreferencesKey("input_language")
        val SANDBOX_ENABLED = booleanPreferencesKey("sandbox_enabled")
        val SANDBOX_TIMEOUT = intPreferencesKey("sandbox_timeout")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val fontSizeFlow: Flow<String> = context.dataStore.data.map { it[Keys.FONT_SIZE] ?: "M" }
    val accentFlow: Flow<String> = context.dataStore.data.map { it[Keys.ACCENT] ?: "purple" }
    val mistralModelFlow: Flow<String> = context.dataStore.data.map { it[Keys.MISTRAL_MODEL] ?: "mistral-small-latest" }
    val temperatureFlow: Flow<Float> = context.dataStore.data.map { it[Keys.TEMPERATURE] ?: 0.7f }
    val customEndpointFlow: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_ENDPOINT] ?: "" }
    val voiceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_ENABLED] ?: true }
    val autoSendFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_SEND] ?: true }
    val autoPlayFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_PLAY] ?: true }
    val sttModelFlow: Flow<String> = context.dataStore.data.map { it[Keys.STT_MODEL] ?: SttModel.WHISPER_LARGE_V3.id }
    val ttsVoiceFlow: Flow<String> = context.dataStore.data.map { it[Keys.TTS_VOICE] ?: TtsVoice.ARISTA.id }
    val speechSpeedFlow: Flow<Float> = context.dataStore.data.map { it[Keys.SPEECH_SPEED] ?: 1.0f }
    val inputLanguageFlow: Flow<String> = context.dataStore.data.map { it[Keys.INPUT_LANGUAGE] ?: "auto" }
    val sandboxEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.SANDBOX_ENABLED] ?: true }
    val sandboxTimeoutFlow: Flow<Int> = context.dataStore.data.map { it[Keys.SANDBOX_TIMEOUT] ?: 15 }
    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setTheme(value: String) { context.dataStore.edit { it[Keys.THEME] = value } }
    suspend fun setFontSize(value: String) { context.dataStore.edit { it[Keys.FONT_SIZE] = value } }
    suspend fun setAccent(value: String) { context.dataStore.edit { it[Keys.ACCENT] = value } }
    suspend fun setMistralModel(value: String) { context.dataStore.edit { it[Keys.MISTRAL_MODEL] = value } }
    suspend fun setTemperature(value: Float) { context.dataStore.edit { it[Keys.TEMPERATURE] = value } }
    suspend fun setCustomEndpoint(value: String) { context.dataStore.edit { it[Keys.CUSTOM_ENDPOINT] = value } }
    suspend fun setVoiceEnabled(value: Boolean) { context.dataStore.edit { it[Keys.VOICE_ENABLED] = value } }
    suspend fun setAutoSend(value: Boolean) { context.dataStore.edit { it[Keys.AUTO_SEND] = value } }
    suspend fun setAutoPlay(value: Boolean) { context.dataStore.edit { it[Keys.AUTO_PLAY] = value } }
    suspend fun setSttModel(value: String) { context.dataStore.edit { it[Keys.STT_MODEL] = value } }
    suspend fun setTtsVoice(value: String) { context.dataStore.edit { it[Keys.TTS_VOICE] = value } }
    suspend fun setSpeechSpeed(value: Float) { context.dataStore.edit { it[Keys.SPEECH_SPEED] = value } }
    suspend fun setInputLanguage(value: String) { context.dataStore.edit { it[Keys.INPUT_LANGUAGE] = value } }
    suspend fun setSandboxEnabled(value: Boolean) { context.dataStore.edit { it[Keys.SANDBOX_ENABLED] = value } }
    suspend fun setSandboxTimeout(value: Int) { context.dataStore.edit { it[Keys.SANDBOX_TIMEOUT] = value } }
    suspend fun setOnboardingDone(value: Boolean) { context.dataStore.edit { it[Keys.ONBOARDING_DONE] = value } }

    suspend fun clearAll() { context.dataStore.edit { it.clear() } }
}

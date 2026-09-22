package com.example.troc.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.troc.domain.model.AccentColor
import com.example.troc.domain.model.AppSettings
import com.example.troc.domain.model.SandboxTool
import com.example.troc.domain.model.ThemeMode
import com.example.troc.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trocDataStore by preferencesDataStore(name = "troc_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model")
        val ENDPOINT = stringPreferencesKey("endpoint")
        val THEME = stringPreferencesKey("theme_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ACCENT = stringPreferencesKey("accent")
        val SANDBOX_ENABLED = booleanPreferencesKey("sandbox_enabled")
        val MAX_EXEC_SECONDS = intPreferencesKey("max_exec_seconds")
        val ALLOWED_TOOLS = stringSetPreferencesKey("allowed_tools")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    override val settings: Flow<AppSettings> = context.trocDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                apiKey = prefs[Keys.API_KEY].orEmpty(),
                model = prefs[Keys.MODEL] ?: AppSettings().model,
                customModel = prefs[Keys.CUSTOM_MODEL].orEmpty(),
                endpoint = prefs[Keys.ENDPOINT] ?: AppSettings.DEFAULT_ENDPOINT,
                themeMode = enumOrDefault(prefs[Keys.THEME], ThemeMode.SYSTEM),
                fontScale = prefs[Keys.FONT_SCALE] ?: 1.0f,
                accent = enumOrDefault(prefs[Keys.ACCENT], AccentColor.VIOLET),
                sandboxEnabled = prefs[Keys.SANDBOX_ENABLED] ?: true,
                maxExecutionSeconds = prefs[Keys.MAX_EXEC_SECONDS] ?: 30,
                allowedTools = prefs[Keys.ALLOWED_TOOLS]
                    ?: SandboxTool.entries.map { it.name }.toSet(),
                onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false
            )
        }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        context.trocDataStore.edit { prefs ->
            prefs[Keys.API_KEY] = next.apiKey
            prefs[Keys.MODEL] = next.model
            prefs[Keys.CUSTOM_MODEL] = next.customModel
            prefs[Keys.ENDPOINT] = next.endpoint
            prefs[Keys.THEME] = next.themeMode.name
            prefs[Keys.FONT_SCALE] = next.fontScale
            prefs[Keys.ACCENT] = next.accent.name
            prefs[Keys.SANDBOX_ENABLED] = next.sandboxEnabled
            prefs[Keys.MAX_EXEC_SECONDS] = next.maxExecutionSeconds
            prefs[Keys.ALLOWED_TOOLS] = next.allowedTools
            prefs[Keys.ONBOARDING_DONE] = next.onboardingDone
        }
    }

    override suspend fun clearAll() {
        context.trocDataStore.edit { it.clear() }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}

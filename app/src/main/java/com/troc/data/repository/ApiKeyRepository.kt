package com.troc.data.repository

import com.troc.data.prefs.EncryptedPrefs
import com.troc.domain.model.ApiKeyState
import com.troc.domain.model.maskKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyRepository @Inject constructor(
    private val encryptedPrefs: EncryptedPrefs
) {
    private val _mistralKeyState = MutableStateFlow<ApiKeyState>(ApiKeyState.Missing)
    val mistralKeyState: StateFlow<ApiKeyState> = _mistralKeyState.asStateFlow()

    private val _groqKeyState = MutableStateFlow<ApiKeyState>(ApiKeyState.Missing)
    val groqKeyState: StateFlow<ApiKeyState> = _groqKeyState.asStateFlow()

    init {
        refreshKeys()
    }

    private fun cleanKey(key: String?): String? {
        if (key.isNullOrBlank()) return null
        val cleaned = key.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
        return cleaned.ifBlank { null }
    }

    private fun getBuildConfigField(name: String): String? {
        return try {
            val clazz = Class.forName("com.troc.BuildConfig")
            val field = clazz.getField(name)
            val value = field.get(null) as? String
            cleanKey(value)
        } catch (e: Exception) {
            null
        }
    }

    fun refreshKeys() {
        // Mistral
        val userMistral = cleanKey(encryptedPrefs.getString(EncryptedPrefs.KEY_MISTRAL_USER))
        val defaultMistral = getBuildConfigField("MISTRAL_API_KEY")
            ?: getBuildConfigField("MISTRAL_API_KEY_FALLBACK")

        _mistralKeyState.value = when {
            !userMistral.isNullOrBlank() -> ApiKeyState.UserProvided(userMistral, maskKey(userMistral))
            !defaultMistral.isNullOrBlank() -> ApiKeyState.Default(defaultMistral, maskKey(defaultMistral))
            else -> ApiKeyState.Missing
        }

        // Groq
        val userGroq = cleanKey(encryptedPrefs.getString(EncryptedPrefs.KEY_GROQ_USER))
        val defaultGroq = getBuildConfigField("GROQ_API_KEY")
            ?: getBuildConfigField("GROQ_API_KEY_FALLBACK")

        _groqKeyState.value = when {
            !userGroq.isNullOrBlank() -> ApiKeyState.UserProvided(userGroq, maskKey(userGroq))
            !defaultGroq.isNullOrBlank() -> ApiKeyState.Default(defaultGroq, maskKey(defaultGroq))
            else -> ApiKeyState.Missing
        }
    }

    fun saveMistralKey(key: String) {
        val cleaned = cleanKey(key)
        if (cleaned == null) {
            encryptedPrefs.remove(EncryptedPrefs.KEY_MISTRAL_USER)
        } else {
            encryptedPrefs.putString(EncryptedPrefs.KEY_MISTRAL_USER, cleaned)
        }
        refreshKeys()
    }

    fun saveGroqKey(key: String) {
        val cleaned = cleanKey(key)
        if (cleaned == null) {
            encryptedPrefs.remove(EncryptedPrefs.KEY_GROQ_USER)
        } else {
            encryptedPrefs.putString(EncryptedPrefs.KEY_GROQ_USER, cleaned)
        }
        refreshKeys()
    }

    fun markMistralInvalid() {
        _mistralKeyState.value = ApiKeyState.Invalid
    }

    fun markGroqInvalid() {
        _groqKeyState.value = ApiKeyState.Invalid
    }

    suspend fun getMistralKeySync(): String? {
        val key = when (val s = _mistralKeyState.value) {
            is ApiKeyState.UserProvided -> s.key
            is ApiKeyState.Default -> s.key
            else -> {
                val user = encryptedPrefs.getString(EncryptedPrefs.KEY_MISTRAL_USER)
                if (!user.isNullOrBlank()) user
                else getBuildConfigField("MISTRAL_API_KEY") ?: getBuildConfigField("MISTRAL_API_KEY_FALLBACK")
            }
        }
        return cleanKey(key)
    }

    suspend fun getGroqKeySync(): String? {
        val key = when (val s = _groqKeyState.value) {
            is ApiKeyState.UserProvided -> s.key
            is ApiKeyState.Default -> s.key
            else -> {
                val user = encryptedPrefs.getString(EncryptedPrefs.KEY_GROQ_USER)
                if (!user.isNullOrBlank()) user
                else getBuildConfigField("GROQ_API_KEY") ?: getBuildConfigField("GROQ_API_KEY_FALLBACK")
            }
        }
        return cleanKey(key)
    }

    fun hasMistralKey(): Boolean {
        val state = _mistralKeyState.value
        return state is ApiKeyState.UserProvided || state is ApiKeyState.Default
    }

    fun clearAllKeys() {
        encryptedPrefs.remove(EncryptedPrefs.KEY_MISTRAL_USER)
        encryptedPrefs.remove(EncryptedPrefs.KEY_GROQ_USER)
        refreshKeys()
    }
}

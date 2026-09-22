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

    private fun getBuildConfigField(name: String): String? {
        return try {
            val clazz = Class.forName("com.troc.BuildConfig")
            val field = clazz.getField(name)
            val value = field.get(null) as? String
            value?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun refreshKeys() {
        // Mistral
        val userMistral = encryptedPrefs.getString(EncryptedPrefs.KEY_MISTRAL_USER)
        val defaultMistral = getBuildConfigField("MISTRAL_API_KEY")
            ?: getBuildConfigField("MISTRAL_API_KEY_FALLBACK")

        _mistralKeyState.value = when {
            !userMistral.isNullOrBlank() -> ApiKeyState.UserProvided(userMistral, maskKey(userMistral))
            !defaultMistral.isNullOrBlank() -> ApiKeyState.Default(defaultMistral, maskKey(defaultMistral))
            else -> ApiKeyState.Missing
        }

        // Groq
        val userGroq = encryptedPrefs.getString(EncryptedPrefs.KEY_GROQ_USER)
        val defaultGroq = getBuildConfigField("GROQ_API_KEY")
            ?: getBuildConfigField("GROQ_API_KEY_FALLBACK")

        _groqKeyState.value = when {
            !userGroq.isNullOrBlank() -> ApiKeyState.UserProvided(userGroq, maskKey(userGroq))
            !defaultGroq.isNullOrBlank() -> ApiKeyState.Default(defaultGroq, maskKey(defaultGroq))
            else -> ApiKeyState.Missing
        }
    }

    fun saveMistralKey(key: String) {
        if (key.isBlank()) {
            encryptedPrefs.remove(EncryptedPrefs.KEY_MISTRAL_USER)
        } else {
            encryptedPrefs.putString(EncryptedPrefs.KEY_MISTRAL_USER, key.trim())
        }
        refreshKeys()
    }

    fun saveGroqKey(key: String) {
        if (key.isBlank()) {
            encryptedPrefs.remove(EncryptedPrefs.KEY_GROQ_USER)
        } else {
            encryptedPrefs.putString(EncryptedPrefs.KEY_GROQ_USER, key.trim())
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
        return when (val s = _mistralKeyState.value) {
            is ApiKeyState.UserProvided -> s.key
            is ApiKeyState.Default -> s.key
            else -> {
                // re-read fallback
                val user = encryptedPrefs.getString(EncryptedPrefs.KEY_MISTRAL_USER)
                if (!user.isNullOrBlank()) return user
                getBuildConfigField("MISTRAL_API_KEY") ?: getBuildConfigField("MISTRAL_API_KEY_FALLBACK")
            }
        }
    }

    suspend fun getGroqKeySync(): String? {
        return when (val s = _groqKeyState.value) {
            is ApiKeyState.UserProvided -> s.key
            is ApiKeyState.Default -> s.key
            else -> {
                val user = encryptedPrefs.getString(EncryptedPrefs.KEY_GROQ_USER)
                if (!user.isNullOrBlank()) return user
                getBuildConfigField("GROQ_API_KEY") ?: getBuildConfigField("GROQ_API_KEY_FALLBACK")
            }
        }
    }

    fun clearAllKeys() {
        encryptedPrefs.remove(EncryptedPrefs.KEY_MISTRAL_USER)
        encryptedPrefs.remove(EncryptedPrefs.KEY_GROQ_USER)
        refreshKeys()
    }
}

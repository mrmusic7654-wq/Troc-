package com.troc

import com.troc.data.prefs.EncryptedPrefs
import com.troc.data.repository.ApiKeyRepository
import com.troc.domain.model.ApiKeyState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiKeyRepositoryTest {

    private lateinit var encryptedPrefs: EncryptedPrefs
    private lateinit var repository: ApiKeyRepository

    @Before
    fun setup() {
        encryptedPrefs = mockk(relaxed = true)
        every { encryptedPrefs.getString(EncryptedPrefs.KEY_MISTRAL_USER) } returns null
        every { encryptedPrefs.getString(EncryptedPrefs.KEY_GROQ_USER) } returns null
        repository = ApiKeyRepository(encryptedPrefs)
    }

    @Test
    fun `missing keys when no user and no default`() {
        // BuildConfig keys are empty in test
        assertTrue(repository.mistralKeyState.value is ApiKeyState.Missing)
        assertTrue(repository.groqKeyState.value is ApiKeyState.Missing)
    }

    @Test
    fun `user key takes precedence over default`() {
        every { encryptedPrefs.getString(EncryptedPrefs.KEY_MISTRAL_USER) } returns "user_mistral_key_1234567890"
        repository.refreshKeys()
        val state = repository.mistralKeyState.value
        assertTrue(state is ApiKeyState.UserProvided)
        assertEquals("user_mistral_key_1234567890", (state as ApiKeyState.UserProvided).key)
    }

    @Test
    fun `save and clear keys`() {
        repository.saveMistralKey("test_key_123456")
        verify { encryptedPrefs.putString(EncryptedPrefs.KEY_MISTRAL_USER, "test_key_123456") }

        repository.saveMistralKey("")
        verify { encryptedPrefs.remove(EncryptedPrefs.KEY_MISTRAL_USER) }
    }

    @Test
    fun `masking shows last 4 chars`() {
        val key = "sk-1234567890abcd"
        val masked = com.troc.domain.model.maskKey(key)
        assertTrue(masked.endsWith("abcd"))
        assertTrue(masked.startsWith("••••"))
    }
}

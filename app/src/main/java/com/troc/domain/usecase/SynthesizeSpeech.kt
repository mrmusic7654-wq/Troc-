package com.troc.domain.usecase

import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.VoiceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SynthesizeSpeech @Inject constructor(
    private val voiceRepository: VoiceRepository,
    private val settingsDataStore: SettingsDataStore
) {
    suspend operator fun invoke(text: String): Result<List<ByteArray>> {
        val voice = settingsDataStore.ttsVoiceFlow.first()
        val speed = settingsDataStore.speechSpeedFlow.first()
        val chunks = voiceRepository.chunkTextForTts(text)
        val results = mutableListOf<ByteArray>()

        for (chunk in chunks) {
            val res = voiceRepository.synthesize(chunk, voice, speed = speed)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
            res.getOrNull()?.let { results.add(it) }
        }
        return Result.success(results)
    }

    suspend fun synthesizeSingle(text: String): Result<ByteArray> {
        val voice = settingsDataStore.ttsVoiceFlow.first()
        val speed = settingsDataStore.speechSpeedFlow.first()
        return voiceRepository.synthesize(text, voice, speed = speed)
    }
}

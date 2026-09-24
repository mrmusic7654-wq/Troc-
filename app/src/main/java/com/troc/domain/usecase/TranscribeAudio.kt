package com.troc.domain.usecase

import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.VoiceRepository
import com.troc.util.Constants
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

class TranscribeAudio @Inject constructor(
    private val voiceRepository: VoiceRepository,
    private val settingsDataStore: SettingsDataStore
) {
    suspend operator fun invoke(wavFile: File): Result<String> {
        val sttModel = settingsDataStore.sttModelFlow.first()
        val language = settingsDataStore.inputLanguageFlow.first()
        val lang = if (language == "auto") null else language
        return voiceRepository.transcribe(wavFile, sttModel, lang)
    }
}

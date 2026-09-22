package com.troc

import com.troc.data.audio.AudioRecorder
import com.troc.data.audio.VoicePlayer
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ChatRepository
import com.troc.data.repository.VoiceRepository
import com.troc.domain.model.VoiceStatus
import com.troc.domain.usecase.SendMessage
import com.troc.domain.usecase.SynthesizeSpeech
import com.troc.domain.usecase.TranscribeAudio
import com.troc.viewmodel.VoiceViewModel
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VoiceViewModelTest {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var voicePlayer: VoicePlayer
    private lateinit var voiceRepository: VoiceRepository
    private lateinit var transcribeAudio: TranscribeAudio
    private lateinit var synthesizeSpeech: SynthesizeSpeech
    private lateinit var sendMessage: SendMessage
    private lateinit var chatRepository: ChatRepository
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setup() {
        audioRecorder = mockk(relaxed = true)
        voicePlayer = mockk(relaxed = true)
        voiceRepository = mockk(relaxed = true)
        transcribeAudio = mockk(relaxed = true)
        synthesizeSpeech = mockk(relaxed = true)
        sendMessage = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)

        io.mockk.every { settingsDataStore.autoSendFlow } returns MutableStateFlow(true)
        io.mockk.every { settingsDataStore.autoPlayFlow } returns MutableStateFlow(true)
    }

    @Test
    fun `initial state is idle`() {
        val context = mockk<android.content.Context>(relaxed = true)
        val viewModel = VoiceViewModel(audioRecorder, voicePlayer, voiceRepository, transcribeAudio, synthesizeSpeech, sendMessage, chatRepository, settingsDataStore, context)
        assertTrue(viewModel.uiState.value.status is VoiceStatus.Idle)
    }

    @Test
    fun `toggle mute`() {
        val context = mockk<android.content.Context>(relaxed = true)
        val viewModel = VoiceViewModel(audioRecorder, voicePlayer, voiceRepository, transcribeAudio, synthesizeSpeech, sendMessage, chatRepository, settingsDataStore, context)
        val initial = viewModel.uiState.value.isMuted
        viewModel.toggleMute()
        assertEquals(!initial, viewModel.uiState.value.isMuted)
    }
}

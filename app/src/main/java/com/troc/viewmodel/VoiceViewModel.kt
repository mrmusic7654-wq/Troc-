package com.troc.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troc.data.audio.AudioRecorder
import com.troc.data.audio.VadDetector
import com.troc.data.audio.VoicePlayer
import com.troc.data.prefs.SettingsDataStore
import com.troc.data.repository.ChatRepository
import com.troc.data.repository.VoiceRepository
import com.troc.domain.model.ChatMessage
import com.troc.domain.model.MessageRole
import com.troc.domain.model.VoiceStatus
import com.troc.domain.usecase.SendMessage
import com.troc.domain.usecase.SynthesizeSpeech
import com.troc.domain.usecase.TranscribeAudio
import com.troc.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class VoiceUiState(
    val status: VoiceStatus = VoiceStatus.Idle,
    val amplitude: Float = 0f,
    val transcript: String = "",
    val assistantResponse: String = "",
    val miniTranscript: List<String> = emptyList(),
    val isMuted: Boolean = false,
    val error: String? = null,
    val chatId: String? = null
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val voicePlayer: VoicePlayer,
    private val voiceRepository: VoiceRepository,
    private val transcribeAudio: TranscribeAudio,
    private val synthesizeSpeech: SynthesizeSpeech,
    private val sendMessage: SendMessage,
    private val chatRepository: ChatRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private val vadDetector = VadDetector()
    private var recordingJob: Job? = null
    private var ttsFiles = mutableListOf<File>()

    fun setChatId(chatId: String) {
        _uiState.update { it.copy(chatId = chatId) }
    }

    fun startVoiceLoop() {
        if (_uiState.value.status is VoiceStatus.Listening) return
        _uiState.update { it.copy(status = VoiceStatus.Listening, error = null) }
        startListening()
    }

    private fun startListening() {
        vadDetector.reset()
        _uiState.update { it.copy(status = VoiceStatus.Listening, amplitude = 0f) }
        recordingJob?.cancel()

        var shouldStop = false
        var silenceDetected = false

        recordingJob = viewModelScope.launch {
            val wavFile = audioRecorder.recordWithAmplitudeToFile(
                onAmplitude = { amp ->
                    _uiState.update { it.copy(amplitude = amp) }
                    val vadEvent = vadDetector.processAmplitude(amp)
                    when (vadEvent) {
                        is VadDetector.VadEvent.SpeechEnded -> {
                            silenceDetected = true
                            shouldStop = true
                        }
                        is VadDetector.VadEvent.SpeechStarted -> {
                            // barge-in: if speaking, stop playback
                            if (_uiState.value.status is VoiceStatus.Speaking) {
                                voicePlayer.stop()
                                _uiState.update { it.copy(status = VoiceStatus.Listening) }
                            }
                        }
                        else -> {}
                    }
                    // Barge-in detection during speaking
                    if (_uiState.value.status is VoiceStatus.Speaking && amp > Constants.BARGE_IN_THRESHOLD) {
                        voicePlayer.stop()
                        _uiState.update { it.copy(status = VoiceStatus.Listening) }
                        shouldStop = true
                    }
                },
                shouldStop = { shouldStop }
            )

            if (wavFile != null && wavFile.length() > 1000) {
                // Transcribe
                _uiState.update { it.copy(status = VoiceStatus.Transcribing) }
                val result = transcribeAudio(wavFile)
                wavFile.delete()

                if (result.isSuccess) {
                    val text = result.getOrNull()?.trim() ?: ""
                    if (text.isEmpty()) {
                        _uiState.update { it.copy(status = VoiceStatus.Error("Couldn't hear that, try again")) }
                        delay(1500)
                        startListening()
                    } else {
                        _uiState.update {
                            it.copy(
                                transcript = text,
                                miniTranscript = (it.miniTranscript + "You: $text").takeLast(10)
                            )
                        }
                        // Auto-send if enabled
                        val autoSend = settingsDataStore.autoSendFlow.first()
                        if (autoSend) {
                            thinkAndSpeak(text)
                        } else {
                            _uiState.update { it.copy(status = VoiceStatus.Idle) }
                        }
                    }
                } else {
                    _uiState.update { it.copy(status = VoiceStatus.Error("Transcription failed: ${result.exceptionOrNull()?.message}")) }
                    delay(1500)
                    startListening()
                }
            } else {
                wavFile?.delete()
                if (silenceDetected) {
                    // no speech, restart listening
                    startListening()
                } else {
                    _uiState.update { it.copy(status = VoiceStatus.Idle) }
                }
            }
        }
    }

    private suspend fun thinkAndSpeak(userText: String) {
        val chatId = _uiState.value.chatId ?: run {
            val newId = chatRepository.createChat(userText.take(50))
            _uiState.update { it.copy(chatId = newId) }
            newId
        }

        // Save user message
        val userMsg = ChatMessage(role = MessageRole.USER, content = userText, chatId = chatId)
        chatRepository.saveMessage(chatId, userMsg)

        _uiState.update { it.copy(status = VoiceStatus.Thinking) }

        try {
            val history = chatRepository.getMessages(chatId)
            val responseBuilder = StringBuilder()
            sendMessage(history, isAgentMode = false).collect { chunk ->
                responseBuilder.append(chunk)
                _uiState.update { it.copy(assistantResponse = responseBuilder.toString()) }
            }

            val finalResponse = responseBuilder.toString()
            val assistantMsg = ChatMessage(role = MessageRole.ASSISTANT, content = finalResponse, chatId = chatId)
            chatRepository.saveMessage(chatId, assistantMsg)

            _uiState.update {
                it.copy(
                    miniTranscript = (it.miniTranscript + "Troc: $finalResponse").takeLast(10),
                    status = VoiceStatus.Speaking
                )
            }

            // TTS
            val autoPlay = settingsDataStore.autoPlayFlow.first()
            if (autoPlay) {
                speakText(finalResponse)
            } else {
                _uiState.update { it.copy(status = VoiceStatus.Idle) }
                startListening()
            }

        } catch (e: Exception) {
            _uiState.update { it.copy(status = VoiceStatus.Error("Thinking failed: ${e.message}")) }
            delay(2000)
            startListening()
        }
    }

    private suspend fun speakText(text: String) {
        val result = synthesizeSpeech(text)
        if (result.isSuccess) {
            val bytesList = result.getOrNull() ?: emptyList()
            ttsFiles.forEach { it.delete() }
            ttsFiles.clear()

            val files = bytesList.map { bytes ->
                val f = File.createTempFile("tts_${System.currentTimeMillis()}", ".wav", context.cacheDir)
                f.writeBytes(bytes)
                f
            }
            ttsFiles.addAll(files)

            if (files.isNotEmpty()) {
                voicePlayer.queueFiles(files)
                // Wait for playback to finish, with barge-in support
                while (voicePlayer.isPlaying()) {
                    delay(200)
                }
                // After speaking, resume listening
                _uiState.update { it.copy(status = VoiceStatus.Idle) }
                delay(300)
                startListening()
            } else {
                _uiState.update { it.copy(status = VoiceStatus.Idle) }
                startListening()
            }
        } else {
            _uiState.update { it.copy(status = VoiceStatus.Error("TTS failed: ${result.exceptionOrNull()?.message}")) }
            delay(2000)
            startListening()
        }
    }

    fun stopListening() {
        recordingJob?.cancel()
        voicePlayer.stop()
        _uiState.update { it.copy(status = VoiceStatus.Idle, amplitude = 0f) }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
        if (_uiState.value.isMuted) {
            voicePlayer.stop()
        }
    }

    fun sendTextManually(text: String) {
        viewModelScope.launch {
            thinkAndSpeak(text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        voicePlayer.stop()
        ttsFiles.forEach { it.delete() }
    }
}

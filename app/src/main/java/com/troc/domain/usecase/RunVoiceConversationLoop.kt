package com.troc.domain.usecase

import com.troc.domain.model.ChatMessage
import com.troc.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class RunVoiceConversationLoop @Inject constructor(
    private val sendMessage: SendMessage,
    private val transcribeAudio: TranscribeAudio,
    private val synthesizeSpeech: SynthesizeSpeech
) {
    // This use case is orchestrated in VoiceViewModel, but we provide a helper for text loop
    fun runTextLoop(history: List<ChatMessage>): Flow<String> = flow {
        val responseBuilder = StringBuilder()
        sendMessage(history, isAgentMode = false).collect { chunk ->
            responseBuilder.append(chunk)
            emit(responseBuilder.toString())
        }
    }
}

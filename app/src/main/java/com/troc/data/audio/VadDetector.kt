package com.troc.data.audio

import com.troc.util.Constants

class VadDetector(
    private val silenceThreshold: Float = 0.02f,
    private val silenceDurationMs: Long = Constants.VAD_SILENCE_MS
) {
    private var silenceStart: Long? = null
    private var isSpeaking = false

    fun processAmplitude(amplitude: Float, timestamp: Long = System.currentTimeMillis()): VadEvent {
        return if (amplitude > silenceThreshold) {
            silenceStart = null
            if (!isSpeaking) {
                isSpeaking = true
                VadEvent.SpeechStarted
            } else {
                VadEvent.SpeechContinues
            }
        } else {
            if (isSpeaking) {
                if (silenceStart == null) {
                    silenceStart = timestamp
                    VadEvent.SilenceStarted
                } else {
                    val elapsed = timestamp - (silenceStart ?: timestamp)
                    if (elapsed >= silenceDurationMs) {
                        isSpeaking = false
                        silenceStart = null
                        VadEvent.SpeechEnded
                    } else {
                        VadEvent.SilenceContinues
                    }
                }
            } else {
                VadEvent.SilenceContinues
            }
        }
    }

    fun reset() {
        silenceStart = null
        isSpeaking = false
    }

    sealed class VadEvent {
        object SpeechStarted : VadEvent()
        object SpeechContinues : VadEvent()
        object SilenceStarted : VadEvent()
        object SilenceContinues : VadEvent()
        object SpeechEnded : VadEvent()
    }
}

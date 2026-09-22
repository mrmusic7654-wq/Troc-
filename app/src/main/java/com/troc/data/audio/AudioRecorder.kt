package com.troc.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.troc.util.Constants
import com.troc.util.WavWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecordingFlow(): Flow<Float> = callbackFlow {
        if (!hasPermission()) {
            close(Exception("RECORD_AUDIO permission not granted"))
            return@callbackFlow
        }

        val sampleRate = Constants.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord?.startRecording()
            isRecording = true

            val buffer = ShortArray(bufferSize / 2)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val amplitude = calculateAmplitude(buffer, read)
                    trySend(amplitude)
                }
            }
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            stopRecording()
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord = null
    }

    suspend fun recordToWavFile(durationMs: Long? = null): File? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val sampleRate = Constants.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val pcmFile = File.createTempFile("pcm_${System.currentTimeMillis()}", ".pcm", context.cacheDir)
        val wavFile = File.createTempFile("rec_${System.currentTimeMillis()}", ".wav", context.cacheDir)

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            recorder.startRecording()
            val startTime = System.currentTimeMillis()
            val buffer = ShortArray(bufferSize / 2)
            val allData = mutableListOf<Short>()

            while (true) {
                if (durationMs != null && System.currentTimeMillis() - startTime > durationMs) break
                if (!isRecording && durationMs == null) {
                    // For manual stop, we check isRecording flag; but for this method we use duration
                }
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) allData.add(buffer[i])
                }
                if (durationMs != null && System.currentTimeMillis() - startTime > durationMs) break
                if (durationMs == null && !isRecording) break
            }

            recorder.stop()
            recorder.release()

            val byteArray = WavWriter.pcmShortArrayToByteArray(allData.toShortArray())
            WavWriter.writeBytesToWav(byteArray, wavFile, sampleRate, 1, 16)
            pcmFile.delete()
            wavFile
        } catch (e: Exception) {
            pcmFile.delete()
            null
        }
    }

    suspend fun recordWithAmplitudeToFile(
        onAmplitude: (Float) -> Unit,
        shouldStop: () -> Boolean
    ): File? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val sampleRate = Constants.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        val wavFile = File.createTempFile("voice_${System.currentTimeMillis()}", ".wav", context.cacheDir)

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            recorder.startRecording()
            val buffer = ShortArray(bufferSize / 2)
            val allData = mutableListOf<Short>()

            while (!shouldStop()) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) allData.add(buffer[i])
                    val amp = calculateAmplitude(buffer, read)
                    onAmplitude(amp)
                }
            }

            recorder.stop()
            recorder.release()

            if (allData.isEmpty()) {
                wavFile.delete()
                return@withContext null
            }

            val byteArray = WavWriter.pcmShortArrayToByteArray(allData.toShortArray())
            WavWriter.writeBytesToWav(byteArray, wavFile, sampleRate, 1, 16)
            wavFile
        } catch (e: Exception) {
            wavFile.delete()
            null
        }
    }

    private fun calculateAmplitude(buffer: ShortArray, read: Int): Float {
        var sum = 0.0
        for (i in 0 until read) {
            sum += buffer[i] * buffer[i]
        }
        val rms = sqrt(sum / read)
        // Normalize to 0..1 (16-bit max 32768)
        return (rms / 10000.0).coerceIn(0.0, 1.0).toFloat()
    }
}

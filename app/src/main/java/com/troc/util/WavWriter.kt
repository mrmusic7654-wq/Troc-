package com.troc.util

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    fun writePcmToWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int = Constants.SAMPLE_RATE,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ) {
        val pcmData = pcmFile.readBytes()
        writeBytesToWav(pcmData, wavFile, sampleRate, channels, bitsPerSample)
    }

    fun writeBytesToWav(
        pcmBytes: ByteArray,
        wavFile: File,
        sampleRate: Int = Constants.SAMPLE_RATE,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmBytes.size
        val totalSize = 36 + dataSize

        FileOutputStream(wavFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size
            header.putShort(1) // PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)
            fos.write(header.array())
            fos.write(pcmBytes)
        }
    }

    fun pcmShortArrayToByteArray(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        shorts.forEach { buffer.putShort(it) }
        return bytes
    }
}

package com.troc.util

import android.content.Context
import android.net.Uri
import java.io.File

object FileUtils {
    fun getFileSizeMb(file: File): Double = file.length() / (1024.0 * 1024.0)

    fun isAllowedExtension(name: String): Boolean {
        val allowed = listOf("csv", "json", "txt", "md", "py", "js", "kt")
        return allowed.any { name.lowercase().endsWith(".$it") }
    }

    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    fun saveToCache(context: Context, fileName: String, content: String): File {
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        return file
    }

    fun createTempWavFile(context: Context): File {
        return File.createTempFile("rec_${System.currentTimeMillis()}", ".wav", context.cacheDir)
    }
}

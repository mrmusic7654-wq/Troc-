package com.example.troc.util

object FileUtils {

    fun humanBytes(bytes: Long): String = when {
        bytes < 0 -> "unknown size"
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }

    fun truncate(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "…[truncated]"
}

package com.example.troc.data.sandbox.security

import com.example.troc.domain.model.SandboxLanguage
import com.example.troc.domain.model.SandboxTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central place for sandbox resource limits and input validation.
 *
 * The Kotlin sandbox is a hand-written interpreter with no reflection or I/O,
 * and the JavaScript sandbox runs inside Rhino with a ClassShutter that denies
 * every Java class — the blacklist below is defense-in-depth that also gives
 * users a clear error message instead of a confusing interpreter failure.
 */
@Singleton
class SandboxSecurityManager @Inject constructor() {

    val maxInputChars: Int = 300_000
    val maxFileSizeBytes: Long = 10L * 1024 * 1024
    val maxExtractedZipBytes: Long = 50L * 1024 * 1024
    val maxZipEntries: Int = 500

    fun clampTimeoutMs(timeoutMs: Long?): Long =
        (timeoutMs ?: DEFAULT_TIMEOUT_MS).coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

    /** Returns null when the input is acceptable, otherwise a user-facing reason. */
    fun validateInput(input: String?): String? {
        if (input.isNullOrBlank()) return "Nothing to execute — the input is empty."
        if (input.length > maxInputChars) {
            return "Input too large (${input.length} characters, max $maxInputChars)."
        }
        return null
    }

    /** Defense-in-depth scan for dangerous APIs (the interpreter cannot reach these anyway). */
    fun checkKotlinCode(code: String): String? {
        for (pattern in KOTLIN_BLACKLIST) {
            if (pattern.containsMatchIn(code)) {
                return "Forbidden API detected: “${pattern.pattern}”. " +
                    "The Kotlin sandbox is a safe subset — no JVM classes, file or network access."
            }
        }
        return null
    }

    fun validateFileSize(bytes: Long): String? {
        if (bytes > maxFileSizeBytes) {
            return "File too large (${humanBytes(bytes)}), max ${humanBytes(maxFileSizeBytes)}."
        }
        return null
    }

    fun humanBytes(bytes: Long): String = when {
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }

    companion object {
        const val MIN_TIMEOUT_MS = 5_000L
        const val MAX_TIMEOUT_MS = 120_000L
        const val DEFAULT_TIMEOUT_MS = 30_000L

        private val KOTLIN_BLACKLIST = listOf(
            Regex("""\bRuntime\b"""),
            Regex("""\bProcessBuilder\b"""),
            Regex("""\bSystem\s*\."""),
            Regex("""\bjava\.(io|net|lang|util)\b"""),
            Regex("""\bClass\s*\.?\s*forName"""),
            Regex("""\breflect\w*\b"""),
            Regex("""\bThread\s*\("""),
            Regex("""\bClassLoader\b"""),
            Regex("""\bsun\.\w+"""),
            Regex("""\bexec\s*\(""")
        )
    }
}

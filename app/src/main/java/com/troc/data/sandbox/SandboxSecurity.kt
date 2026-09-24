package com.troc.data.sandbox

object SandboxSecurity {
    private val blockedKeywords = listOf(
        "ProcessBuilder",
        "Runtime.getRuntime",
        "exec(",
        "System.exit",
        "java.io.File",
        "java.nio.file",
        "java.net",
        "Socket",
        "URL",
        "HttpClient",
        "okhttp",
        "retrofit",
        "import os",
        "import sys",
        "subprocess",
        "__import__",
        "eval(",
        "open(" // for python we allow controlled? but block arbitrary file
    )

    // More permissive: we only block network-related
    private val strictBlocked = listOf(
        "ProcessBuilder",
        "Runtime.getRuntime().exec",
        "java.lang.Runtime",
        "java.net.Socket",
        "java.net.URL",
        "java.net.HttpURLConnection"
    )

    fun isCodeAllowed(code: String, strict: Boolean = false): Boolean {
        val blockList = if (strict) strictBlocked else blockedKeywords.filter { it.contains("ProcessBuilder") || it.contains("Runtime") || it.contains("Socket") || it.contains("URL") || it.contains("HttpClient") }
        return blockList.none { code.contains(it, ignoreCase = false) }
    }

    fun sanitizePython(code: String): String {
        // Remove potentially dangerous imports, but allow pandas/numpy
        val dangerous = listOf("subprocess", "socket", "requests", "urllib")
        var sanitized = code
        dangerous.forEach { lib ->
            if (sanitized.contains("import $lib")) {
                sanitized = sanitized.replace("import $lib", "# blocked import $lib")
            }
        }
        return sanitized
    }
}

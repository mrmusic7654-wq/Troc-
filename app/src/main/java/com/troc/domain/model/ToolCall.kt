package com.troc.domain.model

import java.util.UUID

enum class ToolType { CODE, DATA, FILE, CUSTOM }

data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val tool: ToolType,
    val language: String? = null, // python, kotlin, js
    val input: String,
    val output: String? = null,
    val isRunning: Boolean = false,
    val executionTimeMs: Long? = null,
    val error: String? = null
)

data class ParsedToolCall(
    val tool: String,
    val language: String?,
    val input: String
)

object ToolParser {
    private val jsonRegex = Regex("""\{[^}]*"tool"\s*:\s*"(code|data|file|web_search)"[^}]*\}""", RegexOption.DOT_MATCHES_ALL)
    private val blockRegex = Regex("""```json\s*(\{.*?"tool".*?\})\s*```""", RegexOption.DOT_MATCHES_ALL)

    fun extractToolCalls(text: String): List<ParsedToolCall> {
        val results = mutableListOf<ParsedToolCall>()
        // Look for ```json { "tool": ... } ```
        blockRegex.findAll(text).forEach { match ->
            parseSingle(match.groupValues[1])?.let { results.add(it) }
        }
        // Fallback: raw JSON objects
        if (results.isEmpty()) {
            jsonRegex.findAll(text).forEach { match ->
                parseSingle(match.value)?.let { results.add(it) }
            }
        }
        return results
    }

    private fun parseSingle(jsonStr: String): ParsedToolCall? {
        return try {
            // very lenient manual parse to avoid heavy dependency in parser
            val tool = Regex(""""tool"\s*:\s*"([^"]+)"""").find(jsonStr)?.groupValues?.get(1) ?: return null
            val language = Regex(""""language"\s*:\s*"([^"]+)"""").find(jsonStr)?.groupValues?.get(1)
            val inputMatch = Regex(""""input"\s*:\s*"((?:\\.|[^"\\])*)"""", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
            val rawInput = inputMatch?.groupValues?.get(1) ?: ""
            val input = rawInput
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
            ParsedToolCall(tool, language, input)
        } catch (e: Exception) {
            null
        }
    }
}

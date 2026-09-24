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
    // More robust patterns for automatic agent mode
    private val jsonRegex = Regex("""\{[^}]*"tool"\s*:\s*"(code|data|file|web_search|search)"[^}]*\}""", RegexOption.DOT_MATCHES_ALL)
    private val blockRegex = Regex("""```(?:json)?\s*(\{.*?"tool".*?\})\s*```""", RegexOption.DOT_MATCHES_ALL)
    private val pythonCodeBlock = Regex("""```python\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
    private val jsCodeBlock = Regex("""```(?:javascript|js)\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)

    fun extractToolCalls(text: String): List<ParsedToolCall> {
        val results = mutableListOf<ParsedToolCall>()
        
        // 1. Look for ```json { "tool": ... } ``` or ``` { "tool": ... } ```
        blockRegex.findAll(text).forEach { match ->
            parseSingle(match.groupValues[1])?.let { 
                if (results.none { existing -> existing.input == it.input }) {
                    results.add(it) 
                }
            }
        }
        
        // 2. Fallback: raw JSON objects with tool
        if (results.isEmpty()) {
            jsonRegex.findAll(text).forEach { match ->
                parseSingle(match.value)?.let { 
                    if (results.none { existing -> existing.input == it.input }) {
                        results.add(it) 
                    }
                }
            }
        }

        // 3. Auto-detect Python code blocks as code tool if agent mode and no explicit tool found
        // This makes agent more automatic - if AI writes Python code, we auto-execute it
        if (results.isEmpty() && text.contains("```python")) {
            pythonCodeBlock.findAll(text).forEach { match ->
                val code = match.groupValues[1].trim()
                if (code.length > 10 && !code.contains("\"tool\"")) {
                    results.add(ParsedToolCall("code", "python", code))
                }
            }
        }

        // 4. Auto-detect JS code blocks
        if (results.isEmpty() && text.contains("```javascript") || text.contains("```js")) {
            jsCodeBlock.findAll(text).forEach { match ->
                val code = match.groupValues[1].trim()
                if (code.length > 10 && !code.contains("\"tool\"")) {
                    results.add(ParsedToolCall("code", "javascript", code))
                }
            }
        }

        return results
    }

    fun hasToolCall(text: String): Boolean {
        return text.contains("\"tool\"") && 
               (text.contains("code") || text.contains("data") || text.contains("file") || text.contains("web_search"))
    }

    private fun parseSingle(jsonStr: String): ParsedToolCall? {
        return try {
            // Lenient manual parse
            val toolRaw = Regex(""""tool"\s*:\s*"([^"]+)"""").find(jsonStr)?.groupValues?.get(1) ?: return null
            // Normalize tool names
            val tool = when (toolRaw.lowercase()) {
                "search", "websearch", "web_search" -> "web_search"
                "python", "py" -> "code"
                "javascript", "js" -> "code"
                else -> toolRaw
            }
            val language = Regex(""""language"\s*:\s*"([^"]+)"""").find(jsonStr)?.groupValues?.get(1) ?: 
                          if (tool == "code" && (toolRaw == "python" || toolRaw == "py")) "python" else null
            
            // Handle input which may be multi-line and escaped
            val inputMatch = Regex(""""input"\s*:\s*"((?:\\.|[^"\\])*)"""", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
            var rawInput = inputMatch?.groupValues?.get(1) ?: ""
            
            // If input not found with quoted string, try unquoted or with different pattern
            if (rawInput.isEmpty()) {
                val altMatch = Regex(""""input"\s*:\s*([^,}]+)""").find(jsonStr)
                rawInput = altMatch?.groupValues?.get(1)?.trim()?.trim('"') ?: ""
            }
            
            val input = rawInput
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\\", "\\")
                .trim()
            
            if (input.isEmpty()) return null
            ParsedToolCall(tool, language, input)
        } catch (e: Exception) {
            null
        }
    }
}

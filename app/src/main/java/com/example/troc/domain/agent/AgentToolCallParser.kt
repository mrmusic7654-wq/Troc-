package com.example.troc.domain.agent

import java.util.Locale

/**
 * Tool calls that the agent model can emit inline in its reply:
 *
 * ```xml
 * <run_code language="javascript">1 + 1</run_code>
 * <analyze_data format="csv">column,value\n...</analyze_data>
 * <process_file action="read_text">summarize the attached file</process_file>
 * ```
 */
sealed class AgentToolCall {
    abstract val label: String

    data class RunCode(val language: String, val code: String) : AgentToolCall() {
        override val label: String get() = "run_code (${language.lowercase(Locale.US)})"
    }

    data class AnalyzeData(val format: String, val content: String) : AgentToolCall() {
        override val label: String get() = "analyze_data (${format.lowercase(Locale.US)})"
    }

    data class ProcessFile(val action: String, val argument: String) : AgentToolCall() {
        override val label: String get() = "process_file (${action.lowercase(Locale.US)})"
    }
}

class AgentToolCallParser {

    fun parse(text: String): List<AgentToolCall> {
        val calls = mutableListOf<AgentToolCall>()
        RUN_CODE.find(text)?.let { m ->
            calls += AgentToolCall.RunCode(
                language = m.groupValues[1].ifBlank { "javascript" },
                code = m.groupValues[2].trim()
            )
        }
        ANALYZE_DATA.findAll(text).forEach { m ->
            calls += AgentToolCall.AnalyzeData(
                format = m.groupValues[1].ifBlank { "csv" },
                content = m.groupValues[2].trim()
            )
        }
        PROCESS_FILE.findAll(text).forEach { m ->
            calls += AgentToolCall.ProcessFile(
                action = m.groupValues[1].ifBlank { "read_text" },
                argument = m.groupValues[2].trim()
            )
        }
        return calls
    }

    /** Replaces raw tool tags with friendly one-liners so streaming text stays readable. */
    fun summarizeForDisplay(text: String): String {
        var result = RUN_CODE.replace(text) { m ->
            "\n🛠️ Running code (${m.groupValues[1]})…\n"
        }
        result = ANALYZE_DATA.replace(result) { "\n📊 Analyzing data…\n" }
        result = PROCESS_FILE.replace(result) { m -> "\n📁 Processing file (${m.groupValues[1]})…\n" }
        return result
    }

    /** Removes tool tags entirely (used when persisting "final answer" previews). */
    fun stripToolTags(text: String): String =
        RUN_CODE.replace(text, "").let { ANALYZE_DATA.replace(it, "") }.let { PROCESS_FILE.replace(it, "") }

    private companion object {
        private val RUN_CODE = Regex(
            """<run_code\b[^>]*language\s*=\s*["']?([\w+#-]+)["']?[^>]*>([\s\S]*?)</run_code>""",
            RegexOption.IGNORE_CASE
        )
        private val ANALYZE_DATA = Regex(
            """<analyze_data\b[^>]*(?:format\s*=\s*["']?(\w+)["']?)?[^>]*>([\s\S]*?)</analyze_data>""",
            RegexOption.IGNORE_CASE
        )
        private val PROCESS_FILE = Regex(
            """<process_file\b[^>]*action\s*=\s*["']?(\w+)["']?[^>]*>([\s\S]*?)</process_file>""",
            RegexOption.IGNORE_CASE
        )
    }
}

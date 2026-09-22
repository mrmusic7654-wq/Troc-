package com.example.troc.domain.model

import java.util.UUID

enum class SandboxTool(val label: String, val emoji: String) {
    CODE("Code", "\uD83D\uDCBB"),
    DATA("Data", "\uD83D\uDCCA"),
    FILE("Files", "\uD83D\uDCC1"),
    CUSTOM("Workflow", "\u2699\uFE0F")
}

enum class SandboxLanguage(val label: String) {
    KOTLIN("Kotlin"),
    JAVASCRIPT("JavaScript"),
    PYTHON("Python")
}

enum class FileAction { READ_TEXT, METADATA, LIST_ZIP, EXTRACT_ZIP, REGEX_REPLACE }

/** Result payload that the UI can render as a table / chart / file card. */
data class SandboxPayload(
    val table: SandboxTable? = null,
    val chart: ChartSeries? = null,
    val fileMeta: FileMeta? = null
)

data class SandboxTable(
    val columns: List<String>,
    val rows: List<List<String>>
)

data class ChartSeries(
    val title: String,
    val points: List<ChartPoint>
)

data class ChartPoint(val label: String, val value: Double)

data class FileMeta(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val extra: Map<String, String> = emptyMap(),
    val preview: String? = null
)

sealed class SandboxResult {
    abstract val durationMs: Long

    data class Success(
        val output: String,
        override val durationMs: Long,
        val payload: SandboxPayload? = null
    ) : SandboxResult()

    data class Failure(
        val message: String,
        override val durationMs: Long = 0
    ) : SandboxResult()

    val isSuccessful: Boolean get() = this is Success

    fun text(): String = when (this) {
        is Success -> output
        is Failure -> message
    }

    val payloadOrNull: SandboxPayload? get() = (this as? Success)?.payload
}

data class WorkflowStep(
    val id: String = UUID.randomUUID().toString(),
    val tool: SandboxTool = SandboxTool.CODE,
    val language: SandboxLanguage = SandboxLanguage.JAVASCRIPT,
    val input: String = ""
)

data class SandboxRequest(
    val tool: SandboxTool,
    val language: SandboxLanguage? = null,
    val code: String? = null,
    val data: String? = null,
    val dataFormat: String = "csv",
    /** Data-sandbox operation: summarize | filter | sort | chart | head | select | tocsv | pretty | query */
    val operation: String = "summarize",
    val column: String? = null,
    val params: Map<String, String> = emptyMap(),
    val fileAction: FileAction = FileAction.READ_TEXT,
    val fileName: String? = null,
    val uri: String? = null,
    val regexPattern: String? = null,
    val regexReplacement: String = "",
    val steps: List<WorkflowStep> = emptyList(),
    val timeoutMs: Long? = null
)

sealed class StreamEvent {
    data class Token(val text: String) : StreamEvent()
    data object Done : StreamEvent()
    data class Error(val message: String, val code: Int? = null) : StreamEvent()
}

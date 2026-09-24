package com.troc.domain.model

sealed class SandboxResult {
    data class Success(
        val output: String,
        val executionTimeMs: Long,
        val logs: String = ""
    ) : SandboxResult()

    data class Error(
        val message: String,
        val stackTrace: String? = null,
        val executionTimeMs: Long = 0
    ) : SandboxResult()

    object Timeout : SandboxResult()
}

data class DataStats(
    val rows: Int,
    val columns: Int,
    val columnNames: List<String>,
    val numericStats: Map<String, NumericColumnStats> = emptyMap()
)

data class NumericColumnStats(
    val mean: Double,
    val median: Double,
    val min: Double,
    val max: Double,
    val sum: Double
)

enum class ChartType { BAR, PIE, LINE }

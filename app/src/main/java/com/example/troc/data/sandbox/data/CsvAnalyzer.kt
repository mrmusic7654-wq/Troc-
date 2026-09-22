package com.example.troc.data.sandbox.data

import com.example.troc.domain.model.ChartPoint
import com.example.troc.domain.model.ChartSeries
import com.example.troc.domain.model.SandboxTable
import java.util.Locale

data class ColumnStats(
    val name: String,
    val type: String,
    val count: Int,
    val missing: Int,
    val sum: Double?,
    val mean: Double?,
    val median: Double?,
    val min: Double?,
    val max: Double?,
    val stdDev: Double?,
    val distinct: Int?,
    val topValue: String?
)

/**
 * Pure-Kotlin CSV analysis engine used by the Data sandbox: type inference,
 * descriptive statistics, filtering, sorting and chart series extraction.
 */
object CsvAnalyzer {

    data class Table(val header: List<String>, val rows: List<List<String>>)

    fun load(text: String, hasHeader: Boolean = true): Table {
        val raw = CsvParser.parse(text)
        if (raw.isEmpty()) return Table(emptyList(), emptyList())
        return if (hasHeader) {
            val header = raw.first().mapIndexed { i, h -> h.ifBlank { "column_${i + 1}" } }
            val rows = raw.drop(1).map { it.padTo(header.size) }
            Table(header, rows)
        } else {
            val width = raw.maxOfOrNull { it.size } ?: 0
            val header = (1..width).map { "column_$it" }
            Table(header, raw.map { it.padTo(width) })
        }
    }

    private fun List<String>.padTo(size: Int): List<String> =
        if (size <= this.size) this else this + List(size - this.size) { "" }

    fun inferTypes(table: Table): List<String> = table.header.mapIndexed { col, _ ->
        val values = table.rows.mapNotNull { it.getOrNull(col) }.filter { it.isNotBlank() }
        val numeric = values.isNotEmpty() && values.all { it.toDoubleOrNull() != null }
        if (numeric) "numeric" else "text"
    }

    fun columnStats(table: Table, types: List<String> = inferTypes(table)): List<ColumnStats> =
        table.header.mapIndexed { col, name ->
            val cells = table.rows.map { it.getOrNull(col).orEmpty() }
            val present = cells.filter { it.isNotBlank() }
            val missing = cells.size - present.size
            val distinct = present.distinct().size
            val top = present.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            if (types[col] == "numeric") {
                val nums = present.mapNotNull { it.toDoubleOrNull() }.sorted()
                ColumnStats(
                    name = name,
                    type = "numeric",
                    count = nums.size,
                    missing = missing,
                    sum = nums.sum(),
                    mean = if (nums.isNotEmpty()) nums.sum() / nums.size else null,
                    median = median(nums),
                    min = nums.firstOrNull(),
                    max = nums.lastOrNull(),
                    stdDev = stdDev(nums),
                    distinct = distinct,
                    topValue = top
                )
            } else {
                ColumnStats(
                    name = name,
                    type = "text",
                    count = present.size,
                    missing = missing,
                    sum = null, mean = null, median = null, min = null, max = null, stdDev = null,
                    distinct = distinct,
                    topValue = top
                )
            }
        }

    fun median(sorted: List<Double>): Double? {
        if (sorted.isEmpty()) return null
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    fun stdDev(values: List<Double>): Double? {
        if (values.size < 2) return null
        val mean = values.sum() / values.size
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return kotlin.math.sqrt(variance)
    }

    // ------------------------------------------------------------------ operations

    fun columnIndexOf(table: Table, column: String?): Int {
        if (table.header.isEmpty()) return -1
        if (column.isNullOrBlank()) return 0
        return table.header.indexOfFirst { it.equals(column.trim(), ignoreCase = true) }
    }

    fun filter(table: Table, column: String, op: String, rawValue: String): Table {
        val idx = resolveColumn(table, column)
        val numericValue = rawValue.toDoubleOrNull()
        val rows = table.rows.filter { row ->
            val cell = row.getOrNull(idx).orEmpty()
            when (op) {
                "=", "==" -> if (numericValue != null && cell.toDoubleOrNull() != null) cell.toDouble() == numericValue else cell.equals(rawValue, ignoreCase = true)
                "!=" -> if (numericValue != null && cell.toDoubleOrNull() != null) cell.toDouble() != numericValue else !cell.equals(rawValue, ignoreCase = true)
                ">" -> (cell.toDoubleOrNull() ?: return@filter false) > (numericValue ?: return@filter false)
                "<" -> (cell.toDoubleOrNull() ?: return@filter false) < (numericValue ?: return@filter false)
                ">=" -> (cell.toDoubleOrNull() ?: return@filter false) >= (numericValue ?: return@filter false)
                "<=" -> (cell.toDoubleOrNull() ?: return@filter false) <= (numericValue ?: return@filter false)
                "contains" -> cell.contains(rawValue, ignoreCase = true)
                else -> throw IllegalArgumentException("Unknown filter operator '$op' (use =, !=, >, <, >=, <=, contains)")
            }
        }
        return table.copy(rows = rows)
    }

    fun sort(table: Table, column: String, descending: Boolean): Table {
        val idx = resolveColumn(table, column)
        val rows = table.rows.sortedWith(
            compareBy({ it.getOrNull(idx).orEmpty().toDoubleOrNull() == null }, {
                val d = it.getOrNull(idx).orEmpty().toDoubleOrNull()
                if (d != null) d else Double.MIN_VALUE
            }, { it.getOrNull(idx).orEmpty() })
        )
        return table.copy(rows = if (descending) rows.asReversed() else rows)
    }

    fun select(table: Table, columns: List<String>): Table {
        val idxs = columns.map { resolveColumn(table, it) }
        return Table(
            columns,
            table.rows.map { row -> idxs.map { row.getOrNull(it).orEmpty() } }
        )
    }

    fun chartSeries(table: Table, column: String, topN: Int = 10): ChartSeries {
        val idx = resolveColumn(table, column)
        val numeric = table.rows.firstNotNullOfOrNull { it.getOrNull(idx)?.toDoubleOrNull() } != null
        val points = if (numeric) {
            table.rows.mapIndexedNotNull { i, row ->
                val v = row.getOrNull(idx)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
                ChartPoint(row.firstOrNull() ?: "row ${i + 1}", v)
            }
        } else {
            table.rows.mapNotNull { it.getOrNull(idx) }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .map { ChartPoint(it.key, it.value.toDouble()) }
        }
        val limited = points.sortedByDescending { it.value }.take(topN)
        return ChartSeries(title = table.header.getOrNull(idx) ?: column, points = limited)
    }

    fun summarizeReport(table: Table, types: List<String>, stats: List<ColumnStats>): String {
        if (table.header.isEmpty()) return "The table is empty."
        val sb = StringBuilder()
        sb.appendLine("**Dataset:** ${table.rows.size} rows × ${table.header.size} columns")
        sb.appendLine()
        sb.appendLine("| column | type | count | missing | mean | median | min | max | stddev | distinct |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|---|")
        stats.forEach { s ->
            fun d(v: Double?) = v?.let { trimDouble(it) } ?: "—"
            sb.appendLine(
                "| ${s.name} | ${s.type} | ${s.count} | ${s.missing} | " +
                    if (s.type == "numeric") {
                        "${d(s.mean)} | ${d(s.median)} | ${d(s.min)} | ${d(s.max)} | ${d(s.stdDev)} | ${s.distinct ?: 0} |"
                    } else {
                        "— | — | — | — | — | ${s.distinct ?: 0} |"
                    }
            )
        }
        return sb.toString().trimEnd()
    }

    fun preview(table: Table, maxRows: Int = 100): SandboxTable = SandboxTable(
        columns = table.header,
        rows = table.rows.take(maxRows).map { row -> row.map { it.take(60) } }
    )

    fun toCsv(table: Table): String {
        fun escape(s: String) = if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
        return buildString {
            appendLine(table.header.joinToString(",") { escape(it) })
            table.rows.forEach { appendLine(it.joinToString(",") { c -> escape(c) }) }
        }.trimEnd()
    }

    fun trimDouble(d: Double): String {
        if (d.isNaN()) return "NaN"
        return if (d == d.toLong().toDouble() && kotlin.math.abs(d) < 1e15) {
            String.format(Locale.US, "%.1f", d)
        } else {
            String.format(Locale.US, "%.4f", d).trimEnd('0').trimEnd('.')
        }
    }

    private fun resolveColumn(table: Table, column: String): Int {
        val idx = table.header.indexOfFirst { it.equals(column.trim(), ignoreCase = true) }
        if (idx < 0) {
            throw IllegalArgumentException("Column '${column.trim()}' not found. Available: ${table.header.joinToString(", ")}")
        }
        return idx
    }
}

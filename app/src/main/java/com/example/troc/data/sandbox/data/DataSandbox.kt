package com.example.troc.data.sandbox.data

import com.example.troc.data.sandbox.security.SandboxSecurityManager
import com.example.troc.domain.model.SandboxPayload
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.SandboxTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Data sandbox: parse, summarize, filter, sort, chart and convert CSV/JSON.
 * All operations are pure computation with hard input-size limits.
 */
@Singleton
class DataSandbox @Inject constructor(
    private val security: SandboxSecurityManager
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(
        data: String?,
        dataFormat: String,
        operation: String,
        column: String?,
        params: Map<String, String>
    ): SandboxResult {
        security.validateInput(data)?.let { return SandboxResult.Failure(it) }
        val input = data.orEmpty()
        val start = System.currentTimeMillis()
        return withContext(Dispatchers.Default) {
            try {
                if (dataFormat.equals("json", ignoreCase = true)) {
                    jsonOperation(input, operation, params)
                } else {
                    csvOperation(input, operation, column, params)
                }
            } catch (e: IllegalArgumentException) {
                SandboxResult.Failure(e.message ?: "Invalid data", System.currentTimeMillis() - start)
            } catch (e: Exception) {
                SandboxResult.Failure("Analysis failed: ${e.message}", System.currentTimeMillis() - start)
            }
        }
    }

    // ------------------------------------------------------------------ CSV

    private fun csvOperation(
        input: String,
        operation: String,
        column: String?,
        params: Map<String, String>
    ): SandboxResult {
        val hasHeader = params["header"]?.lowercase() != "false"
        val table = CsvAnalyzer.load(input, hasHeader)
        if (table.header.isEmpty()) return SandboxResult.Failure("No data rows found.")
        val types = CsvAnalyzer.inferTypes(table)
        val stats = CsvAnalyzer.columnStats(table, types)

        return when (operation.lowercase()) {
            "summarize", "stats", "analyze" -> SandboxResult.Success(
                output = CsvAnalyzer.summarizeReport(table, types, stats),
                durationMs = 0,
                payload = SandboxPayload(table = CsvAnalyzer.preview(table))
            )
            "filter" -> {
                val op = params["op"] ?: "="
                val value = params["value"] ?: ""
                val filtered = CsvAnalyzer.filter(table, column ?: table.header.first(), op, value)
                SandboxResult.Success(
                    output = "Filtered: ${filtered.rows.size} of ${table.rows.size} rows match " +
                        "${column} $op $value.\n\n```csv\n${CsvAnalyzer.toCsv(filtered).take(4_000)}\n```",
                    durationMs = 0,
                    payload = SandboxPayload(table = CsvAnalyzer.preview(filtered))
                )
            }
            "sort" -> {
                val descending = params["desc"]?.lowercase() == "true"
                val sorted = CsvAnalyzer.sort(table, column ?: table.header.first(), descending)
                SandboxResult.Success(
                    output = "Sorted by ${column}${if (descending) " (descending)" else ""}.",
                    durationMs = 0,
                    payload = SandboxPayload(table = CsvAnalyzer.preview(sorted))
                )
            }
            "chart", "plot" -> {
                val series = CsvAnalyzer.chartSeries(table, column ?: table.header.first())
                if (series.points.isEmpty()) {
                    SandboxResult.Failure("Nothing to chart in column '$column'.")
                } else {
                    SandboxResult.Success(
                        output = "Chart of \"${series.title}\" (top ${series.points.size}).",
                        durationMs = 0,
                        payload = SandboxPayload(chart = series)
                    )
                }
            }
            "head" -> {
                val n = params["n"]?.toIntOrNull() ?: 10
                SandboxResult.Success(
                    output = "First $n rows.",
                    durationMs = 0,
                    payload = SandboxPayload(table = SandboxTable(table.header, table.rows.take(n)))
                )
            }
            "select" -> {
                val columns = (params["columns"] ?: column ?: table.header.first())
                    .split(',')
                val selected = CsvAnalyzer.select(table, columns)
                SandboxResult.Success(
                    output = "Selected ${selected.header.size} columns.",
                    durationMs = 0,
                    payload = SandboxPayload(table = CsvAnalyzer.preview(selected))
                )
            }
            "tocsv" -> SandboxResult.Success(CsvAnalyzer.toCsv(table), 0)
            "tojson" -> {
                val csvText = CsvAnalyzer.toCsv(table)
                SandboxResult.Success(csvToJson(csvText), 0)
            }
            else -> SandboxResult.Failure(
                "Unknown operation '$operation'. Available: summarize, filter, sort, chart, head, select, tocsv, tojson."
            )
        }
    }

    /** Converts normalized CSV text into a JSON array string. */
    private fun csvToJson(csvText: String): String {
        val rows = CsvParser.parse(csvText)
        if (rows.size < 2) return "[]"
        val header = rows.first()
        val items = rows.drop(1).map { row ->
            header.mapIndexed { i, h -> "\"${jsonEscape(h)}\": \"${jsonEscape(row.getOrNull(i).orEmpty())}\"" }
        }
        return "[\n" + items.joinToString(",\n") { "  {${it.joinToString(", ")}}" } + "\n]"
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")

    // ------------------------------------------------------------------ JSON

    private fun jsonOperation(
        input: String,
        operation: String,
        params: Map<String, String>
    ): SandboxResult {
        val element: JsonElement = runCatching { json.parseToJsonElement(input) }
            .getOrElse { return SandboxResult.Failure("Invalid JSON: ${it.message}") }

        return when (operation.lowercase()) {
            "pretty", "format" -> SandboxResult.Success(pretty(element), 0)
            "summarize", "stats" -> SandboxResult.Success(JsonAnalyzer.summarize(element), 0)
            "query", "extract" -> {
                val path = params["path"].orEmpty().ifBlank {
                    return SandboxResult.Failure("Provide a path, e.g. params[\"path\"] = \"users[].name\"")
                }
                runCatching { JsonAnalyzer.query(element, path) }
                    .fold(
                        onSuccess = { matches ->
                            SandboxResult.Success(
                                "${matches.size} match(es) for `$path`:\n\n" +
                                    matches.take(200).joinToString("\n") { "- `$it`" },
                                0
                            )
                        },
                        onFailure = { SandboxResult.Failure(it.message ?: "Query failed", 0) }
                    )
            }
            "tocsv" -> runCatching { JsonAnalyzer.toCsv(element) }
                .fold(
                    onSuccess = { SandboxResult.Success(it, 0) },
                    onFailure = { SandboxResult.Failure(it.message ?: "Conversion failed", 0) }
                )
            else -> SandboxResult.Success(pretty(element), 0)
        }
    }

    private fun pretty(element: JsonElement): String {
        val prettyJson = Json { prettyPrint = true }
        return prettyJson.encodeToString(JsonElement.serializer(), element).take(100_000)
    }
}

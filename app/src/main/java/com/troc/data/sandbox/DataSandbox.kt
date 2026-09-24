package com.troc.data.sandbox

import com.opencsv.CSVReaderBuilder
import com.troc.domain.model.DataStats
import com.troc.domain.model.NumericColumnStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class DataSandbox @Inject constructor() {

    suspend fun analyzeCsv(csvContent: String): DataStats = withContext(Dispatchers.Default) {
        val reader = CSVReaderBuilder(StringReader(csvContent)).build()
        val allRows = reader.readAll()
        if (allRows.isEmpty()) {
            return@withContext DataStats(0, 0, emptyList())
        }
        val header = allRows.first()
        val dataRows = allRows.drop(1)
        val columnNames = header.toList()

        // Compute numeric stats
        val numericStats = mutableMapOf<String, NumericColumnStats>()
        for (colIdx in header.indices) {
            val colName = header[colIdx]
            val values = dataRows.mapNotNull { row ->
                if (colIdx < row.size) row[colIdx].toDoubleOrNull() else null
            }
            if (values.size > dataRows.size * 0.5) { // at least 50% numeric
                numericStats[colName] = computeStats(values)
            }
        }

        DataStats(
            rows = dataRows.size,
            columns = header.size,
            columnNames = columnNames,
            numericStats = numericStats
        )
    }

    private fun computeStats(values: List<Double>): NumericColumnStats {
        val sorted = values.sorted()
        val sum = values.sum()
        val mean = sum / values.size
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2]
        }
        return NumericColumnStats(
            mean = mean,
            median = median,
            min = sorted.first(),
            max = sorted.last(),
            sum = sum
        )
    }

    suspend fun filterCsv(csvContent: String, column: String, predicate: (String) -> Boolean): String = withContext(Dispatchers.Default) {
        val reader = CSVReaderBuilder(StringReader(csvContent)).build()
        val allRows = reader.readAll()
        if (allRows.isEmpty()) return@withContext csvContent
        val header = allRows.first()
        val colIdx = header.indexOf(column)
        if (colIdx == -1) return@withContext csvContent
        val filtered = allRows.filterIndexed { idx, row ->
            idx == 0 || (colIdx < row.size && predicate(row[colIdx]))
        }
        // Convert back to CSV string
        filtered.joinToString("\n") { it.joinToString(",") }
    }

    suspend fun parseJson(jsonContent: String): Map<String, Any> {
        // Simple JSON parsing via Gson or kotlinx serialization would be used, but for sandbox we return basic
        return mapOf("raw" to jsonContent, "length" to jsonContent.length)
    }
}

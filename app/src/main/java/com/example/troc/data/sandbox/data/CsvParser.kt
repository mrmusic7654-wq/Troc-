package com.example.troc.data.sandbox.data

/**
 * A small RFC-4180-compliant CSV parser (quotes, escaped quotes, embedded
 * commas/newlines) with no external dependencies.
 */
object CsvParser {

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val src = if (text.startsWith("﻿")) text.substring(1) else text

        if (src.isBlank()) return rows

        fun endCell() {
            row.add(cell.toString())
            cell.setLength(0)
        }

        fun endRow() {
            endCell()
            // Skip trailing fully-empty rows produced by final newline
            if (row.size == 1 && row[0].isEmpty() && rows.isNotEmpty()) return
            rows.add(row.toList())
            row.clear()
        }

        while (i < src.length) {
            val c = src[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < src.length && src[i + 1] == '"' -> { cell.append('"'); i += 2 }
                    c == '"' -> { inQuotes = false; i++ }
                    else -> { cell.append(c); i++ }
                }
                c == '"' && cell.isEmpty() -> { inQuotes = true; i++ }
                c == ',' -> { endCell(); i++ }
                c == '\r' -> i++
                c == '\n' -> { endRow(); i++ }
                else -> { cell.append(c); i++ }
            }
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}

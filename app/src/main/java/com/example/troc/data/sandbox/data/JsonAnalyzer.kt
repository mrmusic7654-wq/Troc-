package com.example.troc.data.sandbox.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** JSON utilities for the Data sandbox: pretty-printing, path queries and CSV conversion. */
object JsonAnalyzer {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun parse(text: String): Result<JsonElement> = runCatching { json.parseToJsonElement(text) }

    fun prettyPrint(text: String): String {
        val element = parse(text).getOrElse { throw IllegalArgumentException("Invalid JSON: ${it.message}") }
        return json.encodeToString(JsonElement.serializer(), element)
    }

    /**
     * Simple path queries over a JSON tree:
     *  - `user.name`
     *  - `users[0].email`
     *  - `users[].name`  (map over every element of the array)
     *  - `*` at the start of a segment lists keys of the root object
     */
    fun query(root: JsonElement, path: String): List<String> {
        val segments = path.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return listOf(render(root))
        val results = mutableListOf<JsonElement>()

        fun walk(element: JsonElement, index: Int) {
            if (index == segments.size) {
                results += element
                return
            }
            val segment = segments[index]
            when {
                segment.endsWith("[]") -> {
                    val name = segment.removeSuffix("[]")
                    val target = if (name.isEmpty()) element else descend(element, name)
                    if (target is JsonArray) target.forEach { walk(it, index + 1) }
                }
                segment.contains('[') -> {
                    val name = segment.substringBefore('[')
                    val idx = segment.substringAfter('[').trimEnd(']').trim()
                    val target = if (name.isEmpty()) element else descend(element, name)
                    if (target is JsonArray) {
                        val i = idx.toIntOrNull() ?: throw IllegalArgumentException("Bad array index '$idx'")
                        target.getOrNull(i)?.let { walk(it, index + 1) }
                    }
                }
                else -> walk(descend(element, segment), index + 1)
            }
        }

        walk(root, 0)
        return results.map { render(it) }
    }

    private fun descend(element: JsonElement, name: String): JsonElement = when (element) {
        is JsonObject -> element[name] ?: throw IllegalArgumentException("Key '$name' not found")
        else -> throw IllegalArgumentException("Cannot descend into ${describe(element)} with '$name'")
    }

    /** Converts a JSON array of objects into CSV text (union of top-level keys). */
    fun toCsv(root: JsonElement): String {
        val array = root as? JsonArray
            ?: (root as? JsonObject)?.let { JsonArray(listOf(root)) }
            ?: throw IllegalArgumentException("CSV export needs a JSON array or object")
        val keys = LinkedHashSet<String>()
        val maps = array.map { item ->
            val obj = item as? JsonObject
                ?: throw IllegalArgumentException("Array elements must be JSON objects for CSV export")
            obj.keys.forEach(keys::add)
            obj
        }
        if (keys.isEmpty()) return ""
        fun escape(s: String) = if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
        return buildString {
            appendLine(keys.joinToString(",") { escape(it) })
            maps.forEach { obj ->
                appendLine(keys.joinToString(",") { k -> escape(render(obj[k] ?: JsonNull)) })
            }
        }.trimEnd()
    }

    fun summarize(root: JsonElement): String {
        fun type(e: JsonElement): String = when (e) {
            is JsonObject -> "object(${e.size} keys)"
            is JsonArray -> "array(${e.size} items)"
            is JsonNull -> "null"
            is JsonPrimitive -> if (e.isString) "string" else "number/boolean"
        }
        val sb = StringBuilder()
        sb.appendLine("**Type:** ${type(root)}")
        when (root) {
            is JsonObject -> {
                sb.appendLine("**Keys:** ${root.keys.joinToString(", ") { "`$it`" }}")
            }
            is JsonArray -> {
                val kinds = root.groupingBy { type(it) }.eachCount()
                sb.appendLine("**Element types:** ${kinds.entries.joinToString { "${it.key} × ${it.value}" }}")
                val firstObj = root.firstOrNull() as? JsonObject
                if (firstObj != null) sb.appendLine("**Item keys:** ${firstObj.keys.joinToString(", ") { "`$it`" }}")
            }
            else -> Unit
        }
        return sb.toString().trimEnd()
    }

    private fun render(e: JsonElement): String = when (e) {
        is JsonNull -> "null"
        is JsonPrimitive -> e.content
        is JsonArray -> e.joinToString(", ", "[", "]") { render(it) }
        is JsonObject -> e.entries.joinToString(", ", "{", "}") { "\"${it.key}\": ${render(it.value)}" }
    }

    private fun describe(e: JsonElement): String = when (e) {
        is JsonObject -> "an object"
        is JsonArray -> "an array"
        is JsonNull -> "null"
        is JsonPrimitive -> "a value"
    }
}

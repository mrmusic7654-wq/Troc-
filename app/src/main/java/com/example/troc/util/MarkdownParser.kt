package com.example.troc.util

/** Parsed inline content: plain text plus style spans over that text. */
data class MdInline(val text: String, val spans: List<MdSpan>)

enum class MdSpanType { BOLD, ITALIC, CODE, STRIKETHROUGH, LINK }

data class MdSpan(val type: MdSpanType, val start: Int, val end: Int, val url: String? = null)

sealed class MdBlock {
    data class Heading(val level: Int, val inline: MdInline) : MdBlock()
    data class Paragraph(val inline: MdInline) : MdBlock()
    data class CodeBlock(val language: String?, val code: String) : MdBlock()
    data class Quote(val inline: MdInline) : MdBlock()
    data class BulletList(val items: List<MdInline>) : MdBlock()
    data class OrderedList(val items: List<MdInline>) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    data object HorizontalRule : MdBlock()
}

/**
 * Lightweight GitHub-flavored Markdown parser: headings, paragraphs, fenced code
 * blocks, block quotes, lists, tables, horizontal rules and inline
 * bold/italic/code/strikethrough/links. Pure Kotlin, unit-testable.
 */
object MarkdownParser {

    private val HR = Regex("""^\s{0,3}([-*_])\s*(?:\1\s*){2,}$""")
    private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val ORDERED = Regex("""^\s{0,3}(\d{1,9})[.)]\s+(.*)$""")
    private val BULLET = Regex("""^\s{0,3}[-*+]\s+(.*)$""")
    private val TABLE_DIVIDER = Regex("""^\s{0,3}\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$""")

    fun parseBlocks(markdown: String): List<MdBlock> {
        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split('\n')
        val blocks = mutableListOf<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++
                line.trimStart().startsWith("```") -> {
                    val fence = line.trimStart()
                    val language = fence.removePrefix("```").trim().ifBlank { null }
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        code.appendLine(lines[i])
                        i++
                    }
                    i++ // consume closing fence (or EOF)
                    blocks += MdBlock.CodeBlock(language, code.toString().trimEnd('\n', ' '))
                }
                HEADING.containsMatchIn(line) -> {
                    val m = HEADING.find(line)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2]))
                    i++
                }
                HR.containsMatchIn(line) -> {
                    blocks += MdBlock.HorizontalRule
                    i++
                }
                line.trimStart().startsWith(">") -> {
                    val quote = StringBuilder()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quote.append(lines[i].trimStart().removePrefix(">").trim()).append(' ')
                        i++
                    }
                    blocks += MdBlock.Quote(parseInline(quote.toString().trim()))
                }
                BULLET.containsMatchIn(line) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size) {
                        val m = BULLET.matchEntire(lines[i])
                        if (m != null) {
                            items += m.groupValues[1]
                            i++
                        } else if (lines[i].isNotBlank() && items.isNotEmpty() && !isBlockStart(lines[i])) {
                            items[items.size - 1] += " " + lines[i].trim()
                            i++
                        } else break
                    }
                    blocks += MdBlock.BulletList(items.map { parseInline(it) })
                }
                ORDERED.containsMatchIn(line) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size) {
                        val m = ORDERED.matchEntire(lines[i])
                        if (m != null) {
                            items += m.groupValues[2]
                            i++
                        } else if (lines[i].isNotBlank() && items.isNotEmpty() && !isBlockStart(lines[i])) {
                            items[items.size - 1] += " " + lines[i].trim()
                            i++
                        } else break
                    }
                    blocks += MdBlock.OrderedList(items.map { parseInline(it) })
                }
                line.contains('|') && i + 1 < lines.size && TABLE_DIVIDER.matchEntire(lines[i + 1]) != null -> {
                    val header = splitRow(line)
                    i += 2
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        rows += splitRow(lines[i])
                        i++
                    }
                    blocks += MdBlock.Table(header, rows)
                }
                else -> {
                    val paragraph = StringBuilder()
                    while (i < lines.size && lines[i].isNotBlank() && !isBlockStart(lines[i])) {
                        paragraph.append(lines[i].trim()).append('\n')
                        i++
                    }
                    blocks += MdBlock.Paragraph(parseInline(paragraph.toString().trimEnd()))
                }
            }
        }
        return blocks
    }

    private fun isBlockStart(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("```") || t.startsWith("#") || t.startsWith(">") ||
            BULLET.containsMatchIn(line) || ORDERED.containsMatchIn(line) || HR.containsMatchIn(line)
    }

    private fun splitRow(line: String): List<String> {
        var row = line.trim()
        if (row.startsWith("|")) row = row.substring(1)
        if (row.endsWith("|")) row = row.substring(0, row.length - 1)
        return row.split(Regex("""(?<!\\)\|""")).map { it.trim().replace("\\|", "|") }
    }

    // ------------------------------------------------------------------ inline

    fun parseInline(text: String): MdInline {
        val out = StringBuilder()
        val spans = mutableListOf<MdSpan>()
        scanInline(text, 0, text.length, out, spans)
        return MdInline(out.toString(), spans)
    }

    private fun scanInline(src: String, from: Int, to: Int, out: StringBuilder, spans: MutableList<MdSpan>) {
        var i = from
        while (i < to) {
            val c = src[i]
            when {
                c == '\\' && i + 1 < to -> {
                    out.append(src[i + 1])
                    i += 2
                }
                c == '`' -> {
                    val close = src.indexOf('`', i + 1)
                    if (close == -1 || close >= to) {
                        out.append(c); i++
                    } else {
                        addSpan(spans, out, MdSpanType.CODE) { out.append(src, i + 1, close) }
                        i = close + 1
                    }
                }
                src.startsWith("**", i) -> {
                    val close = src.indexOf("**", i + 2)
                    if (close == -1 || close >= to) {
                        out.append(c); i++
                    } else {
                        scanInline(src, i + 2, close, out, spans)
                        mark(spans, out, MdSpanType.BOLD, out.length - (close - i - 2))
                        i = close + 2
                    }
                }
                src.startsWith("~~", i) -> {
                    val close = src.indexOf("~~", i + 2)
                    if (close == -1 || close >= to) {
                        out.append(c); i++
                    } else {
                        scanInline(src, i + 2, close, out, spans)
                        mark(spans, out, MdSpanType.STRIKETHROUGH, out.length - (close - i - 2))
                        i = close + 2
                    }
                }
                c == '*' || c == '_' -> {
                    val close = src.indexOf(c, i + 1)
                    if (close == -1 || close >= to) {
                        out.append(c); i++
                    } else {
                        scanInline(src, i + 1, close, out, spans)
                        mark(spans, out, MdSpanType.ITALIC, out.length - (close - i - 1))
                        i = close + 1
                    }
                }
                c == '[' -> {
                    val closeBracket = src.indexOf(']', i + 1)
                    if (closeBracket != -1 && closeBracket < to && closeBracket + 1 < to && src[closeBracket + 1] == '(') {
                        val closeParen = src.indexOf(')', closeBracket + 2)
                        if (closeParen != -1 && closeParen <= to) {
                            val labelStart = out.length
                            scanInline(src, i + 1, closeBracket, out, spans)
                            val labelEnd = out.length
                            spans += MdSpan(MdSpanType.LINK, labelStart, labelEnd, src.substring(closeBracket + 2, closeParen))
                            i = closeParen + 1
                        } else {
                            out.append(c); i++
                        }
                    } else {
                        out.append(c); i++
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
    }

    private fun addSpan(spans: MutableList<MdSpan>, out: StringBuilder, type: MdSpanType, content: () -> Unit) {
        val start = out.length
        content()
        spans += MdSpan(type, start, out.length)
    }

    private fun mark(spans: MutableList<MdSpan>, out: StringBuilder, type: MdSpanType, contentStart: Int) {
        spans += MdSpan(type, contentStart, out.length)
    }
}

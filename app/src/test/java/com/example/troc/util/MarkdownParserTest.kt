package com.example.troc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses headings paragraphs lists code tables quotes and rules`() {
        val blocks = MarkdownParser.parseBlocks(
            """
            # Title
            Some **bold** and *italic* and `code` text.

            - item one
            - item two

            ```kotlin
            fun main() { println("hi") }
            ```

            | a | b |
            |---|---|
            | 1 | 2 |

            > quoted line
            ---
            Final paragraph.
            """.trimIndent()
        )
        assertEquals(8, blocks.size)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertTrue(blocks[3] is MdBlock.CodeBlock)
        assertEquals("kotlin", (blocks[3] as MdBlock.CodeBlock).language)
        assertEquals(1, (blocks[4] as MdBlock.Table).rows.size)
        assertTrue(blocks[5] is MdBlock.Quote)
        assertTrue(blocks[6] is MdBlock.HorizontalRule)
        assertTrue(blocks[7] is MdBlock.Paragraph)
    }

    @Test
    fun `inline spans align with cleaned text`() {
        val inline = MarkdownParser.parseInline("Some **bold** and *italic* and `code` text.")
        assertEquals("Some bold and italic and code text.", inline.text)
        val bold = inline.spans.first { it.type == MdSpanType.BOLD }
        assertEquals("bold", inline.text.substring(bold.start, bold.end))
        val code = inline.spans.first { it.type == MdSpanType.CODE }
        assertEquals("code", inline.text.substring(code.start, code.end))
        val italic = inline.spans.first { it.type == MdSpanType.ITALIC }
        assertEquals("italic", inline.text.substring(italic.start, italic.end))
    }

    @Test
    fun `links capture label and url`() {
        val inline = MarkdownParser.parseInline("see [docs](https://example.com) now")
        assertEquals("see docs now", inline.text)
        val link = inline.spans.first { it.type == MdSpanType.LINK }
        assertEquals("https://example.com", link.url)
        assertEquals("docs", inline.text.substring(link.start, link.end))
    }

    @Test
    fun `nested bold containing italic`() {
        val inline = MarkdownParser.parseInline("**bold *nested* end**")
        assertEquals("bold nested end", inline.text)
        val bold = inline.spans.first { it.type == MdSpanType.BOLD }
        assertEquals(0, bold.start)
        assertEquals("bold nested end".length, bold.end)
        assertTrue(inline.spans.any { it.type == MdSpanType.ITALIC })
    }

    @Test
    fun `ordered lists and continuation lines`() {
        val blocks = MarkdownParser.parseBlocks("1. first\n2. second\n   continued\n")
        val list = blocks.single() as MdBlock.OrderedList
        assertEquals(2, list.items.size)
        assertEquals("second continued", list.items[1].text)
    }

    @Test
    fun `escaped pipes survive table cells`() {
        val blocks = MarkdownParser.parseBlocks("| a | b |\n|---|---|\n| 1 \\| 2 | x |")
        val table = blocks.single() as MdBlock.Table
        assertEquals("1 | 2", table.rows[0][0])
    }
}

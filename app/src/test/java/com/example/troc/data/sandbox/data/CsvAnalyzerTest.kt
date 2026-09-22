package com.example.troc.data.sandbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvAnalyzerTest {

    private val csv = "name,qty,price\n\"Widget, small\",4,2.50\n\"He said \"\"hi\"\"\",2,10.0\nGadget,,7.25\n"

    private fun table() = CsvAnalyzer.load(csv)

    @Test
    fun `parser handles quotes escapes and missing cells`() {
        val rows = CsvParser.parse(csv)
        assertEquals(3, rows.size)
        assertEquals("Widget, small", rows[0][0])
        assertEquals("He said \"hi\"", rows[1][0])
        assertEquals("", rows[2][1])
    }

    @Test
    fun `type inference detects numeric columns`() {
        assertEquals(listOf("text", "numeric", "numeric"), CsvAnalyzer.inferTypes(table()))
    }

    @Test
    fun `descriptive stats are correct`() {
        val stats = CsvAnalyzer.columnStats(table())
        assertEquals(3.0, stats[1].mean!!, 1e-9)
        assertEquals(19.75, stats[2].sum!!, 1e-9)
        assertEquals(7.25, stats[2].median!!, 1e-9)
        assertEquals(2.5, stats[2].min!!, 1e-9)
        assertEquals(10.0, stats[2].max!!, 1e-9)
    }

    @Test
    fun `filter keeps matching rows only`() {
        val filtered = CsvAnalyzer.filter(table(), "qty", ">", "1")
        assertEquals(2, filtered.rows.size)
    }

    @Test
    fun `sort orders numerically not lexicographically`() {
        val asc = CsvAnalyzer.sort(table(), "price", descending = false)
        assertEquals("Widget, small", asc.rows.first()[0])
        val desc = CsvAnalyzer.sort(table(), "price", descending = true)
        assertEquals("He said \"hi\"", desc.rows.first()[0])
    }

    @Test
    fun `chart series caps at top N`() {
        val series = CsvAnalyzer.chartSeries(table(), "price", topN = 2)
        assertEquals(2, series.points.size)
        assertEquals(10.0, series.points.first().value, 1e-9)
    }

    @Test
    fun `round trip through toCsv`() {
        val t = CsvAnalyzer.load("a,b\n1,2\n\"x,y\",3")
        val text = CsvAnalyzer.toCsv(t)
        assertTrue(text.contains("\"x,y\""))
        assertEquals(2, CsvAnalyzer.load(text).rows.size)
    }

    @Test
    fun `unknown column throws a friendly error`() {
        try {
            CsvAnalyzer.filter(table(), "nope", "=", "1")
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Column 'nope' not found"))
        }
    }

    @Test
    fun `empty input yields empty table`() {
        assertEquals(0, CsvAnalyzer.load("").rows.size)
    }

    @Test
    fun `headerless input generates column names`() {
        val t = CsvAnalyzer.load("1,x\n2,y", hasHeader = false)
        assertEquals(2, t.header.size)
        assertEquals(2, t.rows.size)
    }
}

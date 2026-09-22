package com.example.troc.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolCallParserTest {

    private val parser = AgentToolCallParser()

    @Test
    fun `extracts run_code and analyze_data calls`() {
        val text = """
            Let me compute that.
            <run_code language="kotlin">println(1 + 1)</run_code>
            Now the data:
            <analyze_data format="csv">a,b
            1,2</analyze_data>
        """.trimIndent()
        val calls = parser.parse(text)
        assertEquals(2, calls.size)
        val runCode = calls[0] as AgentToolCall.RunCode
        assertEquals("kotlin", runCode.language)
        assertEquals("println(1 + 1)", runCode.code)
        val analyze = calls[1] as AgentToolCall.AnalyzeData
        assertEquals("csv", analyze.format)
        assertTrue(analyze.content.contains("1,2"))
    }

    @Test
    fun `extracts process_file with action attribute`() {
        val calls = parser.parse("Reading… <process_file action=\"metadata\">check it</process_file>")
        val call = calls.single() as AgentToolCall.ProcessFile
        assertEquals("metadata", call.action)
        assertEquals("check it", call.argument)
    }

    @Test
    fun `plain text yields no calls`() {
        assertEquals(0, parser.parse("Just chatting, no tools here.").size)
    }

    @Test
    fun `defaults are applied when attributes are missing`() {
        val calls = parser.parse("<run_code>1+1</run_code>")
        assertEquals("javascript", (calls.single() as AgentToolCall.RunCode).language)
    }

    @Test
    fun `display summary replaces tags with friendly chips`() {
        val summary = parser.summarizeForDisplay("x <run_code language=\"javascript\">1</run_code> y")
        assertTrue(summary.contains("Running code (javascript)"))
        assertTrue(!summary.contains("<run_code"))
    }

    @Test
    fun `strip removes all tool tags`() {
        val stripped = parser.stripToolTags(
            "a<run_code language=\"js\">1</run_code>b<analyze_data format=\"csv\">x</analyze_data>c" +
                "<process_file action=\"read_text\">y</process_file>d"
        )
        assertEquals("abcd", stripped)
    }
}

package com.example.troc.data.sandbox.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniKotlinTest {

    private fun run(src: String, maxSteps: Long = 5_000_000L): String =
        MiniKotlin(maxSteps = maxSteps, deadlineNanos = System.nanoTime() + 5_000_000_000L)
            .run(src).output

    private fun runError(src: String): String = try {
        run(src)
        "<no error>"
    } catch (e: Exception) {
        e.message ?: e.toString()
    }

    @Test
    fun `recursive factorial`() {
        assertEquals(
            "120\n",
            run(
                """
                fun factorial(n: Int): Int {
                    if (n == 0) return 1
                    return n * factorial(n - 1)
                }
                println(factorial(5))
                """.trimIndent()
            )
        )
    }

    @Test
    fun `while loop and mutation`() {
        assertEquals(
            "13\n",
            run(
                """
                var a = 0
                var b = 1
                var i = 0
                while (i < 7) {
                    val t = a + b
                    a = b
                    b = t
                    i += 1
                }
                println(a)
                """.trimIndent()
            )
        )
    }

    @Test
    fun `string templates`() {
        assertEquals(
            "Hello Troc, sum=50\n",
            run(
                """
                val name = "Troc"
                val x = 42
                println("Hello ${'$'}name, sum=${'$'}{x + 8}")
                """.trimIndent()
            )
        )
    }

    @Test
    fun `list operations`() {
        assertEquals(
            "4\n17\n[1, 2, 5, 9]\n[50, 90]\n",
            run(
                """
                val nums = listOf(5, 2, 9, 1)
                println(nums.size)
                println(nums.sum())
                println(nums.sorted())
                println(nums.filter { it > 2 }.map { it * 10 })
                """.trimIndent()
            )
        )
    }

    @Test
    fun `for over range and string`() {
        assertEquals(
            "s=15\nab\n",
            run(
                """
                var s = 0
                for (i in 1..5) { s += i }
                println("s=${'$'}s")
                for (c in "ab") { print(c) }
                println()
                """.trimIndent()
            )
        )
    }

    @Test
    fun `maps with to infix`() {
        assertEquals(
            "2\n2\n{a=1, b=2}\n",
            run(
                """
                val m = mapOf("a" to 1, "b" to 2)
                println(m["b"])
                println(m.size)
                println(m)
                """.trimIndent()
            )
        )
    }

    @Test
    fun `if as expression`() {
        assertEquals(
            "big\n",
            run(
                """
                val x = 7
                val label = if (x > 5) "big" else "small"
                println(label)
                """.trimIndent()
            )
        )
    }

    @Test
    fun `kotlin-style integer division and double printing`() {
        assertEquals("3\n", run("println(7 / 2)"))
        assertEquals("3.5\n", run("println(7.0 / 2)"))
        assertEquals("5.0\n", run("println(5.0)"))
    }

    @Test
    fun `mutable lists`() {
        assertEquals(
            "6\n[10, 2, 3]\n",
            run(
                """
                val list = mutableListOf(1)
                list.add(2)
                list.add(3)
                var total = 0
                list.forEach { total += it }
                println(total)
                list[0] = 10
                println(list)
                """.trimIndent()
            )
        )
    }

    @Test
    fun `val reassignment is rejected`() {
        assertTrue(runError("val x = 1\nx = 2").contains("Val cannot be reassigned"))
    }

    @Test
    fun `unresolved reference is reported with line`() {
        assertTrue(runError("println(missing)").contains("Unresolved reference: missing"))
    }

    @Test
    fun `division by zero is a runtime error`() {
        assertTrue(runError("val x = 1 / 0").contains("Division by zero"))
    }

    @Test
    fun `infinite loops hit the step budget`() {
        val message = runError("while (true) { }")
        assertTrue("got: $message", message.contains("steps") || message.contains("timed out"))
    }

    @Test
    fun `deep recursion is caught`() {
        assertTrue(runError("fun boom(n: Int): Int { return boom(n + 1) }\nboom(0)").contains("Stack overflow"))
    }

    @Test
    fun `output limit truncates runaway printing`() {
        assertTrue(runError("for (i in 1..100000) { println(\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\") }").contains("Output limit"))
    }

    @Test
    fun `huge ranges are never materialized`() {
        assertTrue(runError("(1..2000000000).toList()").contains("budget"))
    }

    @Test
    fun `syntax errors carry line numbers`() {
        assertTrue(runError("val = 5").contains("Line 1"))
    }
}

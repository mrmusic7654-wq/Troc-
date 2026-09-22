package com.example.troc.util

/**
 * Tiny regex-based syntax highlighter for code blocks in chat messages.
 * Pure Kotlin; returns character ranges so the UI can build an [androidx.compose.ui.text.AnnotatedString].
 */
object SyntaxHighlighter {

    enum class TokenType { COMMENT, STRING, NUMBER, KEYWORD, ANNOTATION }

    data class Span(val start: Int, val end: Int, val type: TokenType)

    private val KOTLIN_KEYWORDS = setOf(
        "fun", "val", "var", "if", "else", "for", "while", "return", "class", "object",
        "interface", "data", "sealed", "open", "abstract", "override", "private", "public",
        "internal", "protected", "import", "package", "null", "true", "false", "in", "is",
        "when", "try", "catch", "finally", "throw", "this", "super", "typealias", "suspend",
        "companion", "init", "lateinit", "const", "operator", "inline", "vararg", "by", "as"
    )

    private val JS_KEYWORDS = setOf(
        "function", "const", "let", "var", "if", "else", "for", "while", "return", "class",
        "extends", "null", "undefined", "true", "false", "async", "await", "of", "new",
        "typeof", "instanceof", "try", "catch", "finally", "throw", "this", "import",
        "export", "default", "switch", "case", "break", "continue", "do", "delete", "in"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "print", "if", "elif", "else", "for", "while", "return", "class", "None",
        "True", "False", "import", "from", "lambda", "in", "not", "and", "or", "with",
        "as", "try", "except", "finally", "raise", "pass", "break", "continue", "yield",
        "global", "self"
    )

    private fun keywords(language: String?): Set<String> = when {
        language == null -> KOTLIN_KEYWORDS
        language.contains("kotlin", true) || language.contains("kt", true) -> KOTLIN_KEYWORDS
        language.contains("js", true) || language.contains("javascript", true) ||
            language.contains("typescript", true) -> JS_KEYWORDS
        language.contains("python", true) || language.contains("py", true) -> PYTHON_KEYWORDS
        else -> KOTLIN_KEYWORDS + JS_KEYWORDS + PYTHON_KEYWORDS
    }

    private fun regexes(language: String?): List<Pair<Regex, TokenType>> {
        val lineComment = if (language?.contains("python", true) == true || language?.contains("py", true) == true) {
            "#[^\\n]*"
        } else {
            "//[^\\n]*"
        }
        return listOf(
            Regex("/\\*[\\s\\S]*?\\*/") to TokenType.COMMENT,
            Regex(lineComment) to TokenType.COMMENT,
            Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'") to TokenType.STRING,
            Regex("@[A-Za-z_]\\w*") to TokenType.ANNOTATION,
            Regex("\\b\\d[\\d_]*(\\.\\d+)?[fFlL]?\\b") to TokenType.NUMBER
        )
    }

    private val IDENT = Regex("[A-Za-z_]\\w*")

    fun highlight(code: String, language: String?): List<Span> {
        val spans = mutableListOf<Span>()
        val rules = regexes(language)
        val keywords = keywords(language)
        var i = 0
        while (i < code.length) {
            var matched = false
            for ((regex, type) in rules) {
                val m = regex.matchAt(code, i)
                if (m != null) {
                    spans += Span(i, m.value.length + i, type)
                    i += m.value.length
                    matched = true
                    break
                }
            }
            if (matched) continue
            val id = IDENT.matchAt(code, i)
            if (id != null) {
                if (id.value in keywords) spans += Span(i, i + id.value.length, TokenType.KEYWORD)
                i += id.value.length
                continue
            }
            i++
        }
        return spans
    }
}

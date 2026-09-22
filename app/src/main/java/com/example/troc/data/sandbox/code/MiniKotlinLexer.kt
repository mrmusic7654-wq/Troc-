package com.example.troc.data.sandbox.code

enum class Tok {
    IDENT, INT, DOUBLE, STRING, NEWLINE,
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, DOT, COLON, SEMICOLON,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    ASSIGN, EQ, NEQ, LT, GT, LE, GE,
    PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN,
    AND, OR, NOT, RANGE, ARROW, QUESTION,
    VAL, VAR, FUN, IF, ELSE, WHILE, FOR, IN, RETURN,
    TRUE, FALSE, NULL, TO,
    EOF
}

data class Token(val type: Tok, val text: String, val line: Int)

class KtSyntaxError(message: String, val line: Int) : Exception("Line $line: $message")

/**
 * Hand-written lexer for the Mini-Kotlin sandbox language: a safe subset of Kotlin
 * with no reflection, no I/O and no access to the JVM.
 */
object MiniKotlinLexer {

    private val KEYWORDS: Map<String, Tok> = mapOf(
        "val" to Tok.VAL,
        "var" to Tok.VAR,
        "fun" to Tok.FUN,
        "if" to Tok.IF,
        "else" to Tok.ELSE,
        "while" to Tok.WHILE,
        "for" to Tok.FOR,
        "in" to Tok.IN,
        "return" to Tok.RETURN,
        "true" to Tok.TRUE,
        "false" to Tok.FALSE,
        "null" to Tok.NULL,
        "to" to Tok.TO
    )

    fun lex(source: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var line = 1
        var parenDepth = 0

        fun lastType(): Tok? = tokens.lastOrNull()?.type

        fun emit(type: Tok, text: String, atLine: Int = line) {
            tokens.add(Token(type, text, atLine))
        }

        while (i < source.length) {
            val c = source[i]
            when {
                c == ' ' || c == '\t' || c == '\r' -> i++
                c == '\n' -> {
                    if (parenDepth == 0 && lastType() != null && lastType() != Tok.NEWLINE) {
                        emit(Tok.NEWLINE, "\n")
                    }
                    line++
                    i++
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') i++
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) {
                        if (source[i] == '\n') line++
                        i++
                    }
                    i = (i + 2).coerceAtMost(source.length)
                }
                c.isDigit() -> {
                    val start = i
                    while (i < source.length && (source[i].isDigit() || source[i] == '_')) i++
                    if (i + 1 < source.length && source[i] == '.' && source[i + 1].isDigit()) {
                        i++
                        while (i < source.length && source[i].isDigit()) i++
                        emit(Tok.DOUBLE, source.substring(start, i).replace("_", ""))
                    } else {
                        emit(Tok.INT, source.substring(start, i).replace("_", ""))
                    }
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    val word = source.substring(start, i)
                    emit(KEYWORDS[word] ?: Tok.IDENT, word)
                }
                c == '"' -> {
                    val startLine = line
                    val sb = StringBuilder()
                    i++
                    var closed = false
                    while (i < source.length) {
                        val ch = source[i]
                        if (ch == '\n') {
                            line++
                            i++
                            continue
                        }
                        if (ch == '"') {
                            closed = true
                            i++
                            break
                        }
                        if (ch == '\\' && i + 1 < source.length) {
                            when (val esc = source[i + 1]) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                '\\' -> sb.append('\\')
                                '"' -> sb.append('"')
                                '$' -> sb.append("\\$")
                                '\'' -> sb.append('\'')
                                else -> sb.append(esc)
                            }
                            i += 2
                        } else {
                            sb.append(ch)
                            i++
                        }
                    }
                    if (!closed) throw KtSyntaxError("Unterminated string literal", startLine)
                    emit(Tok.STRING, sb.toString(), startLine)
                }
                else -> {
                    val two = if (i + 1 < source.length) source.substring(i, i + 2) else ""
                    val type = when (two) {
                        "==" -> Tok.EQ
                        "!=" -> Tok.NEQ
                        "<=" -> Tok.LE
                        ">=" -> Tok.GE
                        "&&" -> Tok.AND
                        "||" -> Tok.OR
                        ".." -> Tok.RANGE
                        "->" -> Tok.ARROW
                        "+=" -> Tok.PLUS_ASSIGN
                        "-=" -> Tok.MINUS_ASSIGN
                        "*=" -> Tok.STAR_ASSIGN
                        "/=" -> Tok.SLASH_ASSIGN
                        "%=" -> Tok.PERCENT_ASSIGN
                        else -> null
                    }
                    if (type != null) {
                        emit(type, two)
                        i += 2
                    } else {
                        val single = when (c) {
                            '(' -> Tok.LPAREN.also { parenDepth++ }
                            ')' -> Tok.RPAREN.also { parenDepth = (parenDepth - 1).coerceAtLeast(0) }
                            '[' -> Tok.LBRACKET.also { parenDepth++ }
                            ']' -> Tok.RBRACKET.also { parenDepth = (parenDepth - 1).coerceAtLeast(0) }
                            '{' -> Tok.LBRACE
                            '}' -> Tok.RBRACE
                            ',' -> Tok.COMMA
                            '.' -> Tok.DOT
                            ':' -> Tok.COLON
                            ';' -> Tok.SEMICOLON
                            '+' -> Tok.PLUS
                            '-' -> Tok.MINUS
                            '*' -> Tok.STAR
                            '/' -> Tok.SLASH
                            '%' -> Tok.PERCENT
                            '=' -> Tok.ASSIGN
                            '<' -> Tok.LT
                            '>' -> Tok.GT
                            '!' -> Tok.NOT
                            '?' -> Tok.QUESTION
                            else -> throw KtSyntaxError("Unexpected character '$c'", line)
                        }
                        emit(single, c.toString())
                        i++
                    }
                }
            }
        }
        tokens.add(Token(Tok.EOF, "", line))
        return tokens
    }
}

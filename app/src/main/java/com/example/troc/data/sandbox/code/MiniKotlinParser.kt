package com.example.troc.data.sandbox.code

/**
 * AST for the Mini-Kotlin sandbox subset.
 */
sealed class Expr {
    data class Lit(val value: Any?) : Expr()
    /** A string literal that may still contain `$` templates. */
    data class Str(val raw: String) : Expr()
    data class Var(val name: String, val line: Int) : Expr()
    data class Binary(val op: String, val l: Expr, val r: Expr, val line: Int) : Expr()
    data class Unary(val op: String, val e: Expr, val line: Int) : Expr()
    data class Call(val callee: Expr, val args: List<Expr>, val line: Int) : Expr()
    data class Member(val obj: Expr, val name: String, val line: Int) : Expr()
    data class Index(val obj: Expr, val index: Expr, val line: Int) : Expr()
    data class Lambda(val params: List<String>, val body: List<Stmt>) : Expr()
    data class If(val cond: Expr, val thenBranch: Expr, val elseBranch: Expr?, val line: Int) : Expr()
    /** Evaluates its statements; value = last statement's value. */
    data class Block(val stmts: List<Stmt>) : Expr()
    data class Range(val from: Expr, val to: Expr, val line: Int) : Expr()
}

sealed class Stmt {
    data class Val(val name: String, val mutable: Boolean, val expr: Expr, val line: Int) : Stmt()
    data class Fun(val name: String, val params: List<String>, val body: List<Stmt>, val line: Int) : Stmt()
    data class ExprStmt(val expr: Expr, val line: Int) : Stmt()
    data class While(val cond: Expr, val body: List<Stmt>, val line: Int) : Stmt()
    data class For(val name: String, val iterable: Expr, val body: List<Stmt>, val line: Int) : Stmt()
    data class Return(val expr: Expr?, val line: Int) : Stmt()
    data class Assign(val target: Expr, val expr: Expr, val line: Int) : Stmt()
}

class MiniKotlinParser private constructor(private val tokens: List<Token>) {

    companion object {
        fun parseProgram(source: String): List<Stmt> =
            MiniKotlinParser(MiniKotlinLexer.lex(source)).parseStatements(untilEof = true)

        /** Parses a single expression (used for `$\{...\}` string templates). */
        fun parseSingleExpression(source: String): Expr =
            MiniKotlinParser(MiniKotlinLexer.lex(source)).run {
                val e = parseExpression()
                expect(Tok.EOF)
                e
            }
    }

    private var pos = 0

    private fun peek(): Token = tokens[pos]
    private fun peekAhead(offset: Int): Token = tokens[(pos + offset).coerceAtMost(tokens.size - 1)]

    private fun advance(): Token = tokens[pos].also { if (pos < tokens.size - 1) pos++ }

    private fun check(type: Tok): Boolean = peek().type == type

    private fun match(type: Tok): Boolean {
        if (check(type)) {
            advance()
            return true
        }
        return false
    }

    private fun expect(type: Tok): Token {
        val t = peek()
        if (t.type != type) {
            throw KtSyntaxError("Expected ${type.name.lowercase()} but found '${t.text.ifBlank { t.type.name.lowercase() }}'", t.line)
        }
        return advance()
    }

    private fun skipNewlines() {
        while (check(Tok.NEWLINE) || check(Tok.SEMICOLON)) advance()
    }

    // ------------------------------------------------------------------ statements

    fun parseStatements(untilEof: Boolean, until: Tok? = null): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        skipNewlines()
        while (true) {
            val t = peek()
            if (t.type == Tok.EOF) {
                if (until != null) throw KtSyntaxError("Unexpected end of file, missing '${until.name.lowercase()}'", t.line)
                break
            }
            if (until != null && t.type == until) break
            stmts += parseStatement()
            skipNewlines()
        }
        return stmts
    }

    private fun parseStatement(): Stmt {
        val t = peek()
        return when (t.type) {
            Tok.VAL, Tok.VAR -> parseValDecl()
            Tok.FUN -> parseFunDecl()
            Tok.WHILE -> parseWhile()
            Tok.FOR -> parseFor()
            Tok.RETURN -> parseReturn()
            else -> {
                val expr = parseExpression()
                if (check(Tok.ASSIGN)) {
                    advance()
                    if (expr !is Expr.Var && expr !is Expr.Index) {
                        throw KtSyntaxError("Invalid assignment target", t.line)
                    }
                    Stmt.Assign(expr, parseExpression(), t.line)
                } else {
                    val compoundOp = when (peek().type) {
                        Tok.PLUS_ASSIGN -> "+"
                        Tok.MINUS_ASSIGN -> "-"
                        Tok.STAR_ASSIGN -> "*"
                        Tok.SLASH_ASSIGN -> "/"
                        Tok.PERCENT_ASSIGN -> "%"
                        else -> null
                    }
                    if (compoundOp != null) {
                        val line = advance().line
                        if (expr !is Expr.Var && expr !is Expr.Index) {
                            throw KtSyntaxError("Invalid assignment target", t.line)
                        }
                        Stmt.Assign(expr, Expr.Binary(compoundOp, expr, parseExpression(), line), t.line)
                    } else {
                        Stmt.ExprStmt(expr, t.line)
                    }
                }
            }
        }
    }

    private fun skipTypeAnnotation() {
        // Best-effort: consume a (possibly nullable/generic) type expression.
        while (peek().type in setOf(Tok.IDENT, Tok.DOT, Tok.LT, Tok.GT, Tok.QUESTION, Tok.COMMA, Tok.STAR)) {
            advance()
        }
    }

    private fun parseValDecl(): Stmt {
        val start = advance() // val / var
        val mutable = start.type == Tok.VAR
        val name = expect(Tok.IDENT).text
        if (match(Tok.COLON)) skipTypeAnnotation()
        expect(Tok.ASSIGN)
        val expr = if (check(Tok.IF)) parseIfExpression() else parseExpression()
        return Stmt.Val(name, mutable, expr, start.line)
    }

    private fun parseFunDecl(): Stmt {
        val start = advance() // fun
        val name = expect(Tok.IDENT).text
        expect(Tok.LPAREN)
        val params = mutableListOf<String>()
        if (!check(Tok.RPAREN)) {
            do {
                params += expect(Tok.IDENT).text
                if (match(Tok.COLON)) skipTypeAnnotation()
            } while (match(Tok.COMMA))
        }
        expect(Tok.RPAREN)
        if (match(Tok.COLON)) skipTypeAnnotation()
        val body: List<Stmt> = if (match(Tok.ASSIGN)) {
            listOf(Stmt.Return(parseExpression(), start.line))
        } else {
            parseBlock()
        }
        return Stmt.Fun(name, params, body, start.line)
    }

    private fun parseBlock(): List<Stmt> {
        expect(Tok.LBRACE)
        val stmts = parseStatements(untilEof = false, until = Tok.RBRACE)
        expect(Tok.RBRACE)
        return stmts
    }

    /** `{ ... }` or a single statement (no braces). */
    private fun parseBody(): List<Stmt> {
        return if (check(Tok.LBRACE)) parseBlock() else listOf(parseStatement())
    }

    private fun parseWhile(): Stmt {
        val start = advance()
        expect(Tok.LPAREN)
        val cond = parseExpression()
        expect(Tok.RPAREN)
        return Stmt.While(cond, parseBody(), start.line)
    }

    private fun parseFor(): Stmt {
        val start = advance()
        expect(Tok.LPAREN)
        val name = expect(Tok.IDENT).text
        expect(Tok.IN)
        val iterable = parseExpression()
        expect(Tok.RPAREN)
        return Stmt.For(name, iterable, parseBody(), start.line)
    }

    private fun parseReturn(): Stmt {
        val start = advance()
        val expr = if (check(Tok.NEWLINE) || check(Tok.EOF) || check(Tok.RBRACE) || check(Tok.SEMICOLON)) null else parseExpression()
        return Stmt.Return(expr, start.line)
    }

    // ------------------------------------------------------------------ expressions

    private fun parseExpression(): Expr = parseIfExpression()

    private fun parseIfExpression(): Expr {
        if (!check(Tok.IF)) return parseOr()
        val start = advance()
        expect(Tok.LPAREN)
        val cond = parseExpression()
        expect(Tok.RPAREN)
        val thenBranch = parseBranch()
        val elseBranch = if (match(Tok.ELSE)) {
            if (check(Tok.IF)) parseIfExpression() else parseBranch()
        } else null
        return Expr.If(cond, thenBranch, elseBranch, start.line)
    }

    private fun parseBranch(): Expr {
        if (check(Tok.LBRACE)) return Expr.Block(parseBlock())
        skipNewlines()
        return when (peek().type) {
            Tok.RETURN -> Expr.Block(listOf(parseReturn()))
            Tok.VAL, Tok.VAR, Tok.FUN, Tok.WHILE, Tok.FOR -> Expr.Block(listOf(parseStatement()))
            else -> parseExpression()
        }
    }

    private fun parseOr(): Expr {
        var left = parseAnd()
        while (check(Tok.OR)) {
            val op = advance()
            left = Expr.Binary("||", left, parseAnd(), op.line)
        }
        return left
    }

    private fun parseAnd(): Expr {
        var left = parseEquality()
        while (check(Tok.AND)) {
            val op = advance()
            left = Expr.Binary("&&", left, parseEquality(), op.line)
        }
        return left
    }

    private fun parseEquality(): Expr {
        var left = parseComparison()
        while (check(Tok.EQ) || check(Tok.NEQ)) {
            val op = advance()
            left = Expr.Binary(op.text, left, parseComparison(), op.line)
        }
        return left
    }

    private fun parseComparison(): Expr {
        var left = parseToInfix()
        while (check(Tok.LT) || check(Tok.GT) || check(Tok.LE) || check(Tok.GE)) {
            val op = advance()
            left = Expr.Binary(op.text, left, parseToInfix(), op.line)
        }
        return left
    }

    private fun parseToInfix(): Expr {
        var left = parseRange()
        while (check(Tok.TO)) {
            val op = advance()
            left = Expr.Binary("to", left, parseRange(), op.line)
        }
        return left
    }

    private fun parseRange(): Expr {
        var left = parseAdditive()
        while (check(Tok.RANGE)) {
            val op = advance()
            left = Expr.Range(left, parseAdditive(), op.line)
        }
        return left
    }

    private fun parseAdditive(): Expr {
        var left = parseMultiplicative()
        while (check(Tok.PLUS) || check(Tok.MINUS)) {
            val op = advance()
            left = Expr.Binary(op.text, left, parseMultiplicative(), op.line)
        }
        return left
    }

    private fun parseMultiplicative(): Expr {
        var left = parseUnary()
        while (check(Tok.STAR) || check(Tok.SLASH) || check(Tok.PERCENT)) {
            val op = advance()
            left = Expr.Binary(op.text, left, parseUnary(), op.line)
        }
        return left
    }

    private fun parseUnary(): Expr {
        val t = peek()
        if (t.type == Tok.NOT || t.type == Tok.MINUS || t.type == Tok.PLUS) {
            advance()
            val e = parseUnary()
            return when (t.type) {
                Tok.PLUS -> e
                else -> Expr.Unary(t.text, e, t.line)
            }
        }
        return parsePostfix()
    }

    private fun parsePostfix(): Expr {
        var expr = parsePrimary()
        while (true) {
            when {
                check(Tok.LPAREN) -> {
                    val line = peek().line
                    advance()
                    val args = mutableListOf<Expr>()
                    if (!check(Tok.RPAREN)) {
                        do {
                            args += parseExpression()
                        } while (match(Tok.COMMA))
                    }
                    expect(Tok.RPAREN)
                    // Trailing lambda: f(a) { ... }
                    if (check(Tok.LBRACE)) args += parseLambda()
                    expr = Expr.Call(expr, args, line)
                }
                check(Tok.LBRACKET) -> {
                    val line = peek().line
                    advance()
                    val index = parseExpression()
                    expect(Tok.RBRACKET)
                    expr = Expr.Index(expr, index, line)
                }
                check(Tok.DOT) -> {
                    val line = peek().line
                    advance()
                    val name = expect(Tok.IDENT).text
                    if (check(Tok.LPAREN)) {
                        advance()
                        val args = mutableListOf<Expr>()
                        if (!check(Tok.RPAREN)) {
                            do {
                                args += parseExpression()
                            } while (match(Tok.COMMA))
                        }
                        expect(Tok.RPAREN)
                        if (check(Tok.LBRACE)) args += parseLambda()
                        expr = Expr.Call(Expr.Member(expr, name, line), args, line)
                    } else if (check(Tok.LBRACE)) {
                        val lambda = parseLambda()
                        expr = Expr.Call(Expr.Member(expr, name, line), listOf(lambda), line)
                    } else {
                        expr = Expr.Member(expr, name, line)
                    }
                }
                else -> return expr
            }
        }
    }

    private fun parseLambda(): Expr {
        expect(Tok.LBRACE)
        val save = pos
        val params = mutableListOf<String>()
        var hasArrow = false
        if (check(Tok.IDENT)) {
            params += advance().text
            while (match(Tok.COMMA)) params += expect(Tok.IDENT).text
            hasArrow = match(Tok.ARROW)
        }
        if (!hasArrow) {
            pos = save
            params.clear()
        }
        val body = parseStatements(untilEof = false, until = Tok.RBRACE)
        expect(Tok.RBRACE)
        return Expr.Lambda(params, body)
    }

    private fun parsePrimary(): Expr {
        val t = peek()
        return when (t.type) {
            Tok.INT -> Expr.Lit(advance().text.toLong().let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it })
            Tok.DOUBLE -> Expr.Lit(advance().text.toDouble())
            Tok.TRUE -> Expr.Lit(true).also { advance() }
            Tok.FALSE -> Expr.Lit(false).also { advance() }
            Tok.NULL -> Expr.Lit(null).also { advance() }
            Tok.STRING -> Expr.Str(advance().text)
            Tok.IDENT -> Expr.Var(advance().text, t.line)
            Tok.IF -> parseIfExpression()
            Tok.LPAREN -> {
                advance()
                val e = parseExpression()
                expect(Tok.RPAREN)
                e
            }
            Tok.LBRACKET -> {
                advance()
                val args = mutableListOf<Expr>()
                if (!check(Tok.RBRACKET)) {
                    do {
                        args += parseExpression()
                    } while (match(Tok.COMMA))
                }
                expect(Tok.RBRACKET)
                Expr.Call(Expr.Var("listOf", t.line), args, t.line)
            }
            Tok.LBRACE -> parseLambda()
            else -> throw KtSyntaxError("Unexpected token '${t.text.ifBlank { t.type.name.lowercase() }}'", t.line)
        }
    }
}

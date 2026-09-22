package com.example.troc.data.sandbox.code

import java.util.concurrent.atomic.AtomicBoolean

/** Thrown for user-visible runtime errors inside the sandbox. */
class KtRuntimeError(message: String, val line: Int) : Exception("Line $line: $message")

/** Thrown when CPU/step/output budgets are exceeded. */
class KtLimitExceeded(message: String) : Exception(message)

class MiniKotlinResult(val output: String)

/**
 * A small, dependency-free tree-walking interpreter for a safe subset of Kotlin.
 *
 * Supported: val/var, functions, if/else (statement + expression), while, for-in,
 * ranges (a..b), string templates ("x = $x"), lists/maps via listOf/mapOf, indexing,
 * common collection/string operations and lambdas (`it` or named params).
 *
 * By construction there is no reflection, no file/network access and no JVM escape
 * hatch — every loop is budget-checked and execution can be cancelled mid-flight.
 */
class MiniKotlin(
    private val maxSteps: Long = 20_000_000L,
    private val maxDepth: Int = 200,
    private val maxOutputChars: Int = 200_000,
    private val deadlineNanos: Long = 0L,
    private val cancelled: AtomicBoolean = AtomicBoolean(false)
) {

    private class ReturnSignal(val value: Any?) : RuntimeException()

    private class KtFunction(val params: List<String>, val body: List<Stmt>, val closure: Scope)

    private class KtLambda(
        val params: List<String>,
        val body: List<Stmt>,
        val closure: Scope,
        val interpreter: MiniKotlin
    )

    private fun interface BuiltinFn {
        fun call(args: List<Any?>): Any?
    }

    private object KtUnit

    private class Entry(var value: Any?, val mutable: Boolean)

    private inner class Scope(val parent: Scope? = null) {
        private val vars = LinkedHashMap<String, Entry>()

        fun declare(name: String, value: Any?, mutable: Boolean) {
            vars[name] = Entry(value, mutable)
        }

        fun get(name: String, line: Int): Any? {
            var s: Scope? = this
            while (s != null) {
                s.vars[name]?.let { return it.value }
                s = s.parent
            }
            throw KtRuntimeError("Unresolved reference: $name", line)
        }

        fun assign(name: String, value: Any?, line: Int) {
            var s: Scope? = this
            while (s != null) {
                val e = s.vars[name]
                if (e != null) {
                    if (!e.mutable) throw KtRuntimeError("Val cannot be reassigned", line)
                    e.value = value
                    return
                }
                s = s.parent
            }
            throw KtRuntimeError("Unresolved reference: $name", line)
        }
    }

    private val out = StringBuilder()
    private val globals = Scope()
    private var steps = 0L
    private var depth = 0

    // ------------------------------------------------------------------ entry point

    fun run(source: String): MiniKotlinResult {
        registerBuiltins()
        val program = MiniKotlinParser.parseProgram(source)
        execBlock(globals, program)
        return MiniKotlinResult(finalizeOutput())
    }

    fun cancel() = cancelled.set(true)

    private fun tick() {
        steps++
        if (steps > maxSteps) {
            throw KtLimitExceeded("Execution aborted: exceeded $maxSteps evaluation steps")
        }
        if (deadlineNanos > 0 && steps % 4096L == 0L && System.nanoTime() > deadlineNanos) {
            throw KtLimitExceeded("Execution timed out")
        }
        if (cancelled.get()) {
            throw KtLimitExceeded("Execution cancelled")
        }
    }

    // ------------------------------------------------------------------ statements

    private fun execBlock(scope: Scope, stmts: List<Stmt>): Any? {
        var last: Any? = KtUnit
        for (stmt in stmts) {
            last = exec(scope, stmt)
        }
        return last
    }

    private fun exec(scope: Scope, stmt: Stmt): Any? {
        tick()
        when (stmt) {
            is Stmt.Val -> {
                val v = eval(scope, stmt.expr)
                scope.declare(stmt.name, v, stmt.mutable)
                return KtUnit
            }
            is Stmt.Fun -> {
                scope.declare(stmt.name, KtFunction(stmt.params, stmt.body, scope), mutable = false)
                return KtUnit
            }
            is Stmt.ExprStmt -> return eval(scope, stmt.expr)
            is Stmt.While -> {
                while (isTruthy(eval(scope, stmt.cond), stmt.line)) {
                    tick()
                    execBlock(Scope(scope), stmt.body)
                }
                return KtUnit
            }
            is Stmt.For -> {
                val iterable = eval(scope, stmt.iterable)
                when (iterable) {
                    // Iterate lazily — never materialize huge ranges.
                    is IntRange -> iterable.forEach { item ->
                        tick()
                        val loopScope = Scope(scope)
                        loopScope.declare(stmt.name, item, mutable = false)
                        execBlock(loopScope, stmt.body)
                    }
                    is Long -> {
                        if (iterable > 10_000_000L) throw KtLimitExceeded("for-loop over more than 10,000,000 items")
                        for (item in 0L until iterable) {
                            tick()
                            val loopScope = Scope(scope)
                            loopScope.declare(stmt.name, item, mutable = false)
                            execBlock(loopScope, stmt.body)
                        }
                    }
                    is List<*>, is Map<*, *>, is String -> {
                        val items: List<Any?> = when (iterable) {
                            is List<*> -> iterable
                            is Map<*, *> -> iterable.keys.toList()
                            else -> (iterable as String).map { it.toString() }
                        }
                        for (item in items) {
                            tick()
                            val loopScope = Scope(scope)
                            loopScope.declare(stmt.name, item, mutable = false)
                            execBlock(loopScope, stmt.body)
                        }
                    }
                    else -> throw KtRuntimeError(
                        "for-loop expects a range, list, map or string, got ${typeName(iterable)}", stmt.line
                    )
                }
                return KtUnit
            }
            is Stmt.Return -> {
                throw ReturnSignal(stmt.expr?.let { eval(scope, it) })
            }
            is Stmt.Assign -> {
                val value = eval(scope, stmt.expr)
                when (val target = stmt.target) {
                    is Expr.Var -> scope.assign(target.name, value, stmt.line)
                    is Expr.Index -> {
                        val obj = eval(scope, target.obj)
                        val index = eval(scope, target.index)
                        if (obj is MutableList<*>) {
                            @Suppress("UNCHECKED_CAST")
                            (obj as MutableList<Any?>)[indexOfIndex(obj, index, stmt.line)] = value
                        } else if (obj is MutableMap<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            (obj as MutableMap<Any?, Any?>)[index] = value
                        } else {
                            throw KtRuntimeError("Cannot assign into ${typeName(obj)}", stmt.line)
                        }
                    }
                    else -> throw KtRuntimeError("Invalid assignment target", stmt.line)
                }
                return KtUnit
            }
        }
    }

    private fun indexOfIndex(list: List<*>, index: Any?, line: Int): Int {
        if (index !is Int) throw KtRuntimeError("List index must be Int", line)
        if (index < 0 || index >= list.size) {
            throw KtRuntimeError("Index $index out of bounds: list size ${list.size}", line)
        }
        return index
    }

    // ------------------------------------------------------------------ expressions

    private fun eval(scope: Scope, expr: Expr): Any? {
        tick()
        when (expr) {
            is Expr.Lit -> return expr.value
            is Expr.Str -> return expandString(scope, expr.raw)
            is Expr.Var -> return scope.get(expr.name, expr.line)
            is Expr.Block -> return execBlock(Scope(scope), expr.stmts)
            is Expr.If -> {
                return if (isTruthy(eval(scope, expr.cond), expr.line)) {
                    eval(scope, expr.thenBranch)
                } else {
                    expr.elseBranch?.let { eval(scope, it) } ?: KtUnit
                }
            }
            is Expr.Lambda -> return KtLambda(expr.params, expr.body, scope, this)
            is Expr.Range -> {
                val from = eval(scope, expr.from)
                val to = eval(scope, expr.to)
                if (from is Int && to is Int) return from..to
                if (from is Long && to is Long) return from..to
                throw KtRuntimeError("Range bounds must be integers", expr.line)
            }
            is Expr.Unary -> {
                val v = eval(scope, expr.e)
                return when (expr.op) {
                    "!" -> !isTruthy(v, expr.line)
                    "-" -> when (v) {
                        is Int -> -v
                        is Long -> -v
                        is Double -> -v
                        else -> throw KtRuntimeError("Cannot negate ${typeName(v)}", expr.line)
                    }
                    else -> throw KtRuntimeError("Unknown unary operator ${expr.op}", expr.line)
                }
            }
            is Expr.Binary -> return evalBinary(scope, expr)
            is Expr.Index -> {
                val obj = eval(scope, expr.obj)
                val index = eval(scope, expr.index)
                return when (obj) {
                    is List<*> -> obj[indexOfIndex(obj, index, expr.line)]
                    is String -> {
                        val i = index as? Int ?: throw KtRuntimeError("String index must be Int", expr.line)
                        if (i < 0 || i >= obj.length) throw KtRuntimeError("String index $i out of bounds ${obj.length}", expr.line)
                        obj[i].toString()
                    }
                    is Map<*, *> -> obj[index]
                    is IntRange -> {
                        val i = index as? Int ?: throw KtRuntimeError("Range index must be Int", expr.line)
                        val value = obj.first + i
                        if (i < 0 || value > obj.last) throw KtRuntimeError("Range index out of bounds", expr.line)
                        value
                    }
                    else -> throw KtRuntimeError("Cannot index ${typeName(obj)}", expr.line)
                }
            }
            is Expr.Member -> return memberAccess(scope, expr)
            is Expr.Call -> return evalCall(scope, expr)
        }
    }

    private fun evalBinary(scope: Scope, expr: Expr.Binary): Any? {
        val op = expr.op
        if (op == "&&") {
            val l = eval(scope, expr.l)
            return if (!isTruthy(l, expr.line)) false else isTruthy(eval(scope, expr.r), expr.line)
        }
        if (op == "||") {
            val l = eval(scope, expr.l)
            return if (isTruthy(l, expr.line)) true else isTruthy(eval(scope, expr.r), expr.line)
        }
        val l = eval(scope, expr.l)
        val r = eval(scope, expr.r)
        return when (op) {
            "to" -> Pair(l, r)
            "==" -> ktEquals(l, r)
            "!=" -> !ktEquals(l, r)
            "<", "<=", ">", ">=" -> compareValues(ktCompare(l, r, expr.line), op)
            "+" -> addValues(l, r, expr.line)
            "-" -> arith(l, r, expr.line, "-")
            "*" -> arith(l, r, expr.line, "*")
            "/" -> arith(l, r, expr.line, "/")
            "%" -> arith(l, r, expr.line, "%")
            else -> throw KtRuntimeError("Unknown operator '$op'", expr.line)
        }
    }

    private fun compareValues(cmp: Int, op: String): Boolean = when (op) {
        "<" -> cmp < 0
        "<=" -> cmp <= 0
        ">" -> cmp > 0
        ">=" -> cmp >= 0
        else -> false
    }

    private fun addValues(l: Any?, r: Any?, line: Int): Any? = when {
        l is String -> l + ktToString(r)
        r is String -> ktToString(l) + r
        l is MutableList<*> -> {
            @Suppress("UNCHECKED_CAST")
            val list = l as MutableList<Any?>
            if (r is List<*>) list.addAll(r) else list.add(r)
            list
        }
        else -> arith(l, r, line, "+")
    }

    private fun arith(l: Any?, r: Any?, line: Int, op: String): Any? {
        if (l !is Number || r !is Number) {
            throw KtRuntimeError("Operator '$op' expects numbers, got ${typeName(l)} $op ${typeName(r)}", line)
        }
        return if (l is Double || r is Double) {
            val a = l.toDouble()
            val b = r.toDouble()
            when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b == 0.0) throw KtRuntimeError("Division by zero", line) else a / b
                "%" -> if (b == 0.0) throw KtRuntimeError("Modulo by zero", line) else a % b
                else -> throw KtRuntimeError("Unknown operator '$op'", line)
            }
        } else {
            val a = (l as? Long) ?: (l as Int).toLong()
            val b = (r as? Long) ?: (r as Int).toLong()
            val result: Long = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b == 0L) throw KtRuntimeError("Division by zero", line) else a / b
                "%" -> if (b == 0L) throw KtRuntimeError("Modulo by zero", line) else a % b
                else -> throw KtRuntimeError("Unknown operator '$op'", line)
            }
            if (l is Int && r is Int) result.toInt() else result
        }
    }

    private fun ktCompare(l: Any?, r: Any?, line: Int): Int {
        if (l is Number && r is Number) {
            return l.toDouble().compareTo(r.toDouble())
        }
        if (l is String && r is String) return l.compareTo(r)
        if (l is Boolean && r is Boolean) return l.compareTo(r)
        throw KtRuntimeError("Cannot compare ${typeName(l)} with ${typeName(r)}", line)
    }

    private fun ktEquals(l: Any?, r: Any?): Boolean {
        if (l is List<*> && r is List<*>) {
            if (l.size != r.size) return false
            for (i in l.indices) if (!ktEquals(l[i], r[i])) return false
            return true
        }
        if (l is Map<*, *> && r is Map<*, *>) {
            if (l.size != r.size) return false
            for ((k, v) in l) {
                if (!r.containsKey(k) || !ktEquals(v, r[k])) return false
            }
            return true
        }
        if (l is Number && r is Number) return l.toDouble() == r.toDouble()
        return l == r
    }

    private fun isTruthy(v: Any?, line: Int): Boolean {
        if (v !is Boolean) throw KtRuntimeError("Condition must be Boolean, got ${typeName(v)}", line)
        return v
    }

    // ------------------------------------------------------------------ calls & members

    private fun evalCall(scope: Scope, expr: Expr.Call): Any? {
        val callee = expr.callee
        val args = expr.args.map { eval(scope, it) }

        return when (callee) {
            is Expr.Var -> {
                val fn = scope.get(callee.name, callee.line)
                when (fn) {
                    is KtFunction -> callFunction(fn, args, expr.line)
                    is KtLambda -> callLambda(fn, args, expr.line)
                    is BuiltinFn -> fn.call(args)
                    else -> throw KtRuntimeError("'${callee.name}' is not a function", expr.line)
                }
            }
            is Expr.Member -> {
                val obj = eval(scope, callee.obj)
                memberCall(obj, callee.name, args, expr.line)
            }
            else -> throw KtRuntimeError("Unsupported call expression", expr.line)
        }
    }

    private fun memberAccess(scope: Scope, expr: Expr.Member): Any? {
        val obj = eval(scope, expr.obj)
        return when {
            (obj is List<*> || obj is Map<*, *> || obj is String || obj is IntRange) && expr.name == "size" -> sizeOf(obj)
            obj is String && expr.name == "length" -> obj.length
            obj is Map<*, *> && expr.name == "keys" -> obj.keys.toList()
            obj is Map<*, *> && expr.name == "values" -> obj.values.toList()
            obj is Pair<Any?, Any?> && expr.name == "first" -> obj.first
            obj is Pair<Any?, Any?> && expr.name == "second" -> obj.second
            else -> throw KtRuntimeError(
                "Unknown property '${expr.name}' on ${typeName(obj)} (add parentheses if it is a function)", expr.line
            )
        }
    }

    private fun sizeOf(obj: Any?): Int = when (obj) {
        is List<*> -> obj.size
        is Map<*, *> -> obj.size
        is String -> obj.length
        is IntRange -> rangeSpan(obj).toInt()
        else -> throw KtRuntimeError("No size on ${typeName(obj)}", 0)
    }

    /** Guards against materializing gigantic ranges. */
    private fun rangeSpan(range: IntRange): Long =
        if (range.isEmpty()) 0L else (range.last.toLong() - range.first.toLong() + 1L).also {
            if (it > 10_000_000L) throw KtLimitExceeded("Range of $it elements exceeds the sandbox budget (max 10,000,000)")
        }

    private fun callFunction(fn: KtFunction, args: List<Any?>, line: Int): Any? {
        depth++
        try {
            if (depth > maxDepth) throw KtLimitExceeded("Stack overflow: recursion deeper than $maxDepth frames")
            val scope = Scope(fn.closure)
            bindParams(fn.params, args, scope, line)
            try {
                execBlock(scope, fn.body)
                return KtUnit
            } catch (e: ReturnSignal) {
                return e.value
            }
        } finally {
            depth--
        }
    }

    private fun callLambda(fn: KtLambda, args: List<Any?>, line: Int): Any? {
        depth++
        try {
            if (depth > maxDepth) throw KtLimitExceeded("Stack overflow: recursion deeper than $maxDepth frames")
            val scope = Scope(fn.closure)
            if (fn.params.isEmpty() && args.isNotEmpty()) {
                scope.declare("it", args[0], mutable = false)
            } else {
                bindParams(fn.params, args, scope, line)
            }
            return execBlock(scope, fn.body)
        } finally {
            depth--
        }
    }

    private fun bindParams(params: List<String>, args: List<Any?>, scope: Scope, line: Int) {
        if (params.size != args.size) {
            throw KtRuntimeError("Expected ${params.size} argument(s) but got ${args.size}", line)
        }
        params.forEachIndexed { i, p -> scope.declare(p, args[i], mutable = false) }
    }

    private fun memberCall(obj: Any?, name: String, args: List<Any?>, line: Int): Any? {
        tick()
        return when (obj) {
            is String -> stringMember(obj, name, args, line)
            is MutableList<*> -> listMember(obj, name, args, line)
            is List<*> -> listMember(obj, name, args, line)
            is MutableMap<*, *> -> mapMember(obj, name, args, line)
            is Map<*, *> -> mapMember(obj, name, args, line)
            is IntRange -> rangeMember(obj, name, args, line)
            is Int, is Long, is Double -> numberMember(obj, name, args, line)
            else -> throw KtRuntimeError("Unknown function '$name' on ${typeName(obj)}", line)
        }
    }

    private fun needArgs(args: List<Any?>, count: Int, name: String, line: Int) {
        if (args.size != count) {
            throw KtRuntimeError("$name expects $count argument(s), got ${args.size}", line)
        }
    }

    private fun requireLambda(name: String, args: List<Any?>, line: Int): KtLambda =
        args.lastOrNull() as? KtLambda
            ?: throw KtRuntimeError("$name expects a trailing lambda, e.g. list.map { it * 2 }", line)

    private fun stringMember(s: String, name: String, args: List<Any?>, line: Int): Any? = when (name) {
        "length", "size" -> s.length
        "uppercase" -> s.uppercase()
        "lowercase" -> s.lowercase()
        "trim" -> s.trim()
        "isEmpty" -> s.isEmpty()
        "isBlank" -> s.isBlank()
        "reversed" -> s.reversed()
        "first" -> s.firstOrNull()?.toString() ?: throw KtRuntimeError("String is empty", line)
        "last" -> s.lastOrNull()?.toString() ?: throw KtRuntimeError("String is empty", line)
        "toInt" -> s.trim().toIntOrNull() ?: throw KtRuntimeError("Cannot parse '$s' as Int", line)
        "toDouble" -> s.trim().toDoubleOrNull() ?: throw KtRuntimeError("Cannot parse '$s' as Double", line)
        "contains" -> { needArgs(args, 1, name, line); s.contains(ktToString(args[0])) }
        "startsWith" -> { needArgs(args, 1, name, line); s.startsWith(ktToString(args[0])) }
        "endsWith" -> { needArgs(args, 1, name, line); s.endsWith(ktToString(args[0])) }
        "replace" -> { needArgs(args, 2, name, line); s.replace(ktToString(args[0]), ktToString(args[1])) }
        "split" -> { needArgs(args, 1, name, line); s.split(ktToString(args[0])) }
        "substring" -> {
            if (args.size == 1) {
                val a = args[0] as? Int ?: throw KtRuntimeError("substring expects Int", line)
                s.substring(a.coerceIn(0, s.length))
            } else {
                needArgs(args, 2, name, line)
                val a = args[0] as? Int ?: throw KtRuntimeError("substring expects Int", line)
                val b = args[1] as? Int ?: throw KtRuntimeError("substring expects Int", line)
                s.substring(a.coerceIn(0, s.length), b.coerceIn(0, s.length))
            }
        }
        "indexOf" -> { needArgs(args, 1, name, line); s.indexOf(ktToString(args[0])) }
        "repeat" -> {
            needArgs(args, 1, name, line)
            val n = args[0] as? Int ?: throw KtRuntimeError("repeat expects Int", line)
            if (n < 0 || n.toLong() * s.length > 1_000_000L) throw KtLimitExceeded("repeat produces too-large string")
            s.repeat(n)
        }
        else -> throw KtRuntimeError("Unknown String function '$name'", line)
    }

    private fun listMember(list: List<*>, name: String, args: List<Any?>, line: Int): Any? {
        return when (name) {
            "map" -> { needArgs(args, 1, name, line); list.map { callLambda(requireLambda(name, args, line), listOf(it), line) } }
            "mapIndexed" -> {
                needArgs(args, 1, name, line)
                val fn = requireLambda(name, args, line)
                list.mapIndexed { i, v -> callLambda(fn, listOf(i, v), line) }
            }
            "filter" -> { needArgs(args, 1, name, line); list.filter { isTruthy(callLambda(requireLambda(name, args, line), listOf(it), line), line) } }
            "forEach" -> { needArgs(args, 1, name, line); list.forEach { callLambda(requireLambda(name, args, line), listOf(it), line) }; KtUnit }
            "any" -> {
                if (args.isEmpty()) list.isNotEmpty()
                else list.any { isTruthy(callLambda(requireLambda(name, args, line), listOf(it), line), line) }
            }
            "all" -> { needArgs(args, 1, name, line); list.all { isTruthy(callLambda(requireLambda(name, args, line), listOf(it), line), line) } }
            "count" -> {
                if (args.isEmpty()) list.size
                else list.count { isTruthy(callLambda(requireLambda(name, args, line), listOf(it), line), line) }
            }
            else -> plainListMember(list, name, args, line)
        }
    }

    private fun plainListMember(list: List<*>, name: String, args: List<Any?>, line: Int): Any? {
        val mutable = list as? MutableList<Any?>
        return when (name) {
            "size" -> list.size
            "isEmpty" -> list.isEmpty()
            "isNotEmpty" -> list.isNotEmpty()
            "first" -> list.firstOrNull() ?: throw KtRuntimeError("List is empty", line)
            "last" -> list.lastOrNull() ?: throw KtRuntimeError("List is empty", line)
            "sum" -> numericSum(list, line)
            "average" -> {
                val nums = list.filterIsInstance<Number>()
                if (nums.isEmpty()) Double.NaN else nums.sumOf { it.toDouble() } / nums.size
            }
            "max" -> numericExtremum(list, line, max = true)
            "min" -> numericExtremum(list, line, max = false)
            "sorted" -> list.sortedWith { a, b -> ktCompare(a, b, line) }
            "sortedDescending" -> list.sortedWith { a, b -> -ktCompare(a, b, line) }
            "reversed" -> list.reversed()
            "distinct" -> {
                val seen = mutableListOf<Any?>()
                list.forEach { if (seen.none { s -> ktEquals(s, it) }) seen.add(it) }
                seen
            }
            "take" -> { needArgs(args, 1, name, line); list.take(args[0] as? Int ?: throw KtRuntimeError("take expects Int", line)) }
            "drop" -> { needArgs(args, 1, name, line); list.drop(args[0] as? Int ?: throw KtRuntimeError("drop expects Int", line)) }
            "contains" -> { needArgs(args, 1, name, line); list.any { ktEquals(it, args[0]) } }
            "indexOf" -> { needArgs(args, 1, name, line); list.indexOfFirst { ktEquals(it, args[0]) } }
            "joinToString" -> list.joinToString(if (args.isEmpty()) ", " else ktToString(args[0])) { ktToString(it) }
            "toList" -> list.toList()
            "toMutableList" -> list.toMutableList()
            "add" -> {
                needArgs(args, 1, name, line)
                mutable ?: throw KtRuntimeError("add() needs mutableListOf()", line)
                mutable.add(args[0]); KtUnit
            }
            "remove" -> {
                needArgs(args, 1, name, line)
                mutable ?: throw KtRuntimeError("remove() needs mutableListOf()", line)
                val idx = mutable.indexOfFirst { ktEquals(it, args[0]) }
                if (idx >= 0) mutable.removeAt(idx); KtUnit
            }
            "clear" -> { mutable ?: throw KtRuntimeError("clear() needs mutableListOf()", line); mutable.clear(); KtUnit }
            else -> throw KtRuntimeError("Unknown List function '$name'", line)
        }
    }

    private fun numericSum(list: List<*>, line: Int): Any? {
        var any = false
        var hasDouble = false
        var longSum = 0L
        var doubleSum = 0.0
        for (item in list) {
            when (item) {
                is Int -> { any = true; longSum += item }
                is Long -> { any = true; longSum += item }
                is Double -> { any = true; hasDouble = true; doubleSum += item }
                else -> throw KtRuntimeError("sum() expects numbers, found ${typeName(item)}", line)
            }
        }
        if (!any) throw KtRuntimeError("sum() of empty list", line)
        return if (hasDouble) doubleSum + longSum else longSum
    }

    private fun numericExtremum(list: List<*>, line: Int, max: Boolean): Any? {
        var best: Any? = null
        for (item in list) {
            if (item !is Number) throw KtRuntimeError("max()/min() expects numbers", line)
            if (best == null) {
                best = item
            } else {
                val cmp = ktCompare(item, best, line)
                if ((max && cmp > 0) || (!max && cmp < 0)) best = item
            }
        }
        return best
    }

    private fun mapMember(map: Map<*, *>, name: String, args: List<Any?>, line: Int): Any? {
        val mutable = map as? MutableMap<Any?, Any?>
        return when (name) {
            "size" -> map.size
            "isEmpty" -> map.isEmpty()
            "isNotEmpty" -> map.isNotEmpty()
            "containsKey" -> { needArgs(args, 1, name, line); map.containsKey(args[0]) }
            "containsValue" -> { needArgs(args, 1, name, line); map.values.any { ktEquals(it, args[0]) } }
            "get" -> { needArgs(args, 1, name, line); map[args[0]] }
            "put" -> {
                needArgs(args, 2, name, line)
                mutable ?: throw KtRuntimeError("put() needs mutableMapOf()", line)
                mutable.put(args[0], args[1])
            }
            "remove" -> {
                needArgs(args, 1, name, line)
                mutable ?: throw KtRuntimeError("remove() needs mutableMapOf()", line)
                mutable.remove(args[0])
            }
            else -> throw KtRuntimeError("Unknown Map function '$name'", line)
        }
    }

    private fun rangeMember(range: IntRange, name: String, args: List<Any?>, line: Int): Any? = when (name) {
        "toList" -> {
            rangeSpan(range)
            range.toList()
        }
        "size" -> rangeSpan(range).toInt()
        "sum" -> { rangeSpan(range); range.sum() }
        "average" -> { rangeSpan(range); if (range.isEmpty()) Double.NaN else range.average() }
        "reversed" -> { rangeSpan(range); range.reversed().toList() }
        "first" -> range.firstOrNull() ?: throw KtRuntimeError("Empty range", line)
        "last" -> range.lastOrNull() ?: throw KtRuntimeError("Empty range", line)
        "contains" -> { needArgs(args, 1, name, line); range.contains(args[0] as? Int ?: throw KtRuntimeError("contains expects Int", line)) }
        else -> throw KtRuntimeError("Unknown Range function '$name'", line)
    }

    private fun numberMember(n: Any?, name: String, args: List<Any?>, line: Int): Any? = when (name) {
        "toInt" -> when (n) {
            is Double -> n.toInt()
            is Int -> n
            is Long -> n.toInt()
            else -> throw KtRuntimeError("toInt on ${typeName(n)}", line)
        }
        "toDouble" -> when (n) {
            is Double -> n
            is Int -> n.toDouble()
            is Long -> n.toDouble()
            else -> throw KtRuntimeError("toDouble on ${typeName(n)}", line)
        }
        else -> throw KtRuntimeError("Unknown Number function '$name'", line)
    }

    // ------------------------------------------------------------------ string templates

    private fun expandString(scope: Scope, raw: String): String {
        if ('\\' !in raw && '$' !in raw) return raw
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length && raw[i + 1] == '$' -> {
                    sb.append('$')
                    i += 2
                }
                c == '$' && i + 1 < raw.length -> {
                    if (raw[i + 1] == '{') {
                        var depthB = 1
                        var j = i + 2
                        while (j < raw.length && depthB > 0) {
                            if (raw[j] == '{') depthB++
                            if (raw[j] == '}') depthB--
                            if (depthB > 0) j++
                        }
                        if (depthB != 0) throw KtRuntimeError("Unterminated \${...} in string template", 0)
                        val inner = raw.substring(i + 2, j)
                        val expr = MiniKotlinParser.parseSingleExpression(inner)
                        sb.append(ktToString(eval(scope, expr)))
                        i = j + 1
                    } else {
                        var j = i + 1
                        while (j < raw.length && (raw[j].isLetterOrDigit() || raw[j] == '_')) j++
                        if (j == i + 1) {
                            sb.append('$')
                            i++
                        } else {
                            val name = raw.substring(i + 1, j)
                            sb.append(ktToString(scope.get(name, 0)))
                            i = j
                        }
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------ value helpers

    private fun typeName(v: Any?): String = when (v) {
        null -> "null"
        is Int -> "Int"
        is Long -> "Long"
        is Double -> "Double"
        is Boolean -> "Boolean"
        is String -> "String"
        is List<*> -> "List"
        is Map<*, *> -> "Map"
        is IntRange -> "IntRange"
        is Pair<*, *> -> "Pair"
        is KtFunction, is KtLambda, is BuiltinFn -> "Function"
        KtUnit -> "Unit"
        else -> v::class.simpleName ?: "Any"
    }

    fun ktToString(v: Any?): String = when (v) {
        null -> "null"
        is String -> v
        is Boolean -> v.toString()
        is Int, is Long -> v.toString()
        is Double -> formatDouble(v)
        is List<*> -> v.joinToString(", ", "[", "]") { ktToString(it) }
        is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { "${ktToString(it.key)}=${ktToString(it.value)}" }
        is IntRange -> "${v.first}..${v.last}"
        is Pair<*, *> -> "(${ktToString(v.first)}, ${ktToString(v.second)})"
        is KtFunction, is KtLambda -> "Function"
        KtUnit -> "kotlin.Unit"
        else -> v.toString()
    }

    private fun formatDouble(d: Double): String =
        if (d.isFinite() && d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) {
            "%.1f".format(d)
        } else {
            d.toString()
        }

    private fun finalizeOutput(): String {
        if (out.length <= maxOutputChars) return out.toString()
        return out.substring(0, maxOutputChars) + "\n…[output truncated at $maxOutputChars characters]"
    }

    // ------------------------------------------------------------------ builtins

    private fun printlnFn(newline: Boolean) = BuiltinFn { args ->
        val line = args.joinToString(" ") { ktToString(it) }
        if (out.length + line.length + 1 > maxOutputChars) {
            throw KtLimitExceeded("Output limit exceeded ($maxOutputChars characters)")
        }
        out.append(line)
        if (newline) out.append('\n')
        KtUnit
    }

    private fun registerBuiltins() {
        globals.declare("println", printlnFn(true), mutable = false)
        globals.declare("print", printlnFn(false), mutable = false)
        globals.declare("listOf", BuiltinFn { args -> ArrayList<Any?>(args) }, mutable = false)
        globals.declare("mutableListOf", BuiltinFn { args -> ArrayList<Any?>(args) }, mutable = false)
        globals.declare("setOf", BuiltinFn { args -> ArrayList<Any?>(args.distinctBy { it }) }, mutable = false)
        globals.declare("emptyList", BuiltinFn { _ -> ArrayList<Any?>() }, mutable = false)
        globals.declare("listOfNotNull", BuiltinFn { args -> ArrayList<Any?>(args.filterNotNull()) }, mutable = false)
        globals.declare("mapOf", BuiltinFn { args ->
            val map = LinkedHashMap<Any?, Any?>()
            args.forEach { p ->
                if (p !is Pair<*, *>) throw KtRuntimeError("mapOf expects 'key to value' pairs", 0)
                @Suppress("UNCHECKED_CAST")
                map[(p as Pair<Any?, Any?>).first] = p.second
            }
            map
        }, mutable = false)
        globals.declare("mutableMapOf", BuiltinFn { args ->
            val map = LinkedHashMap<Any?, Any?>()
            args.forEach { p ->
                if (p !is Pair<*, *>) throw KtRuntimeError("mutableMapOf expects 'key to value' pairs", 0)
                @Suppress("UNCHECKED_CAST")
                map[(p as Pair<Any?, Any?>).first] = p.second
            }
            map
        }, mutable = false)
        globals.declare("abs", BuiltinFn { args ->
            when (val v = args.firstOrNull()) {
                is Int -> kotlin.math.abs(v)
                is Long -> kotlin.math.abs(v)
                is Double -> kotlin.math.abs(v)
                else -> throw KtRuntimeError("abs expects a number", 0)
            }
        }, mutable = false)
        globals.declare("minOf", BuiltinFn { args ->
            val a = args.getOrNull(0)
            val b = args.getOrNull(1)
            if (ktCompare(a, b, 0) <= 0) a else b
        }, mutable = false)
        globals.declare("maxOf", BuiltinFn { args ->
            val a = args.getOrNull(0)
            val b = args.getOrNull(1)
            if (ktCompare(a, b, 0) >= 0) a else b
        }, mutable = false)
        globals.declare("sqrt", BuiltinFn { args ->
            val v = args.firstOrNull() as? Number ?: throw KtRuntimeError("sqrt expects a number", 0)
            kotlin.math.sqrt(v.toDouble())
        }, mutable = false)
        globals.declare("floor", BuiltinFn { args ->
            val v = args.firstOrNull() as? Number ?: throw KtRuntimeError("floor expects a number", 0)
            kotlin.math.floor(v.toDouble())
        }, mutable = false)
        globals.declare("ceil", BuiltinFn { args ->
            val v = args.firstOrNull() as? Number ?: throw KtRuntimeError("ceil expects a number", 0)
            kotlin.math.ceil(v.toDouble())
        }, mutable = false)
        globals.declare("round", BuiltinFn { args ->
            val v = args.firstOrNull() as? Number ?: throw KtRuntimeError("round expects a number", 0)
            kotlin.math.round(v.toDouble())
        }, mutable = false)
    }
}

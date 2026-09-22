package com.example.troc.data.sandbox.code

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.concurrent.atomic.AtomicBoolean

class JsScriptException(message: String) : Exception(message)

data class JsExecutionResult(val output: String)

/**
 * Sandboxed JavaScript execution on top of Rhino:
 *  - interpreter mode (optimizationLevel = -1) so every loop passes the instruction observer;
 *  - a hard wall-clock deadline enforced by [SandboxedFactory.observeInstructionCount];
 *  - cooperative cancellation via an [AtomicBoolean];
 *  - Java access removed twice over: bridge globals are deleted AND a ClassShutter
 *    denies every class resolution (no `java.lang.Runtime`, no `Packages`, nothing).
 */
class JsEngine {

    class CancelledException : RuntimeException("Execution cancelled")
    class JsTimeoutException : RuntimeException("Execution timed out")

    @Volatile private var deadlineNanos = 0L
    private val cancelled = AtomicBoolean(false)

    private inner class SandboxedFactory : ContextFactory() {
        override fun makeContext(): Context {
            val cx = SandboxedContext()
            cx.languageVersion = Context.VERSION_ES6
            cx.optimizationLevel = -1
            cx.instructionObserverThreshold = 500
            return cx
        }
    }

    private inner class SandboxedContext : Context() {
        override fun observeInstructionCount(instructionCount: Int) {
            if (cancelled.get()) throw CancelledException()
            if (deadlineNanos > 0 && System.nanoTime() > deadlineNanos) throw JsTimeoutException()
        }
    }

    private val factory = SandboxedFactory()

    fun cancel() {
        cancelled.set(true)
    }

    fun execute(code: String, timeoutMs: Long): JsExecutionResult {
        cancelled.set(false)
        deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L
        val out = StringBuilder()
        val cx = factory.enterContext()
        try {
            cx.classShutter = ClassShutter { false }
            val global = cx.initStandardObjects()

            // Remove every Java-bridge and shell-only global.
            for (name in BLOCKED_GLOBALS) {
                global.delete(name)
            }

            val printer = object : BaseFunction() {
                override fun call(
                    cx: Context,
                    scope: Scriptable,
                    thisObj: Scriptable,
                    args: Array<Any?>
                ): Any? {
                    if (out.length > 200_000) throw JsScriptException("Output limit exceeded (200,000 characters)")
                    out.append(args.joinToString(" ") { Context.toString(it) })
                    return Undefined.instance
                }
            }
            ScriptableObject.putProperty(global, "println", printer)
            ScriptableObject.putProperty(global, "print", printer)
            val console = cx.newObject(global)
            ScriptableObject.putProperty(console, "log", printer)
            ScriptableObject.putProperty(console, "info", printer)
            ScriptableObject.putProperty(console, "warn", printer)
            ScriptableObject.putProperty(console, "error", printer)
            ScriptableObject.putProperty(global, "console", console)

            // Script vars live in a child scope; the standard scope stays pristine.
            val scriptScope = cx.newObject(global)
            scriptScope.prototype = global
            scriptScope.parentScope = null

            val result = cx.evaluateString(scriptScope, code, "troc-sandbox.js", 1, null)
            if (result != null && result != Undefined.instance) {
                out.append("=> ").append(Context.toString(result)).append('\n')
            }
            return JsExecutionResult(out.toString())
        } catch (e: CancelledException) {
            throw e
        } catch (e: JsTimeoutException) {
            throw e
        } catch (e: RhinoException) {
            val line = if (e.lineNumber() > 0) " (line ${e.lineNumber()})" else ""
            throw JsScriptException("JS error$line: ${e.message}")
        } finally {
            Context.exit()
        }
    }

    private companion object {
        val BLOCKED_GLOBALS = listOf(
            "Packages", "java", "javax", "javafx", "org", "com", "net", "edu",
            "JavaAdapter", "JavaImporter", "XML", "XMLList", "Namespace", "QName",
            "load", "readFile", "readUrl", "quit", "defineClass", "sync", "deserialize",
            "help", "environment", "arguments", "version", "printErr", "seal", "freeze"
        )
    }
}

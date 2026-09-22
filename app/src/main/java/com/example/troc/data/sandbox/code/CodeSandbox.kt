package com.example.troc.data.sandbox.code

import com.example.troc.data.sandbox.security.SandboxSecurityManager
import com.example.troc.domain.model.SandboxLanguage
import com.example.troc.domain.model.SandboxResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** Internal wrapper that turns every engine failure into a user-facing message. */
internal class SandboxCodeException(message: String) : Exception(message)

/**
 * Executes code snippets in isolated, budgeted interpreters.
 *
 *  - Kotlin  → [MiniKotlin], a safe-subset interpreter with step/deadline budgets.
 *  - JavaScript → [JsEngine] (Rhino, class-shuttered, instruction-observed).
 *  - Python  → intentionally not bundled (Chaquopy adds ~20 MB); the error message
 *    points at the README section that explains how to enable it.
 */
@Singleton
class CodeSandbox @Inject constructor(
    private val security: SandboxSecurityManager
) {

    private val jsEngine = JsEngine()
    private val activeKotlinRun = AtomicReference<MiniKotlin?>(null)

    /** Best-effort cooperative stop for the currently running snippet. */
    fun cancelActive() {
        jsEngine.cancel()
        activeKotlinRun.get()?.cancel()
    }

    suspend fun execute(
        code: String,
        language: SandboxLanguage,
        timeoutMs: Long
    ): SandboxResult {
        security.validateInput(code)?.let { return SandboxResult.Failure(it) }
        if (language == SandboxLanguage.KOTLIN) {
            security.checkKotlinCode(code)?.let { reason -> return SandboxResult.Failure(reason) }
        }

        val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L
        val start = System.currentTimeMillis()

        val output: String? = withContext(Dispatchers.Default) {
            withTimeoutOrNull(timeoutMs) { runEngine(code, language, timeoutMs, deadlineNanos) }
        }

        return when (output) {
            null -> SandboxResult.Failure("Execution timed out after ${timeoutMs / 1000}s", elapsed(start))
            else -> SandboxResult.Success(output, elapsed(start))
        }
    }

    /** Runs the selected engine; every failure becomes [SandboxCodeException]. */
    private suspend fun runEngine(
        code: String,
        language: SandboxLanguage,
        timeoutMs: Long,
        deadlineNanos: Long
    ): String {
        try {
            return when (language) {
                SandboxLanguage.KOTLIN -> {
                    val run = MiniKotlin(
                        maxSteps = 20_000_000L,
                        maxOutputChars = 200_000,
                        deadlineNanos = deadlineNanos
                    )
                    activeKotlinRun.set(run)
                    try {
                        run.run(code).output
                    } finally {
                        activeKotlinRun.compareAndSet(run, null)
                    }
                }
                SandboxLanguage.JAVASCRIPT -> jsEngine.execute(code, timeoutMs).output
                SandboxLanguage.PYTHON -> throw SandboxCodeException(PYTHON_MESSAGE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KtLimitExceeded) {
            throw SandboxCodeException(e.message ?: "Execution limit exceeded")
        } catch (e: KtRuntimeError) {
            throw SandboxCodeException(e.message ?: "Runtime error")
        } catch (e: KtSyntaxError) {
            throw SandboxCodeException("Syntax error: ${e.message}")
        } catch (e: JsScriptException) {
            throw SandboxCodeException(e.message ?: "JS error")
        } catch (e: JsEngine.JsTimeoutException) {
            throw SandboxCodeException("Execution timed out after ${timeoutMs / 1000}s")
        } catch (e: JsEngine.CancelledException) {
            throw SandboxCodeException("Execution cancelled")
        } catch (e: StackOverflowError) {
            throw SandboxCodeException("Stack overflow: recursion too deep")
        } catch (e: Exception) {
            throw SandboxCodeException("Execution failed: ${e.message}")
        }
    }

    private fun elapsed(start: Long): Long = System.currentTimeMillis() - start

    companion object {
        const val PYTHON_MESSAGE =
            "Python isn't bundled with this build (the Chaquopy runtime adds ~20 MB to the APK). " +
                "Enable it via the README, or use the Kotlin / JavaScript sandboxes meanwhile."
    }
}

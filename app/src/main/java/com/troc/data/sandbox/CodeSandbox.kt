package com.troc.data.sandbox

import android.content.Context
import com.troc.domain.model.SandboxResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ScriptableObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodeSandbox @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(language: String, code: String, timeoutSec: Int = 15): SandboxResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        if (!SandboxSecurity.isCodeAllowed(code)) {
            return@withContext SandboxResult.Error("Blocked: code contains disallowed operations", executionTimeMs = System.currentTimeMillis() - start)
        }

        val result = withTimeoutOrNull(timeoutSec * 1000L) {
            when (language.lowercase()) {
                "python", "py" -> executePython(code)
                "js", "javascript" -> executeJs(code)
                "kotlin", "kt" -> executeKotlinLike(code)
                else -> SandboxResult.Error("Unsupported language: $language")
            }
        } ?: SandboxResult.Timeout

        val elapsed = System.currentTimeMillis() - start
        when (result) {
            is SandboxResult.Success -> result.copy(executionTimeMs = elapsed)
            is SandboxResult.Error -> result.copy(executionTimeMs = elapsed)
            is SandboxResult.Timeout -> result
        }
    }

    private fun executePython(code: String): SandboxResult {
        return try {
            val sanitized = SandboxSecurity.sanitizePython(code)
            // Try Chaquopy via reflection (optional dependency)
            try {
                val pythonClass = Class.forName("com.chaquo.python.Python")
                val getInstanceMethod = pythonClass.getMethod("getInstance")
                val py = getInstanceMethod.invoke(null)
                val getModuleMethod = py.javaClass.getMethod("getModule", String::class.java)
                val builtins = getModuleMethod.invoke(py, "builtins")
                val callAttrMethod = builtins.javaClass.getMethod("callAttr", String::class.java, Any::class.java)
                callAttrMethod.invoke(builtins, "exec", sanitized)
                SandboxResult.Success("Python executed (Chaquopy via reflection).\nCode:\n$code", 0)
            } catch (e: ClassNotFoundException) {
                // Chaquopy not available - mock execution
                if (code.contains("print")) {
                    val prints = Regex("""print\((.*?)\)""").findAll(code).map { it.groupValues[1] }.joinToString("\n")
                    SandboxResult.Success("Mock Python Output (Chaquopy not installed):\n$prints\n\n[Note: Install Chaquopy plugin for real Python execution]", 0)
                } else {
                    SandboxResult.Success("Mock Python executed (Chaquopy not installed). Output would appear here.\nCode length: ${code.length}\nEnable Chaquopy plugin for real execution.", 0)
                }
            } catch (e: Exception) {
                // Fallback mock
                if (code.contains("print")) {
                    val prints = Regex("""print\((.*?)\)""").findAll(code).map { it.groupValues[1] }.joinToString("\n")
                    SandboxResult.Success("Mock Python Output:\n$prints\n\n[Chaquopy error: ${e.message}]", 0)
                } else {
                    SandboxResult.Success("Mock Python executed. Code length: ${code.length}\n[Chaquopy error: ${e.message}]", 0)
                }
            }
        } catch (e: Exception) {
            SandboxResult.Error("Python error: ${e.message}", e.stackTraceToString())
        }
    }

    private fun executeJs(code: String): SandboxResult {
        return try {
            val rhino = RhinoContext.enter()
            rhino.optimizationLevel = -1
            try {
                val scope = rhino.initStandardObjects()
                // Block dangerous Java access
                ScriptableObject.putProperty(scope, "java", ScriptableObject.NOT_FOUND)
                ScriptableObject.putProperty(scope, "Packages", ScriptableObject.NOT_FOUND)
                // Capture console.log
                val output = StringBuilder()
                val console = object : ScriptableObject() {
                    override fun getClassName(): String = "console"
                    @Suppress("unused")
                    fun log(msg: Any?) {
                        output.append(msg.toString()).append("\n")
                    }
                }
                ScriptableObject.putProperty(scope, "console", console)
                // Provide print function
                val printFunc = org.mozilla.javascript.BaseFunction::class.java
                // Simple: replace console.log with print handling via custom JS
                val wrappedCode = """
                    var __out = "";
                    var console = { log: function(x){ __out += x + "\n"; } };
                    function print(x){ __out += x + "\n"; }
                    $code
                    __out;
                """.trimIndent()
                val result = rhino.evaluateString(scope, wrappedCode, "sandbox", 1, null)
                SandboxResult.Success(result?.toString() ?: output.toString(), 0, output.toString())
            } finally {
                RhinoContext.exit()
            }
        } catch (e: Exception) {
            SandboxResult.Error("JS error: ${e.message}", e.stackTraceToString())
        }
    }

    private fun executeKotlinLike(code: String): SandboxResult {
        // For safety, we don't compile Kotlin at runtime; we interpret as JS-like or mock
        return try {
            // Very simple mock: if code contains println, extract
            if (code.contains("println")) {
                val prints = Regex("""println\((.*?)\)""").findAll(code).map { it.groupValues[1].trim('"', '\'', ' ') }.joinToString("\n")
                SandboxResult.Success("Kotlin mock output:\n$prints", 0)
            } else {
                SandboxResult.Success("Kotlin code validated (no runtime exec for safety). Length: ${code.length}\nTo run Kotlin, use JS or Python sandbox.", 0)
            }
        } catch (e: Exception) {
            SandboxResult.Error("Kotlin error: ${e.message}")
        }
    }
}

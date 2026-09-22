package com.example.troc.data.sandbox

import com.example.troc.data.sandbox.code.CodeSandbox
import com.example.troc.data.sandbox.data.DataSandbox
import com.example.troc.data.sandbox.file.FileSandbox
import com.example.troc.data.sandbox.security.SandboxSecurityManager
import com.example.troc.domain.model.SandboxLanguage
import com.example.troc.domain.model.SandboxRequest
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.SandboxTool
import com.example.troc.domain.model.WorkflowStep
import com.example.troc.domain.repository.WorkflowRunner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs multi-step agent workflows: steps execute in order and the previous
 * step's output can be injected into later inputs via `{{result}}`
 * (or `{{step1}}`, `{{step2}}`, …).
 */
@Singleton
class WorkflowExecutor @Inject constructor(
    private val security: SandboxSecurityManager,
    private val codeSandbox: CodeSandbox,
    private val dataSandbox: DataSandbox,
    private val fileSandbox: FileSandbox
) : WorkflowRunner {

    override suspend fun execute(
        steps: List<WorkflowStep>,
        onStepResult: suspend (index: Int, result: SandboxResult) -> Unit
    ): List<SandboxResult> {
        val outputs = mutableListOf<String>()
        val results = mutableListOf<SandboxResult>()
        steps.forEachIndexed { index, step ->
            val input = substitute(step.input, outputs)
            val request = SandboxRequest(
                tool = step.tool,
                language = step.language.takeIf { step.tool == SandboxTool.CODE },
                code = input.takeIf { step.tool == SandboxTool.CODE },
                data = input.takeIf { step.tool == SandboxTool.DATA || step.tool == SandboxTool.FILE },
                dataFormat = guessFormat(input),
                operation = if (step.tool == SandboxTool.FILE) "read" else "summarize",
                column = null,
                params = emptyMap()
            )
            val result = runSingle(request)
            results += result
            outputs += result.text()
            onStepResult(index, result)
        }
        return results
    }

    suspend fun runSingle(request: SandboxRequest): SandboxResult {
        val timeoutMs = security.clampTimeoutMs(request.timeoutMs)
        return when (request.tool) {
            SandboxTool.CODE -> codeSandbox.execute(
                code = request.code.orEmpty(),
                language = request.language ?: SandboxLanguage.JAVASCRIPT,
                timeoutMs = timeoutMs
            )
            SandboxTool.DATA -> dataSandbox.execute(
                data = request.data,
                dataFormat = request.dataFormat,
                operation = request.operation,
                column = request.column,
                params = request.params
            )
            SandboxTool.FILE -> fileSandbox.execute(
                uriString = request.uri,
                fileName = request.fileName,
                action = request.fileAction,
                pattern = request.regexPattern,
                replacement = request.regexReplacement,
                content = request.data
            )
            SandboxTool.CUSTOM -> SandboxResult.Failure("Nested workflows are not supported.")
        }
    }

    fun substitute(input: String, outputs: List<String>): String {
        var result = input.replace("{{result}}", outputs.lastOrNull().orEmpty())
        outputs.forEachIndexed { i, output ->
            result = result.replace("{{step${i + 1}}}", output)
        }
        return result
    }

    fun guessFormat(text: String): String {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) "json" else "csv"
    }
}

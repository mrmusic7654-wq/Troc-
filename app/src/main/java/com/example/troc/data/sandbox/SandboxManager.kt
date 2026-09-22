package com.example.troc.data.sandbox

import com.example.troc.data.sandbox.code.CodeSandbox
import com.example.troc.domain.model.SandboxRequest
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.SandboxTool
import com.example.troc.domain.repository.SandboxGateway
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade and single-flight guard in front of the sandbox executors.
 * One sandbox request runs at a time; [cancelActive] stops the current one.
 */
@Singleton
class SandboxManager @Inject constructor(
    private val codeSandbox: CodeSandbox,
    private val workflowExecutor: WorkflowExecutor
) : SandboxGateway {

    private val mutex = Mutex()

    /** Best-effort cooperative stop of the running snippet. */
    override fun cancelActive() {
        codeSandbox.cancelActive()
    }

    override suspend fun execute(request: SandboxRequest): SandboxResult = mutex.withLock {
        if (request.tool == SandboxTool.CUSTOM) {
            // Flatten a single-request workflow: run each step and merge the report.
            val results = workflowExecutor.execute(request.steps)
            val report = results.mapIndexed { i, r ->
                val status = if (r.isSuccessful) "✅" else "❌"
                "$status Step ${i + 1}\n${r.text().take(4_000)}"
            }.joinToString("\n\n---\n\n")
            SandboxResult.Success(report, results.sumOf { it.durationMs })
        } else {
            workflowExecutor.runSingle(request)
        }
    }
}

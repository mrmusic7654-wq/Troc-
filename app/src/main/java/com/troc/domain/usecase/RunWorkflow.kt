package com.troc.domain.usecase

import com.troc.data.repository.SandboxRepository
import com.troc.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class RunWorkflow @Inject constructor(
    private val sandboxRepository: SandboxRepository,
    private val executeSandbox: ExecuteSandbox
) {
    fun runWorkflow(workflow: Workflow): Flow<Pair<WorkflowStep, SandboxResult>> = flow {
        val variables = mutableMapOf<String, String>()

        for (step in workflow.steps) {
            // Replace {{var}} templates
            var input = step.inputTemplate
            variables.forEach { (k, v) ->
                input = input.replace("{{${k}}}", v)
                input = input.replace("{{$k}}", v)
            }

            val result = when (step.tool) {
                ToolType.CODE -> executeSandbox.executeCode(step.language, input)
                ToolType.DATA -> {
                    val stats = executeSandbox.executeData(input)
                    SandboxResult.Success("Stats: $stats", 0)
                }
                ToolType.FILE -> executeSandbox.executeFile("regex_replace", input)
                ToolType.CUSTOM -> SandboxResult.Success("Custom: $input", 0)
            }

            // Store output variable
            val outputStr = when (result) {
                is SandboxResult.Success -> result.output
                is SandboxResult.Error -> "Error: ${result.message}"
                is SandboxResult.Timeout -> "Timeout"
            }
            variables[step.outputVarName] = outputStr

            emit(step to result)
        }
    }
}

package com.troc.domain.model

import java.util.UUID

data class WorkflowStep(
    val id: String = UUID.randomUUID().toString(),
    val tool: ToolType = ToolType.CODE,
    val language: String = "python",
    val inputTemplate: String = "", // may contain {{varName}}
    val outputVarName: String = "output_${UUID.randomUUID().toString().take(4)}",
    val description: String = ""
)

data class Workflow(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<WorkflowStep>,
    val createdAt: Long = System.currentTimeMillis()
)

data class WorkflowExecutionResult(
    val stepResults: List<Pair<WorkflowStep, SandboxResult>>,
    val finalOutput: String
)

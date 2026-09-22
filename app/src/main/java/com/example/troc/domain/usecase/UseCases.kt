package com.example.troc.domain.usecase

import com.example.troc.domain.model.ChatRequestSpec
import com.example.troc.domain.model.ModelInfo
import com.example.troc.domain.model.SandboxRequest
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.StreamEvent
import com.example.troc.domain.model.WorkflowStep
import com.example.troc.domain.repository.MistralClient
import com.example.troc.domain.repository.SandboxGateway
import com.example.troc.domain.repository.WorkflowRunner
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams a chat completion from the configured Mistral endpoint. */
class StreamChatUseCase @Inject constructor(
    private val client: MistralClient
) {
    operator fun invoke(spec: ChatRequestSpec): Flow<StreamEvent> = client.stream(spec)
}

/** Validates an API key by listing the models the account can access. */
class ListModelsUseCase @Inject constructor(
    private val client: MistralClient
) {
    suspend operator fun invoke(baseUrl: String, apiKey: String): Result<List<ModelInfo>> =
        runCatching { client.listModels(baseUrl, apiKey) }
}

/** Executes a single sandbox request (code / data / file / workflow). */
class ExecuteSandboxUseCase @Inject constructor(
    private val sandbox: SandboxGateway
) {
    suspend operator fun invoke(request: SandboxRequest): SandboxResult = sandbox.execute(request)
}

/** Runs a multi-step agent workflow in sequence. */
class RunWorkflowUseCase @Inject constructor(
    private val runner: WorkflowRunner
) {
    suspend operator fun invoke(
        steps: List<WorkflowStep>,
        onStepResult: suspend (Int, SandboxResult) -> Unit = { _, _ -> }
    ): List<SandboxResult> = runner.execute(steps, onStepResult)
}

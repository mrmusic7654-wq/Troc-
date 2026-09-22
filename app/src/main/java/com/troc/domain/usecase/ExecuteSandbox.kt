package com.troc.domain.usecase

import com.troc.data.repository.SandboxRepository
import com.troc.domain.model.SandboxResult
import javax.inject.Inject

class ExecuteSandbox @Inject constructor(
    private val sandboxRepository: SandboxRepository
) {
    suspend fun executeCode(language: String, code: String, timeoutSec: Int = 15): SandboxResult {
        return sandboxRepository.executeCode(language, code, timeoutSec)
    }

    suspend fun executeData(csvContent: String) = sandboxRepository.analyzeData(csvContent)

    suspend fun executeFile(operation: String, input: String) = sandboxRepository.executeFileOperation(operation, input)
}

package com.troc.data.repository

import com.google.gson.Gson
import com.troc.data.db.SandboxDao
import com.troc.data.db.SandboxSessionEntity
import com.troc.data.db.WorkflowDao
import com.troc.data.db.SavedWorkflowEntity
import com.troc.data.sandbox.CodeSandbox
import com.troc.data.sandbox.DataSandbox
import com.troc.data.sandbox.FileSandbox
import com.troc.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SandboxRepository @Inject constructor(
    private val sandboxDao: SandboxDao,
    private val workflowDao: WorkflowDao,
    private val codeSandbox: CodeSandbox,
    private val dataSandbox: DataSandbox,
    private val fileSandbox: FileSandbox,
    private val gson: Gson
) {
    fun observeSessions(): Flow<List<SandboxSessionEntity>> = sandboxDao.observeSessions()

    fun observeSessionsForChat(chatId: String): Flow<List<SandboxSessionEntity>> = sandboxDao.observeSessionsForChat(chatId)

    suspend fun executeCode(language: String, code: String, timeoutSec: Int = 15): SandboxResult {
        val result = codeSandbox.execute(language, code, timeoutSec)
        // Persist
        val entity = SandboxSessionEntity(
            id = UUID.randomUUID().toString(),
            chatId = null,
            toolType = "CODE",
            language = language,
            input = code,
            output = (result as? SandboxResult.Success)?.output,
            error = (result as? SandboxResult.Error)?.message,
            executionTimeMs = when (result) {
                is SandboxResult.Success -> result.executionTimeMs
                is SandboxResult.Error -> result.executionTimeMs
                else -> 0
            },
            createdAt = System.currentTimeMillis()
        )
        sandboxDao.insertSession(entity)
        return result
    }

    suspend fun analyzeData(csvContent: String): DataStats {
        return dataSandbox.analyzeCsv(csvContent)
    }

    suspend fun executeFileOperation(operation: String, input: String): SandboxResult {
        return fileSandbox.execute(operation, input)
    }

    // Workflows
    fun observeWorkflows(): Flow<List<SavedWorkflowEntity>> = workflowDao.observeWorkflows()

    suspend fun saveWorkflow(workflow: Workflow) {
        val json = gson.toJson(workflow.steps)
        workflowDao.insertWorkflow(SavedWorkflowEntity(workflow.id, workflow.name, json, workflow.createdAt))
    }

    suspend fun deleteWorkflow(id: String) = workflowDao.deleteWorkflow(id)

    suspend fun getWorkflow(id: String): Workflow? {
        val entity = workflowDao.getWorkflow(id) ?: return null
        return try {
            val steps = gson.fromJson(entity.stepsJson, Array<WorkflowStep>::class.java).toList()
            Workflow(entity.id, entity.name, steps, entity.createdAt)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clearAllSessions() = sandboxDao.clearAll()
}

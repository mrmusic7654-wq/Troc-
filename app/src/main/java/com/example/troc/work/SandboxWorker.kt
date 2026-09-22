package com.example.troc.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.troc.data.sandbox.WorkflowExecutor
import com.example.troc.domain.model.SandboxLanguage
import com.example.troc.domain.model.SandboxRequest
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.SandboxTool
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Runs long sandbox jobs (e.g. big CSV crunching) outside the UI process lifetime,
 * keeping the app responsive and ANR-free.
 */
@HiltWorker
class SandboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val workflowExecutor: WorkflowExecutor
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tool = inputData.getString(KEY_TOOL) ?: SandboxTool.CODE.name
        val request = when (tool) {
            SandboxTool.CODE.name -> SandboxRequest(
                tool = SandboxTool.CODE,
                language = inputData.getString(KEY_LANGUAGE)
                    ?.let { runCatching { SandboxLanguage.valueOf(it) }.getOrNull() }
                    ?: SandboxLanguage.JAVASCRIPT,
                code = inputData.getString(KEY_INPUT).orEmpty()
            )
            else -> SandboxRequest(
                tool = SandboxTool.DATA,
                data = inputData.getString(KEY_INPUT).orEmpty(),
                dataFormat = inputData.getString(KEY_FORMAT) ?: "csv",
                operation = inputData.getString(KEY_OPERATION) ?: "summarize",
                column = inputData.getString(KEY_COLUMN)
            )
        }
        return when (val result = workflowExecutor.runSingle(request)) {
            is SandboxResult.Success -> Result.success(
                workDataOf(
                    KEY_OUTPUT to result.output.take(MAX_OUTPUT_CHARS),
                    KEY_SUCCESS to true,
                    KEY_DURATION to result.durationMs
                )
            )
            is SandboxResult.Failure -> Result.success(
                workDataOf(
                    KEY_OUTPUT to result.message.take(MAX_OUTPUT_CHARS),
                    KEY_SUCCESS to false,
                    KEY_DURATION to result.durationMs
                )
            )
        }
    }

    companion object {
        const val KEY_TOOL = "tool"
        const val KEY_LANGUAGE = "language"
        const val KEY_INPUT = "input"
        const val KEY_FORMAT = "format"
        const val KEY_OPERATION = "operation"
        const val KEY_COLUMN = "column"
        const val KEY_OUTPUT = "output"
        const val KEY_SUCCESS = "success"
        const val KEY_DURATION = "duration"
        const val MAX_OUTPUT_CHARS = 20_000
    }
}

/** Enqueues and observes background sandbox jobs. */
class SandboxJobScheduler @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    fun enqueue(request: SandboxRequest): UUID {
        val builder = OneTimeWorkRequestBuilder<SandboxWorker>()
        val data = Data.Builder()
            .putString(SandboxWorker.KEY_TOOL, request.tool.name)
        when (request.tool) {
            SandboxTool.CODE -> {
                data.putString(SandboxWorker.KEY_LANGUAGE, request.language?.name)
                data.putString(SandboxWorker.KEY_INPUT, request.code.orEmpty())
            }
            else -> {
                data.putString(SandboxWorker.KEY_INPUT, request.data.orEmpty())
                data.putString(SandboxWorker.KEY_FORMAT, request.dataFormat)
                data.putString(SandboxWorker.KEY_OPERATION, request.operation)
                request.column?.let { data.putString(SandboxWorker.KEY_COLUMN, it) }
            }
        }
        builder.setInputData(data.build())
        val workRequest = builder.build()
        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }

    fun observe(id: UUID): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id)
}

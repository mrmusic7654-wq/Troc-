package com.troc.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.troc.data.sandbox.CodeSandbox
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SandboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val codeSandbox: CodeSandbox
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val language = inputData.getString("language") ?: "python"
        val code = inputData.getString("code") ?: return Result.failure()
        val timeout = inputData.getInt("timeout", 15)

        return try {
            val result = codeSandbox.execute(language, code, timeout)
            val output = when (result) {
                is com.troc.domain.model.SandboxResult.Success -> result.output
                is com.troc.domain.model.SandboxResult.Error -> "Error: ${result.message}"
                is com.troc.domain.model.SandboxResult.Timeout -> "Timeout"
            }
            Result.success(workDataOf("output" to output))
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: "Unknown")))
        }
    }
}

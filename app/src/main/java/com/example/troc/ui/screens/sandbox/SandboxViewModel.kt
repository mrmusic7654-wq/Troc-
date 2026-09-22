package com.example.troc.ui.screens.sandbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.troc.domain.model.FileAttachment
import com.example.troc.domain.model.SandboxLanguage
import com.example.troc.domain.model.SandboxRequest
import com.example.troc.domain.model.SandboxResult
import com.example.troc.domain.model.SandboxTool
import com.example.troc.domain.model.WorkflowStep
import com.example.troc.domain.repository.ChatHistoryRepository
import com.example.troc.domain.repository.FileRepository
import com.example.troc.domain.repository.SettingsRepository
import com.example.troc.domain.repository.SandboxGateway
import com.example.troc.domain.usecase.ExecuteSandboxUseCase
import com.example.troc.work.SandboxJobScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SandboxResultEntry(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val output: String,
    val success: Boolean,
    val durationMs: Long,
    val table: com.example.troc.domain.model.SandboxTable? = null,
    val chart: com.example.troc.domain.model.ChartSeries? = null
)

data class SandboxState(
    val tab: Int = 0,
    val language: SandboxLanguage = SandboxLanguage.JAVASCRIPT,
    val code: String = DEFAULT_CODE,
    val dataText: String = "",
    val dataFormat: String = "csv",
    val dataColumn: String = "",
    val filterOp: String = ">",
    val filterValue: String = "",
    val jsonPath: String = "",
    val timeoutSeconds: Int = 30,
    val executing: Boolean = false,
    val error: String? = null,
    val results: List<SandboxResultEntry> = emptyList(),
    val workflowSteps: List<WorkflowStep> = emptyList(),
    val pickedFile: FileAttachment? = null,
    val regexPattern: String = "",
    val regexReplacement: String = "",
    val backgroundJobId: String? = null,
    val backgroundOutput: String? = null
) {
    val selectedTool: SandboxTool
        get() = when (tab) {
            0 -> SandboxTool.CODE
            1 -> SandboxTool.DATA
            2 -> SandboxTool.FILE
            else -> SandboxTool.CUSTOM
        }

    companion object {
        val DEFAULT_CODE = """// JavaScript sandbox — println() and console.log() both work.
const n = 10;
let a = 0, b = 1;
for (let i = 0; i < n; i++) {
  const t = a + b;
  a = b;
  b = t;
}
println("fib(" + n + ") = " + a);"""
    }
}

@HiltViewModel
class SandboxViewModel @Inject constructor(
    private val executeSandbox: ExecuteSandboxUseCase,
    private val sandboxGateway: SandboxGateway,
    private val historyRepository: ChatHistoryRepository,
    settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
    private val jobScheduler: SandboxJobScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(SandboxState())
    val state: StateFlow<SandboxState> = _state.asStateFlow()

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.troc.domain.model.AppSettings())

    private var chatId: String? = null

    fun setChatId(id: String?) {
        chatId = id
    }

    fun update(transform: (SandboxState) -> SandboxState) = _state.update(transform)

    fun clear() = _state.update {
        SandboxState(
            tab = it.tab,
            language = it.language,
            timeoutSeconds = it.timeoutSeconds,
            workflowSteps = it.workflowSteps
        )
    }

    // ------------------------------------------------------------------ file picking

    fun onFilePicked(uriString: String) {
        viewModelScope.launch {
            val attachment = fileRepository.readAttachment(uriString)
            if (attachment == null) {
                _state.update { it.copy(error = "Could not read the picked file.") }
            } else {
                _state.update { it.copy(pickedFile = attachment) }
            }
        }
    }

    // ------------------------------------------------------------------ execution

    fun run() {
        val current = _state.value
        if (current.executing) return
        val request = buildRequest(current) ?: return
        _state.update { it.copy(executing = true, error = null) }
        viewModelScope.launch {
            val result = executeSandbox(request)
            val entry = SandboxResultEntry(
                label = entryLabel(current),
                output = result.text(),
                success = result.isSuccessful,
                durationMs = result.durationMs,
                table = result.payloadOrNull?.table,
                chart = result.payloadOrNull?.chart
            )
            _state.update { it.copy(executing = false, results = listOf(entry) + it.results) }
            persistSession(request, result)
        }
    }

    fun runInBackground() {
        val current = _state.value
        val request = buildRequest(current) ?: return
        val id = jobScheduler.enqueue(request)
        _state.update { it.copy(backgroundJobId = id.toString()) }
        viewModelScope.launch {
            jobScheduler.observe(id).collect { info ->
                when (info?.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        _state.update {
                            it.copy(
                                backgroundOutput = info.outputData.getString(com.example.troc.work.SandboxWorker.KEY_OUTPUT)
                            )
                        }
                        return@collect
                    }
                    androidx.work.WorkInfo.State.FAILED -> {
                        _state.update { it.copy(backgroundOutput = "Background job failed.") }
                        return@collect
                    }
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        sandboxGateway.cancelActive()
        _state.update { it.copy(executing = false) }
    }

    // ------------------------------------------------------------------ helpers

    private fun buildRequest(current: SandboxState): SandboxRequest? {
        val tool = current.selectedTool
        val settingsValue = settings.value
        if (!settingsValue.sandboxEnabled) {
            _state.update { it.copy(error = "The sandbox is disabled in Settings.") }
            return null
        }
        val timeout = settingsValue.maxExecutionSeconds * 1000L
        return when (tool) {
            SandboxTool.CODE -> {
                if (current.code.isBlank()) {
                    _state.update { it.copy(error = "Write some code first.") }
                    null
                } else {
                    SandboxRequest(
                        tool = SandboxTool.CODE,
                        language = current.language,
                        code = current.code,
                        timeoutMs = current.timeoutSeconds * 1000L
                    )
                }
            }
            SandboxTool.DATA -> {
                val params = when (current.dataFormat.lowercase()) {
                    "json" -> if (current.jsonPath.isNotBlank()) mapOf("path" to current.jsonPath) else emptyMap()
                    else -> mapOf("op" to current.filterOp, "value" to current.filterValue)
                }
                val operation = when (current.dataFormat.lowercase()) {
                    "json" -> if (current.jsonPath.isBlank()) "summarize" else "query"
                    else -> if (current.filterValue.isBlank()) "summarize" else "filter"
                }
                SandboxRequest(
                    tool = SandboxTool.DATA,
                    data = current.dataText.ifBlank { current.pickedFile?.content },
                    dataFormat = current.dataFormat,
                    operation = operation,
                    column = current.dataColumn.ifBlank { null },
                    params = params,
                    timeoutMs = timeout
                )
            }
            SandboxTool.FILE -> SandboxRequest(
                tool = SandboxTool.FILE,
                fileAction = com.example.troc.domain.model.FileAction.READ_TEXT,
                uri = current.pickedFile?.uri,
                fileName = current.pickedFile?.name,
                data = current.pickedFile?.content,
                regexPattern = current.regexPattern.ifBlank { null },
                regexReplacement = current.regexReplacement,
                timeoutMs = timeout
            )
            SandboxTool.CUSTOM -> SandboxRequest(tool = SandboxTool.CUSTOM, steps = current.workflowSteps, timeoutMs = timeout)
        }
    }

    private fun entryLabel(current: SandboxState): String = when (current.selectedTool) {
        SandboxTool.CODE -> "${current.language.label} snippet"
        SandboxTool.DATA -> "${current.dataFormat.uppercase()} · ${if (current.filterValue.isBlank()) "summarize" else "filter"}"
        SandboxTool.FILE -> current.pickedFile?.name ?: "file"
        SandboxTool.CUSTOM -> "workflow (${current.workflowSteps.size} steps)"
    }

    private suspend fun persistSession(request: SandboxRequest, result: SandboxResult) {
        historyRepository.saveSession(
            com.example.troc.domain.model.SandboxSessionRecord(
                chatId = chatId,
                tool = request.tool.name,
                language = request.language?.name,
                input = (request.code ?: request.data).orEmpty().take(2_000),
                output = result.text().take(4_000),
                success = result.isSuccessful,
                durationMs = result.durationMs,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // ------------------------------------------------------------------ workflow steps (Custom tab)

    fun addStep() = _state.update { it.copy(workflowSteps = it.workflowSteps + WorkflowStep()) }

    fun removeStep(id: String) = _state.update {
        it.copy(workflowSteps = it.workflowSteps.filterNot { step -> step.id == id })
    }

    fun updateStep(step: WorkflowStep) = _state.update {
        it.copy(workflowSteps = it.workflowSteps.map { s -> if (s.id == step.id) step else s })
    }
}

package com.example.troc.ui.screens.sandbox

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.troc.domain.model.SandboxLanguage
import com.example.troc.ui.components.BarChart
import com.example.troc.ui.components.ConsoleView
import com.example.troc.ui.components.DataTable
import com.example.troc.ui.components.EmptyState
import com.example.troc.ui.components.LabeledSlider
import com.example.troc.ui.components.WorkflowBuilder
import com.example.troc.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    onBack: () -> Unit,
    chatId: String?,
    viewModel: SandboxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(chatId) { viewModel.setChatId(chatId) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toString()) }
    }

    val tabs = listOf("💻 Code", "📊 Data", "📁 Files", "⚙️ Workflow")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sandbox") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = state.tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.tab == index,
                        onClick = { viewModel.update { it.copy(tab = index) } },
                        text = { Text(title) }
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.tab) {
                    0 -> CodeTab(state, viewModel)
                    1 -> DataTab(state, viewModel)
                    2 -> FilesTab(state, viewModel, onPick = {
                        filePicker.launch(arrayOf("*/*"))
                    })
                    else -> WorkflowTab(state, viewModel)
                }

                state.backgroundOutput?.let {
                    ConsoleView(output = "Background job: $it", isError = false, durationMs = null)
                }

                if (state.results.isEmpty() && !state.executing) {
                    EmptyState(
                        icon = Icons.Filled.PlayArrow,
                        title = "Ready to run",
                        subtitle = "Configure an input above and press Run. Everything executes " +
                            "locally with strict time, memory and output budgets."
                    )
                }
                state.results.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        ConsoleView(output = entry.output, isError = !entry.success, durationMs = entry.durationMs)
                        entry.table?.let { DataTable(columns = it.columns, rows = it.rows) }
                        entry.chart?.let { BarChart(series = it, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeTab(state: SandboxState, viewModel: SandboxViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SandboxLanguage.entries.forEach { language ->
            FilterChip(
                selected = state.language == language,
                onClick = { viewModel.update { it.copy(language = language) } },
                label = {
                    Text(language.label + if (language == SandboxLanguage.PYTHON) " ⚠️" else "")
                }
            )
        }
    }
    if (state.language == SandboxLanguage.PYTHON) {
        Text(
            "Python requires the optional Chaquopy runtime (see README). " +
                "Running it now will return an explanatory error.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    OutlinedTextField(
        value = state.code,
        onValueChange = { code -> viewModel.update { it.copy(code = code) } },
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        placeholder = { Text("// code") },
        minLines = 8,
        maxLines = 20,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    )
    ExecutionControls(state, viewModel)
}

@Composable
private fun DataTab(state: SandboxState, viewModel: SandboxViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("csv", "json").forEach { format ->
            FilterChip(
                selected = state.dataFormat == format,
                onClick = { viewModel.update { it.copy(dataFormat = format) } },
                label = { Text(format.uppercase()) }
            )
        }
    }
    if (state.dataFormat == "csv") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.dataColumn,
                onValueChange = { v -> viewModel.update { it.copy(dataColumn = v) } },
                label = { Text("Column (optional)") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.filterOp,
                onValueChange = { v -> viewModel.update { it.copy(filterOp = v) } },
                label = { Text("Filter op") },
                modifier = Modifier.width(90.dp)
            )
            OutlinedTextField(
                value = state.filterValue,
                onValueChange = { v -> viewModel.update { it.copy(filterValue = v) } },
                label = { Text("Filter value") },
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        OutlinedTextField(
            value = state.jsonPath,
            onValueChange = { v -> viewModel.update { it.copy(jsonPath = v) } },
            label = { Text("Path query (e.g. users[].name, optional)") },
            modifier = Modifier.fillMaxWidth()
        )
    }
    OutlinedTextField(
        value = state.dataText,
        onValueChange = { v -> viewModel.update { it.copy(dataText = v) } },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Paste ${state.dataFormat.uppercase()} data here… (or pick a file below)") },
        minLines = 5,
        maxLines = 12,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    )
    ExecutionControls(state, viewModel)
}

@Composable
private fun FilesTab(state: SandboxState, viewModel: SandboxViewModel, onPick: () -> Unit) {
    OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.FileOpen, contentDescription = null)
        Text("  Pick a file (CSV, JSON, TXT, ZIP, images…)")
    }
    state.pickedFile?.let { file ->
        Text(
            "📎 ${file.name} · ${FileUtils.humanBytes(file.sizeBytes)} · ${file.mimeType}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.regexPattern,
            onValueChange = { v -> viewModel.update { it.copy(regexPattern = v) } },
            label = { Text("Regex replace — pattern") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.regexReplacement,
            onValueChange = { v -> viewModel.update { it.copy(regexReplacement = v) } },
            label = { Text("Regex replace — replacement") },
            modifier = Modifier.fillMaxWidth()
        )
    }
    ExecutionControls(state, viewModel)
}

@Composable
private fun WorkflowTab(state: SandboxState, viewModel: SandboxViewModel) {
    Text(
        "Chain sandbox tools: each step runs in order; use {{result}} or {{step1}} " +
            "in later inputs to inject earlier outputs.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    WorkflowBuilder(
        steps = state.workflowSteps,
        executing = state.executing,
        onAddStep = viewModel::addStep,
        onRemoveStep = viewModel::removeStep,
        onUpdateStep = viewModel::updateStep,
        onRun = viewModel::run
    )
}

@Composable
private fun ExecutionControls(state: SandboxState, viewModel: SandboxViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = viewModel::run, enabled = !state.executing) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text(if (state.executing) "Running…" else "Run")
        }
        OutlinedButton(onClick = viewModel::stop, enabled = state.executing) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Text("Stop")
        }
        OutlinedButton(onClick = viewModel::runInBackground, enabled = !state.executing) {
            Text("⏳ Background")
        }
        IconButton(onClick = { viewModel.clear() }) {
            Icon(Icons.Filled.Delete, contentDescription = "Clear results")
        }
    }
    LabeledSlider(
        label = "Timeout",
        value = state.timeoutSeconds.toFloat(),
        onValueChange = { v -> viewModel.update { it.copy(timeoutSeconds = v.toInt()) } },
        valueRange = 5f..120f,
        valueLabel = "${state.timeoutSeconds}s"
    )
}

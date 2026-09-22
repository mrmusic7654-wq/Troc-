package com.troc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troc.ui.theme.*
import com.troc.viewmodel.SandboxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    onBack: () -> Unit,
    viewModel: SandboxViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val tabs = listOf("💻 Code", "📊 Data", "📁 Files", "⚙️ Custom")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sandbox • ${tabs[state.selectedTab]}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.setTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            when (state.selectedTab) {
                0 -> CodeTab(state, viewModel)
                1 -> DataTab(state, viewModel)
                2 -> FilesTab(state, viewModel)
                3 -> CustomTab(state, viewModel)
            }
        }
    }
}

@Composable
fun CodeTab(state: com.troc.viewmodel.SandboxUiState, viewModel: SandboxViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Language selector
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(state.language)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("python", "js", "kotlin").forEach { lang ->
                        DropdownMenuItem(text = { Text(lang) }, onClick = {
                            viewModel.setLanguage(lang)
                            expanded = false
                        })
                    }
                }
            }

            // Timeout slider
            Text("Timeout: ${state.timeoutSec}s", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = state.timeoutSec.toFloat(),
                onValueChange = { viewModel.setTimeout(it.toInt()) },
                valueRange = 5f..60f,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Code editor (dark)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CodeSurface)
                .padding(12.dp)
        ) {
            Column {
                Row {
                    // Line numbers
                    Column {
                        val lines = state.code.lines().size
                        repeat(lines) { i ->
                            Text(
                                "${i + 1}",
                                color = DarkMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = { viewModel.updateCode(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = CodeText),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.runCode() },
                enabled = !state.isRunning,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running…")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run")
                }
            }
            OutlinedButton(
                onClick = { viewModel.stopCode() },
                enabled = state.isRunning,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Console output
        if (state.output.isNotEmpty() || state.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CodeSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Console", style = MaterialTheme.typography.labelSmall, color = DarkMuted)
                        Spacer(modifier = Modifier.weight(1f))
                        if (state.executionTimeMs != null) {
                            Surface(color = DarkSurfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                Text("${state.executionTimeMs}ms", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = DarkText)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error ?: state.output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (state.error != null) ErrorRed else SecondaryCyan,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
fun DataTab(state: com.troc.viewmodel.SandboxUiState, viewModel: SandboxViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.dataContent,
            onValueChange = { viewModel.updateDataContent(it) },
            label = { Text("Paste CSV / JSON") },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { viewModel.analyzeData() }, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Analytics, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Analyze")
        }

        state.dataStats?.let { stats ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Rows: ${stats.rows}, Columns: ${stats.columns}", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Columns: ${stats.columnNames.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    stats.numericStats.forEach { (col, s) ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(col, style = MaterialTheme.typography.labelMedium)
                                Text("mean=${"%.2f".format(s.mean)} median=${"%.2f".format(s.median)} min=${s.min} max=${s.max} sum=${s.sum}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilesTab(state: com.troc.viewmodel.SandboxUiState, viewModel: SandboxViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        var opExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { opExpanded = true }) {
                Text(state.fileOperation)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = opExpanded, onDismissRequest = { opExpanded = false }) {
                listOf("word_count", "uppercase", "lowercase", "trim", "regex_replace", "metadata", "zip", "unzip").forEach { op ->
                    DropdownMenuItem(text = { Text(op) }, onClick = {
                        viewModel.setFileOperation(op)
                        opExpanded = false
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.fileInput,
            onValueChange = { viewModel.updateFileInput(it) },
            label = { Text("Input text or cache file name") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = { viewModel.runFileOperation() }, enabled = !state.isRunning, shape = RoundedCornerShape(12.dp)) {
            Text("Run ${state.fileOperation}")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.fileOutput.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(state.fileOutput, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun CustomTab(state: com.troc.viewmodel.SandboxUiState, viewModel: SandboxViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Saved Workflows", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Create workflows in Agent Mode and they appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(state.workflows) { workflow ->
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(workflow.name, style = MaterialTheme.typography.titleSmall)
                    Text("${workflow.steps.size} steps", style = MaterialTheme.typography.bodySmall)
                    Text("Created: ${java.text.SimpleDateFormat("MMM dd, HH:mm").format(java.util.Date(workflow.createdAt))}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (state.workflows.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No saved workflows yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

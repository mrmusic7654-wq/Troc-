package com.troc.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.troc.domain.model.ToolType
import com.troc.domain.model.WorkflowStep
import com.troc.ui.components.*
import com.troc.ui.theme.*
import com.troc.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateToSandbox: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.attachFile(it) }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(state.messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(PrimaryPurple, SecondaryCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("T", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Troc", style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    }
                },
                actions = {
                    // Agent Mode toggle
                    SegmentedToggle(
                        isAgentMode = state.isAgentMode,
                        onToggle = { viewModel.toggleAgentMode() }
                    )
                    IconButton(onClick = onNavigateToVoice) {
                        SmallVoiceOrbIcon(isActive = false)
                    }
                    IconButton(onClick = { viewModel.newChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "New chat")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onNavigateToSandbox) {
                        Icon(Icons.Default.Science, contentDescription = "Sandbox")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Column {
                // Agent Mode panel
                AnimatedVisibility(
                    visible = state.isAgentMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    AgentModePanel(
                        steps = state.workflowSteps,
                        onAddStep = { viewModel.addWorkflowStep() },
                        onUpdateStep = { idx, step -> viewModel.updateWorkflowStep(idx, step) },
                        onRemoveStep = { idx -> viewModel.removeWorkflowStep(idx) },
                        onExecute = { viewModel.executeWorkflow() }
                    )
                }

                // Usage bar + Web search indicator
                if (state.apiUsage != null || state.isWebSearchMode || state.isWebSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Live usage badge
                        state.apiUsage?.let { usage ->
                            Surface(
                                color = when {
                                    usage.isOverLimit -> MaterialTheme.colorScheme.errorContainer
                                    usage.isNearLimit -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DataUsage,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (usage.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${usage.totalTokens / 1000}k/${usage.freePlanLimitTokens / 1000000}M • ${"%.1f".format(usage.usagePercent)}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (usage.isNearLimit) {
                                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                    // Progress
                                    LinearProgressIndicator(
                                        progress = usage.usagePercent / 100f,
                                        modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = if (usage.isOverLimit) MaterialTheme.colorScheme.error else if (usage.isNearLimit) MaterialTheme.colorScheme.tertiary else PrimaryPurple
                                    )
                                }
                            }
                        }

                        if (state.isWebSearchMode) {
                            Surface(color = SecondaryCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp), tint = SecondaryCyan)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Web Search ON", style = MaterialTheme.typography.labelSmall, color = SecondaryCyan)
                                    if (state.isWebSearching) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Quick refresh models
                        IconButton(onClick = { viewModel.refreshModels() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh models", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Tool shortcut chips when empty
                if (state.messages.isEmpty()) {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf("▶️ Run Code", "📊 Analyze Data", "📁 Files", "✨ Summarize", "🌐 Web Search")) { chip ->
                            SuggestionChip(
                                onClick = {
                                    if (chip.contains("Web Search")) {
                                        viewModel.setWebSearchMode(true)
                                        viewModel.updateInput("Search web for: ")
                                    } else {
                                        viewModel.updateInput(chip)
                                    }
                                },
                                label = { Text(chip) }
                            )
                        }
                    }
                }

                // Input bar with model selector
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Model selector row + Web search toggle on input bar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Model selector chip with dropdown
                            Box {
                                Surface(
                                    onClick = { viewModel.toggleModelDropdown() },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryPurple)
                                        Text(
                                            state.selectedModel.substringBefore("-latest").take(18),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                        Icon(
                                            if (state.isModelDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = state.isModelDropdownExpanded,
                                    onDismissRequest = { viewModel.setModelDropdownExpanded(false) }
                                ) {
                                    state.availableModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(model, style = MaterialTheme.typography.bodySmall)
                                                    if (model == state.selectedModel) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryPurple)
                                                    }
                                                }
                                            },
                                            onClick = { viewModel.selectModel(model) }
                                        )
                                    }
                                }
                            }

                            // Web search toggle chip
                            FilterChip(
                                selected = state.isWebSearchMode,
                                onClick = { viewModel.toggleWebSearchMode() },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Web", style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                leadingIcon = if (state.isWebSearchMode) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(36.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SecondaryCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = SecondaryCyan
                                )
                            )

                            // Agent mode quick toggle on input bar
                            FilterChip(
                                selected = state.isAgentMode,
                                onClick = { viewModel.toggleAgentMode() },
                                label = { Text("Agent", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(36.dp)
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Usage summary quick
                            state.apiUsage?.let { usage ->
                                Text(
                                    "${usage.todayRequests} today • $${"%.4f".format(usage.estimatedCostUsd)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Attachment preview
                        if (state.attachedFileName != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(state.attachedFileName ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.clearAttachment() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Web search results preview
                        if (state.webSearchResults != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SecondaryCyan.copy(alpha = 0.1f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp), tint = SecondaryCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Web Search Results", style = MaterialTheme.typography.labelSmall, color = SecondaryCyan)
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(onClick = { /* collapse */ }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Text(
                                        state.webSearchResults!!.take(300) + if (state.webSearchResults!!.length > 300) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.Bottom) {
                            // Mic button
                            MicButton(
                                amplitude = state.amplitude,
                                isRecording = state.isRecording,
                                onStartRecording = {
                                    if (audioPermission.status.isGranted) {
                                        viewModel.setRecordingState(true)
                                    } else {
                                        audioPermission.launchPermissionRequest()
                                    }
                                },
                                onStopRecording = {
                                    viewModel.setRecordingState(false)
                                },
                                onCancel = {
                                    viewModel.setRecordingState(false)
                                }
                            )

                            OutlinedTextField(
                                value = state.inputText,
                                onValueChange = { viewModel.updateInput(it) },
                                placeholder = { Text("Message Troc… ${if (state.isWebSearchMode) "(web search)" else ""}") },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            IconButton(onClick = { filePicker.launch("*/*") }) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                            }

                            if (state.isLoading) {
                                IconButton(onClick = { viewModel.stopStreaming() }) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                IconButton(
                                    onClick = { viewModel.sendMessage() },
                                    enabled = state.inputText.isNotBlank() || state.attachedFileContent != null
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Send", tint = PrimaryPurple)
                                }
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) }
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.messages.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(80.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(PrimaryPurple, SecondaryCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("T", color = Color.White, style = MaterialTheme.typography.displayLarge)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("How can I help you today?", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(24.dp))
                    // 2x2 grid of suggestion cards
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SuggestionCard("Run Python code", "Execute code in sandbox") { viewModel.updateInput("Run this Python code: ") }
                            SuggestionCard("Analyze a CSV", "Upload and analyze data") { filePicker.launch("text/*") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SuggestionCard("Explain quantum", "Deep dive topic") { viewModel.updateInput("Explain quantum computing") }
                            SuggestionCard("Voice chat", "Talk hands-free") { onNavigateToVoice() }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        if (message.role.name == "TOOL") {
                            // Render as tool card if possible
                            val toolCall = state.activeToolCalls.find { it.output == message.content } ?: com.troc.domain.model.ToolCall(
                                tool = ToolType.CODE,
                                input = message.content,
                                output = message.content
                            )
                            ToolCard(toolCall = toolCall)
                        } else {
                            ChatBubble(message = message)
                        }
                    }

                    // Active tool calls that haven't been added as messages yet
                    items(state.activeToolCalls.filter { it.isRunning }) { toolCall ->
                        ToolCard(toolCall = toolCall)
                    }

                    if (state.isLoading && state.messages.lastOrNull()?.role?.name != "ASSISTANT") {
                        item {
                            TypingIndicator()
                        }
                    }
                }
            }

            // Error snackbar
            state.error?.let { error ->
                LaunchedEffect(error) {
                    // show snackbar logic would go here
                }
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                            if (error.contains("API key")) {
                                TextButton(onClick = onNavigateToSettings) { Text("Settings") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedToggle(isAgentMode: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
    ) {
        FilterChip(
            selected = !isAgentMode,
            onClick = { if (isAgentMode) onToggle() },
            label = { Text("💬 Chat", style = MaterialTheme.typography.labelSmall) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(32.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        FilterChip(
            selected = isAgentMode,
            onClick = { if (!isAgentMode) onToggle() },
            label = { Text("🤖 Agent", style = MaterialTheme.typography.labelSmall) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
fun SuggestionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AgentModePanel(
    steps: List<WorkflowStep>,
    onAddStep: () -> Unit,
    onUpdateStep: (Int, WorkflowStep) -> Unit,
    onRemoveStep: (Int) -> Unit,
    onExecute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Agent Workflow", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onAddStep, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add step", modifier = Modifier.size(18.dp))
                }
                Button(onClick = onExecute, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("▶️ Execute", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (steps.isEmpty()) {
                Text("Add steps to build a multi-tool workflow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Tool dropdown (simplified)
                            OutlinedTextField(
                                value = step.tool.name,
                                onValueChange = { /* dropdown would update */ },
                                modifier = Modifier.width(80.dp),
                                label = { Text("Tool", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = step.inputTemplate,
                                onValueChange = { onUpdateStep(index, step.copy(inputTemplate = it)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Input (use {{var}})", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = step.outputVarName,
                                onValueChange = { onUpdateStep(index, step.copy(outputVarName = it)) },
                                modifier = Modifier.width(90.dp),
                                label = { Text("Output var", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true
                            )
                            IconButton(onClick = { onRemoveStep(index) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

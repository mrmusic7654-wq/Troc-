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
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(PrimaryPurple, PrimaryGradientEnd, SecondaryCyan),
                                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                        end = androidx.compose.ui.geometry.Offset(32f, 32f)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("T", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Troc", style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            if (state.isAgentMode) {
                                Text(
                                    "Autonomous Agent • Active",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                    color = PrimaryPurple
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Refined Agent Mode toggle - more prominent
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (state.isAgentMode) PrimaryPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !state.isAgentMode,
                                onClick = { if (state.isAgentMode) viewModel.toggleAgentMode() },
                                label = { Text("💬", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(28.dp)
                            )
                            FilterChip(
                                selected = state.isAgentMode,
                                onClick = { if (!state.isAgentMode) viewModel.toggleAgentMode() },
                                label = { Text("🤖 Auto", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryPurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToVoice, modifier = Modifier.size(36.dp)) {
                        SmallVoiceOrbIcon(isActive = state.isRecording)
                    }
                    IconButton(onClick = { viewModel.newChat() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "New chat", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onNavigateToHistory, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                )
            )
        },
        bottomBar = {
            Column {
                // Automatic Agent Mode panel - refined
                AnimatedVisibility(
                    visible = state.isAgentMode,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                    exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
                ) {
                    AutomaticAgentPanel(
                        isExecuting = state.isLoading,
                        activeTools = state.activeToolCalls.size,
                        onDisable = { viewModel.toggleAgentMode() }
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
                // Refined empty state with better design
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated logo with gradient
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(PrimaryPurple, PrimaryGradientEnd, SecondaryCyan),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(88f, 88f)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "T",
                            color = Color.White,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "How can I help you today?",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (state.isAgentMode) "Autonomous agent ready • I plan and execute automatically"
                        else "Ask anything or switch to Agent for autonomous tasks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    // Refined 2x2 grid with better cards
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SuggestionCard(
                                icon = "🐍",
                                title = "Run Python",
                                subtitle = "Code • Analyze • Chart",
                                isAgent = state.isAgentMode
                            ) {
                                if (state.isAgentMode) viewModel.updateInput("Write and run Python code to analyze data and create visualizations")
                                else viewModel.updateInput("Run this Python code: ")
                            }
                            SuggestionCard(
                                icon = "📊",
                                title = "Analyze Data",
                                subtitle = "CSV • Stats • Insights",
                                isAgent = state.isAgentMode
                            ) { filePicker.launch("text/*") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SuggestionCard(
                                icon = "🌐",
                                title = "Web Search",
                                subtitle = "Real-time • Cited",
                                isAgent = state.isAgentMode
                            ) {
                                viewModel.setWebSearchMode(true)
                                viewModel.updateInput("Search web for latest: ")
                            }
                            SuggestionCard(
                                icon = "🎙️",
                                title = "Voice Chat",
                                subtitle = "Hands-free • Natural",
                                isAgent = false
                            ) { onNavigateToVoice() }
                        }
                        if (state.isAgentMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SuggestionCard(
                                    icon = "🤖",
                                    title = "Autonomous Task",
                                    subtitle = "Plan • Execute • Report",
                                    isAgent = true,
                                    isHighlighted = true
                                ) {
                                    viewModel.updateInput("I need you to autonomously research, analyze, and create a report about: ")
                                }
                                SuggestionCard(
                                    icon = "⚡",
                                    title = "Multi-step Workflow",
                                    subtitle = "Auto chain tools",
                                    isAgent = true
                                ) {
                                    viewModel.updateInput("Create a complete workflow: search web, analyze data, write code, and generate report for: ")
                                }
                            }
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
fun SuggestionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: String = "✨",
    isAgent: Boolean = false,
    isHighlighted: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isHighlighted -> PrimaryPurple.copy(alpha = 0.15f)
                isAgent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isHighlighted) androidx.compose.foundation.BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.3f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleSmall)
                if (isAgent) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = PrimaryPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "AUTO",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = PrimaryPurple
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
fun AutomaticAgentPanel(
    isExecuting: Boolean,
    activeTools: Int,
    onDisable: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "agent")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                PrimaryPurple.copy(alpha = shimmer),
                                SecondaryCyan.copy(alpha = 0.8f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Agent Mode • Autonomous",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            if (isExecuting) "EXECUTING" else "READY",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = if (isExecuting) PrimaryPurple else SuccessGreen
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (isExecuting && activeTools > 0) {
                        "Running $activeTools tool${if (activeTools > 1) "s" else ""} autonomously • AI is planning and executing"
                    } else if (isExecuting) {
                        "AI is thinking and planning workflow automatically..."
                    } else {
                        "AI will automatically plan, write code, search web, and execute tasks"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 2
                )
            }

            IconButton(
                onClick = onDisable,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Disable agent",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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
    // Kept for advanced users - now collapsible
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Advanced Workflow Builder",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { isAdvancedExpanded = !isAdvancedExpanded },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(if (isAdvancedExpanded) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    Icon(
                        if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isAdvancedExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Custom Workflow", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onAddStep, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add step", modifier = Modifier.size(18.dp))
                        }
                        Button(onClick = onExecute, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text("▶️ Execute", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (steps.isEmpty()) {
                        Text("Optional: manually chain tools with variables", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            steps.forEachIndexed { index, step ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = step.tool.name,
                                        onValueChange = { },
                                        modifier = Modifier.width(80.dp),
                                        label = { Text("Tool", style = MaterialTheme.typography.labelSmall) },
                                        singleLine = true,
                                        readOnly = true
                                    )
                                    OutlinedTextField(
                                        value = step.inputTemplate,
                                        onValueChange = { onUpdateStep(index, step.copy(inputTemplate = it)) },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("Input {{var}}", style = MaterialTheme.typography.labelSmall) },
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = step.outputVarName,
                                        onValueChange = { onUpdateStep(index, step.copy(outputVarName = it)) },
                                        modifier = Modifier.width(90.dp),
                                        label = { Text("Output", style = MaterialTheme.typography.labelSmall) },
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
    }
}

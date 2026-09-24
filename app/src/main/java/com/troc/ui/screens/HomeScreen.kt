package com.troc.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.troc.domain.model.ToolType
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Glowing Brand Icon
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(PrimaryPurple, PrimaryIndigo, SecondaryCyan)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "T",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black
                                    )
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Troc",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (state.isAgentMode) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = PrimaryPurple.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "AGENT",
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryPurple
                                                )
                                            )
                                        }
                                    }
                                }
                                Text(
                                    if (state.isAgentMode) "Autonomous execution" else "AI Assistant",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Voice Mode Orb Button
                        IconButton(
                            onClick = onNavigateToVoice,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Voice Mode",
                                    tint = SecondaryCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // New Chat Button
                        IconButton(
                            onClick = { viewModel.newChat() },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.AddComment,
                                contentDescription = "New chat",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // History Button
                        IconButton(
                            onClick = onNavigateToHistory,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Settings Button
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                // Secondary Control Row: Model Selector & Mode Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Model Dropdown Chip
                    Box {
                        Surface(
                            onClick = { viewModel.toggleModelDropdown() },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = PrimaryPurple
                                )
                                Text(
                                    state.selectedModel.substringBefore("-latest"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    if (state.isModelDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                model,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (model == state.selectedModel) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                            if (model == state.selectedModel) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = PrimaryPurple
                                                )
                                            }
                                        }
                                    },
                                    onClick = { viewModel.selectModel(model) }
                                )
                            }
                        }
                    }

                    // Mode Toggle: Chat vs Agent
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Chat mode button
                            Surface(
                                onClick = { if (state.isAgentMode) viewModel.setAgentMode(false) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (!state.isAgentMode) MaterialTheme.colorScheme.surface else Color.Transparent,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (!state.isAgentMode) PrimaryIndigo else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Chat",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = if (!state.isAgentMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Agent mode button
                            Surface(
                                onClick = { if (!state.isAgentMode) viewModel.setAgentMode(true) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (state.isAgentMode) PrimaryPurple else Color.Transparent,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (state.isAgentMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Agent",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = if (state.isAgentMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Agent Active Notice Bar
                AnimatedVisibility(
                    visible = state.isAgentMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = PrimaryPurple.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Autonomous Agent Mode active • Code, data, and sandbox tools run automatically",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.setAgentMode(false) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Disable Agent Mode",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Web search active banner
                AnimatedVisibility(
                    visible = state.isWebSearchMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = SecondaryCyan.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = SecondaryCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Live Web Search enabled for next message",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryCyan,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.isWebSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = SecondaryCyan
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            IconButton(
                                onClick = { viewModel.setWebSearchMode(false) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Turn off web search",
                                    modifier = Modifier.size(12.dp),
                                    tint = SecondaryCyan
                                )
                            }
                        }
                    }
                }

                // File Attachment Preview Banner
                state.attachedFileName?.let { fileName ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PrimaryPurple
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                fileName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { viewModel.clearAttachment() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove attached file",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Redesigned Input Bar (Clean, Minimal, No Token Counter or Price)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attach File Button
                        IconButton(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.AddCircleOutline,
                                contentDescription = "Attach file",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Web Search Toggle
                        IconButton(
                            onClick = { viewModel.toggleWebSearchMode() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = "Web Search",
                                tint = if (state.isWebSearchMode) SecondaryCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Input Text Area
                        TextField(
                            value = state.inputText,
                            onValueChange = { viewModel.updateInput(it) },
                            placeholder = {
                                Text(
                                    if (state.isWebSearchMode) "Ask with web search…"
                                    else if (state.isAgentMode) "Assign task to agent…"
                                    else "Message Troc…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        // Mic Button
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

                        Spacer(modifier = Modifier.width(4.dp))

                        // Send / Stop Action Button
                        if (state.isLoading) {
                            IconButton(
                                onClick = { viewModel.stopStreaming() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop generation",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            val canSend = state.inputText.isNotBlank() || state.attachedFileContent != null
                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSend) {
                                            Brush.linearGradient(
                                                listOf(PrimaryPurple, PrimaryIndigo)
                                            )
                                        } else {
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            )
                                        }
                                    )
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.messages.isEmpty()) {
                // Clean Minimal Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated glowing hero logo
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(PrimaryPurple, PrimaryIndigo, SecondaryCyan)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "T",
                            color = Color.White,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        "How can I help you today?",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        if (state.isAgentMode) "Agent Mode ready • Autonomous multi-step execution"
                        else "Ask anything, run code, search the web, or use voice",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Suggestion Cards Grid (2x2)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SuggestionTile(
                                icon = "🐍",
                                title = "Run Python",
                                subtitle = "Execute code & analyze",
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.updateInput("Write and run a Python script to: ")
                            }
                            SuggestionTile(
                                icon = "🌐",
                                title = "Search Web",
                                subtitle = "Real-time answers",
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.setWebSearchMode(true)
                                viewModel.updateInput("Search web for: ")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SuggestionTile(
                                icon = "📊",
                                title = "Analyze Data",
                                subtitle = "CSV, JSON & stats",
                                modifier = Modifier.weight(1f)
                            ) {
                                filePicker.launch("text/*")
                            }
                            SuggestionTile(
                                icon = "🎙️",
                                title = "Voice Mode",
                                subtitle = "Hands-free chat",
                                modifier = Modifier.weight(1f)
                            ) {
                                onNavigateToVoice()
                            }
                        }
                    }
                }
            } else {
                // Active Chat Messages Feed
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        if (message.role.name == "TOOL") {
                            val toolCall = state.activeToolCalls.find { it.output == message.content }
                                ?: ToolCall(
                                    tool = ToolType.CODE,
                                    input = message.content,
                                    output = message.content
                                )
                            ToolCard(toolCall = toolCall)
                        } else {
                            ChatBubble(message = message)
                        }
                    }

                    // Active running tool calls
                    items(state.activeToolCalls.filter { it.isRunning }) { toolCall ->
                        ToolCard(toolCall = toolCall)
                    }

                    // Typing indicator
                    if (state.isLoading && state.messages.lastOrNull()?.role?.name != "ASSISTANT") {
                        item {
                            Box(modifier = Modifier.padding(start = 20.dp, top = 6.dp)) {
                                TypingIndicator()
                            }
                        }
                    }
                }
            }

            // Missing API Key Banner
            if (!state.hasApiKey) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(PrimaryPurple.copy(alpha = 0.5f))
                    ),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "API Key Required",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Add your Mistral API key to start chatting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onNavigateToSettings,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Setup", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Error Toast / Banner
            state.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (error.contains("API key", ignoreCase = true) || error.contains("key", ignoreCase = true)) {
                            TextButton(onClick = onNavigateToSettings) {
                                Text("Settings", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionTile(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

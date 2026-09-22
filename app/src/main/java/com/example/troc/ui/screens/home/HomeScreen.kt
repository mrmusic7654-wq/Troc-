package com.example.troc.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.troc.domain.model.Role
import com.example.troc.ui.components.ChatBubble
import com.example.troc.ui.components.EmptyState
import com.example.troc.ui.components.TypingIndicator
import com.example.troc.ui.components.WorkflowBuilder
import com.example.troc.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    chatId: String?,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) { viewModel.loadChat(chatId) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.attachFile(it.toString()) }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    // Auto-scroll as messages arrive / stream.
    val lastMessage = state.messages.lastOrNull()
    LaunchedEffect(state.messages.size, lastMessage?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Troc", style = MaterialTheme.typography.titleLarge)
                        Text("✨", style = MaterialTheme.typography.titleMedium)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "PRO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.newChat() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                    IconButton(onClick = { navController.navigate(Routes.sandbox(chatId)) }) {
                        Icon(Icons.Filled.Science, contentDescription = "Sandbox")
                    }
                    IconButton(onClick = { navController.navigate(Routes.HISTORY) }) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (settings.apiKey.isBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            "No Mistral API key configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { navController.navigate(Routes.SETTINGS) }) { Text("Fix") }
                    }
                }
            }

            // Chat / Agent mode switch
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !state.isAgentMode,
                    onClick = { viewModel.setAgentMode(false) },
                    label = { Text("Chat") },
                    leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) }
                )
                FilterChip(
                    selected = state.isAgentMode,
                    onClick = { viewModel.setAgentMode(true) },
                    label = { Text("Agent") },
                    leadingIcon = { Icon(Icons.Filled.SmartToy, contentDescription = null) }
                )
            }

            AnimatedVisibility(visible = state.isAgentMode) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            "🤖 Agent Mode — Troc can chain sandbox tools: run code, analyze data and " +
                                "process files. Or build a manual workflow below.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    WorkflowBuilder(
                        steps = state.workflowSteps,
                        executing = state.isExecutingWorkflow,
                        onAddStep = viewModel::addWorkflowStep,
                        onRemoveStep = viewModel::removeWorkflowStep,
                        onUpdateStep = viewModel::updateWorkflowStep,
                        onRun = viewModel::executeWorkflow,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                if (state.messages.none { it.role != Role.SYSTEM }) {
                    EmptyState(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Ask Troc anything",
                        subtitle = "Chat normally, or toggle Agent Mode to chain sandboxed tools " +
                            "like code execution, CSV analysis and file processing."
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.messages.filter { it.role != Role.SYSTEM }, key = { it.id }) { message ->
                            ChatBubble(message = message)
                        }
                        if (state.isLoading) {
                            item { TypingIndicator() }
                        }
                    }
                }
            }

            // Quick actions
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) {
                items(QUICK_ACTIONS) { action ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.setInput(action.template) },
                        label = { Text(action.label) }
                    )
                }
            }

            // Input bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = {
                    filePicker.launch(
                        arrayOf(
                            "text/*", "application/json", "text/csv", "application/csv",
                            "application/zip", "text/plain", "application/octet-stream"
                        )
                    )
                }) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Attach file")
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::setInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask Troc or use a tool…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                if (state.attachment != null) {
                    IconButton(onClick = viewModel::clearAttachment) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove attachment",
                            tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                IconButton(
                    onClick = {
                        if (state.isLoading) viewModel.stopStreaming() else viewModel.send(state.input)
                    }
                ) {
                    Icon(
                        imageVector = if (state.isLoading) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (state.isLoading) "Stop" else "Send",
                        tint = if (state.input.isBlank() && !state.isLoading) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

private data class QuickAction(val label: String, val template: String)

private val QUICK_ACTIONS = listOf(
    QuickAction("💻 Run code", "Write and run a code snippet that "),
    QuickAction("📊 Analyze data", "Analyze this CSV data and give me summary statistics:\n\n"),
    QuickAction("📝 Summarize", "Summarize the following text in 5 bullet points:\n\n"),
    QuickAction("📁 Explain file", "Explain the structure and contents of the attached file.")
)

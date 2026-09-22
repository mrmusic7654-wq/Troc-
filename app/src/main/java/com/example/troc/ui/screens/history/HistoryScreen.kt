package com.example.troc.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.troc.ui.components.ConfirmDialog
import com.example.troc.ui.components.EmptyState
import com.example.troc.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }

    state.pendingDeleteChat?.let { chat ->
        ConfirmDialog(
            title = "Delete chat?",
            text = "“${chat.title}” and all its messages will be removed permanently.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog
        )
    }
    state.pendingDeleteSession?.let { session ->
        ConfirmDialog(
            title = "Delete sandbox session?",
            text = "“${session.tool} · ${ShareUtils.formatTimestamp(session.timestamp)}” will be removed.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search chats and sandbox runs…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("💬 Chats (${chats.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("🧪 Sandbox (${sessions.size})") })
            }
            when (tab) {
                0 -> {
                    if (chats.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = "No chats yet",
                            subtitle = "Your conversations will appear here once you start chatting."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chats, key = { it.id }) { chat ->
                                Card(Modifier.fillMaxWidth().clickable { onOpenChat(chat.id) }) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                            Text(
                                                "${chat.messages.size} messages · ${ShareUtils.formatTimestamp(chat.timestamp)}" +
                                                    if (chat.isAgentMode) " · 🤖 agent" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            ShareUtils.shareText(context, chat.title, ShareUtils.chatToMarkdown(chat))
                                        }) {
                                            Icon(Icons.Filled.Share, contentDescription = "Export as Markdown")
                                        }
                                        IconButton(onClick = { viewModel.askDeleteChat(chat) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (sessions.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Science,
                            title = "No sandbox sessions",
                            subtitle = "Code runs, data analyses and workflows will be listed here."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sessions, key = { it.id }) { session ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.SmartToy,
                                            contentDescription = null,
                                            tint = if (session.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "${session.tool}${session.language?.let { " · $it" } ?: ""}",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                "${ShareUtils.formatTimestamp(session.timestamp)} · ${session.durationMs} ms",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                session.input.take(80),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1
                                            )
                                        }
                                        IconButton(onClick = {
                                            ShareUtils.shareText(context, "Sandbox run", "${session.tool}\n${session.output}")
                                        }) {
                                            Icon(Icons.Filled.OpenInNew, contentDescription = "Export")
                                        }
                                        IconButton(onClick = { viewModel.askDeleteSession(session) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
}

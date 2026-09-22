package com.troc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troc.domain.model.SttModel
import com.troc.domain.model.TtsVoice
import com.troc.ui.components.ApiKeyField
import com.troc.ui.theme.PrimaryPurple
import com.troc.ui.theme.SecondaryCyan
import com.troc.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showWipeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: API Keys
            item {
                SettingsSection(title = "API Keys", icon = Icons.Default.Key) {
                    ApiKeyField(
                        label = "Mistral API Key",
                        value = state.mistralKeyState.keyOrNull() ?: "",
                        maskedValue = state.mistralKeyState.maskedOrEmpty(),
                        isValid = state.mistralValid,
                        isTesting = state.isTestingMistral,
                        onValueChange = { viewModel.updateMistralInput(it) },
                        onTest = { viewModel.testMistralKey() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.saveMistralKey() }, enabled = state.mistralInput.isNotBlank(), shape = RoundedCornerShape(12.dp)) {
                            Text("Save Mistral Key")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    ApiKeyField(
                        label = "Groq API Key (for voice)",
                        value = state.groqKeyState.keyOrNull() ?: "",
                        maskedValue = state.groqKeyState.maskedOrEmpty(),
                        isValid = state.groqValid,
                        isTesting = state.isTestingGroq,
                        onValueChange = { viewModel.updateGroqInput(it) },
                        onTest = { viewModel.testGroqKey() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.saveGroqKey() }, enabled = state.groqInput.isNotBlank(), shape = RoundedCornerShape(12.dp)) {
                            Text("Save Groq Key")
                        }
                    }

                    if (state.groqKeyState is com.troc.domain.model.ApiKeyState.Missing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                            Text("🔒 Voice features locked until Groq key added", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Section 1.5: Live API Usage (NEW)
            item {
                SettingsSection(title = "Live API Usage", icon = Icons.Default.DataUsage) {
                    if (state.apiUsage == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading usage...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        val usage = state.apiUsage!!
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Total usage
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Tokens", style = MaterialTheme.typography.labelMedium)
                                Text("${usage.totalTokens} / ${usage.freePlanLimitTokens}", style = MaterialTheme.typography.labelSmall)
                            }
                            LinearProgressIndicator(
                                progress = (usage.usagePercent / 100f).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    usage.isOverLimit -> MaterialTheme.colorScheme.error
                                    usage.isNearLimit -> MaterialTheme.colorScheme.tertiary
                                    else -> PrimaryPurple
                                }
                            )
                            Text("${"%.2f".format(usage.usagePercent)}% used • ${usage.remainingTokens} remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            // Today
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Today", style = MaterialTheme.typography.labelMedium)
                                Text("${usage.todayRequests} requests • ${usage.todayPromptTokens + usage.todayCompletionTokens} tokens", style = MaterialTheme.typography.labelSmall)
                            }

                            // Requests
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Requests", style = MaterialTheme.typography.labelMedium)
                                Text("${usage.requestCount} / ${usage.freePlanRequestLimit}", style = MaterialTheme.typography.labelSmall)
                            }
                            LinearProgressIndicator(
                                progress = (usage.requestCount.toFloat() / usage.freePlanRequestLimit).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = SecondaryCyan
                            )

                            // Cost
                            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Estimated Cost", style = MaterialTheme.typography.labelSmall)
                                        Text("$${"%.6f".format(usage.estimatedCostUsd)}", style = MaterialTheme.typography.titleMedium, color = PrimaryPurple)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Avg per req", style = MaterialTheme.typography.labelSmall)
                                        Text("$${"%.6f".format(if (usage.requestCount > 0) usage.estimatedCostUsd / usage.requestCount else 0.0)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            // History preview
                            if (state.usageHistory.isNotEmpty()) {
                                Text("Recent Requests", style = MaterialTheme.typography.labelMedium)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    state.usageHistory.take(5).forEach { item ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${item.model.take(20)} • ${item.totalTokens} tok", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                            Text(item.formattedDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (item.isWebSearch) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(12.dp), tint = SecondaryCyan)
                                            }
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.refreshUsage() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh")
                                }
                                OutlinedButton(onClick = { viewModel.clearUsage() }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear")
                                }
                            }

                            if (usage.isNearLimit) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (usage.isOverLimit) "⚠️ Limit exceeded! Further requests may fail." else "⚠️ Approaching free plan limit (${"%.0f".format(usage.usagePercent)}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            Text(
                                "Free plan: ~1B tokens, 1000 req. Live tracking from API responses + local estimate (1 token ≈ 4 chars). Updates in real-time.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 2: Model
            item {
                SettingsSection(title = "Model", icon = Icons.Default.Memory) {
                    Text("Mistral Model", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(12.dp)) {
                            Text(state.mistralModel)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("mistral-small-latest", "mistral-medium-latest", "codestral-latest", "mistral-large-latest").forEach { model ->
                                DropdownMenuItem(text = { Text(model) }, onClick = {
                                    viewModel.setMistralModel(model)
                                    expanded = false
                                })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Temperature: ${"%.2f".format(state.temperature)}", style = MaterialTheme.typography.labelMedium)
                    Slider(value = state.temperature, onValueChange = { viewModel.setTemperature(it) }, valueRange = 0f..1f)

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.customEndpoint,
                        onValueChange = { viewModel.setCustomEndpoint(it) },
                        label = { Text("Custom Endpoint (self-hosted)") },
                        placeholder = { Text("https://api.mistral.ai/") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Section 3: Voice
            item {
                SettingsSection(title = "Voice", icon = Icons.Default.RecordVoiceOver) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable Voice Mode", modifier = Modifier.weight(1f))
                        Switch(checked = state.voiceEnabled, onCheckedChange = { viewModel.setVoiceEnabled(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Auto-send transcribed speech", modifier = Modifier.weight(1f))
                        Switch(checked = state.autoSend, onCheckedChange = { viewModel.setAutoSend(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Auto-play responses", modifier = Modifier.weight(1f))
                        Switch(checked = state.autoPlay, onCheckedChange = { viewModel.setAutoPlay(it) })
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    // STT model
                    var sttExpanded by remember { mutableStateOf(false) }
                    Text("STT Model", style = MaterialTheme.typography.labelSmall)
                    Box {
                        OutlinedButton(onClick = { sttExpanded = true }, shape = RoundedCornerShape(12.dp)) {
                            Text(state.sttModel)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = sttExpanded, onDismissRequest = { sttExpanded = false }) {
                            SttModel.values().forEach { model ->
                                DropdownMenuItem(text = { Text(model.id) }, onClick = {
                                    viewModel.setSttModel(model.id)
                                    sttExpanded = false
                                })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    var ttsExpanded by remember { mutableStateOf(false) }
                    Text("TTS Voice", style = MaterialTheme.typography.labelSmall)
                    Box {
                        OutlinedButton(onClick = { ttsExpanded = true }, shape = RoundedCornerShape(12.dp)) {
                            Text(state.ttsVoice)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = ttsExpanded, onDismissRequest = { ttsExpanded = false }) {
                            TtsVoice.values().forEach { voice ->
                                DropdownMenuItem(text = { Text("${voice.displayName} (${voice.id})") }, onClick = {
                                    viewModel.setTtsVoice(voice.id)
                                    ttsExpanded = false
                                })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Speech Speed: ${"%.1f".format(state.speechSpeed)}x", style = MaterialTheme.typography.labelSmall)
                    Slider(value = state.speechSpeed, onValueChange = { viewModel.setSpeechSpeed(it) }, valueRange = 0.5f..2f)

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.inputLanguage,
                        onValueChange = { viewModel.setInputLanguage(it) },
                        label = { Text("Input Language (auto/en/...)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Section 4: Sandbox
            item {
                SettingsSection(title = "Sandbox", icon = Icons.Default.Science) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable Sandbox", modifier = Modifier.weight(1f))
                        Switch(checked = state.sandboxEnabled, onCheckedChange = { viewModel.setSandboxEnabled(it) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Default Timeout: ${state.sandboxTimeout}s", style = MaterialTheme.typography.labelSmall)
                    Slider(value = state.sandboxTimeout.toFloat(), onValueChange = { viewModel.setSandboxTimeout(it.toInt()) }, valueRange = 5f..60f)
                }
            }

            // Section 5: Appearance
            item {
                SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                    Text("Theme", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf("system", "light", "dark").forEach { theme ->
                            FilterChip(
                                selected = state.theme == theme,
                                onClick = { viewModel.setTheme(theme) },
                                label = { Text(theme.capitalize()) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Font Size", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf("S", "M", "L").forEach { size ->
                            FilterChip(
                                selected = state.fontSize == size,
                                onClick = { viewModel.setFontSize(size) },
                                label = { Text(size) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Accent Color", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf("purple", "cyan", "emerald").forEach { accent ->
                            FilterChip(
                                selected = state.accent == accent,
                                onClick = { viewModel.setAccent(accent) },
                                label = { Text(accent.capitalize()) }
                            )
                        }
                    }
                }
            }

            // Section 6: Privacy & Data
            item {
                SettingsSection(title = "Privacy & Data", icon = Icons.Default.Security) {
                    Button(onClick = { viewModel.clearAllChats() }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text("Clear all chats", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.clearSandboxSessions() }, shape = RoundedCornerShape(12.dp)) {
                        Text("Clear sandbox sessions")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showWipeDialog = true }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wipe everything")
                    }
                }
            }

            // Section 7: About
            item {
                SettingsSection(title = "About", icon = Icons.Default.Info) {
                    Text("Troc v1.0.0", style = MaterialTheme.typography.titleSmall)
                    Text("Premium AI assistant — chat + agent workflows + sandboxed tools + full voice conversations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(onClick = {}, label = { Text("Mistral Docs") })
                        SuggestionChip(onClick = {}, label = { Text("Groq Docs") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text("✨ Premium", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        if (showWipeDialog) {
            AlertDialog(
                onDismissRequest = { showWipeDialog = false },
                title = { Text("Wipe everything?") },
                text = { Text("This will clear all chats, sandbox sessions, and API keys. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.wipeEverything()
                        showWipeDialog = false
                    }) { Text("Wipe", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

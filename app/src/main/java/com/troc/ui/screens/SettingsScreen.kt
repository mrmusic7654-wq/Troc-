package com.troc.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troc.domain.model.SttModel
import com.troc.domain.model.TtsVoice
import com.troc.ui.components.ApiKeyField
import com.troc.ui.theme.*
import com.troc.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showWipeDialog by remember { mutableStateOf(false) }
    var showClearChatsDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Save Feedback Banner
            item {
                AnimatedVisibility(
                    visible = state.saveSuccessMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = TertiaryEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = TertiaryEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                state.saveSuccessMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = TertiaryEmerald
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearSaveSuccessMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TertiaryEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: AI API Keys
            item {
                SettingsCard(
                    title = "API Configuration",
                    icon = Icons.Default.Key,
                    iconTint = PrimaryPurple
                ) {
                    ApiKeyField(
                        label = "Mistral API Key (Required for chat)",
                        value = state.mistralKeyState.keyOrNull() ?: "",
                        maskedValue = state.mistralKeyState.maskedOrEmpty(),
                        isValid = state.mistralValid,
                        isTesting = state.isTestingMistral,
                        onValueChange = { viewModel.updateMistralInput(it) },
                        onTest = { viewModel.testMistralKey() }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.mistralInput.isNotBlank()) {
                        Button(
                            onClick = { viewModel.saveMistralKey() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Mistral Key")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(16.dp))

                    ApiKeyField(
                        label = "Groq API Key (Optional, for voice)",
                        value = state.groqKeyState.keyOrNull() ?: "",
                        maskedValue = state.groqKeyState.maskedOrEmpty(),
                        isValid = state.groqValid,
                        isTesting = state.isTestingGroq,
                        onValueChange = { viewModel.updateGroqInput(it) },
                        onTest = { viewModel.testGroqKey() }
                    )

                    if (state.groqInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.saveGroqKey() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Groq Key")
                        }
                    }
                }
            }

            // Section 2: Model & Generation
            item {
                SettingsCard(
                    title = "Model & Parameters",
                    icon = Icons.Default.AutoAwesome,
                    iconTint = PrimaryIndigo
                ) {
                    Text(
                        "Default Model",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    var modelExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { modelExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(state.mistralModel, style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            listOf(
                                "mistral-small-latest",
                                "mistral-medium-latest",
                                "mistral-large-latest",
                                "codestral-latest",
                                "open-mistral-nemo"
                            ).forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.setMistralModel(model)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Temperature: ${"%.2f".format(state.temperature)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            if (state.temperature < 0.4f) "Precise" else if (state.temperature > 0.8f) "Creative" else "Balanced",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryPurple
                        )
                    }
                    Slider(
                        value = state.temperature,
                        onValueChange = { viewModel.setTemperature(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.customEndpoint,
                        onValueChange = { viewModel.setCustomEndpoint(it) },
                        label = { Text("Custom Endpoint (Optional)") },
                        placeholder = { Text("https://api.mistral.ai/") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Section 3: Voice & Speech
            item {
                SettingsCard(
                    title = "Voice Mode & Audio",
                    icon = Icons.Default.Mic,
                    iconTint = SecondaryCyan
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Voice Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Enable hands-free conversations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.voiceEnabled,
                            onCheckedChange = { viewModel.setVoiceEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-send Transcription", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Send automatically when silence detected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.autoSend,
                            onCheckedChange = { viewModel.setAutoSend(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-play Responses", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Read assistant replies aloud", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.autoPlay,
                            onCheckedChange = { viewModel.setAutoPlay(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Speech Speed: ${"%.1f".format(state.speechSpeed)}x",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Slider(
                        value = state.speechSpeed,
                        onValueChange = { viewModel.setSpeechSpeed(it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = SecondaryCyan, activeTrackColor = SecondaryCyan)
                    )
                }
            }

            // Section 4: Appearance & Theme
            item {
                SettingsCard(
                    title = "Appearance",
                    icon = Icons.Default.Palette,
                    iconTint = AccentViolet
                ) {
                    Text(
                        "Theme Mode",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("system", "dark", "light").forEach { theme ->
                            FilterChip(
                                selected = state.theme == theme,
                                onClick = { viewModel.setTheme(theme) },
                                label = {
                                    Text(
                                        when (theme) {
                                            "system" -> "System"
                                            "dark" -> "Dark Mode"
                                            else -> "Light Mode"
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section 5: Usage & Analytics (Cleanly housed here in settings)
            item {
                SettingsCard(
                    title = "API Usage & Statistics",
                    icon = Icons.Default.Insights,
                    iconTint = TertiaryEmerald
                ) {
                    if (state.apiUsage != null) {
                        val usage = state.apiUsage!!
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Tokens Used", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${usage.totalTokens} tokens",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Today Requests", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${usage.todayRequests} requests",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Estimated Cost", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "$${"%.4f".format(usage.estimatedCostUsd)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TertiaryEmerald
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.refreshUsage() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.clearUsage() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset")
                                }
                            }
                        }
                    } else {
                        Text(
                            "No usage recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 6: Privacy & Data Reset
            item {
                SettingsCard(
                    title = "Data & Privacy",
                    icon = Icons.Default.Security,
                    iconTint = MaterialTheme.colorScheme.error
                ) {
                    OutlinedButton(
                        onClick = { showClearChatsDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Chats")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showWipeDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wipe All App Data & Keys")
                    }
                }
            }
        }

        // Clear Chats Confirmation Dialog
        if (showClearChatsDialog) {
            AlertDialog(
                onDismissRequest = { showClearChatsDialog = false },
                title = { Text("Clear Chat History?") },
                text = { Text("All your conversations will be permanently deleted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllChats()
                            showClearChatsDialog = false
                        }
                    ) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearChatsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Full Wipe Confirmation Dialog
        if (showWipeDialog) {
            AlertDialog(
                onDismissRequest = { showWipeDialog = false },
                title = { Text("Wipe Everything?") },
                text = { Text("This will delete all saved API keys, chat conversations, and reset all settings to default.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllData()
                            showWipeDialog = false
                        }
                    ) {
                        Text("Wipe All Data", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWipeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

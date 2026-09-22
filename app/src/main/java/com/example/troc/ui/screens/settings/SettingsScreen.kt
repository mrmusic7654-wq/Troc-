package com.example.troc.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.troc.domain.model.AccentColor
import com.example.troc.domain.model.SandboxTool
import com.example.troc.domain.model.ThemeMode
import com.example.troc.ui.components.ConfirmDialog
import com.example.troc.ui.components.LabeledSlider
import com.example.troc.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
    var showKey by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(connectionTest.result) {
        if (connectionTest.result != null && !connectionTest.success) {
            // Auto-clear error text after a while
            kotlinx.coroutines.delay(4_000)
            viewModel.clearTestData()
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Erase everything?",
            text = "All chats, sandbox sessions and your API key will be deleted from this device.",
            confirmLabel = "Erase",
            onConfirm = {
                confirmClear = false
                viewModel.clearAllData(onBack)
            },
            onDismiss = { confirmClear = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ------------------------------------------------------------ API
            SectionCard(title = "Mistral API", icon = Icons.Filled.Key) {
                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = viewModel::setApiKey,
                    label = { Text("API key") },
                    placeholder = { Text("Paste your key from console.mistral.ai") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "🙈" else "👁")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::testConnection, enabled = !connectionTest.testing) {
                        if (connectionTest.testing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("  Testing…")
                        } else {
                            Text("Test connection")
                        }
                    }
                    connectionTest.result?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionTest.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text("Model", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "mistral-small-latest" to "Small",
                        "mistral-medium-latest" to "Medium",
                        "mistral-large-latest" to "Large"
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = settings.model == id && settings.customModel.isBlank(),
                            onClick = { viewModel.setModel(id) },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = settings.customModel,
                    onValueChange = viewModel::setCustomModel,
                    label = { Text("Custom model id (overrides selection)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.endpoint,
                    onValueChange = viewModel::setEndpoint,
                    label = { Text("Custom endpoint (self-hosted)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ------------------------------------------------------------ Sandbox
            SectionCard(title = "Sandbox", icon = Icons.Filled.Memory) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable sandbox tools", modifier = Modifier.weight(1f))
                    Switch(checked = settings.sandboxEnabled, onCheckedChange = viewModel::setSandboxEnabled)
                }
                LabeledSlider(
                    label = "Max execution time",
                    value = settings.maxExecutionSeconds.toFloat(),
                    onValueChange = { viewModel.setMaxExecutionSeconds(it.toInt()) },
                    valueRange = 5f..120f,
                    valueLabel = "${settings.maxExecutionSeconds}s"
                )
                Text("Allowed tools", style = MaterialTheme.typography.titleSmall)
                SandboxTool.entries.forEach { tool ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = tool.name in settings.allowedTools,
                            onCheckedChange = { viewModel.toggleTool(tool) }
                        )
                        Text("${tool.emoji} ${tool.label}")
                    }
                }
                Text(
                    "Security: sandbox code runs in isolated interpreters with no network, " +
                        "no file access and hard CPU-time/output budgets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ------------------------------------------------------------ Appearance
            SectionCard(title = "Appearance", icon = Icons.Filled.Palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.setThemeMode(mode) }
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                LabeledSlider(
                    label = "Font size",
                    value = settings.fontScale,
                    onValueChange = viewModel::setFontScale,
                    valueRange = 0.8f..1.4f,
                    valueLabel = "${(settings.fontScale * 100).toInt()}%"
                )
                Text("Accent color", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentColor.entries.forEach { accent ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(accent.hex), CircleShape)
                                .padding(3.dp)
                                .clickable { viewModel.setAccent(accent) }
                        ) {
                            if (settings.accent == accent) {
                                Text("✓", Modifier.align(Alignment.Center), color = Color.White)
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------------------ Data & privacy
            SectionCard(title = "Data & privacy", icon = Icons.Filled.CleaningServices) {
                OutlinedButton(onClick = viewModel::exportAllData, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  Export chats & sessions (zip)")
                }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear all data", color = MaterialTheme.colorScheme.error)
                }
            }

            // ------------------------------------------------------------ About
            SectionCard(title = "About", icon = Icons.Filled.OpenInNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Troc ✨", style = MaterialTheme.typography.titleMedium)
                    Text("  v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Your AI Agent with Sandboxed Superpowers. Powered by the Mistral API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Get an API key → console.mistral.ai",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

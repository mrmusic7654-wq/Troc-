package com.example.troc.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keyState by viewModel.keyState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 4 })
    var apiKey by rememberSaveable { mutableStateOf("") }

    val fileProbe = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> ApiKeyPage(
                    apiKey = apiKey,
                    onApiKeyChange = {
                        apiKey = it
                        viewModel.setApiKey(it)
                    },
                    keyState = keyState,
                    onTest = viewModel::testConnection
                )
                2 -> SecurityPage(
                    onProbeFiles = { fileProbe.launch(arrayOf("*/*")) }
                )
                else -> TutorialPage()
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.complete(); onDone() }) { Text("Skip") }
            Spacer(Modifier.height(1.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(onClick = {
                        kotlinx.coroutines.let { }
                        kotlinx.coroutines.runBlocking { }
                    }, enabled = false) { }
                }
                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            kotlinx.coroutines.let { }
                            advance(pagerState)
                        } else {
                            viewModel.complete()
                            onDone()
                        }
                    }
                ) {
                    Text(if (pagerState.currentPage < 3) "Next" else "Get started ✨")
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private suspend fun advance(pagerState: androidx.compose.foundation.pager.PagerState) {
    val next = (pagerState.currentPage + 1).coerceAtMost(3)
    pagerState.animateScrollToPage(next)
}

@Composable
private fun WelcomePage() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🤖", style = MaterialTheme.typography.headlineMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified))
        Spacer(Modifier.height(12.dp))
        Text("Troc ✨", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your AI Agent with Sandboxed Superpowers",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        FeatureRow(Icons.Filled.SmartToy, "Premium chat with the Mistral API")
        FeatureRow(Icons.Filled.Code, "Sandboxed code execution (Kotlin + JavaScript)")
        FeatureRow(Icons.Filled.Analytics, "CSV / JSON analysis & charts")
        FeatureRow(Icons.Filled.FolderOpen, "File processing with scoped storage")
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ApiKeyPage(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    keyState: OnboardingKeyState,
    onTest: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("1. Connect the brain 🧠", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Troc talks to the Mistral API. Paste your API key — it stays on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Mistral API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onTest, enabled = !keyState.testing) {
                if (keyState.testing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  Testing…")
                } else {
                    Text("Test connection")
                }
            }
            keyState.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (keyState.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Get a free key at console.mistral.ai. You can also skip now and add it later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SecurityPage(onProbeFiles: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("2. Sandboxed & safe 🛡️", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        SecurityBullet("Sandboxed code runs in isolated interpreters — no network, no filesystem escape.")
        SecurityBullet("Hard limits: CPU-time budget, output cap, 5–120s timeouts you control.")
        SecurityBullet("Files are read only through the system picker (scoped storage). Nothing leaves your device except your prompts.")
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Verify file access", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Troc uses the Android document picker — on modern Android this needs no permission. " +
                        "Try picking any file to confirm it works:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onProbeFiles) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text("  Pick a file")
                }
            }
        }
    }
}

@Composable
private fun SecurityBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), Modifier.padding(vertical = 4.dp)) {
        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TutorialPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("3. Three ways to work 🎛️", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("💬 Chat mode", style = MaterialTheme.typography.titleSmall)
                Text("Ask anything — answers stream in with full Markdown, code highlighting and tables.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("🤖 Agent mode", style = MaterialTheme.typography.titleSmall)
                Text("Troc plans, then chains tools: <run_code>, <analyze_data>, <process_file> — results loop back until the task is done.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("🧪 Sandbox", style = MaterialTheme.typography.titleSmall)
                Text("Direct access: run code, crunch CSV/JSON with charts, transform files, or build multi-step workflows.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

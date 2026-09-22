package com.troc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troc.ui.components.VoiceOrb
import com.troc.domain.model.VoiceStatus
import com.troc.ui.theme.*
import com.troc.viewmodel.OnboardingViewModel
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val audioPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(audioPermission.status) {
        viewModel.setAudioPermission(audioPermission.status.isGranted)
    }

    TrocTheme(themeSetting = "dark") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            when (state.step) {
                0 -> OnboardingStep1(onNext = { viewModel.nextStep() })
                1 -> OnboardingStep2(
                    mistralKey = state.mistralKey,
                    groqKey = state.groqKey,
                    mistralValid = state.mistralValid,
                    groqValid = state.groqValid,
                    isTestingMistral = state.isTestingMistral,
                    isTestingGroq = state.isTestingGroq,
                    onMistralChange = { viewModel.updateMistralKey(it) },
                    onGroqChange = { viewModel.updateGroqKey(it) },
                    onTestMistral = { viewModel.testMistral() },
                    onTestGroq = { viewModel.testGroq() },
                    onNext = { viewModel.nextStep() },
                    onSkipGroq = { viewModel.skipGroq() }
                )
                2 -> OnboardingStep3(onNext = { viewModel.nextStep() })
                3 -> OnboardingStep4(
                    hasPermission = state.hasAudioPermission,
                    onRequestPermission = { audioPermission.launchPermissionRequest() },
                    onFinish = {
                        viewModel.saveKeysAndFinish()
                        onFinish()
                    },
                    onSkip = {
                        viewModel.saveKeysAndFinish()
                        onFinish()
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingStep1(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .background(
                    Brush.linearGradient(listOf(PrimaryPurple, SecondaryCyan)),
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("T", style = MaterialTheme.typography.displayLarge.copy(color = LightSurface, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), fontSize = 48.dp.let { androidx.compose.ui.unit.TextUnit(it.value, androidx.compose.ui.unit.TextUnitType.Sp) })
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Troc", style = MaterialTheme.typography.displayLarge, color = DarkText)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Your AI Agent with Sandboxed Superpowers", style = MaterialTheme.typography.bodyMedium, color = DarkMuted)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Get Started")
        }
    }
}

@Composable
fun OnboardingStep2(
    mistralKey: String,
    groqKey: String,
    mistralValid: Boolean?,
    groqValid: Boolean?,
    isTestingMistral: Boolean,
    isTestingGroq: Boolean,
    onMistralChange: (String) -> Unit,
    onGroqChange: (String) -> Unit,
    onTestMistral: () -> Unit,
    onTestGroq: () -> Unit,
    onNext: () -> Unit,
    onSkipGroq: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("API Key Setup", style = MaterialTheme.typography.displayLarge, color = DarkText)
        Text("Add your keys to unlock Troc's full power. Groq is optional for voice.", style = MaterialTheme.typography.bodyMedium, color = DarkMuted)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = mistralKey,
            onValueChange = onMistralChange,
            label = { Text("Mistral API Key") },
            placeholder = { Text("•••• last 4 chars") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = onTestMistral, enabled = !isTestingMistral) {
                if (isTestingMistral) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test")
            }
            mistralValid?.let {
                Text(if (it) "✅ Valid" else "❌ Invalid", color = if (it) TertiaryEmerald else ErrorRed)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = groqKey,
            onValueChange = onGroqChange,
            label = { Text("Groq API Key (for voice) — optional") },
            placeholder = { Text("•••• optional") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = onTestGroq, enabled = !isTestingGroq) {
                if (isTestingGroq) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test Voice")
            }
            groqValid?.let {
                Text(if (it) "✅ Valid" else "❌ Invalid", color = if (it) TertiaryEmerald else ErrorRed)
            }
            TextButton(onClick = onSkipGroq) { Text("Skip") }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
            Text("Continue")
        }
    }
}

@Composable
fun OnboardingStep3(onNext: () -> Unit) {
    var currentSlide by remember { mutableStateOf(0) }
    val slides = listOf(
        Triple("💬 Chat", "Streaming conversations with Mistral AI", Icons.Default.Chat),
        Triple("🧪 Agent + Sandbox", "Run Python, JS, analyze CSVs, transform files — all sandboxed", Icons.Default.Code),
        Triple("🎤 Voice Mode", "Hands-free conversations with Groq Whisper + PlayAI TTS", Icons.Default.Mic)
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Feature Tour", style = MaterialTheme.typography.displayLarge, color = DarkText)
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(slides[currentSlide].first, style = MaterialTheme.typography.titleLarge, color = DarkText)
                Spacer(modifier = Modifier.height(12.dp))
                Text(slides[currentSlide].second, style = MaterialTheme.typography.bodyMedium, color = DarkMuted)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            slides.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier.size(8.dp)
                        .background(if (index == currentSlide) PrimaryPurple else DarkSurfaceVariant, RoundedCornerShape(4.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (currentSlide > 0) {
                OutlinedButton(onClick = { currentSlide-- }) { Text("Back") }
            }
            if (currentSlide < slides.size - 1) {
                Button(onClick = { currentSlide++ }) { Text("Next") }
            } else {
                Button(onClick = onNext) { Text("Continue") }
            }
        }
    }
}

@Composable
fun OnboardingStep4(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(64.dp), tint = PrimaryPurple)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Microphone Access", style = MaterialTheme.typography.displayLarge, color = DarkText)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Enable mic for voice chat. You can skip and enable later in settings.", style = MaterialTheme.typography.bodyMedium, color = DarkMuted)
        Spacer(modifier = Modifier.height(24.dp))

        if (hasPermission) {
            Text("✅ Permission granted", color = TertiaryEmerald)
        } else {
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Grant Microphone Permission")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSkip) { Text("Skip for now") }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
            Text("Enter Troc")
        }
    }
}

package com.troc.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troc.ui.components.ApiKeyField
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
                    onNext = { viewModel.nextStep() }
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
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .background(
                    Brush.linearGradient(listOf(PrimaryPurple, PrimaryIndigo, SecondaryCyan)),
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "T",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            "Troc",
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            color = DarkText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your Autonomous AI Assistant & Sandbox",
            style = MaterialTheme.typography.bodyLarge,
            color = DarkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Get Started", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
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
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            "Configure API Key",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = DarkText
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Paste your Mistral API key to get started. You can also configure this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkMuted
        )

        Spacer(modifier = Modifier.height(24.dp))

        ApiKeyField(
            label = "Mistral API Key",
            value = mistralKey,
            maskedValue = if (mistralKey.isNotEmpty()) "••••••••" else "",
            isValid = mistralValid,
            isTesting = isTestingMistral,
            onValueChange = onMistralChange,
            onTest = onTestMistral
        )

        Spacer(modifier = Modifier.height(16.dp))

        ApiKeyField(
            label = "Groq API Key (Optional for Voice)",
            value = groqKey,
            maskedValue = if (groqKey.isNotEmpty()) "••••••••" else "",
            isValid = groqValid,
            isTesting = isTestingGroq,
            onValueChange = onGroqChange,
            onTest = onTestGroq
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
fun OnboardingStep3(onNext: () -> Unit) {
    var currentSlide by remember { mutableStateOf(0) }
    val slides = listOf(
        Triple("💬 Intelligent Chat", "Ultra-fast streaming conversations with Mistral models", Icons.Default.Chat),
        Triple("🧪 Sandboxed Agent", "Autonomous code execution, data crunching, and file processing", Icons.Default.SmartToy),
        Triple("🌐 Live Web Search", "Real-time answers cited with reliable sources", Icons.Default.Language),
        Triple("🎙️ Voice Conversations", "Natural voice conversations with Groq Whisper STT", Icons.Default.Mic)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Features & Superpowers",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = DarkText
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = slides[currentSlide].third,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    slides[currentSlide].first,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    slides[currentSlide].second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            slides.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentSlide) 18.dp else 8.dp, 8.dp)
                        .background(
                            if (index == currentSlide) PrimaryIndigo else DarkSurfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentSlide > 0) {
                OutlinedButton(
                    onClick = { currentSlide-- },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back")
                }
            }
            Button(
                onClick = {
                    if (currentSlide < slides.size - 1) currentSlide++
                    else onNext()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (currentSlide < slides.size - 1) "Next" else "Continue")
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
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SecondaryCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = SecondaryCyan,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Microphone Access",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = DarkText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Enable voice mode to talk naturally with Troc hands-free.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        if (!hasPermission) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Allow Microphone")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSkip) {
                Text("Skip for now", color = DarkMuted)
            }
        } else {
            Surface(
                color = TertiaryEmerald.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TertiaryEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Microphone permission granted", color = TertiaryEmerald, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Start Using Troc")
            }
        }
    }
}

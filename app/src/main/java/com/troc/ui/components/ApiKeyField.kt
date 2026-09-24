package com.troc.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troc.ui.theme.PrimaryPurple
import com.troc.ui.theme.TertiaryEmerald

@Composable
fun ApiKeyField(
    label: String,
    value: String,
    maskedValue: String,
    isValid: Boolean?,
    isTesting: Boolean,
    onValueChange: (String) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var reveal by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var textInput by remember { mutableStateOf("") }

    // Sync input with external value updates if textInput is empty
    LaunchedEffect(value) {
        if (textInput.isEmpty() && value.isNotEmpty()) {
            textInput = value
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                when (isValid) {
                    true -> TertiaryEmerald.copy(alpha = 0.5f)
                    false -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    null -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                }
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Label + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryPurple
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Status Badge
                AnimatedVisibility(
                    visible = isValid != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    when (isValid) {
                        true -> {
                            Surface(
                                color = TertiaryEmerald.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = TertiaryEmerald
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Connected",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = TertiaryEmerald
                                    )
                                }
                            }
                        }
                        false -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Invalid Key",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        null -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = {
                    textInput = it
                    onValueChange(it)
                },
                placeholder = {
                    Text(
                        if (maskedValue.isNotBlank() && maskedValue != "Not set") maskedValue else "Paste your API key here (sk-...)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (textInput.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    textInput = ""
                                    onValueChange("")
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { reveal = !reveal },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (reveal) "Hide key" else "Show key",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row: Paste from clipboard + Test connection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Paste button
                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            val clean = clip.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
                            textInput = clean
                            onValueChange(clean)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste", style = MaterialTheme.typography.labelMedium)
                }

                // Test connection button
                FilledTonalButton(
                    onClick = onTest,
                    enabled = !isTesting && (textInput.isNotBlank() || value.isNotBlank()),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryPurple
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying…", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(
                            Icons.Default.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

package com.troc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

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
    var inputText by remember(value) { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            when (isValid) {
                true -> {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("●", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Valid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
                false -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("●", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Invalid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                null -> {}
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = if (reveal) inputText.ifEmpty { value } else "",
            onValueChange = {
                inputText = it
                onValueChange(it)
            },
            placeholder = {
                Text(
                    maskedValue.ifEmpty { "Paste your API key here" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { reveal = !reveal }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (reveal) "Hide" else "Show",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = when (isValid) {
                    true -> MaterialTheme.colorScheme.tertiary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.primary
                }
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onTest,
                enabled = !isTesting && (inputText.isNotBlank() || value.isNotBlank()),
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Testing…", style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("Test Connection", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (isValid == true) {
                Text(
                    "✓ Key is working",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else if (isValid == false) {
                Text(
                    "Check key and try again",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

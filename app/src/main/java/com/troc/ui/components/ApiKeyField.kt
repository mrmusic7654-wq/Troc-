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
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = if (reveal) inputText.ifEmpty { value } else "",
            onValueChange = {
                inputText = it
                onValueChange(it)
            },
            placeholder = { Text(maskedValue) },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            imageVector = if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (reveal) "Hide" else "Show"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTest,
                enabled = !isTesting,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Testing…")
                } else {
                    Text("Test Connection")
                }
            }

            when (isValid) {
                true -> {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text("✅ Valid", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                false -> {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                        Text("❌ Invalid", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                null -> {}
            }
        }
    }
}

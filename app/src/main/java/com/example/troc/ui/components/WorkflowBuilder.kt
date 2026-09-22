package com.example.troc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.troc.domain.model.SandboxLanguage
import com.example.troc.domain.model.SandboxTool
import com.example.troc.domain.model.WorkflowStep

/**
 * Workflow builder shared by the Home agent panel and the Sandbox "Custom" tab:
 * a list of steps (tool + input) that execute in sequence.
 */
@Composable
fun WorkflowBuilder(
    steps: List<WorkflowStep>,
    executing: Boolean,
    onAddStep: () -> Unit,
    onRemoveStep: (String) -> Unit,
    onUpdateStep: (WorkflowStep) -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            WorkflowStepCard(
                index = index,
                step = step,
                onRemove = { onRemoveStep(step.id) },
                onUpdate = onUpdateStep
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onAddStep,
                label = { Text("Add step") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(0.dp)) }
            )
            Button(
                onClick = onRun,
                enabled = !executing && steps.isNotEmpty()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(if (executing) "Running…" else "Execute workflow")
            }
        }
    }
}

@Composable
private fun WorkflowStepCard(
    index: Int,
    step: WorkflowStep,
    onRemove: () -> Unit,
    onUpdate: (WorkflowStep) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Step ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove step", tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolDropdown(
                    selected = step.tool,
                    onSelect = { onUpdate(step.copy(tool = it)) },
                    modifier = Modifier.weight(1f)
                )
                if (step.tool == SandboxTool.CODE) {
                    LanguageDropdown(
                        selected = step.language,
                        onSelect = { onUpdate(step.copy(language = it)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            OutlinedTextField(
                value = step.input,
                onValueChange = { onUpdate(step.copy(input = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        when (step.tool) {
                            SandboxTool.CODE -> "// code — use {{result}} to inject the previous output"
                            SandboxTool.DATA -> "Paste CSV/JSON data…"
                            SandboxTool.FILE -> "Describe the file processing…"
                            SandboxTool.CUSTOM -> "Step input…"
                        }
                    )
                },
                minLines = 2,
                maxLines = 8,
                textStyle = if (step.tool == SandboxTool.CODE) {
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodySmall
                }
            )
        }
    }
}

@Composable
fun ToolDropdown(selected: SandboxTool, onSelect: (SandboxTool) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        androidx.compose.material3.OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${selected.emoji} ${selected.label}", modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SandboxTool.entries.forEach { tool ->
                DropdownMenuItem(
                    text = { Text("${tool.emoji} ${tool.label}") },
                    onClick = {
                        expanded = false
                        onSelect(tool)
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageDropdown(selected: SandboxLanguage, onSelect: (SandboxLanguage) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        androidx.compose.material3.OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SandboxLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.label) },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    }
                )
            }
        }
    }
}

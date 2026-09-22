package com.troc.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.troc.domain.model.ToolCall
import com.troc.domain.model.ToolType
import com.troc.ui.theme.PrimaryPurple
import com.troc.ui.theme.SecondaryCyan
import com.troc.ui.theme.TertiaryEmerald

@Composable
fun ToolCard(
    toolCall: ToolCall,
    modifier: Modifier = Modifier,
    onExpandToggle: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded; onExpandToggle?.invoke() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                toolCall.isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                toolCall.error != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                toolCall.isRunning -> SecondaryCyan.copy(alpha = pulseAlpha)
                toolCall.error != null -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (toolCall.tool) {
                                ToolType.CODE -> PrimaryPurple.copy(alpha = 0.15f)
                                ToolType.DATA -> SecondaryCyan.copy(alpha = 0.15f)
                                ToolType.FILE -> TertiaryEmerald.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (toolCall.tool) {
                            ToolType.CODE -> Icons.Default.Code
                            ToolType.DATA -> Icons.Default.BarChart
                            ToolType.FILE -> Icons.Default.Folder
                            ToolType.CUSTOM -> Icons.Default.Language
                        },
                        contentDescription = null,
                        tint = when (toolCall.tool) {
                            ToolType.CODE -> PrimaryPurple
                            ToolType.DATA -> SecondaryCyan
                            ToolType.FILE -> TertiaryEmerald
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            toolCall.isRunning -> when (toolCall.tool) {
                                ToolType.CODE -> "Running ${toolCall.language ?: "code"} • Autonomous"
                                ToolType.DATA -> "Analyzing data • Auto"
                                ToolType.FILE -> "Processing files • Auto"
                                else -> "Running ${toolCall.tool} • Auto"
                            }
                            toolCall.error != null -> "Failed: ${toolCall.tool}"
                            else -> "${toolCall.tool.name.lowercase().replaceFirstChar { it.uppercase() }} • ${toolCall.executionTimeMs ?: 0}ms • Done"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!toolCall.isRunning && toolCall.input.isNotBlank()) {
                        Text(
                            toolCall.input.take(60) + if (toolCall.input.length > 60) "..." else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (toolCall.isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryPurple)
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Input section
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Input", style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    toolCall.language ?: toolCall.tool.name,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = toolCall.input.take(2000),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.1f
                                )
                            )
                        }
                    }

                    // Output section
                    Column {
                        Text("Output", style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = if (toolCall.error != null) MaterialTheme.colorScheme.error else TertiaryEmerald)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (toolCall.error != null) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                text = toolCall.output ?: toolCall.error ?: "No output",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (toolCall.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

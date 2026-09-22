package com.troc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.troc.domain.model.ApiUsage
import com.troc.ui.theme.PrimaryPurple
import com.troc.ui.theme.SecondaryCyan

@Composable
fun UsageCard(
    usage: ApiUsage,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DataUsage, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Live API Usage", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    }
                }
                if (onClear != null) {
                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Tokens progress
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tokens", style = MaterialTheme.typography.labelSmall)
                    Text("${usage.totalTokens}/${usage.freePlanLimitTokens} (${"%.1f".format(usage.usagePercent)}%)", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = (usage.usagePercent / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = when {
                        usage.isOverLimit -> MaterialTheme.colorScheme.error
                        usage.isNearLimit -> MaterialTheme.colorScheme.tertiary
                        else -> PrimaryPurple
                    }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatChip(label = "Today", value = "${usage.todayRequests} req", icon = Icons.Default.CalendarToday)
                StatChip(label = "Cost", value = "$${"%.4f".format(usage.estimatedCostUsd)}", icon = Icons.Default.AttachMoney)
                StatChip(label = "Remaining", value = "${usage.remainingTokens / 1000}k", icon = Icons.Default.BatteryChargingFull)
            }

            if (usage.isNearLimit) {
                Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (usage.isOverLimit) "Limit exceeded! Requests may fail." else "Approaching free tier limit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.labelMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WebSearchToggle(
    isEnabled: Boolean,
    isSearching: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isEnabled,
        onClick = onToggle,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEnabled) "Web Search ON" else "Web Search OFF", style = MaterialTheme.typography.labelSmall)
                if (isSearching) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                }
            }
        },
        leadingIcon = if (isEnabled) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SecondaryCyan.copy(alpha = 0.2f),
            selectedLabelColor = SecondaryCyan
        ),
        modifier = modifier
    )
}

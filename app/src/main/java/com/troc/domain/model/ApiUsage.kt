package com.troc.domain.model

import java.text.SimpleDateFormat
import java.util.*

data class ApiUsage(
    val totalPromptTokens: Long = 0,
    val totalCompletionTokens: Long = 0,
    val totalTokens: Long = 0,
    val requestCount: Int = 0,
    val todayPromptTokens: Long = 0,
    val todayCompletionTokens: Long = 0,
    val todayRequests: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val freePlanLimitTokens: Long = 1_000_000_000L,
    val freePlanRequestLimit: Int = 1000,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val remainingTokens: Long get() = (freePlanLimitTokens - totalTokens).coerceAtLeast(0)
    val remainingRequests: Int get() = (freePlanRequestLimit - requestCount).coerceAtLeast(0)
    val usagePercent: Float get() = if (freePlanLimitTokens > 0) (totalTokens.toFloat() / freePlanLimitTokens * 100).coerceIn(0f, 100f) else 0f
    val todayUsagePercent: Float get() = if (freePlanLimitTokens > 0) (todayPromptTokens + todayCompletionTokens).toFloat() / freePlanLimitTokens * 100 else 0f
    val isNearLimit: Boolean get() = usagePercent > 80f
    val isOverLimit: Boolean get() = remainingTokens <= 0 || remainingRequests <= 0
}

data class UsageHistoryItem(
    val id: String,
    val timestamp: Long,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val costUsd: Double,
    val isWebSearch: Boolean = false
) {
    fun formattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

// Model pricing (approximate, per 1M tokens)
enum class MistralModelPricing(val modelId: String, val promptPer1M: Double, val completionPer1M: Double) {
    SMALL("mistral-small-latest", 2.0, 6.0),
    MEDIUM("mistral-medium-latest", 2.7, 8.1),
    LARGE("mistral-large-latest", 8.0, 24.0),
    CODESTRAL("codestral-latest", 1.0, 3.0),
    NEMO("open-mistral-nemo", 0.3, 0.3),
    MINISTRAL_3B("ministral-3b-latest", 0.04, 0.04),
    MINISTRAL_8B("ministral-8b-latest", 0.1, 0.1);

    companion object {
        fun forModel(model: String): MistralModelPricing {
            return values().find { model.contains(it.modelId, ignoreCase = true) } ?: SMALL
        }
    }

    fun calculateCost(promptTokens: Int, completionTokens: Int): Double {
        return (promptTokens / 1_000_000.0 * promptPer1M) + (completionTokens / 1_000_000.0 * completionPer1M)
    }
}

enum class ChatModeExtended {
    CHAT,
    AGENT,
    WEB_SEARCH
}

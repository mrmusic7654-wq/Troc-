package com.troc.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_stats")
data class UsageEntity(
    @PrimaryKey val id: String = "current",
    val totalPromptTokens: Long = 0,
    val totalCompletionTokens: Long = 0,
    val totalTokens: Long = 0,
    val requestCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    // Daily tracking
    val todayPromptTokens: Long = 0,
    val todayCompletionTokens: Long = 0,
    val todayRequests: Int = 0,
    val todayDate: String = "", // YYYY-MM-DD
    // Estimated cost and limits
    val estimatedCostUsd: Double = 0.0,
    val freePlanLimitTokens: Long = 1_000_000_000L, // 1B tokens approx for free tier, adjustable
    val freePlanRequestLimit: Int = 1000
)

@Entity(tableName = "usage_history")
data class UsageHistoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val costUsd: Double,
    val isWebSearch: Boolean = false
)

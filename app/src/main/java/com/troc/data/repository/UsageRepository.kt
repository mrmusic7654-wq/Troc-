package com.troc.data.repository

import com.troc.data.db.UsageDao
import com.troc.data.db.UsageEntity
import com.troc.data.db.UsageHistoryEntity
import com.troc.domain.model.ApiUsage
import com.troc.domain.model.MistralModelPricing
import com.troc.domain.model.UsageHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepository @Inject constructor(
    private val usageDao: UsageDao
) {
    fun observeUsage(): Flow<ApiUsage> {
        return usageDao.observeUsage().map { entity ->
            entity?.toDomain() ?: ApiUsage()
        }
    }

    suspend fun getUsage(): ApiUsage {
        return usageDao.getUsage()?.toDomain() ?: ApiUsage()
    }

    fun observeHistory(): Flow<List<UsageHistoryItem>> {
        return usageDao.observeHistory().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun trackUsage(
        model: String,
        promptTokens: Int,
        completionTokens: Int,
        isWebSearch: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val pricing = MistralModelPricing.forModel(model)
        val cost = pricing.calculateCost(promptTokens, completionTokens)

        // Update current stats
        val current = usageDao.getUsage() ?: UsageEntity()

        val isNewDay = current.todayDate != todayStr

        val updated = current.copy(
            totalPromptTokens = current.totalPromptTokens + promptTokens,
            totalCompletionTokens = current.totalCompletionTokens + completionTokens,
            totalTokens = current.totalTokens + promptTokens + completionTokens,
            requestCount = current.requestCount + 1,
            lastUpdated = now,
            todayPromptTokens = if (isNewDay) promptTokens.toLong() else current.todayPromptTokens + promptTokens,
            todayCompletionTokens = if (isNewDay) completionTokens.toLong() else current.todayCompletionTokens + completionTokens,
            todayRequests = if (isNewDay) 1 else current.todayRequests + 1,
            todayDate = todayStr,
            estimatedCostUsd = current.estimatedCostUsd + cost
        )

        usageDao.upsertUsage(updated)

        // Insert history
        val history = UsageHistoryEntity(
            id = UUID.randomUUID().toString(),
            timestamp = now,
            model = model,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = promptTokens + completionTokens,
            costUsd = cost,
            isWebSearch = isWebSearch
        )
        usageDao.insertHistory(history)
    }

    suspend fun clearAll() {
        usageDao.clearStats()
        usageDao.clearHistory()
    }

    // Estimate tokens from text (rough: 1 token ~ 4 chars)
    fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    private fun UsageEntity.toDomain(): ApiUsage {
        return ApiUsage(
            totalPromptTokens = totalPromptTokens,
            totalCompletionTokens = totalCompletionTokens,
            totalTokens = totalTokens,
            requestCount = requestCount,
            todayPromptTokens = todayPromptTokens,
            todayCompletionTokens = todayCompletionTokens,
            todayRequests = todayRequests,
            estimatedCostUsd = estimatedCostUsd,
            freePlanLimitTokens = freePlanLimitTokens,
            freePlanRequestLimit = freePlanRequestLimit,
            lastUpdated = lastUpdated
        )
    }

    private fun UsageHistoryEntity.toDomain(): UsageHistoryItem {
        return UsageHistoryItem(
            id = id,
            timestamp = timestamp,
            model = model,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            costUsd = costUsd,
            isWebSearch = isWebSearch
        )
    }
}

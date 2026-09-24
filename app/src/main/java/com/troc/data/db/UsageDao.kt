package com.troc.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_stats WHERE id = 'current' LIMIT 1")
    fun observeUsage(): Flow<UsageEntity?>

    @Query("SELECT * FROM usage_stats WHERE id = 'current' LIMIT 1")
    suspend fun getUsage(): UsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsage(entity: UsageEntity)

    @Query("SELECT * FROM usage_history ORDER BY timestamp DESC LIMIT 100")
    fun observeHistory(): Flow<List<UsageHistoryEntity>>

    @Query("SELECT * FROM usage_history ORDER BY timestamp DESC")
    suspend fun getHistory(): List<UsageHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: UsageHistoryEntity)

    @Query("DELETE FROM usage_history")
    suspend fun clearHistory()

    @Query("DELETE FROM usage_stats")
    suspend fun clearStats()
}

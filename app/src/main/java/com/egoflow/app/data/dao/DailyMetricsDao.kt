package com.egoflow.app.data.dao

import androidx.room.*
import com.egoflow.app.data.entity.DailyMetricsEntity

@Dao
interface DailyMetricsDao {
    @Query("SELECT * FROM daily_metrics WHERE date_str = :dateStr")
    suspend fun getMetricsForDay(dateStr: String): DailyMetricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metrics: DailyMetricsEntity)

    @Update
    suspend fun update(metrics: DailyMetricsEntity)

    @Query("SELECT * FROM daily_metrics ORDER BY date_str DESC LIMIT 7")
    suspend fun getRecentWeek(): List<DailyMetricsEntity>
}

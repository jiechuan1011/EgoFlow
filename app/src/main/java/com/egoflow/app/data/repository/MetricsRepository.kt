package com.egoflow.app.data.repository

import com.egoflow.app.data.dao.DailyMetricsDao
import com.egoflow.app.data.entity.DailyMetricsEntity

class MetricsRepository(private val metricsDao: DailyMetricsDao) {

    suspend fun getMetricsForDay(dateStr: String): DailyMetricsEntity? =
        metricsDao.getMetricsForDay(dateStr)

    suspend fun saveMetrics(metrics: DailyMetricsEntity) =
        metricsDao.insert(metrics)

    suspend fun getRecentWeek(): List<DailyMetricsEntity> =
        metricsDao.getRecentWeek()
}

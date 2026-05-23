package com.egoflow.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_metrics")
data class DailyMetricsEntity(
    @PrimaryKey val dateStr: String,
    @ColumnInfo(name = "sleep_duration_minutes") val sleepDurationMinutes: Int? = null,
    @ColumnInfo(name = "fatigue_level") val fatigueLevel: String? = null, // FRESH, NORMAL, TIRED
    @ColumnInfo(name = "workout_done") val workoutDone: Int = 0,
    @ColumnInfo(name = "supplements_json") val supplementsJson: String? = null
)

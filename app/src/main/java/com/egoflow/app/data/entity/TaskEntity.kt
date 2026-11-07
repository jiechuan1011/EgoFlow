package com.egoflow.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "category") val category: String, // MAIN_LINE or SUB_LINE
    @ColumnInfo(name = "drain_level") val drainLevel: String, // HIGH or LOW
    @ColumnInfo(name = "status") val status: String = "POOL", // POOL, SCHEDULED, DONE, ABANDONED
    @ColumnInfo(name = "deadline") val deadline: Long? = null,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

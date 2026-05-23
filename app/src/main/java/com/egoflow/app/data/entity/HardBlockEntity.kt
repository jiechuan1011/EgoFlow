package com.egoflow.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hard_blocks")
data class HardBlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "subject_name") val subjectName: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long
)

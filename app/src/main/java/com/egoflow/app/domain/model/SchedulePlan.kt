package com.egoflow.app.domain.model

import com.egoflow.app.data.entity.TaskEntity

data class SchedulePlan(
    val dateStr: String,
    val energyBlocks: List<EnergyBlock>,
    val totalMainLineMinutes: Int = 0,
    val totalSubLineMinutes: Int = 0,
    val unlockedRewardMinutes: Int = 0,
    val subLineTasks: List<TaskEntity> = emptyList()
)

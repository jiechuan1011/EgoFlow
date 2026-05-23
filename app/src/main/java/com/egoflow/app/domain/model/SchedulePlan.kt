package com.egoflow.app.domain.model

data class SchedulePlan(
    val dateStr: String,
    val energyBlocks: List<EnergyBlock>,
    val totalMainLineMinutes: Int = 0,
    val totalSubLineMinutes: Int = 0,
    val unlockedRewardMinutes: Int = 0
)

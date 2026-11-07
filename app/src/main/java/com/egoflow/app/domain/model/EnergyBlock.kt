package com.egoflow.app.domain.model

data class EnergyBlock(
    val id: String,
    val title: String,
    val taskId: String,
    val category: String,       // MAIN_LINE or SUB_LINE
    val drainLevel: String,     // HIGH or LOW
    val startTime: Long,
    val endTime: Long,
    val isHardBlock: Boolean = false,
    val isCompleted: Boolean = false
)

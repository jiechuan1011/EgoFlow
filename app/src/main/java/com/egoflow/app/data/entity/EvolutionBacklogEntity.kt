package com.egoflow.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evolution_backlog")
data class EvolutionBacklogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source") val source: String, // USER_PROMPT or AI_DIAGNOSIS
    @ColumnInfo(name = "category") val category: String, // TECH_STACK, FEATURE_REQ, UI_UX
    @ColumnInfo(name = "raw_content") val rawContent: String,
    @ColumnInfo(name = "ai_refined_spec") val aiRefinedSpec: String? = null,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    @ColumnInfo(name = "status") val status: String = "PENDING" // PENDING, IMPLEMENTED, DEPRECATED
)

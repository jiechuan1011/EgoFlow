package com.egoflow.app.domain.model

data class CoachMessage(
    val id: String,
    val role: String, // "user" or "coach"
    val content: String,
    val timestamp: Long,
    val isEvolutionIntercepted: Boolean = false
)

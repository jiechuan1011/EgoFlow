package com.egoflow.app.domain.model

data class EvolutionConfig(
    val evolutionAction: String = "",
    val diagnose: String = "",
    val targetConfigOverrides: Map<String, Any> = emptyMap(),
    val injectedCoachNotes: String = ""
)

package com.egoflow.app.ui.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.data.entity.EvolutionBacklogEntity
import com.egoflow.app.data.repository.EvolutionRepository
import com.egoflow.app.scheduler.ElasticSchedulingEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EvolutionUiState(
    val entries: List<EvolutionBacklogEntity> = emptyList(),
    val selectedFilter: String = "ALL", // ALL, USER_PROMPT, AI_DIAGNOSIS
    val configOverrides: Map<String, Any> = emptyMap(),
    val isLoading: Boolean = true
)

class EvolutionCenterViewModel(
    private val evolutionRepository: EvolutionRepository,
    private val schedulingEngine: ElasticSchedulingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(EvolutionUiState())
    val uiState: StateFlow<EvolutionUiState> = _uiState.asStateFlow()

    init {
        loadEntries()
        loadConfig()
    }

    private fun loadEntries() {
        viewModelScope.launch {
            evolutionRepository.getAll().collect { entries ->
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            }
        }
    }

    private fun loadConfig() {
        val config = schedulingEngine.getConfig()
        _uiState.update {
            it.copy(
                configOverrides = mapOf(
                    "high_drain_buffer_minutes" to config.highDrainBufferMinutes,
                    "sub_line_lock_until_hours" to config.subLineLockUntilHours,
                    "main_line_threshold_minutes" to config.mainLineThresholdMinutes
                )
            )
        }
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun markImplemented(id: String) {
        viewModelScope.launch {
            evolutionRepository.updateStatus(id, "IMPLEMENTED")
        }
    }

    fun markDeprecated(id: String) {
        viewModelScope.launch {
            evolutionRepository.updateStatus(id, "DEPRECATED")
        }
    }

    /**
     * 导出月度进化蓝图（生成 Markdown）
     */
    fun exportBlueprint(): String {
        val sb = StringBuilder()
        sb.appendLine("# EgoFlow 月度进化蓝图")
        sb.appendLine()
        sb.appendLine("## 1. 用户驱动功能需求 (User-Driven Features)")
        sb.appendLine()
        val userEntries = _uiState.value.entries.filter {
            it.source == "USER_PROMPT" && it.status == "PENDING"
        }
        if (userEntries.isEmpty()) {
            sb.appendLine("无待处理用户需求。")
        } else {
            userEntries.forEach { entry ->
                sb.appendLine("- **${entry.category}**: ${entry.rawContent}")
                if (!entry.aiRefinedSpec.isNullOrBlank()) {
                    sb.appendLine("  - 细化规格: ${entry.aiRefinedSpec}")
                }
            }
        }

        sb.appendLine()
        sb.appendLine("## 2. AI 自主进化突变案 (AI-Self-Reflected Mutations)")
        sb.appendLine()
        val aiEntries = _uiState.value.entries.filter {
            it.source == "AI_DIAGNOSIS" && it.status == "PENDING"
        }
        if (aiEntries.isEmpty()) {
            sb.appendLine("无待处理 AI 诊断。")
        } else {
            aiEntries.forEach { entry ->
                sb.appendLine("- **${entry.category}**: ${entry.rawContent}")
            }
        }

        sb.appendLine()
        sb.appendLine("## 3. 当前系统配置覆盖")
        sb.appendLine()
        _uiState.value.configOverrides.forEach { (key, value) ->
            sb.appendLine("- `$key`: $value")
        }

        return sb.toString()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = EgoFlowApp.instance
            return EvolutionCenterViewModel(
                evolutionRepository = app.evolutionRepository,
                schedulingEngine = app.schedulingEngine
            ) as T
        }
    }
}

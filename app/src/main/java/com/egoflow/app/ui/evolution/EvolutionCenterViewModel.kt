package com.egoflow.app.ui.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.data.entity.EvolutionBacklogEntity
import com.egoflow.app.data.repository.EvolutionRepository
import com.egoflow.app.data.repository.TaskRepository
import com.egoflow.app.scheduler.ElasticSchedulingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class EvolutionUiState(
    val entries: List<EvolutionBacklogEntity> = emptyList(),
    val selectedFilter: String = "ALL", // ALL, USER_PROMPT, AI_DIAGNOSIS
    val configOverrides: Map<String, Any> = emptyMap(),
    val isLoading: Boolean = true,
    val isEvolving: Boolean = false,
    val evolveResult: String? = null
)

class EvolutionCenterViewModel(
    private val evolutionRepository: EvolutionRepository,
    private val schedulingEngine: ElasticSchedulingEngine,
    private val taskRepository: TaskRepository
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

    /** 每日 AI 自我进化：分析已完成任务生成诊断条目 */
    fun runDailyEvolution() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEvolving = true) }
            try {
                val yesterday = System.currentTimeMillis() - 86400_000
                val doneMinutes = withContext(Dispatchers.IO) {
                    taskRepository.getCompletedMainLineMinutesSince(yesterday)
                }
                val count = doneMinutes / 30 // 大约每30分钟算一个任务
                if (count > 0) {
                    evolutionRepository.addEntry(
                        source = "AI_DIAGNOSIS",
                        category = "TECH_STACK",
                        rawContent = "每日自我进化：过去24小时完成了约 ${count} 个任务（${doneMinutes}分钟）。建议：${generateSuggestion(count, doneMinutes)}",
                        aiRefinedSpec = "自动生成于 ${java.text.SimpleDateFormat("MM月dd日", java.util.Locale.CHINA).format(java.util.Date())}"
                    )
                    _uiState.update { it.copy(evolveResult = "已生成 ${count} 个任务的进化分析") }
                } else {
                    evolutionRepository.addEntry(
                        source = "AI_DIAGNOSIS",
                        category = "UI_UX",
                        rawContent = "每日自我进化：过去24小时无已完成任务。考虑降低任务门槛或检查排程合理性。",
                        aiRefinedSpec = "自动生成"
                    )
                    _uiState.update { it.copy(evolveResult = "今日无完成任务，已记录诊断") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(evolveResult = "进化分析失败: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isEvolving = false) }
            }
        }
    }

    fun clearEvolveResult() {
        _uiState.update { it.copy(evolveResult = null) }
    }

    private fun generateSuggestion(count: Int, minutes: Int): String {
        return when {
            minutes >= 480 -> "已完成 ${minutes} 分钟，已达推荐日上限。建议适当休息。"
            minutes >= 240 -> "高效日！已完成 ${minutes} 分钟。继续保持当前节奏。"
            minutes >= 120 -> "进度正常（${minutes}分钟）。可考虑增加一个高耗任务。"
            else -> "今日完成较少（${minutes}分钟）。检查是否有任务被阻塞。"
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

    fun markAllImplemented() {
        viewModelScope.launch {
            evolutionRepository.getAll().first().forEach { entry ->
                if (entry.status == "PENDING") {
                    evolutionRepository.updateStatus(entry.id, "IMPLEMENTED")
                }
            }
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
                schedulingEngine = app.schedulingEngine,
                taskRepository = app.taskRepository
            ) as T
        }
    }
}

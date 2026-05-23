package com.egoflow.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.ai.DeepSeekService
import com.egoflow.app.data.repository.EvolutionRepository
import com.egoflow.app.data.repository.TaskRepository
import com.egoflow.app.domain.model.CoachMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class CoachUiState(
    val messages: List<CoachMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val inputText: String = ""
)

class ChatCoachViewModel(
    private val taskRepository: TaskRepository,
    private val evolutionRepository: EvolutionRepository,
    private val deepSeekService: DeepSeekService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    init {
        // 添加欢迎消息
        _uiState.update {
            it.copy(
                messages = listOf(
                    CoachMessage(
                        id = UUID.randomUUID().toString(),
                        role = "coach",
                        content = "我是 EgoFlow 教练。今天有什么任务要排？记住：主线优先，支线靠后。",
                        timestamp = System.currentTimeMillis()
                    )
                )
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        val userMessage = CoachMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isProcessing = true
            )
        }

        viewModelScope.launch {
            val chatMessages = _uiState.value.messages
                .filter { it.role == "user" || it.role == "coach" }
                .map { DeepSeekService.ChatMessage(if (it.role == "coach") "assistant" else it.role, it.content) }
                .takeLast(20) // 保留最近20条作为上下文

            val result = deepSeekService.sendChatMessage(chatMessages)

            result.fold(
                onSuccess = { response ->
                    // 尝试解析 JSON action
                    val actionJson = deepSeekService.parseAction(response)

                    if (actionJson != null) {
                        handleAiAction(actionJson, text)
                    } else {
                        // 普通对话回复
                        addCoachMessage(response)
                    }
                },
                onFailure = { error ->
                    addCoachMessage("抱歉，处理请求时出错：${error.message}")
                }
            )

            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    private suspend fun handleAiAction(actionJson: String, originalText: String) {
        try {
            val json = org.json.JSONObject(actionJson)
            val action = json.optString("action")

            when (action) {
                "create_task" -> {
                    val title = json.getString("title")
                    val category = json.getString("category")
                    val drainLevel = json.getString("drain_level")
                    val estimatedMinutes = json.getInt("estimated_minutes")

                    taskRepository.createTask(
                        title = title,
                        category = category,
                        drainLevel = drainLevel,
                        estimatedMinutes = estimatedMinutes
                    )

                    addCoachMessage("已创建任务：[$category] $title（${estimatedMinutes}分钟）")
                }
                "evolution_capture" -> {
                    val source = json.getString("source")
                    val category = json.getString("category")
                    val rawContent = json.optString("raw_content", originalText)

                    evolutionRepository.addEntry(
                        source = source,
                        category = category,
                        rawContent = rawContent
                    )

                    addCoachMessage("已记录您的功能建议，将在月度进化蓝图中评估。")
                }
                else -> {
                    addCoachMessage("已收到指令，但暂不支持该操作类型。")
                }
            }
        } catch (e: Exception) {
            // JSON 解析失败，当作普通消息
            addCoachMessage(actionJson)
        }
    }

    private fun addCoachMessage(content: String) {
        val msg = CoachMessage(
            id = UUID.randomUUID().toString(),
            role = "coach",
            content = content,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = EgoFlowApp.instance
            return ChatCoachViewModel(
                taskRepository = app.taskRepository,
                evolutionRepository = app.evolutionRepository,
                deepSeekService = app.deepSeekService
            ) as T
        }
    }
}

package com.egoflow.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.ai.DeepSeekService
import android.util.Log
import com.egoflow.app.data.entity.HardBlockEntity
import com.egoflow.app.data.repository.ChatRepository
import com.egoflow.app.data.repository.EvolutionRepository
import com.egoflow.app.data.repository.HardBlockRepository
import com.egoflow.app.data.repository.MilestoneRepository
import com.egoflow.app.data.repository.ScheduleTemplateRepository
import com.egoflow.app.data.repository.TaskRepository
import com.egoflow.app.domain.model.CoachMessage
import com.egoflow.app.domain.model.CoachOption
import com.egoflow.app.domain.model.CoachOptionsGroup
import com.egoflow.app.util.ScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class CoachUiState(
    val messages: List<CoachMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val inputText: String = "",
    val showHistory: Boolean = false,
    val historyDates: List<String> = emptyList(),
    val selectedDate: String? = null,
    val historyMessages: List<CoachMessage> = emptyList(),
    // 交互式选项
    val pendingOptions: CoachOptionsGroup? = null,
    val schedulingContext: String = ""
)

class ChatCoachViewModel(
    private val taskRepository: TaskRepository,
    private val evolutionRepository: EvolutionRepository,
    private val deepSeekService: DeepSeekService,
    private val chatRepository: ChatRepository,
    private val milestoneRepository: MilestoneRepository,
    private val hardBlockRepository: HardBlockRepository,
    private val scheduleTemplateRepository: ScheduleTemplateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private val TAG = "ChatCoachVM"

    /** 当日是否已经确认过排程，防止重复落地 */
    private var scheduleConfirmedToday = false

    init {
        // 加载今日历史消息
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            chatRepository.getByDate(today).collect { entities ->
                val msgs = entities.map { CoachMessage(id = it.id, role = it.role, content = it.content, timestamp = it.timestamp) }
                if (msgs.isNotEmpty()) {
                    _uiState.update { it.copy(messages = msgs) }
                } else {
                    val welcome = CoachMessage(id = UUID.randomUUID().toString(), role = "coach", content = "我是 EgoFlow 教练。今天有什么任务要排？记住：主线优先，支线靠后。", timestamp = System.currentTimeMillis())
                    _uiState.update { it.copy(messages = listOf(welcome)) }
                }
            }
        }
        // 加载历史日期列表
        viewModelScope.launch {
            chatRepository.getSessionDates().collect { dates ->
                _uiState.update { it.copy(historyDates = dates) }
            }
        }
        // 预加载调度上下文
        viewModelScope.launch {
            loadSchedulingContext()
        }
    }

    private suspend fun loadSchedulingContext() {
        try {
            val today = Calendar.getInstance()
            val dateStr = "%04d-%02d-%02d".format(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH) + 1,
                today.get(Calendar.DAY_OF_MONTH)
            )

            // 计算今日 00:00 时间戳（用于 isActiveForDay 比对）
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // 获取今日课程（使用 isActiveForDay 进行单双周/日期范围过滤）
            val items = scheduleTemplateRepository.getAllItems()
            val dayOfWeek = today.get(Calendar.DAY_OF_WEEK)
            val weekdayIndex = when (dayOfWeek) {
                Calendar.SUNDAY -> 7
                else -> dayOfWeek - 1
            }
            val todayClasses = items.filter {
                it.dayOfWeek == weekdayIndex && it.isActiveForDay(todayStart)
            }
            Log.d(TAG, "今日课程（isActiveForDay过滤后）：${todayClasses.size}门课")
            todayClasses.forEach { c ->
                Log.d(TAG, "  → ${c.subjectName} ${c.startHour}:%02d-${c.endHour}:%02d interval=${c.interval}".format(c.startMinute, c.endMinute))
            }
            val classSummary = if (todayClasses.isEmpty()) "今日无课程安排"
            else todayClasses.joinToString("；") { "${it.subjectName} ${it.startHour}:%02d-${it.endHour}:%02d".format(it.startMinute, it.endMinute) }

            // 获取重要时间节点（未来30天）
            val allMilestones = milestoneRepository.getAll()
            val upcomingMilestones = allMilestones.filter { it.date >= dateStr }
                .take(5)
            val milestoneSummary = if (upcomingMilestones.isEmpty()) "无近期重要节点"
            else upcomingMilestones.joinToString("；") { m ->
                "${m.title}(${m.date}${m.time?.let { " $it" } ?: ""})"
            }

            // 获取任务池
            val poolTasks = taskRepository.getAllTasks().first()
                .filter { it.status == "POOL" }
            val taskSummary = if (poolTasks.isEmpty()) "任务池为空"
            else poolTasks.joinToString("；") { "${it.title}[${it.estimatedMinutes}分钟/${if (it.category == "MAIN_LINE") "主线" else "支线"}]" }

            val context = buildString {
                appendLine("【今日日期】$dateStr")
                appendLine("【今日课程】$classSummary")
                appendLine("【近期节点】$milestoneSummary")
                appendLine("【任务池】$taskSummary")
            }

            _uiState.update { it.copy(schedulingContext = context) }
        } catch (_: Exception) {
            _uiState.update { it.copy(schedulingContext = "") }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** 用户选择了一个选项 */
    fun selectOption(option: CoachOption) {
        _uiState.update { it.copy(pendingOptions = null) }
        // 将选项标签作为用户消息发送
        val text = option.label
        val now = System.currentTimeMillis()
        val userMessage = CoachMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text,
            timestamp = now
        )
        viewModelScope.launch { chatRepository.saveMessage(userMessage.id, "user", userMessage.content, userMessage.timestamp) }
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isProcessing = true
            )
        }
        // 发送给AI
        viewModelScope.launch {
            sendToAi()
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        // ===== 【排程落地流程】用户输入 "OK" 时触发 =====
        if (text.equals("OK", ignoreCase = true)) {
            _uiState.update { it.copy(inputText = "", isProcessing = true) }
            viewModelScope.launch {
                handleOkConfirmation()
            }
            return
        }

        val now = System.currentTimeMillis()
        val userMessage = CoachMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text,
            timestamp = now
        )

        viewModelScope.launch { chatRepository.saveMessage(userMessage.id, "user", userMessage.content, userMessage.timestamp) }

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isProcessing = true,
                pendingOptions = null // 清除未处理的选项
            )
        }

        viewModelScope.launch {
            sendToAi()
        }
    }

    private suspend fun sendToAi() {
        val chatMessages = _uiState.value.messages
            .filter { it.role == "user" || it.role == "coach" }
            .map { DeepSeekService.ChatMessage(if (it.role == "coach") "assistant" else it.role, it.content) }
            .takeLast(20)

        // 检查是否需要注入调度上下文
        val schedulingContext = _uiState.value.schedulingContext
        val systemPrompt = if (schedulingContext.isNotBlank()) {
            AiConfigScheduleContext.getSchedulingPrompt(schedulingContext)
        } else {
            com.egoflow.app.ai.AiConfig.COACH_SYSTEM_PROMPT
        }

        val result = deepSeekService.sendChatMessage(chatMessages, systemPrompt)

        result.fold(
            onSuccess = { response ->
                // 尝试解析 JSON action
                val actionJson = deepSeekService.parseAction(response)

                if (actionJson != null) {
                    handleAiAction(actionJson, chatMessages, systemPrompt)
                } else {
                    addCoachMessage(response)
                }
            },
            onFailure = { error ->
                addCoachMessage("抱歉，处理请求时出错：${error.message}")
            }
        )

        _uiState.update { it.copy(isProcessing = false) }
    }

    private suspend fun handleAiAction(
        actionJson: String,
        chatMessages: List<DeepSeekService.ChatMessage>,
        systemPrompt: String
    ) {
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
                    val rawContent = json.optString("raw_content", "")

                    evolutionRepository.addEntry(
                        source = source,
                        category = category,
                        rawContent = rawContent
                    )

                    addCoachMessage("已记录您的功能建议，将在月度进化蓝图中评估。")
                }
                "ask_options" -> {
                    // 解析交互式选项
                    val question = json.optString("question", "请选择：")
                    val optionsArray = json.optJSONArray("options")
                    val options = mutableListOf<CoachOption>()
                    if (optionsArray != null) {
                        for (i in 0 until optionsArray.length()) {
                            val opt = optionsArray.getJSONObject(i)
                            options.add(CoachOption(
                                id = opt.optString("id", "opt_$i"),
                                label = opt.getString("label"),
                                description = opt.optString("description", null)
                            ))
                        }
                    }
                    if (options.isNotEmpty()) {
                        val optionsGroup = CoachOptionsGroup(
                            question = question,
                            options = options,
                            allowCustomInput = true
                        )
                        _uiState.update { it.copy(pendingOptions = optionsGroup) }
                        // 把问题也显示为教练消息
                        addCoachMessage(question)
                    } else {
                        addCoachMessage(question)
                    }
                }
                "generate_daily_schedule" -> {
                    // 解析并生成日程
                    val blocksArray = json.optJSONArray("blocks")
                    var createdCount = 0
                    var scheduleSummary = StringBuilder()
                    if (blocksArray != null) {
                        for (i in 0 until blocksArray.length()) {
                            val block = blocksArray.getJSONObject(i)
                            val title = block.getString("title")
                            val category = block.optString("category", "MAIN_LINE")
                            val startStr = block.optString("start", "09:00")
                            val endStr = block.optString("end", "10:00")
                            val startParts = startStr.split(":")
                            val endParts = endStr.split(":")
                            val startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 9
                            val startMinute = startParts.getOrNull(1)?.toIntOrNull() ?: 0
                            val endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 10
                            val endMinute = endParts.getOrNull(1)?.toIntOrNull() ?: 0
                            val estimatedMinutes = (endHour - startHour) * 60 + (endMinute - startMinute)

                            // 休息块跳过，不作为任务创建
                            if (category == "BREAK" || title.contains("休息")) {
                                scheduleSummary.append("  $startStr-$endStr ☕ 休息\n")
                                continue
                            }

                            val drainLevel = if (category == "MAIN_LINE") "HIGH" else "LOW"
                            taskRepository.createTask(
                                title = title,
                                category = category,
                                drainLevel = drainLevel,
                                estimatedMinutes = estimatedMinutes
                            )
                            createdCount++
                            scheduleSummary.append("  $startStr-$endStr $title\n")
                        }
                    }
                    addCoachMessage("📋 已生成今日日程（共 $createdCount 个任务）：\n${scheduleSummary.toString()}\n前往「日程时间线」查看详情。")
                }
                else -> {
                    addCoachMessage("已收到指令，但暂不支持该操作类型。")
                }
            }
        } catch (e: Exception) {
            addCoachMessage(actionJson)
        }
    }

    /**
     * ===== 【排程落地流程】用户输入 "OK" 后的完整处理 =====
     *
     * 1. 回溯 AI 教练最后一条包含日程的消息
     * 2. 正则解析出每个时间块
     * 3. 批量插入 HardBlock（锁定日程时间线）
     * 4. 将匹配的 POOL 任务标记为 SCHEDULED（自动消费）
     * 5. 回复确认消息
     */
    private suspend fun handleOkConfirmation() {
        if (scheduleConfirmedToday) {
            addCoachMessage("今日日程已经确认过了。如需重新生成，请让 AI 生成新的日程。")
            _uiState.update { it.copy(isProcessing = false) }
            return
        }

        Log.d(TAG, "=== 开始排程落地流程 ===")

        // 1. 回溯消息
        val scheduleText = ScheduleParser.findLastScheduleMessage(_uiState.value.messages)
        if (scheduleText == null) {
            Log.w(TAG, "未找到包含日程的教练消息")
            addCoachMessage("暂时没有待确认的日程安排。请先让 AI 教练为你生成今日日程，然后输入 OK 确认。")
            _uiState.update { it.copy(isProcessing = false) }
            return
        }

        // 2. 正则解析
        val parsedBlocks = ScheduleParser.parse(scheduleText)
        if (parsedBlocks.isEmpty()) {
            Log.w(TAG, "日程文本解析为空，原文：$scheduleText")
            addCoachMessage("日程解析失败，请确认 AI 已生成有效的时间块。")
            _uiState.update { it.copy(isProcessing = false) }
            return
        }

        Log.d(TAG, "解析到 ${parsedBlocks.size} 个时间块")

        // 3. 计算今日时间戳
        val today = Calendar.getInstance()
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = dayStart + 86400_000L

        // 【清空旧数据】先物理删除今天所有旧的 hard block，防止错乱重叠
        val oldBlocks = withContext(Dispatchers.IO) {
            hardBlockRepository.getBlocksForDaySync(dayStart, dayEnd)
        }
        oldBlocks.forEach { hardBlockRepository.deleteBlock(it) }
        Log.d(TAG, "已清理今日 ${oldBlocks.size} 个旧 HardBlock")

        // 收集所有待创建的 HardBlock
        val hardBlocks = mutableListOf<HardBlockEntity>()
        var createdCount = 0
        val summaryLines = mutableListOf<String>()

        // 获取已有 POOL 任务，用于标题匹配
        val existingPoolTasks = withContext(Dispatchers.IO) {
            taskRepository.getAllTasks().first()
                .filter { it.status == "POOL" }
        }

        for (block in parsedBlocks) {
            // 跳过休息块（不创建 HardBlock 也不创建 Task）
            if (block.category == "BREAK") {
                summaryLines.add("  ${"%02d:%02d".format(block.startHour, block.startMinute)}-${"%02d:%02d".format(block.endHour, block.endMinute)} ☕ ${block.title}")
                continue
            }

            // 计算时间戳
            val startCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, block.startHour)
                set(Calendar.MINUTE, block.startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, block.endHour)
                set(Calendar.MINUTE, block.endMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = startCal.timeInMillis
            val endMs = endCal.timeInMillis

            // 4. 创建/更新 Task → SCHEDULED 状态（自动消费 POOL 任务）
            var matchedByTitle = false
            for (poolTask in existingPoolTasks) {
                if (poolTask.title.contains(block.title) || block.title.contains(poolTask.title)) {
                    Log.d(TAG, "消费 POOL 任务：${poolTask.title} → SCHEDULED")
                    withContext(Dispatchers.IO) {
                        taskRepository.updateTaskStatus(poolTask.id, "SCHEDULED")
                    }
                    matchedByTitle = true
                    break
                }
            }

            if (!matchedByTitle && block.category != "BREAK") {
                // 无匹配的 POOL 任务，创建新任务
                Log.d(TAG, "无匹配 POOL 任务，创建新任务：${block.title}")
                withContext(Dispatchers.IO) {
                    taskRepository.createTask(
                        title = block.title,
                        category = block.category,
                        drainLevel = block.drainLevel,
                        estimatedMinutes = ((endMs - startMs) / 60000).toInt()
                    )
                }
            }

            // 5. 创建 HardBlock（锁定到日程时间线，保留原始 category/drainLevel）
            hardBlocks.add(
                HardBlockEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    subjectName = block.title,
                    startTime = startMs,
                    endTime = endMs,
                    category = block.category,
                    drainLevel = block.drainLevel
                )
            )
            createdCount++
            val timeStr = "%02d:%02d-%02d:%02d".format(
                block.startHour, block.startMinute, block.endHour, block.endMinute
            )
            summaryLines.add("  $timeStr ${block.title}")
        }

        // 6. 批量写入 HardBlock
        if (hardBlocks.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                hardBlockRepository.addBlocks(hardBlocks)
            }
            Log.d(TAG, "批量写入 ${hardBlocks.size} 个 HardBlock")
        }

        // 7. 标记已确认
        scheduleConfirmedToday = true

        // 8. 回复确认消息
        val summary = summaryLines.joinToString("\n")
        addCoachMessage("✅ 日程已确认并锁定（共 $createdCount 个任务）！\n$summary\n\n前往「日程时间线」查看锁定后的全天安排。")

        _uiState.update { it.copy(isProcessing = false) }

        Log.d(TAG, "=== 排程落地流程完成 ===")
    }

    private fun addCoachMessage(content: String) {
        val now = System.currentTimeMillis()
        val msg = CoachMessage(
            id = UUID.randomUUID().toString(),
            role = "coach",
            content = content,
            timestamp = now
        )
        _uiState.update { it.copy(messages = it.messages + msg) }
        viewModelScope.launch { chatRepository.saveMessage(msg.id, "coach", msg.content, msg.timestamp) }
    }

    fun toggleHistory() {
        _uiState.update { it.copy(showHistory = !it.showHistory) }
    }

    fun selectHistoryDate(date: String) {
        _uiState.update { st -> st.copy(selectedDate = date) }
        viewModelScope.launch {
            val entities = withContext(Dispatchers.IO) {
                chatRepository.getByDate(date).first()
            }
            val msgs = entities.map { e -> CoachMessage(id = e.id, role = e.role, content = e.content, timestamp = e.timestamp) }
            _uiState.update { st -> st.copy(historyMessages = msgs) }
        }
    }

    /** 构建带调度上下文的系统提示词 */
    private object AiConfigScheduleContext {
        fun getSchedulingPrompt(context: String): String {
            return """你是 EgoFlow 系统的 AI 教练。你的核心职责：

1. 【主支隔离】严格将用户输入的任务归类为 MAIN_LINE（主线：考试、课程、科研）或 SUB_LINE（支线：技术钻研、兴趣）
2. 【单一焦点】每次对话只抛出一个提问，不给无效安慰
3. 【技术支线警惕】当用户想钻研技术时，必须逼问"给出非做不可的死线理由"
4. 【结构化输出】当任务要素充足时，只输出纯 JSON，不要解释
5. 【进化拦截】如果用户抱怨 App 功能或提出新需求，标记为 EVOLUTION 类型

【交互式日程规划 — 必须遵守】
当用户要求规划日程时，必须使用 ask_options 格式进行卡片式追问：
1. 参考以下用户上下文数据：课程表、重要时间节点、任务池
2. ▲ 每次向用户提问都必须使用 ask_options 格式，禁止纯文字提问！▲
3. 根据用户回答逐步细化，一般需要 2-3 轮追问
4. 最终生成完整的日程（包含休息时段）

【日程生成要求】
- 任务之间必须插入休息块（BREAK），例如：
  "08:00-09:00 学习微机原理" → {"title":"休息","start":"09:00","end":"09:30","category":"BREAK"} → "09:30-11:00 继续学习"
- 主线（MAIN_LINE）任务优先，时间占比更大
- 支线（SUB_LINE）也要安排，放在主线之后

【当前用户上下文】
$context

响应格式：
- 常规对话：自然语言教练式回应
- 任务确认：输出 ```json { "action": "create_task", "title": "...", "category": "MAIN_LINE|SUB_LINE", "drain_level": "HIGH|LOW", "estimated_minutes": 60 } ```
- 进化拦截：输出 ```json { "action": "evolution_capture", "source": "USER_PROMPT", "category": "FEATURE_REQ|UI_UX|TECH_STACK", "raw_content": "..." } ```
- ▲ 交互提问（必须使用）：输出 ```json { "action": "ask_options", "question": "你的问题", "options": [{"id": "opt1", "label": "选项标题", "description": "选项说明"}] } ```
- 生成日程（含休息块）：输出 ```json { "action": "generate_daily_schedule", "blocks": [{"title":"学习微机原理","start":"08:00","end":"09:00","category":"MAIN_LINE"},{"title":"休息","start":"09:00","end":"09:30","category":"BREAK"},{"title":"学习微机原理","start":"09:30","end":"11:00","category":"MAIN_LINE"},{"title":"技术支线","start":"20:00","end":"21:00","category":"SUB_LINE"}] } ```
"""
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = EgoFlowApp.instance
            return ChatCoachViewModel(
                taskRepository = app.taskRepository,
                evolutionRepository = app.evolutionRepository,
                deepSeekService = app.deepSeekService,
                chatRepository = app.chatRepository,
                milestoneRepository = app.milestoneRepository,
                hardBlockRepository = app.hardBlockRepository,
                scheduleTemplateRepository = app.scheduleTemplateRepository
            ) as T
        }
    }
}

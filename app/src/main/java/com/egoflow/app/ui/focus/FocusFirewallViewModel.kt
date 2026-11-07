package com.egoflow.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.data.entity.TaskEntity
import com.egoflow.app.data.repository.TaskRepository
import com.egoflow.app.data.repository.HardBlockRepository
import com.egoflow.app.domain.model.EnergyBlock
import com.egoflow.app.domain.model.SchedulePlan
import com.egoflow.app.scheduler.ElasticSchedulingEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FocusUiState(
    val currentBlock: EnergyBlock? = null,
    val timerSeconds: Long = 0,
    val isTimerRunning: Boolean = false,
    val isPomodoroMode: Boolean = false,
    val schedulePlan: SchedulePlan? = null,
    val completedMainLineMinutes: Int = 0,
    val showTaskPool: Boolean = false,
    val poolTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
    val showTimePicker: Boolean = false,
    val pickerHour: Int = 8,
    val pickerMinute: Int = 0
)

class FocusFirewallViewModel(
    private val taskRepository: TaskRepository,
    private val hardBlockRepository: HardBlockRepository,
    private val schedulingEngine: ElasticSchedulingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        loadTodaySchedule()
    }

    private fun loadTodaySchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 获取当日硬墙
            val dayStart = getDayStartMillis()
            val dayEnd = dayStart + 24 * 3600_000L

            hardBlockRepository.getBlocksForDay(dayStart, dayEnd).collect { hardBlocks ->
                // 获取待排任务
                taskRepository.getAllTasks().collect { allTasks ->
                    val poolTasks = allTasks.filter { it.status == "POOL" }
                    val doneMainLineMinutes = taskRepository.getCompletedMainLineMinutesSince(dayStart)

                    // 生成计划
                    val plan = schedulingEngine.generateDailyPlan(
                        tasks = poolTasks,
                        hardBlocks = hardBlocks,
                        completedMainLineMinutes = doneMainLineMinutes
                    )

                    val currentBlock = findCurrentBlock(plan)

                    _uiState.update {
                        it.copy(
                            currentBlock = currentBlock,
                            schedulePlan = plan,
                            completedMainLineMinutes = doneMainLineMinutes,
                            poolTasks = poolTasks,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun startPomodoro() {
        val block = _uiState.value.currentBlock ?: return
        val totalSeconds = (block.endTime - block.startTime) / 1000

        _uiState.update { it.copy(isPomodoroMode = true, isTimerRunning = true, timerSeconds = totalSeconds) }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerSeconds > 0) {
                delay(1000)
                _uiState.update { it.copy(timerSeconds = it.timerSeconds - 1) }
            }
            // 计时结束
            _uiState.update { it.copy(isTimerRunning = false, isPomodoroMode = false) }
            completeCurrentBlock()
        }
    }

    fun pausePomodoro() {
        _uiState.update { it.copy(isTimerRunning = false) }
        timerJob?.cancel()
    }

    fun resumePomodoro() {
        _uiState.update { it.copy(isTimerRunning = true) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerSeconds > 0) {
                delay(1000)
                _uiState.update { it.copy(timerSeconds = it.timerSeconds - 1) }
            }
            _uiState.update { it.copy(isTimerRunning = false, isPomodoroMode = false) }
            completeCurrentBlock()
        }
    }

    private suspend fun completeCurrentBlock() {
        val block = _uiState.value.currentBlock ?: return
        if (block.category == "MAIN_LINE") {
            taskRepository.updateTaskStatus(block.taskId, "DONE")
            _uiState.update {
                it.copy(completedMainLineMinutes = it.completedMainLineMinutes +
                    ((block.endTime - block.startTime) / 60000).toInt())
            }
        } else {
            taskRepository.updateTaskStatus(block.taskId, "DONE")
        }
        loadTodaySchedule()
    }

    fun toggleTaskPool() {
        _uiState.update { it.copy(showTaskPool = !it.showTaskPool) }
    }

    fun completeWithoutTimer() {
        viewModelScope.launch {
            completeCurrentBlock()
        }
    }

    fun deleteTask() {
        viewModelScope.launch {
            val block = _uiState.value.currentBlock ?: return@launch
            taskRepository.getTaskById(block.taskId)?.let { taskRepository.deleteTask(it) }
            loadTodaySchedule()
        }
    }

    fun skipCurrentBlock() {
        viewModelScope.launch {
            val block = _uiState.value.currentBlock ?: return@launch
            taskRepository.updateTaskStatus(block.taskId, "ABANDONED")
            loadTodaySchedule()
        }
    }

    /** 延期：将任务放回任务池 */
    fun deferTask() {
        viewModelScope.launch {
            val block = _uiState.value.currentBlock ?: return@launch
            taskRepository.updateTaskStatus(block.taskId, "POOL")
            loadTodaySchedule()
        }
    }

    fun showTimePicker() {
        val block = _uiState.value.currentBlock ?: return
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = block.startTime
        _uiState.update { it.copy(showTimePicker = true, pickerHour = cal.get(java.util.Calendar.HOUR_OF_DAY), pickerMinute = cal.get(java.util.Calendar.MINUTE)) }
    }

    fun hideTimePicker() { _uiState.update { it.copy(showTimePicker = false) } }

    fun updatePickerTime(hour: Int, minute: Int) { _uiState.update { it.copy(pickerHour = hour, pickerMinute = minute) } }

    /** 将当前任务重新排到指定时间 */
    fun rescheduleTask(hour: Int, minute: Int) {
        viewModelScope.launch {
            val block = _uiState.value.currentBlock ?: return@launch
            val today = java.util.Calendar.getInstance()
            val newStart = let {
                val c = java.util.Calendar.getInstance()
                c.set(java.util.Calendar.HOUR_OF_DAY, hour)
                c.set(java.util.Calendar.MINUTE, minute)
                c.set(java.util.Calendar.SECOND, 0)
                c.set(java.util.Calendar.MILLISECOND, 0)
                c.timeInMillis
            }
            val duration = block.endTime - block.startTime
            val newEnd = newStart + duration
            // 删除旧 hard block，创建新的
            val existing = hardBlockRepository.getBlocksForDaySync(
                newStart - 86400_000, newStart + 86400_000
            )
            existing.filter { it.id == block.taskId }.forEach { hardBlockRepository.deleteBlock(it) }
            hardBlockRepository.addBlock(subjectName = block.title, startTime = newStart, endTime = newEnd)
            _uiState.update { it.copy(showTimePicker = false) }
            loadTodaySchedule()
        }
    }

    /** 公开的完成方法（给 UI 调用） */
    fun markTaskDone() {
        viewModelScope.launch {
            val block = _uiState.value.currentBlock ?: return@launch
            taskRepository.updateTaskStatus(block.taskId, "DONE")
            _uiState.update {
                it.copy(completedMainLineMinutes = it.completedMainLineMinutes +
                    ((block.endTime - block.startTime) / 60000).toInt())
            }
            loadTodaySchedule()
        }
    }

    private fun findCurrentBlock(plan: SchedulePlan): EnergyBlock? {
        val now = System.currentTimeMillis()
        return plan.energyBlocks.firstOrNull { block ->
            now in block.startTime until block.endTime && !block.isHardBlock
        }
    }

    private fun getDayStartMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = EgoFlowApp.instance
            return FocusFirewallViewModel(
                taskRepository = app.taskRepository,
                hardBlockRepository = app.hardBlockRepository,
                schedulingEngine = app.schedulingEngine
            ) as T
        }
    }
}

package com.egoflow.app.ui.timeline

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.data.entity.TaskEntity
import com.egoflow.app.data.repository.HardBlockRepository
import com.egoflow.app.data.repository.TaskRepository
import com.egoflow.app.domain.model.EnergyBlock
import com.egoflow.app.domain.model.SchedulePlan
import com.egoflow.app.scheduler.ElasticSchedulingEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class TimelineUiState(
    val schedulePlan: SchedulePlan? = null,
    val selectedDate: String = "",
    val dragSource: EnergyBlock? = null,
    val swapTarget: EnergyBlock? = null,
    val swapErrorMessage: String? = null,
    val isLoading: Boolean = true,
    val editingBlock: EnergyBlock? = null,
    val pickerHour: Int = 8,
    val pickerMinute: Int = 0,
    val pickerEndHour: Int = 9,
    val pickerEndMinute: Int = 0
)

class ScheduleTimelineViewModel(
    private val taskRepository: TaskRepository,
    private val hardBlockRepository: HardBlockRepository,
    private val schedulingEngine: ElasticSchedulingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        loadSchedule()
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            try {
                val today = Calendar.getInstance()
                val dateStr = "%04d-%02d-%02d".format(
                    today.get(Calendar.YEAR),
                    today.get(Calendar.MONTH) + 1,
                    today.get(Calendar.DAY_OF_MONTH)
                )
                val dayStart = getDayStartMillis()
                val dayEnd = dayStart + 24 * 3600_000L

                _uiState.update { it.copy(selectedDate = dateStr, isLoading = true) }

                hardBlockRepository.getBlocksForDay(dayStart, dayEnd).collect { hardBlocks ->
                    taskRepository.getAllTasks().collect { tasks ->
                        val poolTasks = tasks.filter { it.status == "POOL" }
                        val doneMainLineMinutes = taskRepository.getCompletedMainLineMinutesSince(dayStart)

                        val plan = schedulingEngine.generateDailyPlan(
                            tasks = poolTasks,
                            hardBlocks = hardBlocks,
                            completedMainLineMinutes = doneMainLineMinutes
                        )

                        _uiState.update {
                            it.copy(
                                schedulePlan = plan,
                                isLoading = false,
                                swapTarget = null,
                                swapErrorMessage = null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TimelineVM", "loadSchedule failed", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ===== 拖拽互换 =====

    fun selectDragSource(block: EnergyBlock) {
        _uiState.update { it.copy(dragSource = block, swapTarget = null, swapErrorMessage = null) }
    }

    fun swapWith(block: EnergyBlock) {
        val source = _uiState.value.dragSource ?: return

        if (!schedulingEngine.canSwapBlocks(source, block)) {
            _uiState.update {
                it.copy(
                    swapErrorMessage = "错误：不同能量损耗等级的任务不可互换",
                    dragSource = null
                )
            }
            return
        }

        viewModelScope.launch {
            taskRepository.updateTaskStatus(source.taskId, "POOL")
            taskRepository.updateTaskStatus(block.taskId, "POOL")
        }
    }

    fun clearDragSource() { _uiState.update { it.copy(dragSource = null) } }
    fun clearError() { _uiState.update { it.copy(swapErrorMessage = null) } }

    // ===== 删除任务块 =====

    fun deleteBlock(block: EnergyBlock) {
        viewModelScope.launch {
            if (!block.isHardBlock && block.taskId.isNotBlank()) {
                taskRepository.updateTaskStatus(block.taskId, "ABANDONED")
            }
            // 尝试删除对应的硬墙块
            val dayStart = block.startTime - 86400_000
            val dayEnd = block.startTime + 86400_000
            val existing = hardBlockRepository.getBlocksForDaySync(dayStart, dayEnd)
            existing.filter { it.id == block.taskId || it.subjectName == block.title }
                .forEach { hardBlockRepository.deleteBlock(it) }
        }
    }

    // ===== 时间编辑（起止双时间） =====

    fun startEditTime(block: EnergyBlock) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = block.startTime
        val endCal = Calendar.getInstance()
        endCal.timeInMillis = block.endTime
        _uiState.update {
            it.copy(
                editingBlock = block,
                pickerHour = cal.get(Calendar.HOUR_OF_DAY),
                pickerMinute = cal.get(Calendar.MINUTE),
                pickerEndHour = endCal.get(Calendar.HOUR_OF_DAY),
                pickerEndMinute = endCal.get(Calendar.MINUTE)
            )
        }
    }

    fun cancelEdit() { _uiState.update { it.copy(editingBlock = null) } }

    fun rescheduleBlock(
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int
    ) {
        val block = _uiState.value.editingBlock ?: return
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, startHour)
            cal.set(Calendar.MINUTE, startMinute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val newStart = cal.timeInMillis
            val endCal = Calendar.getInstance()
            endCal.set(Calendar.HOUR_OF_DAY, endHour)
            endCal.set(Calendar.MINUTE, endMinute)
            endCal.set(Calendar.SECOND, 0)
            endCal.set(Calendar.MILLISECOND, 0)
            val newEnd = endCal.timeInMillis

            // 如果是任务块，将原任务标记为 SCHEDULED 防止重复调度
            if (!block.isHardBlock && block.taskId.isNotBlank()) {
                taskRepository.updateTaskStatus(block.taskId, "SCHEDULED")
            }

            // 删除旧的 hard block
            val dayStart = newStart - 86400_000
            val dayEnd = newStart + 86400_000
            val existing = hardBlockRepository.getBlocksForDaySync(dayStart, dayEnd)
            existing.filter { it.id == block.taskId || it.subjectName == block.title }
                .forEach { hardBlockRepository.deleteBlock(it) }

            // 创建新硬墙块
            hardBlockRepository.addBlock(
                subjectName = block.title,
                startTime = newStart,
                endTime = newEnd
            )

            _uiState.update { it.copy(editingBlock = null) }
        }
    }

    // ===== 支线 TodoList =====

    fun toggleSubLineTask(taskId: String) {
        viewModelScope.launch {
            val task = taskRepository.getTaskById(taskId) ?: return@launch
            val newStatus = if (task.status == "DONE") "POOL" else "DONE"
            taskRepository.updateTaskStatus(taskId, newStatus)
        }
    }

    fun deleteSubLineTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.getTaskById(taskId)?.let { taskRepository.deleteTask(it) }
        }
    }

    // ===== 工具方法 =====

    private fun getDayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = EgoFlowApp.instance
            return ScheduleTimelineViewModel(
                taskRepository = app.taskRepository,
                hardBlockRepository = app.hardBlockRepository,
                schedulingEngine = app.schedulingEngine
            ) as T
        }
    }
}

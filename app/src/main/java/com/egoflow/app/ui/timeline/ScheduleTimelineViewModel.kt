package com.egoflow.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
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
    val pickerMinute: Int = 0
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
        }
    }

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
            loadSchedule()
        }
    }

    fun clearDragSource() { _uiState.update { it.copy(dragSource = null) } }
    fun clearError() { _uiState.update { it.copy(swapErrorMessage = null) } }

    // ===== 时间编辑 =====
    fun startEditTime(block: EnergyBlock) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = block.startTime
        _uiState.update { it.copy(editingBlock = block, pickerHour = cal.get(Calendar.HOUR_OF_DAY), pickerMinute = cal.get(Calendar.MINUTE)) }
    }

    fun cancelEdit() { _uiState.update { it.copy(editingBlock = null) } }

    fun updatePickerTime(hour: Int, minute: Int) { _uiState.update { it.copy(pickerHour = hour, pickerMinute = minute) } }

    fun rescheduleBlock(hour: Int, minute: Int) {
        val block = _uiState.value.editingBlock ?: return
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val newStart = cal.timeInMillis
            val duration = block.endTime - block.startTime
            val newEnd = newStart + duration

            val dayStart = newStart - 86400_000
            val dayEnd = newStart + 86400_000
            val existing = hardBlockRepository.getBlocksForDaySync(dayStart, dayEnd)
            existing.filter { it.id == block.taskId }.forEach { hardBlockRepository.deleteBlock(it) }
            hardBlockRepository.addBlock(subjectName = block.title, startTime = newStart, endTime = newEnd)

            _uiState.update { it.copy(editingBlock = null) }
            loadSchedule()
        }
    }

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

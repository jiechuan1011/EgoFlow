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
    val isLoading: Boolean = true
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

    /**
     * 选择拖拽源块
     */
    fun selectDragSource(block: EnergyBlock) {
        _uiState.update { it.copy(dragSource = block, swapTarget = null, swapErrorMessage = null) }
    }

    /**
     * 选择交换目标并执行互换
     */
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
            // 执行互换 —— 在数据库层面交换两个任务的排程状态
            taskRepository.updateTaskStatus(source.taskId, "POOL")
            taskRepository.updateTaskStatus(block.taskId, "POOL")
            loadSchedule()
        }
    }

    fun clearDragSource() {
        _uiState.update { it.copy(dragSource = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(swapErrorMessage = null) }
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

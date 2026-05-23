package com.egoflow.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.data.repository.ScheduleTemplateRepository
import com.egoflow.app.data.repository.HardBlockRepository
import com.egoflow.app.domain.model.ScheduleTemplateItem
import com.egoflow.app.util.IcsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class ScheduleUiState(
    val items: List<ScheduleTemplateItem> = emptyList(),
    val showAddDialog: Boolean = false,
    val saved: Boolean = false,
    val importResult: String? = null
)

class ScheduleViewModel(
    private val templateRepository: ScheduleTemplateRepository,
    private val hardBlockRepository: HardBlockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            templateRepository.templateItems.collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun addItem(
        subjectName: String,
        dayOfWeek: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        viewModelScope.launch {
            templateRepository.addItem(
                subjectName = subjectName,
                dayOfWeek = dayOfWeek,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute
            )
            _uiState.update { it.copy(showAddDialog = false, saved = true) }
        }
    }

    fun removeItem(id: String) {
        viewModelScope.launch {
            templateRepository.removeItem(id)
        }
    }

    /** 将模板生成为本周的 HardBlock（只取当周有效课程） */
    fun generateThisWeek() {
        viewModelScope.launch {
            val items = templateRepository.getAllItems()
            val today = Calendar.getInstance()
            val dayOfWeek = today.get(Calendar.DAY_OF_WEEK)
            val mondayOffset = when (dayOfWeek) {
                Calendar.SUNDAY -> -6
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> -1
                Calendar.WEDNESDAY -> -2
                Calendar.THURSDAY -> -3
                Calendar.FRIDAY -> -4
                Calendar.SATURDAY -> -5
                else -> 0
            }
            val monday = today.clone() as Calendar
            monday.add(Calendar.DAY_OF_MONTH, mondayOffset)
            monday.set(Calendar.HOUR_OF_DAY, 0)
            monday.set(Calendar.MINUTE, 0)
            monday.set(Calendar.SECOND, 0)
            monday.set(Calendar.MILLISECOND, 0)

            // 本周7天
            for (d in 0..6) {
                val day = monday.clone() as Calendar
                day.add(Calendar.DAY_OF_MONTH, d)
                val dayStart = day.timeInMillis
                val dayEnd = dayStart + 86400_000

                // 先删除当天的旧 hard blocks
                val existing = hardBlockRepository.getBlocksForDaySync(dayStart, dayEnd)
                existing.forEach { hardBlockRepository.deleteBlock(it) }

                // 添加当天有效（按日期区间过滤）的课程
                val weekdayIndex = d + 1 // 1=周一
                items.filter { it.dayOfWeek == weekdayIndex && it.isActiveForDay(dayStart) }
                    .forEach { item ->
                        val startCal = day.clone() as Calendar
                        startCal.set(Calendar.HOUR_OF_DAY, item.startHour)
                        startCal.set(Calendar.MINUTE, item.startMinute)

                        val endCal = day.clone() as Calendar
                        endCal.set(Calendar.HOUR_OF_DAY, item.endHour)
                        endCal.set(Calendar.MINUTE, item.endMinute)

                        hardBlockRepository.addBlock(
                            subjectName = item.subjectName,
                            startTime = startCal.timeInMillis,
                            endTime = endCal.timeInMillis
                        )
                    }
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    /**
     * 导入 ICS 文件内容，解析为课程表条目
     */
    fun importIcs(icsContent: String) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.Default) {
                IcsParser.parseToTemplateItems(icsContent)
            }
            if (items.isEmpty()) {
                _uiState.update { it.copy(importResult = "未识别到课程事件，请检查 ICS 文件格式") }
                return@launch
            }
            // 合并到现有条目
            val current = templateRepository.getAllItems()
            val merged = current + items
            templateRepository.saveItems(merged)
            _uiState.update {
                it.copy(importResult = "成功导入 ${items.size} 个课程，去重后共 ${merged.size} 项")
            }
        }
    }

    fun setImportResult(msg: String) {
        _uiState.update { it.copy(importResult = msg) }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }

    fun clearAll() {
        viewModelScope.launch {
            templateRepository.saveItems(emptyList())
            _uiState.update { it.copy(saved = true, importResult = "已清空所有课程") }
        }
    }

    fun clearSavedFlag() {
        _uiState.update { it.copy(saved = false) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = EgoFlowApp.instance
            return ScheduleViewModel(
                templateRepository = app.scheduleTemplateRepository,
                hardBlockRepository = app.hardBlockRepository
            ) as T
        }
    }
}

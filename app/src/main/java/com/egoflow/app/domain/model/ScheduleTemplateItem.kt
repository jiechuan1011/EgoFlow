package com.egoflow.app.domain.model

/**
 * 每周重复课程表条目
 * dayOfWeek: 1=周一 … 7=周日
 */
data class ScheduleTemplateItem(
    val id: String,
    val subjectName: String,
    val dayOfWeek: Int,       // 1..7
    val startHour: Int,       // 0..23
    val startMinute: Int,     // 0..59
    val endHour: Int,
    val endMinute: Int
) {
    companion object {
        val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}

package com.egoflow.app.domain.model

/**
 * 每周重复课程表条目
 * dayOfWeek: 1=周一 … 7=周日
 * validFrom/validUntil: ICS 中的生效日期范围（毫秒时间戳，可为 null 表示长期有效）
 */
data class ScheduleTemplateItem(
    val id: String,
    val subjectName: String,
    val dayOfWeek: Int,       // 1..7
    val startHour: Int,       // 0..23
    val startMinute: Int,     // 0..59
    val endHour: Int,
    val endMinute: Int,
    val validFrom: Long? = null,   // 课程生效起始日（毫秒，当天 00:00）
    val validUntil: Long? = null   // 课程生效截止日（毫秒，当天 23:59）
) {
    /** 判断在当前周的某一天是否有效 */
    fun isActiveForDay(dayMillis: Long): Boolean {
        if (validFrom != null && dayMillis < validFrom) return false
        if (validUntil != null && dayMillis > validUntil) return false
        return true
    }

    companion object {
        val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}

package com.egoflow.app.domain.model

import android.util.Log

/**
 * 每周重复课程表条目
 * dayOfWeek: 1=周一 … 7=周日
 * validFrom/validUntil: ICS 中的生效日期范围（毫秒时间戳，可为 null 表示长期有效）
 * interval: RRULE 中的循环间隔（1=每周，2=隔周/单双周）
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
    val validUntil: Long? = null,  // 课程生效截止日（毫秒，当天 23:59）
    val interval: Int = 1         // RRULE 循环间隔（1=每周，2=单双周交替）
) {
    /**
     * 判断在目标日期当天是否有效（含单双周过滤）
     *
     * 核心逻辑：
     * 1. 目标日期必须在 validFrom 之后、validUntil 之前
     * 2. 如果 interval > 1（如单双周），计算目标日期距 validFrom 的周数差，
     *    只有周数差能被 interval 整除时才生效
     *
     * @param dayMillis 目标日期的当天 00:00 时间戳
     */
    fun isActiveForDay(dayMillis: Long): Boolean {
        // 1. 日期范围检查
        if (validFrom != null && dayMillis < validFrom) return false
        if (validUntil != null && dayMillis > validUntil) return false

        // 2. 单双周/周间隔检查
        if (interval > 1 && validFrom != null) {
            val weekMillis = 7 * 86400_000L
            val weeksSinceStart = ((dayMillis - validFrom) / weekMillis).toInt()
            val isActive = weeksSinceStart % interval == 0
            if (!isActive) {
                Log.d("ScheduleItem", "[$subjectName] weekOffset=$weeksSinceStart interval=$interval → SKIP (date=$dayMillis)")
                return false
            }
            Log.d("ScheduleItem", "[$subjectName] weekOffset=$weeksSinceStart interval=$interval → ACTIVE (date=$dayMillis)")
        }

        return true
    }

    companion object {
        val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        /**
         * 计算目标日期是当学期第几周（从 validFrom 所在周为第 1 周）
         */
        fun computeWeekNumber(targetMillis: Long, semesterStartMillis: Long?): Int {
            if (semesterStartMillis == null) return 1
            val diff = targetMillis - semesterStartMillis
            if (diff < 0) return 1
            return (diff / (7 * 86400_000L)).toInt() + 1
        }
    }
}

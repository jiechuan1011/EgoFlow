package com.egoflow.app.domain.model

/**
 * 日常周循环任务预设
 */
data class RoutineTask(
    val id: String,
    val name: String,
    val dayOfWeek: Int,      // 1=周一..7=周日, 0=每天
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int,
    val enabled: Boolean = true
) {
    companion object {
        val PRESETS = listOf(
            RoutineTask("rt_morning_wash", "洗漱", 0, 7, 30, 30),
            RoutineTask("rt_evening_wash", "洗漱", 0, 22, 0, 30),
            RoutineTask("rt_laundry_wed", "洗衣服", 3, 19, 0, 60),
            RoutineTask("rt_laundry_sat", "洗衣服", 6, 10, 0, 60),
            RoutineTask("rt_hang_clothes_mon", "搭衣服", 1, 20, 0, 15),
            RoutineTask("rt_hang_clothes_thu", "搭衣服", 4, 20, 0, 15),
            RoutineTask("rt_exercise", "运动", 0, 17, 30, 45),
            RoutineTask("rt_meditation", "冥想", 0, 23, 0, 15)
        )
    }
}

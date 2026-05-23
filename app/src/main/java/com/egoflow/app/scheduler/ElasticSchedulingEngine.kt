package com.egoflow.app.scheduler

import com.egoflow.app.data.entity.HardBlockEntity
import com.egoflow.app.data.entity.TaskEntity
import com.egoflow.app.domain.model.EnergyBlock
import com.egoflow.app.domain.model.SchedulePlan
import java.util.Calendar
import java.util.UUID

/**
 * 弹性日程调度引擎
 *
 * 核心原则：
 * 1. 硬墙时段（HARD_BLOCK）不可侵犯
 * 2. 主线任务（MAIN_LINE）优先占用黄金精力时段
 * 3. 技术支线（SUB_LINE）作为主线完成后的奖励机制
 * 4. 同 Drain Level 任务可互换，高低不可混
 */
class ElasticSchedulingEngine {

    data class SchedulingConfig(
        val dayStartHour: Int = 6,          // 每日开始时间 06:00
        val dayEndHour: Int = 23,           // 每日结束时间 23:00
        val goldenHourStart: Int = 8,       // 黄金精力时期开始 08:00
        val goldenHourEnd: Int = 12,        // 黄金精力时期结束 12:00
        val afternoonStart: Int = 14,       // 下午时段开始 14:00
        val afternoonEnd: Int = 17,         // 下午时段结束 17:00
        val rewardHourStart: Int = 20,      // 奖励时段开始 20:00
        val subLineUnlockMinutes: Int = 90, // 解锁支线时长 90分钟
        val mainLineThresholdMinutes: Int = 180, // 主线满3小时解锁支线
        val highDrainBufferMinutes: Int = 15,    // 高耗任务缓冲间隔
        val subLineLockUntilHours: Double = 4.5,  // 支线锁定4.5小时后解锁
        val maxDailyMinutes: Int = 480,     // 每日最多学习 8 小时
        val autoBreakMinutes: Int = 10,     // 任务间自动休息 10 分钟
        val longBreakAfterMinutes: Int = 120, // 每2小时一次长休息
        val longBreakMinutes: Int = 30      // 长休息30分钟
    )

    private var config = SchedulingConfig()

    fun updateConfig(overrides: Map<String, Any>) {
        overrides.forEach { (key, value) ->
            config = when (key) {
                "high_drain_buffer_minutes" -> config.copy(highDrainBufferMinutes = (value as Number).toInt())
                "sub_line_lock_until_hours" -> config.copy(subLineLockUntilHours = (value as Number).toDouble())
                "tech_task_alert_trigger" -> config // 布尔开关，由调用方处理
                else -> config
            }
        }
    }

    fun getConfig(): SchedulingConfig = config

    /**
     * 生成一天的计划
     *
     * @param tasks 待排程的任务（来自 POOL）
     * @param hardBlocks 刚性课表/硬墙
     * @param completedMainLineMinutes 当日已完成主线分钟数
     * @return SchedulePlan
     */
    fun generateDailyPlan(
        tasks: List<TaskEntity>,
        hardBlocks: List<HardBlockEntity>,
        completedMainLineMinutes: Int = 0
    ): SchedulePlan {
        val today = Calendar.getInstance()
        val dateStr = "%04d-%02d-%02d".format(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH)
        )
        val dayStart = getTimeInMillis(today, config.dayStartHour, 0)
        val dayEnd = getTimeInMillis(today, config.dayEndHour, 0)

        val blocks = mutableListOf<EnergyBlock>()

        // Step 1: 添加硬墙时段
        hardBlocks.forEach { hb ->
            blocks.add(
                EnergyBlock(
                    id = hb.id,
                    title = hb.subjectName,
                    taskId = hb.id,
                    category = "MAIN_LINE",
                    drainLevel = "HIGH",
                    startTime = hb.startTime,
                    endTime = hb.endTime,
                    isHardBlock = true
                )
            )
        }

        // Step 2: 分离主线与支线任务
        val mainLineTasks = tasks
            .filter { it.category == "MAIN_LINE" && it.status == "POOL" }
            .sortedByDescending { it.drainLevel == "HIGH" } // HIGH drain 优先
        val subLineTasks = tasks
            .filter { it.category == "SUB_LINE" && it.status == "POOL" }

        // Step 3: 确定今日可用总时段（从当前时间开始）
        val allHardBlocks = blocks.filter { it.isHardBlock }
        val freeSlots = computeFreeSlots(dayStart, dayEnd, allHardBlocks)
        val now = System.currentTimeMillis()
        // 裁剪已过去的时段
        for (i in freeSlots.indices) {
            val slot = freeSlots[i]
            if (slot.end <= now) {
                freeSlots[i] = slot.copy(start = slot.end) // 标记为不可用
            } else if (slot.start < now) {
                freeSlots[i] = slot.copy(start = now)
            }
        }
        freeSlots.removeAll { it.start >= it.end }

        // Step 4: 分配主线任务到黄金时段和可用时段，带自动休息
        var mainLineScheduled = 0
        var totalDailyMinutes = completedMainLineMinutes
        val scheduledMainLineTasks = mutableListOf<TaskEntity>()

        for (task in mainLineTasks) {
            if (freeSlots.isEmpty()) break
            // 检查是否超出每日上限
            if (totalDailyMinutes + task.estimatedMinutes > config.maxDailyMinutes) break
            val slot = findBestSlot(task, freeSlots, dayStart)
            if (slot != null) {
                val blockEnd = minOf(slot.end, slot.start + task.estimatedMinutes * 60_000L)
                if (blockEnd - slot.start >= 30 * 60_000) { // 至少30分钟
                    // 与前一个任务之间插入休息
                    if (blocks.isNotEmpty() && blocks.last().isHardBlock.not()) {
                        val prevEnd = blocks.last().endTime
                        val gap = slot.start - prevEnd
                        if (gap > 5 * 60_000) {
                            val breakLen = if (totalDailyMinutes % config.longBreakAfterMinutes < task.estimatedMinutes
                                && totalDailyMinutes >= config.longBreakAfterMinutes)
                                config.longBreakMinutes else config.autoBreakMinutes
                            val breakEnd = minOf(slot.start, prevEnd + breakLen * 60_000L)
                            if (breakEnd > prevEnd + 60_000) {
                                blocks.add(EnergyBlock(
                                    id = UUID.randomUUID().toString(),
                                    title = "☕ 休息",
                                    taskId = "",
                                    category = "BREAK",
                                    drainLevel = "LOW",
                                    startTime = prevEnd,
                                    endTime = breakEnd
                                ))
                            }
                        }
                    }

                    blocks.add(
                        EnergyBlock(
                            id = UUID.randomUUID().toString(),
                            title = task.title,
                            taskId = task.id,
                            category = task.category,
                            drainLevel = task.drainLevel,
                            startTime = slot.start,
                            endTime = blockEnd
                        )
                    )
                    mainLineScheduled += task.estimatedMinutes
                    totalDailyMinutes += task.estimatedMinutes
                    scheduledMainLineTasks.add(task)
                    adjustFreeSlot(freeSlots, slot.start, blockEnd, config.highDrainBufferMinutes)
                }
            }
        }

        // Step 5: 主线达标后，解锁支线奖励时段
        val totalCompletedMainLine = completedMainLineMinutes + mainLineScheduled
        val rewardUnlocked = totalCompletedMainLine >= config.mainLineThresholdMinutes

        if (rewardUnlocked) {
            val rewardStart = getTimeInMillis(today, config.rewardHourStart, 0)
            // 主线与奖励之间插入长休息
            if (blocks.isNotEmpty() && blocks.last().isHardBlock.not() && blocks.last().endTime < rewardStart - 5 * 60_000) {
                blocks.add(EnergyBlock(
                    id = UUID.randomUUID().toString(),
                    title = "☕ 长休息",
                    taskId = "",
                    category = "BREAK",
                    drainLevel = "LOW",
                    startTime = blocks.last().endTime,
                    endTime = minOf(rewardStart, blocks.last().endTime + config.longBreakMinutes * 60_000L)
                ))
            }
            val rewardEnd = rewardStart + config.subLineUnlockMinutes * 60_000L

            // 检查奖励时段是否与硬墙冲突
            val rewardSlotBlocked = allHardBlocks.any { block ->
                rewardStart < block.endTime && rewardEnd > block.startTime
            }

            if (!rewardSlotBlocked) {
                // 分配支线任务到奖励时段（同样受每日上限约束）
                var rewardSlotStart = rewardStart
                for (task in subLineTasks) {
                    if (totalDailyMinutes + task.estimatedMinutes > config.maxDailyMinutes) break
                    val taskEnd = minOf(rewardEnd, rewardSlotStart + task.estimatedMinutes * 60_000L)
                    if (taskEnd > rewardSlotStart) {
                        totalDailyMinutes += task.estimatedMinutes
                        blocks.add(
                            EnergyBlock(
                                id = UUID.randomUUID().toString(),
                                title = task.title,
                                taskId = task.id,
                                category = task.category,
                                drainLevel = task.drainLevel,
                                startTime = rewardSlotStart,
                                endTime = taskEnd
                            )
                        )
                        rewardSlotStart = taskEnd + config.highDrainBufferMinutes * 60_000L
                        if (rewardSlotStart >= rewardEnd) break
                    }
                }
            }
        }

        // 按时间排序
        blocks.sortBy { it.startTime }

        return SchedulePlan(
            dateStr = dateStr,
            energyBlocks = blocks,
            totalMainLineMinutes = mainLineScheduled,
            totalSubLineMinutes = if (rewardUnlocked) subLineTasks.sumOf { it.estimatedMinutes } else 0,
            unlockedRewardMinutes = if (rewardUnlocked) config.subLineUnlockMinutes else 0
        )
    }

    /**
     * 检测任务是否与硬墙冲突
     */
    fun detectCollision(
        taskStart: Long,
        taskEnd: Long,
        hardBlocks: List<HardBlockEntity>
    ): CollisionResult {
        for (block in hardBlocks) {
            if (taskStart < block.endTime && taskEnd > block.startTime) {
                return CollisionResult(
                    hasCollision = true,
                    collidingBlock = block,
                    suggestedStart = block.endTime
                )
            }
        }
        return CollisionResult(hasCollision = false)
    }

    data class CollisionResult(
        val hasCollision: Boolean,
        val collidingBlock: HardBlockEntity? = null,
        val suggestedStart: Long? = null
    )

    /**
     * 同 Drain Level 互换（拖拽对调）
     */
    fun canSwapBlocks(blockA: EnergyBlock, blockB: EnergyBlock): Boolean {
        if (blockA.isHardBlock || blockB.isHardBlock) return false
        return blockA.drainLevel == blockB.drainLevel
    }

    // ===== 私有辅助方法 =====

    private data class TimeSlot(val start: Long, val end: Long)

    private fun computeFreeSlots(dayStart: Long, dayEnd: Long, hardBlocks: List<EnergyBlock>): MutableList<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        var currentStart = dayStart
        val sorted = hardBlocks.sortedBy { it.startTime }

        for (block in sorted) {
            if (currentStart < block.startTime) {
                slots.add(TimeSlot(currentStart, block.startTime))
            }
            currentStart = maxOf(currentStart, block.endTime)
        }
        if (currentStart < dayEnd) {
            slots.add(TimeSlot(currentStart, dayEnd))
        }
        return slots
    }

    private fun findBestSlot(task: TaskEntity, freeSlots: MutableList<TimeSlot>, dayStart: Long): TimeSlot? {
        val taskDuration = task.estimatedMinutes * 60_000L
        val goldenStart = dayStart + (config.goldenHourStart - config.dayStartHour) * 3600_000L
        val goldenEnd = dayStart + (config.goldenHourEnd - config.dayStartHour) * 3600_000L

        // HIGH drain 任务优先排入黄金时段
        if (task.drainLevel == "HIGH") {
            val goldenSlot = freeSlots.firstOrNull { slot ->
                slot.start in goldenStart until goldenEnd && (slot.end - slot.start) >= taskDuration
            }
            if (goldenSlot != null) return goldenSlot
        }

        // 找第一个足够大的时段
        return freeSlots.firstOrNull { slot ->
            (slot.end - slot.start) >= taskDuration
        }
    }

    private fun adjustFreeSlot(
        freeSlots: MutableList<TimeSlot>,
        blockStart: Long,
        blockEnd: Long,
        bufferMinutes: Int
    ) {
        val bufferedEnd = blockEnd + bufferMinutes * 60_000L
        val iterator = freeSlots.iterator()
        while (iterator.hasNext()) {
            val slot = iterator.next()
            if (slot.start in blockStart until bufferedEnd || slot.end in blockStart until bufferedEnd) {
                iterator.remove()
            } else if (slot.start >= bufferedEnd) {
                // 后续时段不受影响
                break
            }
        }
        // 重新计算剩余空闲
    }

    private fun getTimeInMillis(calendar: Calendar, hour: Int, minute: Int): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

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
        val highDrainBufferMinutes: Int = 15,    // 高耗任务缓冲间隔
        val maxDailyMinutes: Int = 480,     // 每日最多学习 8 小时
        val autoBreakMinutes: Int = 10,     // 任务间自动休息 10 分钟
        val longBreakAfterMinutes: Int = 120, // 每2小时一次长休息
        val longBreakMinutes: Int = 30,      // 长休息30分钟
        val subLineRatio: Float = 0.3f      // 支线占总可用时间的比例（主线优先，剩余给支线）
    )

    private var config = SchedulingConfig()

    fun updateConfig(overrides: Map<String, Any>) {
        overrides.forEach { (key, value) ->
            config = when (key) {
                "high_drain_buffer_minutes" -> config.copy(highDrainBufferMinutes = (value as Number).toInt())
                "sub_line_ratio" -> config.copy(subLineRatio = (value as Number).toFloat())
                else -> config
            }
        }
    }

    fun getConfig(): SchedulingConfig = config

    /**
     * 生成一天的计划
     *
     * 主线优先排入黄金时段，支线排入后续空闲时段
     * 主线占用约 70% 可用时间，支线约 30%
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

        // Step 1: 添加硬墙时段（保留原始 category/drainLevel）
        hardBlocks.forEach { hb ->
            blocks.add(
                EnergyBlock(
                    id = hb.id,
                    title = hb.subjectName,
                    taskId = hb.id,
                    category = hb.category,
                    drainLevel = hb.drainLevel,
                    startTime = hb.startTime,
                    endTime = hb.endTime,
                    isHardBlock = true
                )
            )
        }

        // Step 2: 分离主线与支线任务
        val mainLineTasks = tasks
            .filter { it.category == "MAIN_LINE" && it.status == "POOL" }
            .sortedByDescending { it.drainLevel == "HIGH" }
        val subLinePoolTasks = tasks
            .filter { it.category == "SUB_LINE" && it.status == "POOL" }

        // Step 3: 确定可用时段
        val allHardBlocks = blocks.filter { it.isHardBlock }
        val freeSlots = computeFreeSlots(dayStart, dayEnd, allHardBlocks)
        var totalDailyMinutes = completedMainLineMinutes
        var mainLineScheduled = 0
        var subLineScheduled = 0

        // Step 4: 主线任务优先分配（黄金时段优先）
        for (task in mainLineTasks) {
            if (freeSlots.isEmpty()) break
            if (totalDailyMinutes + task.estimatedMinutes > config.maxDailyMinutes) break
            val slot = findBestSlot(task, freeSlots)
            if (slot != null) {
                val blockEnd = minOf(slot.end, slot.start + task.estimatedMinutes * 60_000L)
                if (blockEnd - slot.start >= 30 * 60_000) {
                    // 插入休息
                    insertBreakBefore(blocks, slot.start, totalDailyMinutes, config)

                    blocks.add(EnergyBlock(
                        id = UUID.randomUUID().toString(),
                        title = task.title,
                        taskId = task.id,
                        category = task.category,
                        drainLevel = task.drainLevel,
                        startTime = slot.start,
                        endTime = blockEnd
                    ))
                    mainLineScheduled += task.estimatedMinutes
                    totalDailyMinutes += task.estimatedMinutes
                    adjustFreeSlot(freeSlots, slot.start, blockEnd, config.highDrainBufferMinutes)
                }
            }
        }

        // Step 5: 按时间排序
        blocks.sortBy { it.startTime }

        return SchedulePlan(
            dateStr = dateStr,
            energyBlocks = blocks,
            totalMainLineMinutes = mainLineScheduled,
            totalSubLineMinutes = 0,
            unlockedRewardMinutes = 0,
            subLineTasks = subLinePoolTasks
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

    private fun findBestSlot(task: TaskEntity, freeSlots: MutableList<TimeSlot>): TimeSlot? {
        val taskDuration = task.estimatedMinutes * 60_000L
        val now = System.currentTimeMillis()
        // 优先从当前时间之后的时段开始排
        val validSlots = freeSlots.filter { it.end > now }
        if (validSlots.isEmpty()) return null

        // 主线 HIGH drain 任务优先排入黄金时段
        val goldenStart = getTimeInMillis(Calendar.getInstance(), config.goldenHourStart, 0)
        val goldenEnd = getTimeInMillis(Calendar.getInstance(), config.goldenHourEnd, 0)

        // HIGH drain 任务优先排入黄金时段
        if (task.drainLevel == "HIGH" && task.category == "MAIN_LINE") {
            val goldenSlot = validSlots.firstOrNull { slot ->
                slot.start in goldenStart until goldenEnd && (slot.end - slot.start) >= taskDuration
            }
            if (goldenSlot != null) return goldenSlot
        }

        // 找第一个足够大的时段
        return validSlots.firstOrNull { slot ->
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

    /** 在任务前插入休息块（如果与前一个块有间隔） */
    private fun insertBreakBefore(
        blocks: MutableList<EnergyBlock>,
        taskStart: Long,
        totalDailyMinutes: Int,
        config: SchedulingConfig
    ) {
        if (blocks.isEmpty() || blocks.last().isHardBlock) return
        val prevEnd = blocks.last().endTime
        val gap = taskStart - prevEnd
        if (gap > 5 * 60_000) {
            val breakLen = if (totalDailyMinutes > 0 && totalDailyMinutes % config.longBreakAfterMinutes < 30)
                config.longBreakMinutes else config.autoBreakMinutes
            val breakEnd = minOf(taskStart, prevEnd + breakLen * 60_000L)
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

    private fun getTimeInMillis(calendar: Calendar, hour: Int, minute: Int): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

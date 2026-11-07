package com.egoflow.app.util

import android.util.Log

/**
 * 日程文本解析器
 *
 * 将AI教练回答中的日程文本（如 "08:00-11:30 复习微机原理"）解析为结构化数据。
 * 自动根据内容关键词分类耗能等级：
 * - HIGH（高耗主线）：复习、焊板子、改PPT、考试、实验、课程
 * - LOW（低耗/休息）：休息、☕、冥想、拉伸、散步
 */
data class ParsedScheduleBlock(
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val category: String,   // MAIN_LINE, SUB_LINE, BREAK
    val drainLevel: String  // HIGH, LOW
)

object ScheduleParser {

    private val TAG = "ScheduleParser"

    // 匹配 "HH:mm-HH:mm title" 格式（允许前后空格、中间 &ndash; 或 -）
    private val LINE_REGEX = Regex(
        """^\s*(\d{1,2}):(\d{2})\s*[–\-—]\s*(\d{1,2}):(\d{2})\s+(.+?)\s*$"""
    )

    // 高耗主线关键词
    private val HIGH_DRAIN_KEYWORDS = setOf(
        "复习", "焊板子", "改PPT", "考试", "实验", "课程", "学习",
        "微机原理", "模电", "数电", "信号", "编程", "算法",
        "作业", "报告", "论文", "项目"
    )

    // 休息/低耗关键词
    private val LOW_DRAIN_KEYWORDS = setOf(
        "休息", "☕", "冥想", "拉伸", "散步", "午休", "吃饭",
        "午餐", "早餐", "晚餐", "喝水", "小憩", "放松"
    )

    /**
     * 解析日程文本，返回结构化块列表
     */
    fun parse(text: String): List<ParsedScheduleBlock> {
        val lines = text.split("\n")
        val results = mutableListOf<ParsedScheduleBlock>()
        var inScheduleSection = false

        for (line in lines) {
            val trimmed = line.trim()

            // 检测日程部分的开始（中文冒号/英文冒号后的内容）
            if (trimmed.contains("已生成今日日程") || trimmed.contains("今日日程")) {
                inScheduleSection = true
                continue
            }

            // 检测日程部分的结束（空行或非时间行）
            if (inScheduleSection && trimmed.isEmpty()) {
                continue // 跳过空行
            }

            val match = LINE_REGEX.find(trimmed)
            if (match != null) {
                val startHour = match.groupValues[1].toInt()
                val startMinute = match.groupValues[2].toInt()
                val endHour = match.groupValues[3].toInt()
                val endMinute = match.groupValues[4].toInt()
                val title = match.groupValues[5].trim()

                val classification = classify(title)

                results.add(
                    ParsedScheduleBlock(
                        title = title,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        category = classification.first,
                        drainLevel = classification.second
                    )
                )
                Log.d(TAG, "Parsed: $startHour:$startMinute-$endHour:$endMinute [$title] → ${classification.first}/${classification.second}")
            } else if (inScheduleSection && trimmed.isNotBlank()) {
                // 日程段内的非匹配行，作为可能的正文跳过
                Log.d(TAG, "Skip non-block line: $trimmed")
            }
        }

        Log.d(TAG, "Total parsed blocks: ${results.size}")
        return results
    }

    /**
     * 从聊天消息列表中找到教练最近一次包含日程的完整消息文本
     */
    fun findLastScheduleMessage(messages: List<*>): String? {
        // 支持 CoachMessage 类型（field = content）或 String
        for (i in (messages.size - 1) downTo 0) {
            val msg = messages[i]
            val content = when (msg) {
                is com.egoflow.app.domain.model.CoachMessage -> msg.content
                is String -> msg
                else -> continue
            }
            if (content.contains("已生成今日日程") || content.contains("今日日程")) {
                return content
            }
        }
        return null
    }

    /**
     * 根据标题关键字自动分类  category / drainLevel
     */
    private fun classify(title: String): Pair<String, String> {
        val cleanTitle = title.removePrefix("☕").trim()

        return when {
            // 休息类 → BREAK
            LOW_DRAIN_KEYWORDS.any { cleanTitle.contains(it) } ||
            title.contains("☕") -> "BREAK" to "LOW"

            // 高耗主线
            HIGH_DRAIN_KEYWORDS.any { cleanTitle.contains(it) } -> "MAIN_LINE" to "HIGH"

            // 默认：支线低耗
            else -> "SUB_LINE" to "LOW"
        }
    }
}

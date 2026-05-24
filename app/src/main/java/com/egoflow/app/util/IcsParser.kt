package com.egoflow.app.util

import com.egoflow.app.domain.model.ScheduleTemplateItem
import java.io.BufferedReader
import java.io.StringReader
import java.util.Calendar
import java.util.UUID

/**
 * 简易 ICS (iCalendar) 解析器
 *
 * 提取 VEVENT 中的课程信息，转换为 ScheduleTemplateItem（周重复课程）
 */
object IcsParser {

    private val ICS_DAY_MAP = mapOf(
        "MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4,
        "FR" to 5, "SA" to 6, "SU" to 7
    )

    data class IcsEvent(
        val summary: String,
        val dtStart: Calendar,
        val dtEnd: Calendar,
        val rrule: RRule? = null
    )

    data class RRule(
        val freq: String,       // WEEKLY, DAILY, etc.
        val byDay: List<Int>,   // 1=Mon ... 7=Sun
        val interval: Int = 1,
        val until: Long? = null // UNTIL 时间戳（毫秒）
    )

    /**
     * 解析 ICS 文本，返回可用于课程表的周重复条目
     */
    fun parseToTemplateItems(icsContent: String): List<ScheduleTemplateItem> {
        val events = parseEvents(icsContent)
        val items = mutableListOf<ScheduleTemplateItem>()

        for (event in events) {
            val rrule = event.rrule
            val days = mutableListOf<Int>()

            // 计算有效日期范围
            val validFrom = normalizeToDayStart(event.dtStart.timeInMillis)
            val validUntil = rrule?.until   // RRULE 的 UNTIL

            if (rrule != null && rrule.freq == "WEEKLY") {
                if (rrule.byDay.isNotEmpty()) {
                    days.addAll(rrule.byDay)
                } else {
                    dtStartToOurDay(event.dtStart)?.let { days.add(it) }
                }
                days.sort()
            } else {
                dtStartToOurDay(event.dtStart)?.let { days.add(it) }
            }

            val interval = rrule?.interval ?: 1

            for (day in days) {
                items.add(
                    ScheduleTemplateItem(
                        id = UUID.randomUUID().toString(),
                        subjectName = event.summary,
                        dayOfWeek = day,
                        startHour = event.dtStart.get(Calendar.HOUR_OF_DAY),
                        startMinute = event.dtStart.get(Calendar.MINUTE),
                        endHour = event.dtEnd.get(Calendar.HOUR_OF_DAY),
                        endMinute = event.dtEnd.get(Calendar.MINUTE),
                        validFrom = validFrom,
                        validUntil = validUntil,
                        interval = interval
                    )
                )
            }
        }

        // 合并去重：相同(课程名+星期+开始时间) → 合并日期范围，保留最宽区间
        return items.groupBy { Triple(it.subjectName, it.dayOfWeek, it.startHour * 100 + it.startMinute) }
            .map { (_, dups) ->
                dups.reduce { a, b ->
                    a.copy(
                        validFrom = minOf(a.validFrom ?: Long.MAX_VALUE, b.validFrom ?: Long.MAX_VALUE)
                            .let { if (it == Long.MAX_VALUE) null else it },
                        validUntil = maxOf(a.validUntil ?: 0L, b.validUntil ?: 0L)
                            .let { if (it == 0L) null else it }
                    )
                }
            }
    }

    /** 将时间戳归零到当天 00:00 */
    private fun normalizeToDayStart(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 将 Calendar.DAY_OF_WEEK 转为我们的 1=周一 … 7=周日 */
    private fun dtStartToOurDay(cal: Calendar): Int? {
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> null
        }
    }

    /**
     * 解析 ICS 文本中的所有 VEVENT
     */
    private fun parseEvents(icsContent: String): List<IcsEvent> {
        val unfolded = unfoldLines(icsContent)
        val events = mutableListOf<IcsEvent>()

        // 按 BEGIN:VEVENT ... END:VEVENT 分割（兼容 CRLF / LF）
        val normalized = unfolded.replace("\r\n", "\n")
        val veventRegex = Regex(
            "BEGIN:VEVENT\\s*\\n(.*?)\\nEND:VEVENT",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val matches = veventRegex.findAll(normalized)

        for (match in matches) {
            val block = match.groupValues[1]
            val props = parseProperties(block)
            if (props.containsKey("SUMMARY") && props.containsKey("DTSTART")) {
                val event = buildEvent(props)
                if (event != null) {
                    events.add(event)
                }
            }
        }

        return events
    }

    /**
     * ICS 行折叠：以空格或 tab 开头的行是上一行的延续
     */
    private fun unfoldLines(text: String): String {
        return text.replace("\r\n", "\n")
            .lines()
            .joinToString("") { line ->
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    line.trimStart()
                } else {
                    "\n$line"
                }
            }
            .trimStart('\n')
    }

    /**
     * 解析属性块为 key→value 映射（保留参数）
     */
    private fun parseProperties(block: String): Map<String, String> {
        val props = mutableMapOf<String, String>()
        for (line in block.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("BEGIN:") || trimmed.startsWith("END:")) continue

            val colonIdx = trimmed.indexOf(':')
            if (colonIdx == -1) continue

            val key = trimmed.substring(0, colonIdx)
            val value = trimmed.substring(colonIdx + 1)

            // 只存简单 key（去掉参数部分）
            val simpleKey = key.split(";")[0].uppercase()
            if (!props.containsKey(simpleKey)) {
                props[simpleKey] = value
            }
        }
        return props
    }

    private fun buildEvent(props: Map<String, String>): IcsEvent? {
        val summary = props["SUMMARY"] ?: "未命名课程"
        val dtStartStr = props["DTSTART"] ?: return null
        val dtEndStr = props["DTEND"] ?: return null

        val dtStart = parseDateTime(dtStartStr) ?: return null
        val dtEnd = parseDateTime(dtEndStr) ?: return null

        val rrule = parseRRule(props["RRULE"])

        return IcsEvent(
            summary = summary,
            dtStart = dtStart,
            dtEnd = dtEnd,
            rrule = rrule
        )
    }

    /**
     * 解析 ICS 日期时间字符串
     * 支持格式：
     *   - 20240101T080000    (local)
     *   - 20240101T080000Z   (UTC)
     *   - TZID=...:20240101T080000
     */
    private fun parseDateTime(raw: String): Calendar? {
        // 去掉 TZID 前缀
        val dateStr = if (raw.contains(":")) raw.substringAfterLast(":") else raw
        val clean = dateStr.trimEnd('Z')

        if (clean.length < 15) return null

        try {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, clean.substring(0, 4).toInt())
            cal.set(Calendar.MONTH, clean.substring(4, 6).toInt() - 1)
            cal.set(Calendar.DAY_OF_MONTH, clean.substring(6, 8).toInt())
            cal.set(Calendar.HOUR_OF_DAY, clean.substring(9, 11).toInt())
            cal.set(Calendar.MINUTE, clean.substring(11, 13).toInt())
            cal.set(Calendar.SECOND, if (clean.length >= 15) clean.substring(13, 15).toInt() else 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * 解析 RRULE 行
     * 示例：FREQ=WEEKLY;BYDAY=MO,WE,FR;INTERVAL=1
     */
    private fun parseRRule(rruleStr: String?): RRule? {
        if (rruleStr.isNullOrBlank()) return null

        val params = mutableMapOf<String, String>()
        rruleStr.split(";").forEach { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx != -1) {
                params[part.substring(0, eqIdx).trim()] = part.substring(eqIdx + 1).trim()
            }
        }

        val freq = params["FREQ"] ?: return null

        val byDay = mutableListOf<Int>()
        params["BYDAY"]?.let { daysStr ->
            daysStr.split(",").forEach { day ->
                val code = day.trim().takeLast(2).uppercase()
                ICS_DAY_MAP[code]?.let { byDay.add(it) }
            }
        }

        val interval = params["INTERVAL"]?.toIntOrNull() ?: 1

        // 解析 UNTIL（格式同 DTSTART: 20260607T160000Z）
        val until = params["UNTIL"]?.let { parseDateTimeEndOfDay(it) }

        return RRule(
            freq = freq,
            byDay = byDay,       // 保持原样：无 BYDAY 时为空列表，由上层 fallback 到 DTSTART
            interval = interval,
            until = until
        )
    }

    /** 解析 UNTIL 日期为当天 23:59 的时间戳 */
    private fun parseDateTimeEndOfDay(raw: String): Long? {
        val clean = raw.trimEnd('Z')
        if (clean.length < 8) return null
        return try {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, clean.substring(0, 4).toInt())
            cal.set(Calendar.MONTH, clean.substring(4, 6).toInt() - 1)
            cal.set(Calendar.DAY_OF_MONTH, clean.substring(6, 8).toInt())
            cal.set(Calendar.HOUR_OF_DAY, if (clean.length >= 15) clean.substring(9, 11).toInt() else 23)
            cal.set(Calendar.MINUTE, if (clean.length >= 15) clean.substring(11, 13).toInt() else 59)
            cal.set(Calendar.SECOND, if (clean.length >= 15) clean.substring(13, 15).toInt() else 59)
            cal.set(Calendar.MILLISECOND, 999)
            cal.timeInMillis
        } catch (_: Exception) { null }
    }
}

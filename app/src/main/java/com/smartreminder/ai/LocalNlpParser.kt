package com.smartreminder.ai

import com.smartreminder.domain.model.TriggerCondition
import com.smartreminder.domain.model.IntervalUnit
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 本地自然语言解析器
 * 参考 Python scheduler_parser.py 中的 LocalParser 实现
 *
 * 支持解析：
 * - 中文数字转换（一二三 → 123）
 * - 相对时间解析：今天、明天、后天、大后天、今晚、明早、下周、下个月
 * - 时间段解析：早上、下午、晚上、凌晨、中午、傍晚
 * - 星期几解析：周一、周二、星期日
 * - 时长解析：半小时后、5分钟后、2小时后
 * - 复杂重复模式：每隔2天、每3小时、每天、每周、每月
 */
class LocalNlpParser {

    companion object {
        private const val DEFAULT_HOUR = 8
        private const val DEFAULT_MINUTE = 0

        private val CN_NUM = mapOf(
            '〇' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
            '零' to 0, '壹' to 1, '贰' to 2, '叁' to 3, '肆' to 4, '伍' to 5, '陆' to 6, '柒' to 7, '捌' to 8, '玖' to 9,
            '貮' to 2, '两' to 2
        )

        private val CN_UNIT = mapOf(
            '十' to 10, '百' to 100, '千' to 1000, '万' to 10000, '亿' to 100000000
        )

        private val WEEKDAY_MAP = mapOf(
            "周日" to 7, "星期天" to 7, "星期日" to 7,
            "周一" to 1, "星期一" to 1,
            "周二" to 2, "星期二" to 2,
            "周三" to 3, "星期三" to 3,
            "周四" to 4, "星期四" to 4,
            "周五" to 5, "星期五" to 5,
            "周六" to 6, "星期六" to 6
        )
    }

    /**
     * 解析结果
     */
    data class ParseResult(
        val time: LocalDateTime,
        val repeat: Map<String, Int>,  // 如 {"days" to 1, "hours" to 2}
        val event: String,
        val desc: String,
        val weekday: Int? = null  // 解析出的周几（1-7，周一到周日）
    )

    class ParseError(message: String) : Exception(message)

    /**
     * 主解析入口
     */
    fun parseByRules(text: String): ParseResult? {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return null

        val now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

        try {
            // 解析时间
            val timeInfo = parseTimeInfo(cleanText, now)

            // 解析事件内容
            val event = parseEvent(cleanText)

            return ParseResult(
                time = timeInfo.time,
                repeat = timeInfo.repeat,
                event = event,
                desc = cleanText,
                weekday = timeInfo.weekday
            )
        } catch (e: Exception) {
            return null
        }
    }

    private data class TimeInfo(
        val time: LocalDateTime,
        val repeat: Map<String, Int>,
        val weekday: Int? = null  // 解析出的周几（1-7，周一到周日）
    )

    /**
     * 解析时间信息
     */
    private fun parseTimeInfo(text: String, now: LocalDateTime): TimeInfo {
        var currentTime = now
        val repeat = mutableMapOf<String, Int>()
        var hour = DEFAULT_HOUR
        var minute = DEFAULT_MINUTE
        var dayOffset = 0
        var monthOffset = 0
        var yearOffset = 0
        var dayOfMonth: Int? = null
        var month: Int? = null
        var isAfternoon = false
        var weekday: Int? = null

        // 1. 解析重复模式
        val repeatMatch = parseRepeatPattern(text)
        if (repeatMatch != null) {
            repeat.putAll(repeatMatch)
        }

        // 2. 解析相对时间（今天、明天、后天等）
        val relativeTime = parseRelativeTime(text, now)
        dayOffset = relativeTime.dayOffset
        isAfternoon = relativeTime.isAfternoon
        hour = relativeTime.hour ?: hour
        minute = relativeTime.minute ?: minute

        // 3. 解析周几
        val weekdayMatch = parseWeekday(text)
        if (weekdayMatch != null) {
            weekday = weekdayMatch
        }

        // 4. 解析具体时间（几点、几点几分）
        val exactTime = parseExactTime(text)
        if (exactTime != null) {
            hour = exactTime.first
            minute = exactTime.second
            isAfternoon = exactTime.third ?: isAfternoon
        }

        // 5. 解析时长后（半小时后、5分钟后等）
        val durationAfter = parseDurationAfter(text, now)
        if (durationAfter != null) {
            currentTime = durationAfter
        } else {
            // 计算最终时间
            currentTime = now
                .plusDays(dayOffset.toLong())
                .plusMonths(monthOffset.toLong())
                .plusYears(yearOffset.toLong())
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
        }

        // 处理星期几
        if (weekday != null && weekday > 0) {
            val currentDayOfWeek = currentTime.dayOfWeek.value
            var daysToAdd = weekday - currentDayOfWeek
            if (daysToAdd <= 0) daysToAdd += 7
            // 如果是重复提醒（每周/每月等）且目标日是今天或已过，应跳到下周
            if (repeat.isNotEmpty()) {
                daysToAdd += 7
            }
            currentTime = currentTime.plusDays(daysToAdd.toLong())
        }

        // 如果时间已过且是单次提醒，设置到明天
        if (repeat.isEmpty() && currentTime.isBefore(now)) {
            currentTime = currentTime.plusDays(1)
        }

        return TimeInfo(time = currentTime, repeat = repeat, weekday = weekday)
    }

    /**
     * 解析重复模式
     */
    private fun parseRepeatPattern(text: String): Map<String, Int>? {
        val result = mutableMapOf<String, Int>()

        // 每隔N天/小时/分钟
        val intervalRegex = Regex("每隔?(\\d+)(?:个)?(天|日|小时|钟头|分钟|分|周|星期|个月|月|年)")
        val intervalMatch = intervalRegex.find(text)
        if (intervalMatch != null) {
            val num = intervalMatch.groupValues[1].toIntOrNull() ?: 1
            val unit = intervalMatch.groupValues[2]
            when (unit) {
                "天", "日" -> result["days"] = num
                "小时", "钟头" -> result["hours"] = num
                "分钟", "分" -> result["minutes"] = num
                "周", "星期" -> result["weeks"] = num
                "个月", "月" -> result["months"] = num
                "年" -> result["years"] = num
            }
            return result
        }

        // 每天
        if (text.contains("每天") || text.contains("每日")) {
            result["days"] = 1
            return result
        }

        // 每周
        if (text.contains("每周") || text.contains("每星期")) {
            result["weeks"] = 1
            return result
        }

        // 每月
        if (text.contains("每月")) {
            result["months"] = 1
            return result
        }

        // 每年
        if (text.contains("每年")) {
            result["years"] = 1
            return result
        }

        return if (result.isEmpty()) null else result
    }

    /**
     * 解析相对时间
     */
    private data class RelativeTimeResult(
        val dayOffset: Int,
        val isAfternoon: Boolean,
        val hour: Int?,
        val minute: Int?
    )

    private fun parseRelativeTime(text: String, now: LocalDateTime): RelativeTimeResult {
        var dayOffset = 0
        var isAfternoon = false
        var hour: Int? = null
        var minute: Int? = null

        when {
            text.contains("今天") -> dayOffset = 0
            text.contains("明天") || text.contains("明日") -> dayOffset = 1
            text.contains("后天") -> dayOffset = 2
            text.contains("大后天") -> dayOffset = 3
        }

        when {
            text.contains("今晚") || text.contains("晚上") -> {
                isAfternoon = true
                hour = 20
                minute = 0
            }
            text.contains("明晚") -> {
                dayOffset = maxOf(1, dayOffset)
                isAfternoon = true
                hour = 20
                minute = 0
            }
            text.contains("明早") || text.contains("明儿") -> {
                dayOffset = maxOf(1, dayOffset)
                isAfternoon = false
                hour = DEFAULT_HOUR
                minute = DEFAULT_MINUTE
            }
        }

        // X天后
        val daysAfterRegex = Regex("(\\d+)天(后|以后)")
        val daysAfterMatch = daysAfterRegex.find(text)
        if (daysAfterMatch != null) {
            dayOffset = daysAfterMatch.groupValues[1].toIntOrNull() ?: 0
        }

        // X个星期后
        val weeksAfterRegex = Regex("(\\d+)个?(周|星期)(后|以后)")
        val weeksAfterMatch = weeksAfterRegex.find(text)
        if (weeksAfterMatch != null) {
            val weeks = weeksAfterMatch.groupValues[1].toIntOrNull() ?: 1
            dayOffset = weeks * 7
        }

        // X个月后
        val monthsAfterRegex = Regex("(\\d+)个?月(后|以后)")
        val monthsAfterMatch = monthsAfterRegex.find(text)
        if (monthsAfterMatch != null) {
            // 这个需要在后面处理月份
        }

        // 下周
        if (text.contains("下周") || text.contains("下星期") || text.contains("下礼拜")) {
            // 找到下周的周一
            val nextMonday = now.plusWeeks(1).with(java.time.DayOfWeek.MONDAY)
            dayOffset = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), nextMonday.toLocalDate()).toInt()
        }

        return RelativeTimeResult(dayOffset, isAfternoon, hour, minute)
    }

    /**
     * 解析星期几
     */
    private fun parseWeekday(text: String): Int? {
        for ((pattern, day) in WEEKDAY_MAP) {
            if (text.contains(pattern)) {
                return day
            }
        }
        return null
    }

    /**
     * 解析具体时间（几点几分）
     */
    private fun parseExactTime(text: String): Triple<Int, Int, Boolean?>? {
        var isAfternoon: Boolean? = null

        // 时间段前缀
        when {
            text.contains("凌晨") || text.contains("半夜") || text.contains("夜里") || text.contains("深夜") -> {
                isAfternoon = false
            }
            text.contains("早上") || text.contains("早晨") || text.contains("上午") || text.contains("今早") -> {
                isAfternoon = false
            }
            text.contains("中午") -> {
                isAfternoon = false
            }
            text.contains("下午") || text.contains("午后") -> {
                isAfternoon = true
            }
            text.contains("傍晚") -> {
                isAfternoon = true
            }
        }

        // 匹配时间格式：8点、8:00、8点30分、8点30等
        val timePatterns = listOf(
            Regex("(\\d{1,2}):(\\d{2})"),  // 8:00 或 08:00
            Regex("(\\d{1,2})点(\\d{1,2})分"),  // 8点30分
            Regex("(\\d{1,2})点(\\d{1,2})"),  // 8点30
            Regex("(\\d{1,2})点")  // 8点
        )

        for (pattern in timePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val groups = match.destructured
                var hour = groups.component1().toIntOrNull() ?: return null
                var minute = if (groups.component2().isNotEmpty()) {
                    groups.component2().toIntOrNull() ?: 0
                } else {
                    0
                }

                // 处理下午
                if (isAfternoon == true && hour < 12) {
                    hour += 12
                }

                // 处理晚上/凌晨
                if (text.contains("晚上") && hour < 12) {
                    hour += 12
                }
                if (text.contains("凌晨") && hour == 12) {
                    hour = 0
                }

                return Triple(hour, minute, isAfternoon)
            }
        }

        // 匹配"半点"、"一刻"等
        if (text.contains("半点") || text.contains("半小时")) {
            val hourRegex = Regex("(?:早上|上午|下午|晚上)?(\\d{1,2})点?半")
            val match = hourRegex.find(text)
            if (match != null) {
                var hour = match.groupValues[1].toIntOrNull() ?: 8
                if (isAfternoon == true && hour < 12) hour += 12
                return Triple(hour, 30, isAfternoon)
            }
        }

        // 匹配"一刻"
        if (text.contains("一刻")) {
            val hourRegex = Regex("(?:早上|上午|下午|晚上)?(\\d{1,2})点?一刻")
            val match = hourRegex.find(text)
            if (match != null) {
                var hour = match.groupValues[1].toIntOrNull() ?: 8
                if (isAfternoon == true && hour < 12) hour += 12
                return Triple(hour, 15, isAfternoon)
            }
        }

        return null
    }

    /**
     * 解析"X分钟后/小时后"这样的时长
     */
    private fun parseDurationAfter(text: String, now: LocalDateTime): LocalDateTime? {
        var hours = 0L
        var minutes = 0L

        // 半小时后
        if (text.contains("半小时后") || text.contains("半小时以后")) {
            minutes = 30
        }

        // X分钟后
        val minuteAfterRegex = Regex("(\\d+)分钟?(后|以后)")
        val minuteMatch = minuteAfterRegex.find(text)
        if (minuteMatch != null) {
            minutes = minuteMatch.groupValues[1].toLongOrNull() ?: 0
        }

        // X小时后
        val hourAfterRegex = Regex("(\\d+)小时?(后|以后)")
        val hourMatch = hourAfterRegex.find(text)
        if (hourMatch != null) {
            hours = hourMatch.groupValues[1].toLongOrNull() ?: 0
        }

        // 等会、一会儿
        if (text.contains("等会") || text.contains("一会") || text.contains("一会儿")) {
            minutes = 10
        }

        if (hours > 0 || minutes > 0) {
            return now.plusHours(hours).plusMinutes(minutes)
        }

        return null
    }

    /**
     * 解析事件内容（提醒做什么）
     */
    private fun parseEvent(text: String): String {
        // 提取"提醒我xxx"中的xxx
        val patterns = listOf(
            Regex("(?:提醒我|提醒|告诉我说|跟我说|叫我)(.+)"),
            Regex("(?:提醒)(.+?)(?:在|于|到时候)"),
            Regex("(.+?)(?:提醒|告诉我)")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val content = match.destructured.component1()
                    .trim()
                    .replace(Regex("[的过在于是]"), "")
                    .trim()
                if (content.isNotEmpty()) {
                    return content
                }
            }
        }

        // 如果没找到，返回原始文本
        return text
    }

    /**
     * 将解析结果转换为 TriggerCondition
     */
    fun toTriggerCondition(result: ParseResult): TriggerCondition {
        val time = result.time
        val repeat = result.repeat

        return when {
            "years" in repeat -> {
                TriggerCondition.Yearly(time.monthValue, time.dayOfMonth, time.hour, time.minute)
            }
            "months" in repeat -> {
                TriggerCondition.Monthly(time.dayOfMonth, time.hour, time.minute)
            }
            "weeks" in repeat -> {
                // 优先使用解析出的 weekday，否则使用当前时间的星期
                val dayOfWeek = result.weekday ?: time.dayOfWeek.value
                TriggerCondition.Weekly(dayOfWeek, time.hour, time.minute)
            }
            "days" in repeat -> {
                val days = repeat["days"] ?: 1
                if (days == 1) {
                    TriggerCondition.Daily(time.hour, time.minute)
                } else {
                    TriggerCondition.Interval(days.toLong(), IntervalUnit.DAYS)
                }
            }
            "hours" in repeat -> {
                TriggerCondition.Interval(repeat["hours"]?.toLong() ?: 1L, IntervalUnit.HOURS)
            }
            "minutes" in repeat -> {
                TriggerCondition.Interval(repeat["minutes"]?.toLong() ?: 1L, IntervalUnit.MINUTES)
            }
            else -> {
                TriggerCondition.Once(time.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli())
            }
        }
    }

    /**
     * 人类可读的时间描述
     */
    fun natureTime(dt: LocalDateTime): String {
        val now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        val delta = java.time.Duration.between(now, dt)
        val absDelta = if (delta.isNegative) delta.negated() else delta

        val days = absDelta.toDays()
        val hours = absDelta.toHours() % 24
        val minutes = absDelta.toMinutes() % 60

        val tense = if (delta.isNegative) "前" else "后"

        return when {
            days > 365 -> "${days / 365}年${days % 365 / 30}个月$tense"
            days > 30 -> "${days / 30}个月${days % 30}天$tense"
            days > 0 -> "${days}天${hours}小时$tense"
            hours > 0 -> "${hours}小时${minutes}分钟$tense"
            minutes > 0 -> "${minutes}分钟$tense"
            else -> "即将"
        }
    }
}

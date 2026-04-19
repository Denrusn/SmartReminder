package com.smartreminder.ai

import com.smartreminder.domain.model.TriggerCondition
import com.smartreminder.domain.model.IntervalUnit
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * 本地自然语言解析器
 * 参考 Python scheduler_parser.py 中的 LocalParser 实现
 *
 * 支持解析：
 * - 中文数字转换（一二三 → 123，三百八十二 → 382）
 * - 词法分析：按字符/词组分析
 * - 重复模式：每隔N年/月/天/周/小时
 * - 相对时间：今天、明天、后天、大后天、今晚、明早、下周、下个月、明年
 * - 时间段：凌晨、早上、下午、晚上、中午、傍晚
 * - 具体时间：8点、8:30、8点30分、半点、一刻
 * - 星期几：周一、周二、星期日、下周
 * - 时长：半小时后、5分钟后、2小时后
 */
class LocalNlpParser {

    companion object {
        private const val DEFAULT_HOUR = 8
        private const val DEFAULT_MINUTE = 0

        // 中文数字映射
        private val CN_NUM = mapOf(
            '〇' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
            '零' to 0, '壹' to 1, '贰' to 2, '叁' to 3, '肆' to 4, '伍' to 5, '陆' to 6, '柒' to 7, '捌' to 8, '玖' to 9,
            '貮' to 2, '两' to 2
        )

        // 中文单位映射
        private val CN_UNIT = mapOf(
            '十' to 10, '百' to 100, '千' to 1000, '万' to 10000, '亿' to 100000000
        )

        // 星期映射 (1=周一, 7=周日)
        private val WEEKDAY_MAP = mapOf(
            "周日" to 7, "星期天" to 7, "星期日" to 7,
            "周一" to 1, "星期一" to 1,
            "周二" to 2, "星期二" to 2,
            "周三" to 3, "星期三" to 3,
            "周四" to 4, "星期四" to 4,
            "周五" to 5, "星期五" to 5,
            "周六" to 6, "星期六" to 6
        )

        // 重复关键字
        private const val REPEAT_KEY_YEAR = "year"
        private const val REPEAT_KEY_MONTH = "month"
        private const val REPEAT_KEY_DAY = "day"
        private const val REPEAT_KEY_WEEK = "week"
        private const val REPEAT_KEY_HOUR = "hour"
        private const val REPEAT_KEY_MINUTE = "minute"
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

    // ==================== 内部状态 ====================
    private var idx = 0
    private var words = listOf<Pair<String, String>>()  // (word, flag)
    private var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
    private var timeFields = mutableMapOf<String, Int>()  // year, month, day, hour, minute, second
    private var timeDeltaFields = mutableMapOf<String, Int>()  // years, months, days, hours, minutes, seconds, weeks
    private var repeat = mutableMapOf<String, Int>()
    private var isAfternoon: Boolean? = null
    private var parseBeginning = 0
    private var doWhat = ""

    /**
     * 主解析入口
     */
    fun parseByRules(text: String): ParseResult? {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return null

        // 初始化状态
        idx = 0
        words = tokenize(cleanText)
        now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        timeFields.clear()
        timeDeltaFields.clear()
        repeat.clear()
        isAfternoon = null
        parseBeginning = 0
        doWhat = ""

        try {
            return parse()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 简单分词 - 将文本分割为(词, 词性)对
     * 策略：贪婪匹配最长词
     */
    private fun tokenize(text: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var i = 0
        val len = text.length

        // 所有多字符词（按长度降序排列去重）
        val multiCharWords = listOf(
            // 重复前缀
            "每隔", "每周", "每月", "每天", "每年",
            // 星期
            "下星期一", "下星期二", "下星期三", "下星期四", "下星期五", "下星期六", "下星期日",
            "下礼拜一", "下礼拜二", "下礼拜三", "下礼拜四", "下礼拜五", "下礼拜六", "下礼拜日",
            "下个星期一", "下个星期二", "下个星期三", "下个星期四", "下个星期五", "下个星期六", "下个星期日",
            "下个周", "下个月",
            "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六",
            "下星期", "下礼拜", "下周", "周日", "周一", "周二", "周三", "周四", "周五", "周六",
            // 时间词
            "今天", "明天", "后天", "大后天", "今日", "明日", "后日", "大后日",
            "今晚", "明晚", "今早", "明早", "明儿",
            "今年", "明年", "后年", "下个月", "下月",
            "凌晨", "早上", "早晨", "上午", "中午", "下午", "傍晚", "晚上", "半夜", "夜里", "深夜",
            // 时间单位
            "点钟", "点整", "半点", "一刻", "刻钟",
            "分钟", "秒钟",
            "年前", "年后", "月后", "个月后", "周后", "星期后",
            "小时后", "小时后", "小时", "钟头", "半",
            "个半", "每隔"
        ).sortedByDescending { it.length }.distinct()

        while (i < len) {
            // 跳过空白和标点
            if (text[i].isWhitespace() || text[i] in "的。，．。''\"\"’’''！？?") {
                i++
                continue
            }

            // 尝试匹配多字符词（贪婪：找最长的精确匹配）
            var bestWord: String? = null
            var bestLength = 0

            for (word in multiCharWords) {
                if (i + word.length <= len) {
                    val substr = text.substring(i, i + word.length)
                    if (substr == word && word.length > bestLength) {
                        // 进一步验证：检查是否有更长词以当前词为前缀且确实存在于位置i
                        var hasLongerAtPosition = false
                        for (longerWord in multiCharWords) {
                            if (longerWord.length > word.length &&
                                i + longerWord.length <= len &&
                                text.substring(i, i + longerWord.length) == longerWord) {
                                hasLongerAtPosition = true
                                break
                            }
                        }
                        if (!hasLongerAtPosition) {
                            bestWord = word
                            bestLength = word.length
                        }
                    }
                }
            }

            if (bestWord != null) {
                val tag = when {
                    bestWord in listOf("每隔", "每周", "每月", "每天", "每年") -> "r"
                    bestWord.startsWith("星期") || bestWord == "下周" || bestWord == "下个周" ||
                            bestWord.startsWith("周") || bestWord.startsWith("礼拜") ||
                            bestWord in listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六") -> "t"
                    bestWord in listOf("点钟", "点整", "半点", "一刻", "刻钟", "分钟", "秒钟",
                        "年前", "年后", "月后", "个月后", "周后", "星期后", "小时后", "小时后", "钟头") -> "u"
                    else -> "t"
                }
                result.add(bestWord to tag)
                i += bestLength
                continue
            }

            // 匹配单字符（数字或中文）
            val char = text[i]
            if (char.isDigit()) {
                result.add(char.toString() to "n")
            } else {
                result.add(char.toString() to "x")
            }
            i++
        }

        return result
    }

    /**
     * 核心解析循环
     */
    private fun parse(): ParseResult? {
        while (hasNext()) {
            parseBeginning = getIndex()

            // 消费各种时间成分
            consumeRepeat()
            consumeYearPeriod() || consumeMonthPeriod() || consumeDayPeriod()
            consumeWeekdayPeriod() || consumeHourPeriod() || consumeMinutePeriod() || consumeSecondPeriod()
            consumeYear() || consumeMonth() || consumeDay()
            consumeHour()

            if (getIndex() != parseBeginning) {
                // 找到时间
                consumeWord("准时")
                consumeWord("是")
                if (consumeWord("提醒")) {
                    consumeWord("我")
                }
                if (currentTag() == "v" && peekNextWord() == "我") {
                    advance(2)
                }
                consumeToEnd()

                // 计算最终时间
                try {
                    // 应用时间增量
                    var resultTime = now
                    if (timeDeltaFields.containsKey("years")) {
                        resultTime = resultTime.plusYears(timeDeltaFields["years"]!!.toLong())
                    }
                    if (timeDeltaFields.containsKey("months")) {
                        resultTime = resultTime.plusMonths(timeDeltaFields["months"]!!.toLong())
                    }
                    if (timeDeltaFields.containsKey("weeks")) {
                        resultTime = resultTime.plusWeeks(timeDeltaFields["weeks"]!!.toLong())
                    }
                    if (timeDeltaFields.containsKey("days")) {
                        resultTime = resultTime.plusDays(timeDeltaFields["days"]!!.toLong())
                    }
                    if (timeDeltaFields.containsKey("hours")) {
                        resultTime = resultTime.plusHours(timeDeltaFields["hours"]!!.toLong())
                    }
                    if (timeDeltaFields.containsKey("minutes")) {
                        resultTime = resultTime.plusMinutes(timeDeltaFields["minutes"]!!.toLong())
                    }
                    if (timeDeltaFields.containsKey("seconds")) {
                        resultTime = resultTime.plusSeconds(timeDeltaFields["seconds"]!!.toLong())
                    }

                    // 应用固定时间字段
                    if (timeFields.containsKey("year")) {
                        resultTime = resultTime.withYear(timeFields["year"]!!)
                    }
                    if (timeFields.containsKey("month")) {
                        resultTime = resultTime.withMonth(timeFields["month"]!!)
                    }
                    if (timeFields.containsKey("day")) {
                        resultTime = resultTime.withDayOfMonth(timeFields["day"]!!)
                    }
                    if (timeFields.containsKey("hour")) {
                        resultTime = resultTime.withHour(timeFields["hour"]!!)
                    }
                    if (timeFields.containsKey("minute")) {
                        resultTime = resultTime.withMinute(timeFields["minute"]!!)
                    }
                    if (timeFields.containsKey("second")) {
                        resultTime = resultTime.withSecond(timeFields["second"]!!)
                    } else {
                        resultTime = resultTime.withSecond(0)
                    }
                    resultTime = resultTime.withNano(0)

                    // 处理星期几
                    var weekday: Int? = null
                    if (timeDeltaFields.containsKey("weekday")) {
                        weekday = timeDeltaFields["weekday"]
                        val currentDayOfWeek = resultTime.dayOfWeek.value
                        var daysToAdd = weekday!! - currentDayOfWeek
                        if (daysToAdd <= 0) daysToAdd += 7
                        // 如果是重复提醒且目标日是今天或已过，应跳到下周
                        if (repeat.isNotEmpty()) {
                            daysToAdd += 7
                        }
                        resultTime = resultTime.plusDays(daysToAdd.toLong())
                    }

                    // 如果时间已过且是单次提醒，设置到明天
                    if (repeat.isEmpty() && resultTime.isBefore(now)) {
                        resultTime = resultTime.plusDays(1)
                    }

                    val delta = mutableMapOf<String, Int>()
                    for ((key, value) in repeat) {
                        val deltaKey = when (key) {
                            REPEAT_KEY_YEAR -> "years"
                            REPEAT_KEY_MONTH -> "months"
                            REPEAT_KEY_DAY -> "days"
                            REPEAT_KEY_WEEK -> "weeks"
                            REPEAT_KEY_HOUR -> "hours"
                            REPEAT_KEY_MINUTE -> "minutes"
                            else -> continue
                        }
                        delta[deltaKey] = value
                    }

                    return ParseResult(
                        time = resultTime,
                        repeat = delta,
                        event = doWhat,
                        desc = words.joinToString("") { it.first },
                        weekday = weekday
                    )
                } catch (e: Exception) {
                    throw ParseError("时间或日期超出范围")
                }
            } else {
                advance()
            }
        }
        return null
    }

    // ==================== consume_* 方法 ====================

    /**
     * 消费重复模式：每/每隔/每N天/每小时等
     */
    private fun consumeRepeat(): Int {
        val beginning = getIndex()

        if (!consumeWord("每", "每隔")) return 0
        consumeWord("间隔")

        val repeatCount = consumeDigit() ?: 1
        if (repeatCount > 100) {
            throw ParseError("时间跨度太大")
        }
        consumeWord("个")

        // 每隔N年 + 每月
        if (consumeWord("年") && consumeMonth()) {
            repeat[REPEAT_KEY_YEAR] = repeatCount
            return getIndex() - beginning
        }
        // 每隔N个月 + 每天
        if (consumeWord("月") && consumeDay()) {
            repeat[REPEAT_KEY_MONTH] = repeatCount
            return getIndex() - beginning
        }
        // 每隔N天
        if (consumeWord("天")) {
            repeat[REPEAT_KEY_DAY] = repeatCount
            if (!consumeHour()) {
                timeFields["hour"] = DEFAULT_HOUR
                timeFields["minute"] = DEFAULT_MINUTE
            }
            return getIndex() - beginning
        }
        // 每N周/每周
        if (currentWord() == "周" || currentWord() == "星期") {
            if (consumeWord("周", "星期")) {
                if (consumeWeekdayPeriod()) {
                    repeat[REPEAT_KEY_WEEK] = repeatCount
                    return getIndex() - beginning
                }
            }
        }
        // 每隔N小时
        if (consumeWord("小时", "小时后")) {
            // consumeMinute可能会消费"半"或"1刻"等
            consumeMinute()
            if (repeatCount < 2) {
                throw ParseError("小时提醒间隔至少2小时")
            }
            repeat[REPEAT_KEY_HOUR] = repeatCount
            return getIndex() - beginning
        }
        // 每隔N分钟（不支持）
        if (consumeWord("分", "分钟")) {
            throw ParseError("暂不支持分钟级别重复提醒")
        }
        // 工作日（不支持）
        if (consumeWord("工作日")) {
            throw ParseError("暂不支持工作日提醒")
        }

        setIndex(beginning)
        return 0
    }

    /**
     * 消费年份：2024年、2024-05-01
     */
    private fun consumeYear(): Int {
        val beginning = getIndex()
        val year = consumeDigit() ?: return 0
        if (!consumeWord("年", "-", "/", ".")) {
            setIndex(beginning)
            return 0
        }
        if (consumeMonth() > 0) {
            if (year > 3000) {
                throw ParseError("年份超出范围")
            }
            if (year < now.year) {
                throw ParseError("年份不能是过去")
            }
            timeFields["year"] = year
            return getIndex() - beginning
        }
        // 即使没有月份，也设置年份
        timeFields["year"] = year
        return getIndex() - beginning
    }

    /**
     * 消费月份：5月、5-20
     */
    private fun consumeMonth(): Int {
        val beginning = getIndex()
        if (consumeWord("农历", "阴历")) {
            throw ParseError("暂不支持农历提醒")
        }
        if (consumeWord("工作日")) {
            throw ParseError("暂不支持工作日提醒")
        }
        val month = consumeDigit() ?: return 0
        if (!consumeWord("月", "-", "/", ".")) {
            setIndex(beginning)
            return 0
        }
        if (month > 12) {
            throw ParseError("月份超出范围")
        }
        if (consumeDay() > 0) {
            timeFields["month"] = month
            return getIndex() - beginning
        }
        setIndex(beginning)
        return 0
    }

    /**
     * 消费日期：20号、20日
     */
    private fun consumeDay(): Int {
        val beginning = getIndex()
        if (currentWord().endsWith("节")) {
            throw ParseError("暂不支持节假日提醒")
        }
        val day = consumeDigit() ?: return 0
        if (day > 31) {
            throw ParseError("日期超出范围")
        }
        if (!consumeWord("日", "号") && beginning == parseBeginning) {
            setIndex(beginning)
            return 0
        }
        timeFields["day"] = day

        // 消费括号中的星期
        consumeWord("(", "（")
        if (consumeWord("周", "星期")) {
            consumeWord("日", "天") || consumeDigit()
        }
        consumeWord(")", "）")

        // 设置默认时间
        if (!consumeHour()) {
            timeFields["hour"] = DEFAULT_HOUR
            timeFields["minute"] = DEFAULT_MINUTE
        }
        excludeTimeRange { consumeDay() }
        return getIndex() - beginning
    }

    /**
     * 消费小时：8点、8:30、下午3点
     */
    private fun consumeHour(): Int {
        val beginning1 = getIndex()

        // 时间段前缀
        if (consumeWord("凌晨", "半夜", "夜里", "深夜")) {
            isAfternoon = false
            timeDeltaFields["days"] = 1
            timeFields["hour"] = 0
            timeFields["minute"] = DEFAULT_MINUTE
        } else if (consumeWord("早", "早上", "早晨", "今早", "上午")) {
            isAfternoon = false
            timeFields["hour"] = DEFAULT_HOUR
            timeFields["minute"] = DEFAULT_MINUTE
        } else if (consumeWord("中午")) {
            isAfternoon = false
            timeFields["hour"] = 12
            timeFields["minute"] = DEFAULT_MINUTE
        } else if (consumeWord("下午")) {
            isAfternoon = true
            timeFields["hour"] = 13
            timeFields["minute"] = DEFAULT_MINUTE
        } else if (consumeWord("傍晚")) {
            isAfternoon = true
            timeFields["hour"] = 18
            timeFields["minute"] = DEFAULT_MINUTE
        } else if (consumeWord("晚上", "今晚")) {
            isAfternoon = true
            timeFields["hour"] = 20
            timeFields["minute"] = DEFAULT_MINUTE
        }

        val beginning2 = getIndex()
        val hour = consumeDigit() ?: return 0
        if (!consumeWord("点", "点钟", "点整", ":", "：", ".", "時", "时")) {
            setIndex(beginning2)
            return 0
        }

        // 下午/晚上处理
        if (isAfternoon == true && hour == 0) {
            // 晚上零点特殊处理
            timeDeltaFields["days"] = 1
        } else if (hour < 12) {
            if (isAfternoon == true || (now.hour >= 12 && timeFields.isEmpty() &&
                        timeDeltaFields.isEmpty() && repeat.isEmpty())) {
                // 需要加12小时的情况
                timeFields["hour"] = hour + 12
            } else {
                timeFields["hour"] = hour
            }
        } else {
            timeFields["hour"] = hour
        }

        if (!consumeMinute()) {
            timeFields["minute"] = DEFAULT_MINUTE
        }
        excludeTimeRange { consumeHour() }
        return getIndex() - beginning1
    }

    /**
     * 消费分钟：30分、45分钟
     */
    private fun consumeMinute(): Int {
        val beginning = getIndex()
        val minute = consumeDigit()
        if (minute != null) {
            if (minute !in 0..60) {
                throw ParseError("分钟超出范围")
            }
            timeFields["minute"] = minute
            consumeWord("分", "分钟", ":")
            consumeSecond()
        } else if (consumeWord("半")) {
            timeFields["minute"] = 30
        } else if (currentWord() == "1" && peekNextWord() == "刻") {
            advance(2)
            timeFields["minute"] = 15
        } else if (currentWord() == "3" && peekNextWord() == "刻") {
            advance(2)
            timeFields["minute"] = 45
        }
        return getIndex() - beginning
    }

    /**
     * 消费秒
     */
    private fun consumeSecond(): Int {
        val beginning = getIndex()
        val second = consumeDigit() ?: return 0
        if (consumeWord("秒", "秒钟")) {
            if (second !in 0..60) {
                throw ParseError("秒超出范围")
            }
            timeFields["second"] = second
            return getIndex() - beginning
        }
        setIndex(beginning)
        return 0
    }

    /**
     * 消费年份周期：今年、明年、后年、X年后
     */
    private fun consumeYearPeriod(): Int {
        val beginning = getIndex()

        if (consumeWord("今年")) {
            timeDeltaFields["years"] = 0
        } else if (consumeWord("明年")) {
            timeDeltaFields["years"] = 1
        } else if (consumeWord("后年")) {
            timeDeltaFields["years"] = 2
        } else {
            val tmp = consumeDigit() ?: return 0
            if (currentWord() == "年" && peekNextWord() in listOf("后", "以后")) {
                timeDeltaFields["years"] = tmp
                advance(2)
            } else {
                setIndex(beginning)
                return 0
            }
        }

        if (!timeDeltaFields.containsKey("years")) {
            setIndex(beginning)
            return 0
        }

        consumeWord("的")
        if (timeDeltaFields["years"]!! >= 100) {
            throw ParseError("年份跨度太大")
        }
        consumeMonth()
        return getIndex() - beginning
    }

    /**
     * 消费月份周期：下个月、下月、X个月后
     */
    private fun consumeMonthPeriod(): Int {
        val beginning = getIndex()

        if (consumeWord("下个月", "下月")) {
            timeDeltaFields["months"] = 1
        } else if (currentWord().isNotEmpty() && currentWord().first().isDigit()) {
            val tmp = consumeDigit() ?: return 0
            consumeWord("个")
            if (currentWord() == "月" && peekNextWord() in listOf("后", "以后")) {
                timeDeltaFields["months"] = tmp
                advance(2)
            } else {
                setIndex(beginning)
                return 0
            }
        } else {
            setIndex(beginning)
            return 0
        }

        if (!timeDeltaFields.containsKey("months")) {
            setIndex(beginning)
            return 0
        }

        consumeWord("的")
        if (timeDeltaFields["months"]!! > 100) {
            throw ParseError("月份跨度太大")
        }
        consumeDay()
        return getIndex() - beginning
    }

    /**
     * 消费日期周期：今天、明天、后天、X天后
     */
    private fun consumeDayPeriod(): Int {
        val beginning = getIndex()
        var hasHour = false
        var hour = DEFAULT_HOUR
        var days: Int? = null

        if (consumeWord("今天")) {
            days = 0
        } else if (consumeWord("今早")) {
            days = 0
            isAfternoon = false
        } else if (consumeWord("今晚")) {
            days = 0
            isAfternoon = true
            hour = 20
        } else if (consumeWord("明天", "明日", "明儿")) {
            days = 1
        } else if (consumeWord("明早")) {
            days = 1
            isAfternoon = false
        } else if (consumeWord("明晚")) {
            days = 1
            isAfternoon = true
            hour = 20
        } else if (consumeWord("后天")) {
            days = 2
        } else if (consumeWord("大后天")) {
            days = 3
        } else {
            val tmp = consumeDigit() ?: return 0
            if (consumeWord("天")) {
                if (consumeWord("后", "以后")) {
                    days = tmp
                } else if (consumeHourPeriod()) {
                    days = tmp
                    hasHour = true
                }
            }
        }

        if (days == null) {
            setIndex(beginning)
            return 0
        }

        if (days > 1000) {
            throw ParseError("天数跨度太大")
        }

        timeDeltaFields["days"] = days

        // 消费括号中的星期
        consumeWord("(", "（")
        if (consumeWord("周", "星期")) {
            consumeWord("日", "天") || consumeDigit()
        }
        consumeWord(")", "）")

        // 两天后下午三点
        if (!hasHour && !consumeHour()) {
            timeFields["hour"] = hour
            timeFields["minute"] = Random.nextInt(4)  // 随机0-3分钟避免同时触发
        }

        return getIndex() - beginning
    }

    /**
     * 消费星期周期：周一、星期日、下周
     */
    private fun consumeWeekdayPeriod(): Int {
        val beginning = getIndex()
        var weekday: Int? = null
        var weekDelta = 0

        if (consumeWord("周", "下周", "下个周", "星期", "下星期", "下个星期", "礼拜", "下礼拜", "下个礼拜")) {
            if (consumeWord("日", "天")) {
                weekday = 7
            } else {
                val day = consumeDigit(consume = false)
                if (day != null) {
                    weekday = day - 1
                    if (weekday !in 0..5) {
                        throw ParseError("星期超出范围")
                    }
                    if (now.dayOfWeek.value == weekday) {
                        weekDelta = 1
                    }
                    consumeDigit()  // 消费数字
                }
            }
        } else if (currentWord().isNotEmpty() && currentWord().first().isDigit()) {
            val tmp = consumeDigit() ?: return 0
            consumeWord("个")
            if (currentWord() in listOf("周", "星期", "礼拜") && peekNextWord() in listOf("后", "以后")) {
                weekDelta = tmp
                advance(2)
            }
        }

        if (weekday != null) {
            timeDeltaFields["weekday"] = weekday
            timeDeltaFields["days"] = 1
        } else if (weekDelta != 0) {
            if (weekDelta > 100) {
                throw ParseError("星期跨度太大")
            }
            timeDeltaFields["weeks"] = weekDelta
        } else {
            setIndex(beginning)
            return 0
        }

        if (!consumeHour()) {
            timeFields["hour"] = DEFAULT_HOUR
            timeFields["minute"] = DEFAULT_MINUTE
        }
        excludeTimeRange { consumeWeekdayPeriod() }
        return getIndex() - beginning
    }

    /**
     * 消费小时周期：半小时后、2小时后
     */
    private fun consumeHourPeriod(): Int {
        val beginning = getIndex()

        if (currentWord().isNotEmpty() && currentWord().first().isDigit()) {
            val tmp = consumeDigit() ?: return 0
            consumeWord("个")
            if (consumeWord("半小时") || (consumeWord("半") && consumeWord("钟头"))) {
                if (consumeWord("后", "以后")) {
                    timeDeltaFields["hours"] = tmp
                    timeDeltaFields["minutes"] = 30
                }
            } else if (consumeWord("小时", "钟头")) {
                if (consumeWord("后", "以后") || consumeMinutePeriod()) {
                    timeDeltaFields["hours"] = tmp
                }
            }
        } else if (consumeWord("半小时") || (consumeWord("半个") && consumeWord("小时", "钟头"))) {
            if (consumeWord("后", "以后")) {
                timeDeltaFields["hours"] = 0
                timeDeltaFields["minutes"] = 30
            }
        }

        if (!timeDeltaFields.containsKey("hours")) {
            setIndex(beginning)
            return 0
        }

        if (timeDeltaFields["hours"]!! > 100) {
            throw ParseError("小时跨度太大")
        }
        return getIndex() - beginning
    }

    /**
     * 消费分钟周期：5分钟后
     */
    private fun consumeMinutePeriod(): Int {
        val beginning = getIndex()
        val minuteDelta = consumeDigit() ?: return 0

        if (consumeWord("分", "分钟")) {
            consumeSecondPeriod()
            if (consumeWord("后", "以后")) {
                if (minuteDelta > 1000) {
                    throw ParseError("分钟跨度太大")
                }
                timeDeltaFields["minutes"] = minuteDelta
                return getIndex() - beginning
            }
        } else if (consumeWord("等会", "一会", "一会儿")) {
            timeDeltaFields["minutes"] = 10
            return getIndex() - beginning
        }

        setIndex(beginning)
        return 0
    }

    /**
     * 消费秒周期
     */
    private fun consumeSecondPeriod(): Int {
        val beginning = getIndex()
        val secondDelta = consumeDigit() ?: return 0

        if (consumeWord("秒", "秒钟")) {
            if (consumeWord("后", "以后")) {
                if (secondDelta > 10000) {
                    throw ParseError("秒跨度太大")
                }
                timeDeltaFields["seconds"] = secondDelta
                return getIndex() - beginning
            }
        }
        setIndex(beginning)
        return 0
    }

    /**
     * 排除时间段（如周一至周五）
     */
    private fun excludeTimeRange(nextExpectation: () -> Int) {
        val rangeIndex = getIndex()
        if (consumeWord("到", "至", "-", "~")) {
            if (nextExpectation() > 0) {
                throw ParseError("暂不支持连续时间段的提醒")
            }
        }
        setIndex(rangeIndex)
    }

    // ==================== 辅助方法 ====================

    /**
     * 消费到末尾，提取事件内容
     */
    private fun consumeToEnd() {
        doWhat = words.subList(idx, words.size).joinToString("") { it.first }.trim()
    }

    /**
     * 消费一个词
     */
    private fun consumeWord(vararg words: String): Boolean {
        for (word in words) {
            if (currentWord() == word) {
                advance()
                return true
            }
        }
        return false
    }

    /**
     * 消费连续多个词
     */
    private fun consumePhrase(vararg words: String): Int {
        val beginning = getIndex()
        for (word in words) {
            if (!consumeWord(word)) {
                setIndex(beginning)
                return 0
            }
        }
        return getIndex() - beginning
    }

    /**
     * 消费数字
     */
    private fun consumeDigit(consume: Boolean = true): Int? {
        val word = currentWord()
        if (word.isNotEmpty() && word.first().isDigit()) {
            val digit = word.toIntOrNull()
            if (consume && digit != null) {
                advance()
            }
            return digit
        }
        return null
    }

    /**
     * 获取当前词
     */
    private fun currentWord(): String {
        if (idx >= words.size) return ""
        // 跳过空白和虚词
        val word = words[idx].first
        if (word.isBlank() || word in listOf("的", "。", "，", "'", "\"", "！", "？")) {
            idx++  // 直接跳过，不回退
            return currentWord()
        }
        return word
    }

    /**
     * 获取当前词性
     */
    private fun currentTag(): String {
        if (idx >= words.size) return ""
        // 跳过空白
        if (words[idx].first.isBlank() || words[idx].first in listOf("的",)) {
            words = words.toMutableList().apply { removeAt(idx) }
            return currentTag()
        }
        return words[idx].second
    }

    /**
     * 预览下一个词
     */
    private fun peekNextWord(step: Int = 1): String {
        val beginning = getIndex()
        val wordList = mutableListOf<String>()
        var s = step
        while (s > 0) {
            advance()
            wordList.add(currentWord())
            s--
        }
        setIndex(beginning)
        return wordList.joinToString("")
    }

    /**
     * 获取当前索引
     */
    private fun getIndex(): Int = idx

    /**
     * 设置索引
     */
    private fun setIndex(newIdx: Int) {
        idx = newIdx
    }

    /**
     * 是否有下一个词
     */
    private fun hasNext(): Boolean = idx < words.size

    /**
     * 前进
     */
    private fun advance(step: Int = 1) {
        idx += step
    }

    // ==================== 公共方法 ====================

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

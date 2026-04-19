package com.smartreminder.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 触发条件 - 决定提醒何时执行
 */
sealed class TriggerCondition {
    /** 每天固定时间 */
    data class Daily(val hour: Int, val minute: Int) : TriggerCondition()

    /** 每周指定星期几 */
    data class Weekly(val dayOfWeek: Int, val hour: Int, val minute: Int) : TriggerCondition()

    /** 每月指定日期 */
    data class Monthly(val dayOfMonth: Int, val hour: Int, val minute: Int) : TriggerCondition()

    /** 每年指定月日 */
    data class Yearly(val month: Int, val day: Int, val hour: Int, val minute: Int) : TriggerCondition()

    /** 间隔循环 */
    data class Interval(val interval: Long, val unit: IntervalUnit) : TriggerCondition()

    /** 只执行一次 */
    data class Once(val timestamp: Long) : TriggerCondition()

    /** Cron表达式 */
    data class Cron(val expression: String) : TriggerCondition()

    fun toDisplayString(): String = when (this) {
        is Daily -> "每天 ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        is Weekly -> "每周${getDayOfWeekChinese(dayOfWeek)} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        is Monthly -> "每月${dayOfMonth}日 ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        is Yearly -> "每年${month}月${day}日 ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        is Interval -> "每${interval}${unit.toDisplayString()}"
        is Once -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            "一次性 ${sdf.format(Date(timestamp))}"
        }
        is Cron -> "自定义(${expression})"
    }

    /**
     * 获取下一次触发的显示字符串
     */
    fun getNextTriggerDisplayString(): String {
        val now = System.currentTimeMillis()

        when (this) {
            is Daily -> {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                if (calendar.timeInMillis <= now) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                return formatRelativeDate(calendar)
            }
            is Weekly -> {
                val calendar = Calendar.getInstance()
                val targetDay = dayOfWeek.coerceIn(1, 7)
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                // Calendar.DAY_OF_WEEK: 1=周日, 2=周一, ..., 7=周六
                // 我们用 1=周一, ..., 7=周日，所以需要转换
                val currentWeekday = if (currentDayOfWeek == 1) 7 else currentDayOfWeek - 1
                var daysToAdd = targetDay - currentWeekday
                if (daysToAdd <= 0) daysToAdd += 7
                calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return formatRelativeDate(calendar)
            }
            is Monthly -> {
                val calendar = Calendar.getInstance()
                val targetDay = dayOfMonth.coerceIn(1, 31)
                calendar.set(Calendar.DAY_OF_MONTH, targetDay)
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                if (calendar.get(Calendar.DAY_OF_MONTH) != targetDay || calendar.timeInMillis <= now) {
                    calendar.add(Calendar.MONTH, 1)
                    calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                return formatRelativeDate(calendar)
            }
            is Yearly -> {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
                calendar.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, 28))
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                if (calendar.timeInMillis <= now) {
                    calendar.add(Calendar.YEAR, 1)
                }
                return formatRelativeDate(calendar)
            }
            is Interval -> {
                val intervalMillis = when (unit) {
                    IntervalUnit.MINUTES -> interval * 60 * 1000
                    IntervalUnit.HOURS -> interval * 60 * 60 * 1000
                    IntervalUnit.DAYS -> interval * 24 * 60 * 60 * 1000
                }
                return "约 ${SimpleDateFormat("HH:mm", Locale.CHINA).format(now + intervalMillis)}"
            }
            is Once -> {
                return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
            }
            is Cron -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                return formatRelativeDate(calendar)
            }
        }
    }

    private fun formatRelativeDate(calendar: Calendar): String {
        val today = Calendar.getInstance()
        val sdfTime = SimpleDateFormat("HH:mm", Locale.CHINA)
        val sdfFull = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

        return when {
            isSameDay(calendar, today) -> "今天 ${sdfTime.format(calendar)}"
            isTomorrow(calendar, today) -> "明天 ${sdfTime.format(calendar)}"
            else -> sdfFull.format(calendar)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isTomorrow(cal1: Calendar, cal2: Calendar): Boolean {
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = cal2.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return isSameDay(cal1, tomorrow)
    }

    private fun getDayOfWeekChinese(day: Int): String = when (day) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> "?"
    }
}

enum class IntervalUnit {
    MINUTES, HOURS, DAYS;
    
    fun toDisplayString(): String = when (this) {
        MINUTES -> "分钟"
        HOURS -> "小时"
        DAYS -> "天"
    }
}

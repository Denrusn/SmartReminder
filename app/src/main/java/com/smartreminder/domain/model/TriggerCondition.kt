package com.smartreminder.domain.model

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
        is Once -> "一次性"
        is Cron -> "自定义(${expression})"
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

package com.smartreminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.smartreminder.domain.model.TriggerCondition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 检查是否具有精确闹钟权限（Android 12+）
     * @return true 如果可以调度精确闹钟
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 获取精确闹钟设置页面的Intent
     * 用于引导用户前往系统设置开启精确闹钟权限
     */
    fun getExactAlarmSettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            null
        }
    }
    
    /**
     * 调度提醒
     */
    fun schedule(reminderId: Long, condition: TriggerCondition) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerTime = calculateNextTriggerTime(condition)

        // 时间已过（Once类型编辑到过去时间），不调度
        if (triggerTime == -1L) return

        when (condition) {
            is TriggerCondition.Once -> {
                // 一次性提醒，使用单次Alarm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            }
            else -> {
                // 重复提醒，使用精确闹钟 + 手动计算下次触发时间
                // Android 12+ setRepeating 已废弃，改用 setExactAndAllowWhileIdle
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            }
        }
    }
    
    /**
     * 取消调度
     */
    fun cancel(reminderId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
    }
    
    /**
     * 计算下次触发时间
     */
    private fun calculateNextTriggerTime(condition: TriggerCondition): Long {
        val now = Calendar.getInstance()

        when (condition) {
            is TriggerCondition.Daily -> {
                val calendar = now.clone() as Calendar
                calendar.set(Calendar.HOUR_OF_DAY, condition.hour)
                calendar.set(Calendar.MINUTE, condition.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                // 如果时间已过，明天再触发
                if (calendar.timeInMillis <= now.timeInMillis) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                return calendar.timeInMillis
            }
            is TriggerCondition.Weekly -> {
                val targetDay = condition.dayOfWeek.coerceIn(1, 7)
                val calendar = now.clone() as Calendar
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                // Calendar.DAY_OF_WEEK: 1=周日, 2=周一, ..., 7=周六
                // 我们用 1=周一, ..., 7=周日，所以需要转换
                val currentWeekday = if (currentDayOfWeek == 1) 7 else currentDayOfWeek - 1
                var daysToAdd = targetDay - currentWeekday
                if (daysToAdd <= 0) daysToAdd += 7
                calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                calendar.set(Calendar.HOUR_OF_DAY, condition.hour)
                calendar.set(Calendar.MINUTE, condition.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return calendar.timeInMillis
            }
            is TriggerCondition.Monthly -> {
                val calendar = now.clone() as Calendar
                val targetDay = condition.dayOfMonth.coerceIn(1, 31)
                // 设置为目标日
                calendar.set(Calendar.DAY_OF_MONTH, targetDay)
                calendar.set(Calendar.HOUR_OF_DAY, condition.hour)
                calendar.set(Calendar.MINUTE, condition.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                // 如果本月该日已过或无效，跳到下月
                if (calendar.get(Calendar.DAY_OF_MONTH) != targetDay || calendar.timeInMillis <= now.timeInMillis) {
                    calendar.add(Calendar.MONTH, 1)
                    calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                return calendar.timeInMillis
            }
            is TriggerCondition.Yearly -> {
                val calendar = now.clone() as Calendar
                val targetMonth = condition.month.coerceIn(1, 12) - 1
                val targetDay = condition.day.coerceIn(1, 28) // 防止月底溢出
                calendar.set(Calendar.MONTH, targetMonth)
                calendar.set(Calendar.DAY_OF_MONTH, targetDay)
                calendar.set(Calendar.HOUR_OF_DAY, condition.hour)
                calendar.set(Calendar.MINUTE, condition.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                // 如果今年时间已过，跳到明年
                if (calendar.timeInMillis <= now.timeInMillis) {
                    calendar.add(Calendar.YEAR, 1)
                }
                return calendar.timeInMillis
            }
            is TriggerCondition.Interval -> {
                val intervalMillis = when (condition.unit) {
                    com.smartreminder.domain.model.IntervalUnit.MINUTES -> condition.interval * 60 * 1000
                    com.smartreminder.domain.model.IntervalUnit.HOURS -> condition.interval * 60 * 60 * 1000
                    com.smartreminder.domain.model.IntervalUnit.DAYS -> condition.interval * 24 * 60 * 60 * 1000
                }
                return System.currentTimeMillis() + intervalMillis
            }
            is TriggerCondition.Once -> {
                val ts = condition.timestamp
                return if (ts > System.currentTimeMillis()) ts else -1L
            }
            is TriggerCondition.Cron -> {
                val calendar = now.clone() as Calendar
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                return calendar.timeInMillis
            }
        }
    }
    
    /**
     * 获取重复间隔
     */
    private fun getRepeatInterval(condition: TriggerCondition): Long {
        return when (condition) {
            is TriggerCondition.Daily -> 24 * 60 * 60 * 1000L  // 1天
            is TriggerCondition.Weekly -> 7 * 24 * 60 * 60 * 1000L  // 7天
            is TriggerCondition.Monthly -> 30 * 24 * 60 * 60 * 1000L  // 30天（简化）
            is TriggerCondition.Yearly -> 365 * 24 * 60 * 60 * 1000L  // 365天
            is TriggerCondition.Interval -> when (condition.unit) {
                com.smartreminder.domain.model.IntervalUnit.MINUTES -> condition.interval * 60 * 1000
                com.smartreminder.domain.model.IntervalUnit.HOURS -> condition.interval * 60 * 60 * 1000
                com.smartreminder.domain.model.IntervalUnit.DAYS -> condition.interval * 24 * 60 * 60 * 1000
            }
            else -> 24 * 60 * 60 * 1000L
        }
    }
}

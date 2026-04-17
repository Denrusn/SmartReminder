package com.smartreminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
                // 重复提醒，使用重复Alarm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setRepeating(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            getRepeatInterval(condition),
                            pendingIntent
                        )
                    } else {
                        alarmManager.setRepeating(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            getRepeatInterval(condition),
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        getRepeatInterval(condition),
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
        val calendar = Calendar.getInstance()
        
        when (condition) {
            is TriggerCondition.Daily -> {
                calendar.set(Calendar.HOUR_OF_DAY, condition.hour)
                calendar.set(Calendar.MINUTE, condition.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            is TriggerCondition.Weekly -> {
                calendar.set(Calendar.DAY_OF_WEEK, condition.dayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, condition.hour)
                calendar.set(Calendar.MINUTE, condition.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            is TriggerCondition.Monthly -> {
                calendar.set(Calendar.DAY_OF_MONTH, condition.dayOfMonth)
                calendar.set(Calendar.HOUR_OF_DAY, condition.hour)
                calendar.set(Calendar.MINUTE, condition.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            is TriggerCondition.Yearly -> {
                calendar.set(Calendar.MONTH, condition.month - 1)
                calendar.set(Calendar.DAY_OF_MONTH, condition.day)
                calendar.set(Calendar.HOUR_OF_DAY, condition.hour)
                calendar.set(Calendar.MINUTE, condition.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            is TriggerCondition.Interval -> {
                // 下次触发时间 = 当前时间 + 间隔
                val intervalMillis = when (condition.unit) {
                    com.smartreminder.domain.model.IntervalUnit.MINUTES -> condition.interval * 60 * 1000
                    com.smartreminder.domain.model.IntervalUnit.HOURS -> condition.interval * 60 * 60 * 1000
                    com.smartreminder.domain.model.IntervalUnit.DAYS -> condition.interval * 24 * 60 * 60 * 1000
                }
                return System.currentTimeMillis() + intervalMillis
            }
            is TriggerCondition.Once -> {
                return condition.timestamp
            }
            is TriggerCondition.Cron -> {
                // 简化处理：使用默认时间
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        // 如果时间已过，加一天（用于每日重复）
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return calendar.timeInMillis
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

package com.smartreminder.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 提醒实体 - Room数据库Entity
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    
    // 触发条件 - 使用JSON存储
    val triggerType: String,           // daily, weekly, monthly, yearly, interval, once, cron
    val triggerHour: Int? = null,
    val triggerMinute: Int? = null,
    val triggerDayOfWeek: Int? = null,
    val triggerDayOfMonth: Int? = null,
    val triggerMonth: Int? = null,
    val triggerInterval: Long? = null,
    val triggerIntervalUnit: String? = null,
    val triggerTimestamp: Long? = null,
    val triggerCron: String? = null,
    
    // 提醒方式
    val reminderMethod: String = "NOTIFICATION",
    
    // 执行动作 - 使用JSON存储
    val actionsJson: String = "[]",
    
    // 时间戳
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

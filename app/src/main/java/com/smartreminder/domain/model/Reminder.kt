package com.smartreminder.domain.model

/**
 * 提醒实体
 */
data class Reminder(
    val id: Long = 0,
    val name: String,                     // 提醒名称
    val description: String = "",         // 描述
    val isEnabled: Boolean = true,        // 是否启用
    val triggerCondition: TriggerCondition, // 触发条件
    val reminderMethod: ReminderMethod = ReminderMethod.NOTIFICATION, // 提醒方式
    val actions: List<ReminderAction> = emptyList(), // 执行动作列表
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

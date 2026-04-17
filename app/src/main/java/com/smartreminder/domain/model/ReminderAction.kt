package com.smartreminder.domain.model

/**
 * 提醒方式
 */
enum class ReminderMethod {
    NOTIFICATION,           // 普通通知
    STRONG_REMINDER,       // 强提醒
    STRONG_REMINDER_WITH_SETTINGS  // 强提醒+自定义设置
}

/**
 * 执行动作 - 提醒触发后执行的具体操作
 */
sealed class ReminderAction {
    /** 发送通知 */
    data class SendNotification(
        val title: String,
        val content: String
    ) : ReminderAction()
    
    /** 强提醒 */
    data class StrongReminder(
        val title: String,
        val content: String,
        val soundEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,
        val popupType: PopupType = PopupType.FULL_SCREEN
    ) : ReminderAction()
    
    /** 弹出对话框 */
    data class ShowDialog(
        val title: String,
        val content: String
    ) : ReminderAction()
    
    /** 打开URL */
    data class OpenUrl(val url: String) : ReminderAction()
    
    /** 启动应用 */
    data class LaunchApp(val packageName: String) : ReminderAction()
    
    /** 拨打电话 */
    data class MakeCall(val phoneNumber: String) : ReminderAction()
    
    /** 发送短信 */
    data class SendSms(val phoneNumber: String, val content: String) : ReminderAction()
    
    /** 设置闹钟 */
    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String,
        val vibrationEnabled: Boolean = true
    ) : ReminderAction()
    
    /** 清理缓存 */
    data class ClearCache() : ReminderAction()
    
    /** 卸载应用 */
    data class UninstallApp(val packageName: String) : ReminderAction()
}

enum class PopupType {
    FULL_SCREEN,   // 全屏弹窗
    ACTIVITY,      // 活动窗口
    HEAD_UP        // 横幅通知
}

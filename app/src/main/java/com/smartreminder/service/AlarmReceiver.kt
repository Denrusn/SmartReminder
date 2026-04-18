package com.smartreminder.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartreminder.domain.repository.ReminderRepository
import com.smartreminder.service.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_TRIGGER) return

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId == -1L) return

        // 【关键修复】在启动服务前先重新调度下次触发
        // setExactAndAllowWhileIdle 不是 repeating，需要手动重调度
        // 使用 goAsync() 防止 ANR，让重调度在 BroadcastReceiver 返回前完成
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = reminderRepository.getReminderById(reminderId)
                if (reminder != null && reminder.isEnabled) {
                    // 重新调度下次（Once 会在 schedule() 里检测到 -1L 自动跳过）
                    reminderScheduler.schedule(reminderId, reminder.triggerCondition)
                }
            } finally {
                pendingResult.finish()
            }
        }

        // 启动强提醒服务
        val serviceIntent = Intent(context, StrongReminderService::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.smartreminder.ALARM_TRIGGER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}

package com.smartreminder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.smartreminder.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // 设备启动后等待系统就绪，避免数据库未准备好
        Handler(Looper.getMainLooper()).postDelayed({
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reminders = reminderRepository.getEnabledReminders()
                    reminders.forEach { reminder ->
                        reminderScheduler.schedule(reminder.id, reminder.triggerCondition)
                    }
                } catch (e: Exception) {
                    // 忽略错误，重启后可能数据库还未准备好
                }
            }
        }, BOOT_DELAY_MS)
    }

    companion object {
        private const val BOOT_DELAY_MS = 5_000L // 等待5秒确保系统完全启动
    }
}

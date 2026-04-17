package com.smartreminder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        
        // 重新调度所有启用的提醒
        CoroutineScope(Dispatchers.IO).launch {
            val reminders = reminderRepository.getEnabledReminders()
            reminders.forEach { reminder ->
                reminderScheduler.schedule(reminder.id, reminder.triggerCondition)
            }
        }
    }
}

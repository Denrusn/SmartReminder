package com.smartreminder.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartreminder.MainActivity
import com.smartreminder.R
import com.smartreminder.SmartReminderApp
import com.smartreminder.domain.model.ReminderMethod
import com.smartreminder.domain.repository.ReminderRepository
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
        
        // 在新进程中启动服务处理
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

package com.smartreminder.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.smartreminder.MainActivity
import com.smartreminder.R
import com.smartreminder.SmartReminderApp
import com.smartreminder.domain.model.ExecutionLog
import com.smartreminder.domain.model.ExecutionResult
import com.smartreminder.domain.model.ReminderAction
import com.smartreminder.domain.model.ReminderMethod
import com.smartreminder.domain.repository.ReminderRepository
import com.smartreminder.ui.reminder.StrongReminderActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StrongReminderService : Service() {
    
    @Inject
    lateinit var reminderRepository: ReminderRepository
    
    @Inject
    lateinit var reminderScheduler: ReminderScheduler
    
    private var reminderId: Long = -1
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        reminderId = intent?.getLongExtra(AlarmReceiver.EXTRA_REMINDER_ID, -1) ?: -1
        
        if (reminderId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            val reminder = reminderRepository.getReminderById(reminderId)
            if (reminder == null) {
                stopSelf()
                return@launch
            }
            
            // 记录执行日志
            reminderRepository.insertLog(
                ExecutionLog(
                    reminderId = reminderId,
                    reminderName = reminder.name,
                    executedAt = System.currentTimeMillis(),
                    result = ExecutionResult.SUCCESS
                )
            )
            
            // 根据提醒方式处理
            when (reminder.reminderMethod) {
                ReminderMethod.NOTIFICATION -> {
                    showNotification(reminder.name, reminder.description)
                    stopSelf()
                }
                ReminderMethod.STRONG_REMINDER, 
                ReminderMethod.STRONG_REMINDER_WITH_SETTINGS -> {
                    showStrongReminder(reminder.name, reminder.description)
                    stopSelf()
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun showNotification(title: String, content: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, SmartReminderApp.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(reminderId.toInt(), notification)
    }
    
    private fun showStrongReminder(title: String, content: String) {
        // 震动
        vibrate()
        
        // 播放声音
        playSound()
        
        // 启动强提醒Activity
        val intent = Intent(this, StrongReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(StrongReminderActivity.EXTRA_TITLE, title)
            putExtra(StrongReminderActivity.EXTRA_CONTENT, content)
            putExtra(StrongReminderActivity.EXTRA_REMINDER_ID, reminderId)
        }
        startActivity(intent)
    }
    
    private fun vibrate() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        val effect = VibrationEffect.createWaveform(
            longArrayOf(0, 500, 200, 500, 200, 500),
            -1
        )
        vibrator.vibrate(effect)
    }
    
    private fun playSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

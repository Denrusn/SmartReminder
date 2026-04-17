package com.smartreminder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartReminderApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // 普通提醒渠道
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                enableVibration(true)
            }
            
            // 强提醒渠道
            val strongReminderChannel = NotificationChannel(
                CHANNEL_STRONG_REMINDER,
                getString(R.string.strong_reminder_channel_name),
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = getString(R.string.strong_reminder_channel_description)
                enableVibration(true)
                setSound(null, null)
            }
            
            notificationManager.createNotificationChannels(
                listOf(reminderChannel, strongReminderChannel)
            )
        }
    }
    
    companion object {
        const val CHANNEL_REMINDER = "reminder_channel"
        const val CHANNEL_STRONG_REMINDER = "strong_reminder_channel"
    }
}

package com.smartreminder.domain.repository

import com.smartreminder.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getAllReminders(): Flow<List<Reminder>>
    suspend fun getReminderById(id: Long): Reminder?
    suspend fun getEnabledReminders(): List<Reminder>
    suspend fun insertReminder(reminder: Reminder): Long
    suspend fun updateReminder(reminder: Reminder)
    suspend fun deleteReminder(id: Long)
    suspend fun updateReminderEnabled(id: Long, isEnabled: Boolean)
    fun searchReminders(query: String): Flow<List<Reminder>>
    
    fun getAllLogs(): Flow<List<ExecutionLog>>
    fun getLogsByReminderId(reminderId: Long): Flow<List<ExecutionLog>>
    fun getRecentLogs(limit: Int): Flow<List<ExecutionLog>>
    suspend fun insertLog(log: ExecutionLog): Long
    suspend fun deleteLogsByReminderId(reminderId: Long)
    suspend fun deleteAllLogs()
}

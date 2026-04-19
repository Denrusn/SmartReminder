package com.smartreminder.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartreminder.data.local.db.*
import com.smartreminder.domain.model.*
import com.smartreminder.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val executionLogDao: ExecutionLogDao,
    private val gson: Gson
) : ReminderRepository {
    
    // ============ Reminder ============
    
    override fun getAllReminders(): Flow<List<Reminder>> {
        return reminderDao.getAllReminders().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getReminderById(id: Long): Reminder? {
        return reminderDao.getReminderById(id)?.toDomain()
    }
    
    override suspend fun getEnabledReminders(): List<Reminder> {
        return reminderDao.getEnabledReminders().map { it.toDomain() }
    }
    
    override suspend fun insertReminder(reminder: Reminder): Long {
        return reminderDao.insertReminder(reminder.toEntity())
    }
    
    override suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder.toEntity())
    }
    
    override suspend fun deleteReminder(id: Long) {
        reminderDao.deleteReminderById(id)
        executionLogDao.deleteLogsByReminderId(id)
    }
    
    override suspend fun updateReminderEnabled(id: Long, isEnabled: Boolean) {
        reminderDao.updateReminderEnabled(id, isEnabled)
    }
    
    override fun searchReminders(query: String): Flow<List<Reminder>> {
        return reminderDao.searchReminders(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    // ============ ExecutionLog ============
    
    override fun getAllLogs(): Flow<List<ExecutionLog>> {
        return executionLogDao.getAllLogs().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getLogsByReminderId(reminderId: Long): Flow<List<ExecutionLog>> {
        return executionLogDao.getLogsByReminderId(reminderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getRecentLogs(limit: Int): Flow<List<ExecutionLog>> {
        return executionLogDao.getRecentLogs(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun insertLog(log: ExecutionLog): Long {
        return executionLogDao.insertLog(log.toEntity())
    }
    
    override suspend fun deleteLogsByReminderId(reminderId: Long) {
        executionLogDao.deleteLogsByReminderId(reminderId)
    }
    
    override suspend fun deleteAllLogs() {
        executionLogDao.deleteAllLogs()
    }
    
    // ============ Mapper ============

    private fun ReminderEntity.toDomain(): Reminder {
        val triggerCondition: TriggerCondition = try {
            when (triggerType) {
                "daily" -> TriggerCondition.Daily(triggerHour ?: 0, triggerMinute ?: 0)
                "weekly" -> {
                    val dow = (triggerDayOfWeek ?: 1).coerceIn(1, 7)
                    TriggerCondition.Weekly(dow, triggerHour ?: 0, triggerMinute ?: 0)
                }
                "monthly" -> {
                    val dom = (triggerDayOfMonth ?: 1).coerceIn(1, 31)
                    TriggerCondition.Monthly(dom, triggerHour ?: 0, triggerMinute ?: 0)
                }
                "yearly" -> {
                    val m = (triggerMonth ?: 1).coerceIn(1, 12)
                    val d = (triggerDayOfMonth ?: 1).coerceIn(1, 28)
                    TriggerCondition.Yearly(m, d, triggerHour ?: 0, triggerMinute ?: 0)
                }
                "interval" -> {
                    val unit = try {
                        IntervalUnit.valueOf(triggerIntervalUnit ?: "DAYS")
                    } catch (e: Exception) {
                        IntervalUnit.DAYS
                    }
                    TriggerCondition.Interval(triggerInterval ?: 1, unit)
                }
                "once" -> TriggerCondition.Once(triggerTimestamp ?: System.currentTimeMillis())
                "cron" -> TriggerCondition.Cron(triggerCron ?: "")
                else -> TriggerCondition.Once(System.currentTimeMillis() + 86400000)
            }
        } catch (e: Exception) {
            // 解析失败时使用一次性提醒作为安全默认值，防止 App 崩溃
            TriggerCondition.Once(System.currentTimeMillis() + 86400000)
        }

        val actions: List<ReminderAction> = try {
            val type = object : TypeToken<List<ReminderActionJson>>() {}.type
            val jsonActions: List<ReminderActionJson> = gson.fromJson(actionsJson, type)
            jsonActions.mapNotNull { it.toDomainOrNull() }
        } catch (e: Exception) {
            emptyList()
        }

        val reminderMethod: ReminderMethod = try {
            ReminderMethod.valueOf(reminderMethod)
        } catch (e: Exception) {
            ReminderMethod.NOTIFICATION
        }

        return Reminder(
            id = id,
            name = name,
            description = description,
            isEnabled = isEnabled,
            triggerCondition = triggerCondition,
            reminderMethod = reminderMethod,
            actions = actions,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    private fun Reminder.toEntity(): ReminderEntity {
        val (triggerType, triggerHour, triggerMinute, triggerDayOfWeek, triggerDayOfMonth, triggerMonth, triggerInterval, triggerIntervalUnit, triggerTimestamp, triggerCron) = when (triggerCondition) {
            is TriggerCondition.Daily -> TriggerFields("daily", triggerCondition.hour, triggerCondition.minute, null, null, null, null, null, null, null)
            is TriggerCondition.Weekly -> TriggerFields("weekly", triggerCondition.hour, triggerCondition.minute, triggerCondition.dayOfWeek, null, null, null, null, null, null)
            is TriggerCondition.Monthly -> TriggerFields("monthly", triggerCondition.hour, triggerCondition.minute, null, triggerCondition.dayOfMonth, null, null, null, null, null)
            is TriggerCondition.Yearly -> TriggerFields("yearly", triggerCondition.hour, triggerCondition.minute, null, triggerCondition.day, triggerCondition.month, null, null, null, null)
            is TriggerCondition.Interval -> TriggerFields("interval", null, null, null, null, null, triggerCondition.interval, triggerCondition.unit.name, null, null)
            is TriggerCondition.Once -> TriggerFields("once", null, null, null, null, null, null, null, triggerCondition.timestamp, null)
            is TriggerCondition.Cron -> TriggerFields("cron", null, null, null, null, null, null, null, null, triggerCondition.expression)
        }
        
        val actionsJson = gson.toJson(actions.map { it.toJson() })
        
        return ReminderEntity(
            id = id,
            name = name,
            description = description,
            isEnabled = isEnabled,
            triggerType = triggerType,
            triggerHour = triggerHour,
            triggerMinute = triggerMinute,
            triggerDayOfWeek = triggerDayOfWeek,
            triggerDayOfMonth = triggerDayOfMonth,
            triggerMonth = triggerMonth,
            triggerInterval = triggerInterval,
            triggerIntervalUnit = triggerIntervalUnit,
            triggerTimestamp = triggerTimestamp,
            triggerCron = triggerCron,
            reminderMethod = reminderMethod.name,
            actionsJson = actionsJson,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    private data class TriggerFields(
        val triggerType: String,
        val triggerHour: Int?,
        val triggerMinute: Int?,
        val triggerDayOfWeek: Int?,
        val triggerDayOfMonth: Int?,
        val triggerMonth: Int?,
        val triggerInterval: Long?,
        val triggerIntervalUnit: String?,
        val triggerTimestamp: Long?,
        val triggerCron: String?
    )
    
    private data class ReminderActionJson(
        val type: String,
        val title: String? = null,
        val content: String? = null,
        val soundEnabled: Boolean? = null,
        val vibrationEnabled: Boolean? = null,
        val popupType: String? = null,
        val url: String? = null,
        val packageName: String? = null,
        val phoneNumber: String? = null,
        val hour: Int? = null,
        val minute: Int? = null,
        val label: String? = null
    )
    
    private fun ReminderActionJson.toDomainOrNull(): ReminderAction? = try {
        when (type) {
            "SendNotification" -> ReminderAction.SendNotification(title ?: "", content ?: "")
            "StrongReminder" -> {
                val popupTypeEnum = try {
                    PopupType.valueOf(popupType ?: "FULL_SCREEN")
                } catch (e: Exception) {
                    PopupType.FULL_SCREEN
                }
                ReminderAction.StrongReminder(
                    title ?: "",
                    content ?: "",
                    soundEnabled ?: true,
                    vibrationEnabled ?: true,
                    popupTypeEnum
                )
            }
            "ShowDialog" -> ReminderAction.ShowDialog(title ?: "", content ?: "")
            "OpenUrl" -> ReminderAction.OpenUrl(url ?: "")
            "LaunchApp" -> ReminderAction.LaunchApp(packageName ?: "")
            "MakeCall" -> ReminderAction.MakeCall(phoneNumber ?: "")
            "SendSms" -> ReminderAction.SendSms(phoneNumber ?: "", content ?: "")
            "SetAlarm" -> ReminderAction.SetAlarm(hour ?: 0, minute ?: 0, label ?: "", vibrationEnabled ?: true)
            "ClearCache" -> ReminderAction.ClearCache
            "UninstallApp" -> ReminderAction.UninstallApp(packageName ?: "")
            else -> ReminderAction.SendNotification("提醒", content ?: "")
        }
    } catch (e: Exception) {
        null
    }
    
    private fun ReminderAction.toJson(): ReminderActionJson = when (this) {
        is ReminderAction.SendNotification -> ReminderActionJson("SendNotification", title = title, content = content)
        is ReminderAction.StrongReminder -> ReminderActionJson("StrongReminder", title = title, content = content, soundEnabled = soundEnabled, vibrationEnabled = vibrationEnabled, popupType = popupType.name)
        is ReminderAction.ShowDialog -> ReminderActionJson("ShowDialog", title = title, content = content)
        is ReminderAction.OpenUrl -> ReminderActionJson("OpenUrl", url = url)
        is ReminderAction.LaunchApp -> ReminderActionJson("LaunchApp", packageName = packageName)
        is ReminderAction.MakeCall -> ReminderActionJson("MakeCall", phoneNumber = phoneNumber)
        is ReminderAction.SendSms -> ReminderActionJson("SendSms", phoneNumber = phoneNumber, content = content)
        is ReminderAction.SetAlarm -> ReminderActionJson("SetAlarm", hour = hour, minute = minute, label = label, vibrationEnabled = vibrationEnabled)
        is ReminderAction.ClearCache -> ReminderActionJson("ClearCache")
        is ReminderAction.UninstallApp -> ReminderActionJson("UninstallApp", packageName = packageName)
    }
    
    private fun ExecutionLogEntity.toDomain(): ExecutionLog = ExecutionLog(
        id = id,
        reminderId = reminderId,
        reminderName = reminderName,
        executedAt = executedAt,
        result = ExecutionResult.valueOf(result),
        errorMessage = errorMessage
    )
    
    private fun ExecutionLog.toEntity(): ExecutionLogEntity = ExecutionLogEntity(
        id = id,
        reminderId = reminderId,
        reminderName = reminderName,
        executedAt = executedAt,
        result = result.name,
        errorMessage = errorMessage
    )
}

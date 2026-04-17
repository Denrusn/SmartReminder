package com.smartreminder.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 执行日志实体
 */
@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: Long,
    val reminderName: String,
    val executedAt: Long,
    val result: String,           // SUCCESS, FAILED, SKIPPED, STRONG_REMINDER_PENDING
    val errorMessage: String? = null
)

package com.smartreminder.domain.model

/**
 * 执行日志
 */
data class ExecutionLog(
    val id: Long = 0,
    val reminderId: Long,
    val reminderName: String,
    val executedAt: Long,
    val result: ExecutionResult,
    val errorMessage: String? = null
)

enum class ExecutionResult {
    SUCCESS,                    // 成功
    FAILED,                     // 失败
    SKIPPED,                    // 跳过
    STRONG_REMINDER_PENDING     // 强提醒待确认
}

package com.smartreminder.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY executedAt DESC")
    fun getAllLogs(): Flow<List<ExecutionLogEntity>>
    
    @Query("SELECT * FROM execution_logs WHERE reminderId = :reminderId ORDER BY executedAt DESC")
    fun getLogsByReminderId(reminderId: Long): Flow<List<ExecutionLogEntity>>
    
    @Query("SELECT * FROM execution_logs ORDER BY executedAt DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<ExecutionLogEntity>>
    
    @Insert
    suspend fun insertLog(log: ExecutionLogEntity): Long
    
    @Query("DELETE FROM execution_logs WHERE reminderId = :reminderId")
    suspend fun deleteLogsByReminderId(reminderId: Long)
    
    @Query("DELETE FROM execution_logs")
    suspend fun deleteAllLogs()
}

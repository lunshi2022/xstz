package com.huaying.xstz.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.huaying.xstz.data.entity.OperationLog
import com.huaying.xstz.data.entity.OperationType
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationLogDao {
    
    @Insert
    suspend fun insert(operationLog: OperationLog): Long
    
    @Query("SELECT * FROM operation_logs ORDER BY createdAt DESC")
    fun getAllOperationLogs(): Flow<List<OperationLog>>
    
    @Query("SELECT * FROM operation_logs ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentOperationLogs(limit: Int): Flow<List<OperationLog>>
    
    @Query("SELECT * FROM operation_logs WHERE operationType = :type ORDER BY createdAt DESC")
    fun getOperationLogsByType(type: OperationType): Flow<List<OperationLog>>
    
    @Query("SELECT * FROM operation_logs WHERE targetId = :targetId ORDER BY createdAt DESC")
    fun getOperationLogsByTarget(targetId: Long): Flow<List<OperationLog>>
    
    @Query("SELECT * FROM operation_logs WHERE createdAt >= :startTime ORDER BY createdAt DESC")
    fun getOperationLogsSince(startTime: Long): Flow<List<OperationLog>>
    
    @Query("SELECT * FROM operation_logs WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun getOperationLogsBetween(startTime: Long, endTime: Long): Flow<List<OperationLog>>
    
    @Query("DELETE FROM operation_logs WHERE createdAt < :beforeTime")
    suspend fun deleteOldOperationLogs(beforeTime: Long)
    
    @Query("DELETE FROM operation_logs")
    suspend fun deleteAllOperationLogs()
    
    @Query("SELECT COUNT(*) FROM operation_logs")
    fun getOperationLogCount(): Flow<Int>
    
    @Query("SELECT * FROM operation_logs ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestOperationLog(): OperationLog?
}

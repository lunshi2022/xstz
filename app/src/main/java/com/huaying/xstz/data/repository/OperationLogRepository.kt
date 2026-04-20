package com.huaying.xstz.data.repository

import com.huaying.xstz.data.AppDatabase
import com.huaying.xstz.data.entity.OperationLog
import com.huaying.xstz.data.entity.OperationType
import kotlinx.coroutines.flow.Flow

class OperationLogRepository(
    private val database: AppDatabase
) {
    
    fun getAllOperationLogs(): Flow<List<OperationLog>> = 
        database.operationLogDao().getAllOperationLogs()
    
    fun getRecentOperationLogs(limit: Int = 100): Flow<List<OperationLog>> = 
        database.operationLogDao().getRecentOperationLogs(limit)
    
    fun getOperationLogsByType(type: OperationType): Flow<List<OperationLog>> = 
        database.operationLogDao().getOperationLogsByType(type)
    
    fun getOperationLogsByTarget(targetId: Long): Flow<List<OperationLog>> = 
        database.operationLogDao().getOperationLogsByTarget(targetId)
    
    fun getOperationLogsSince(startTime: Long): Flow<List<OperationLog>> = 
        database.operationLogDao().getOperationLogsSince(startTime)
    
    fun getOperationLogsBetween(startTime: Long, endTime: Long): Flow<List<OperationLog>> = 
        database.operationLogDao().getOperationLogsBetween(startTime, endTime)
    
    fun getOperationLogCount(): Flow<Int> = 
        database.operationLogDao().getOperationLogCount()
    
    suspend fun getLatestOperationLog(): OperationLog? = 
        database.operationLogDao().getLatestOperationLog()
    
    suspend fun insertOperationLog(operationLog: OperationLog): Long = 
        database.operationLogDao().insert(operationLog)
    
    suspend fun logOperation(
        type: OperationType,
        title: String,
        description: String,
        targetId: Long? = null,
        targetName: String? = null
    ): Long {
        val log = OperationLog(
            operationType = type,
            title = title,
            description = description,
            targetId = targetId,
            targetName = targetName
        )
        return insertOperationLog(log)
    }
    
    suspend fun deleteOldOperationLogs(beforeTime: Long) = 
        database.operationLogDao().deleteOldOperationLogs(beforeTime)
    
    suspend fun deleteAllOperationLogs() = 
        database.operationLogDao().deleteAllOperationLogs()
    
    // 保留最近N天的操作记录
    suspend fun retainRecentLogs(days: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        deleteOldOperationLogs(cutoffTime)
    }
}

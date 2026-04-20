package com.huaying.xstz.data.dao

import androidx.room.*
import com.huaying.xstz.data.entity.NetValueRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface NetValueRecordDao {
    @Query("SELECT * FROM net_value_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<NetValueRecord>>
    
    @Query("SELECT * FROM net_value_records ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentRecords(limit: Int): Flow<List<NetValueRecord>>
    
    @Query("SELECT * FROM net_value_records WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun getRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<NetValueRecord>>
    
    @Query("SELECT * FROM net_value_records WHERE createdAt >= :startTime ORDER BY createdAt DESC")
    fun getRecordsSince(startTime: Long): Flow<List<NetValueRecord>>
    
    @Insert
    suspend fun insertRecord(record: NetValueRecord): Long
    
    @Update
    suspend fun updateRecord(record: NetValueRecord)
    
    @Delete
    suspend fun deleteRecord(record: NetValueRecord)
    
    @Query("DELETE FROM net_value_records WHERE createdAt < :timestamp")
    suspend fun deleteOldRecords(timestamp: Long)
    
    @Query("DELETE FROM net_value_records")
    suspend fun deleteAllRecords()
}

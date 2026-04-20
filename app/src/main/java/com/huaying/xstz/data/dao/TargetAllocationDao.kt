package com.huaying.xstz.data.dao

import androidx.room.*
import com.huaying.xstz.data.entity.TargetAllocation
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetAllocationDao {
    @Query("SELECT * FROM target_allocation WHERE id = 1")
    fun getTargetAllocation(): Flow<TargetAllocation?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargetAllocation(allocation: TargetAllocation)
    
    @Update
    suspend fun updateTargetAllocation(allocation: TargetAllocation)
}

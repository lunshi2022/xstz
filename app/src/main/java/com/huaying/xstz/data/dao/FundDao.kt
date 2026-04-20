package com.huaying.xstz.data.dao

import androidx.room.*
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import kotlinx.coroutines.flow.Flow

@Dao
interface FundDao {
    @Query("SELECT * FROM funds ORDER BY type, code")
    fun getAllFunds(): Flow<List<Fund>>
    
    @Query("SELECT * FROM funds WHERE id = :id")
    suspend fun getFundById(id: Long): Fund?
    
    @Query("SELECT * FROM funds WHERE code = :code")
    suspend fun getFundByCode(code: String): Fund?
    
    @Query("SELECT * FROM funds WHERE type = :type")
    fun getFundsByType(type: AssetType): Flow<List<Fund>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFund(fund: Fund): Long
    
    @Update
    suspend fun updateFund(fund: Fund)
    
    @Delete
    suspend fun deleteFund(fund: Fund)
    
    @Query("DELETE FROM funds WHERE id = :id")
    suspend fun deleteFundById(id: Long)
    
    @Query("DELETE FROM funds")
    suspend fun deleteAllFunds()

    @Query("DELETE FROM funds WHERE type != :type")
    suspend fun deleteFundsExcludeType(type: AssetType)

    @Query("UPDATE funds SET holdingQuantity = 0, totalCost = 0 WHERE type = :type")
    suspend fun resetFundByType(type: AssetType)
    
    @Query("SELECT SUM(holdingQuantity * currentPrice) FROM funds")
    suspend fun getTotalMarketValue(): Double?
    
    @Query("SELECT SUM(totalCost) FROM funds")
    suspend fun getTotalCost(): Double?
    
    @Query("SELECT SUM(holdingQuantity * currentPrice) FROM funds WHERE type = :type")
    suspend fun getMarketValueByType(type: AssetType): Double?
}

package com.huaying.xstz.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.huaying.xstz.data.repository.TimeRepository

@Entity(tableName = "net_value_records")
data class NetValueRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val totalAssets: Double,  // 总资产
    val principal: Double,   // 本金
    val totalReturn: Double, // 累计收益
    val returnRate: Double,  // 收益率
    
    // 各资产类别市值
    val stockValue: Double = 0.0,
    val bondValue: Double = 0.0,
    val goldValue: Double = 0.0,
    val cashValue: Double = 0.0,
    
    val createdAt: Long = TimeRepository.getCurrentTimeMillis()
)

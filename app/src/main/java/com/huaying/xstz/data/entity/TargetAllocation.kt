package com.huaying.xstz.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "target_allocation")
data class TargetAllocation(
    @PrimaryKey
    val id: Int = 1,
    
    val stockRatio: Double = 40.0,  // 股票目标比例
    val bondRatio: Double = 30.0,   // 债券目标比例
    val goldRatio: Double = 10.0,   // 黄金目标比例
    val cashRatio: Double = 20.0    // 现金目标比例
) {
    fun isValid(): Boolean {
        val total = stockRatio + bondRatio + goldRatio + cashRatio
        return kotlin.math.abs(total - 100.0) < 0.01
    }
}

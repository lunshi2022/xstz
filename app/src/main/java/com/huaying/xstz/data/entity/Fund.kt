package com.huaying.xstz.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.data.converter.EncryptedDoubleConverter

@Entity(
    tableName = "funds",
    indices = [Index(value = ["code"], unique = true)]
)
data class Fund(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val code: String,  // 基金代码（6位）
    val name: String,  // 基金名称
    val type: AssetType,  // 资产类别
    
    // 敏感字段加密存储
    @TypeConverters(EncryptedDoubleConverter::class)
    val holdingQuantity: Double,  // 持有份额
    
    @TypeConverters(EncryptedDoubleConverter::class)
    val totalCost: Double,  // 总成本
    
    val currentPrice: Double = 0.0,  // 当前价格
    val changePercent: Double = 0.0,  // 涨跌幅
    
    val targetRatio: Double = 25.0,  // 目标比例
    
    val createdAt: Long = TimeRepository.getCurrentTimeMillis(),
    val updatedAt: Long = TimeRepository.getCurrentTimeMillis()
)

enum class AssetType {
    STOCK,       // 股票
    BOND,        // 债券
    COMMODITY,   // 商品
    CASH         // 现金
}

fun AssetType.toDisplayName(): String {
    return when (this) {
        AssetType.STOCK -> "股票"
        AssetType.BOND -> "债券"
        AssetType.COMMODITY -> "商品"
        AssetType.CASH -> "现金"
    }
}

fun String.toAssetType(): AssetType {
    return when (this) {
        "股票" -> AssetType.STOCK
        "债券" -> AssetType.BOND
        "商品" -> AssetType.COMMODITY
        "现金" -> AssetType.CASH
        "GOLD" -> AssetType.COMMODITY
        else -> AssetType.STOCK
    }
}

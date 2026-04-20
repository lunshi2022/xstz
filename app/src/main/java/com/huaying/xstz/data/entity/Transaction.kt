package com.huaying.xstz.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.huaying.xstz.data.repository.TimeRepository

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val fundId: Long,  // 基金ID
    val fundCode: String,  // 基金代码
    val fundName: String,  // 基金名称
    
    val type: TransactionType,  // 交易类型
    val amount: Double,  // 金额（正数）
    val price: Double,  // 单价
    val quantity: Double,  // 份额
    
    val remark: String = "",  // 备注
    
    val createdAt: Long = TimeRepository.getCurrentTimeMillis()
)

enum class TransactionType {
    BUY,          // 买入
    SELL,         // 卖出
    REBALANCE,    // 再平衡
    ADD_FUNDS,    // 新增资金
    WITHDRAW      // 取出资金
}

fun TransactionType.toDisplayName(): String {
    return when (this) {
        TransactionType.BUY -> "买入"
        TransactionType.SELL -> "卖出"
        TransactionType.REBALANCE -> "再平衡"
        TransactionType.ADD_FUNDS -> "买入"
        TransactionType.WITHDRAW -> "取出资金"
    }
}

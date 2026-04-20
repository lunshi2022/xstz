package com.huaying.xstz.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.huaying.xstz.data.repository.TimeRepository

@Entity(tableName = "operation_logs")
data class OperationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val operationType: OperationType,  // 操作类型
    val title: String,                 // 操作标题
    val description: String,           // 操作描述
    val targetId: Long? = null,        // 相关对象ID（如基金ID）
    val targetName: String? = null,    // 相关对象名称
    val createdAt: Long = TimeRepository.getCurrentTimeMillis()
)

enum class OperationType {
    ADD_FUND,           // 添加基金
    DELETE_FUND,        // 删除基金
    EDIT_FUND,          // 编辑基金
    BUY,                // 买入
    SELL,               // 卖出
    REBALANCE,          // 再平衡
    ADD_CASH,           // 添加资金
    WITHDRAW_CASH,      // 取出资金
    EDIT_HOLDING,       // 修改持仓
    EDIT_COST,          // 修改成本
    EDIT_TARGET_RATIO,  // 修改目标比例
    EDIT_ASSET_TYPE,    // 修改资产类型
    CLEAR_DATA,         // 清空数据
    EXPORT_DATA,        // 导出数据
    IMPORT_DATA,        // 导入数据
    SETTINGS_CHANGE,    // 设置变更
    VIEW_CHART,         // 查看图表
    VIEW_DETAIL,        // 查看详情
    OTHER               // 其他操作
}

fun OperationType.toDisplayName(): String {
    return when (this) {
        OperationType.ADD_FUND -> "添加基金"
        OperationType.DELETE_FUND -> "删除基金"
        OperationType.EDIT_FUND -> "编辑基金"
        OperationType.BUY -> "买入"
        OperationType.SELL -> "卖出"
        OperationType.REBALANCE -> "再平衡"
        OperationType.ADD_CASH -> "添加资金"
        OperationType.WITHDRAW_CASH -> "取出资金"
        OperationType.EDIT_HOLDING -> "修改持仓"
        OperationType.EDIT_COST -> "修改成本"
        OperationType.EDIT_TARGET_RATIO -> "修改目标比例"
        OperationType.EDIT_ASSET_TYPE -> "修改资产类型"
        OperationType.CLEAR_DATA -> "清空数据"
        OperationType.EXPORT_DATA -> "导出数据"
        OperationType.IMPORT_DATA -> "导入数据"
        OperationType.SETTINGS_CHANGE -> "设置变更"
        OperationType.VIEW_CHART -> "查看图表"
        OperationType.VIEW_DETAIL -> "查看详情"
        OperationType.OTHER -> "其他操作"
    }
}

fun OperationType.toIcon(): String {
    return when (this) {
        OperationType.ADD_FUND -> "➕"
        OperationType.DELETE_FUND -> "🗑️"
        OperationType.EDIT_FUND -> "✏️"
        OperationType.BUY -> "📥"
        OperationType.SELL -> "📤"
        OperationType.REBALANCE -> "⚖️"
        OperationType.ADD_CASH -> "💰"
        OperationType.WITHDRAW_CASH -> "💸"
        OperationType.EDIT_HOLDING -> "📊"
        OperationType.EDIT_COST -> "💵"
        OperationType.EDIT_TARGET_RATIO -> "🎯"
        OperationType.EDIT_ASSET_TYPE -> "🏷️"
        OperationType.CLEAR_DATA -> "🗑️"
        OperationType.EXPORT_DATA -> "📤"
        OperationType.IMPORT_DATA -> "📥"
        OperationType.SETTINGS_CHANGE -> "⚙️"
        OperationType.VIEW_CHART -> "📈"
        OperationType.VIEW_DETAIL -> "👁️"
        OperationType.OTHER -> "📝"
    }
}

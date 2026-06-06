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
    // 基金操作
    ADD_FUND,           // 添加基金
    DELETE_FUND,        // 删除基金
    EDIT_FUND,          // 编辑基金
    
    // 交易操作
    BUY,                // 买入
    SELL,               // 卖出
    REBALANCE,          // 再平衡
    ADD_CASH,           // 添加资金
    WITHDRAW_CASH,      // 取出资金
    EDIT_HOLDING,       // 修改持仓
    EDIT_COST,          // 修改成本
    EDIT_TARGET_RATIO,  // 修改目标比例
    EDIT_ASSET_TYPE,    // 修改资产类型
    
    // 数据操作
    CLEAR_DATA,         // 清空数据
    EXPORT_DATA,        // 导出数据
    IMPORT_DATA,        // 导入数据
    
    // 设置操作
    SETTINGS_CHANGE,    // 设置变更
    
    // 导航操作
    PAGE_VIEW,          // 查看页面
    PAGE_SWITCH,        // 切换页面
    
    // 交互操作
    BUTTON_CLICK,       // 点击按钮
    ITEM_CLICK,         // 点击列表项
    CHART_INTERACTION,  // 图表交互
    
    // 系统操作
    SEARCH,             // 搜索
    FILTER,             // 筛选
    SORT,               // 排序
    REFRESH,            // 刷新
    EXPAND_COLLAPSE,    // 展开/折叠
    
    // 其他
    OTHER               // 其他操作
}

fun OperationType.toDisplayName(): String {
    return when (this) {
        // 基金操作
        OperationType.ADD_FUND -> "添加基金"
        OperationType.DELETE_FUND -> "删除基金"
        OperationType.EDIT_FUND -> "编辑基金"
        
        // 交易操作
        OperationType.BUY -> "买入"
        OperationType.SELL -> "卖出"
        OperationType.REBALANCE -> "再平衡"
        OperationType.ADD_CASH -> "添加资金"
        OperationType.WITHDRAW_CASH -> "取出资金"
        OperationType.EDIT_HOLDING -> "修改持仓"
        OperationType.EDIT_COST -> "修改成本"
        OperationType.EDIT_TARGET_RATIO -> "修改目标比例"
        OperationType.EDIT_ASSET_TYPE -> "修改资产类型"
        
        // 数据操作
        OperationType.CLEAR_DATA -> "清空数据"
        OperationType.EXPORT_DATA -> "导出数据"
        OperationType.IMPORT_DATA -> "导入数据"
        
        // 设置操作
        OperationType.SETTINGS_CHANGE -> "设置变更"
        
        // 导航操作
        OperationType.PAGE_VIEW -> "查看页面"
        OperationType.PAGE_SWITCH -> "切换页面"
        
        // 交互操作
        OperationType.BUTTON_CLICK -> "点击按钮"
        OperationType.ITEM_CLICK -> "点击项目"
        OperationType.CHART_INTERACTION -> "图表交互"
        
        // 系统操作
        OperationType.SEARCH -> "搜索"
        OperationType.FILTER -> "筛选"
        OperationType.SORT -> "排序"
        OperationType.REFRESH -> "刷新"
        OperationType.EXPAND_COLLAPSE -> "展开/折叠"
        
        // 其他
        OperationType.OTHER -> "其他操作"
    }
}

fun OperationType.toIcon(): String {
    return when (this) {
        // 基金操作 - 使用基金相关图标
        OperationType.ADD_FUND -> "🏦"
        OperationType.DELETE_FUND -> "🗑️"
        OperationType.EDIT_FUND -> "✏️"

        // 交易操作 - 使用交易相关图标
        OperationType.BUY -> "📥"
        OperationType.SELL -> "📤"
        OperationType.REBALANCE -> "⚖️"
        OperationType.ADD_CASH -> "💰"
        OperationType.WITHDRAW_CASH -> "💸"
        OperationType.EDIT_HOLDING -> "📊"
        OperationType.EDIT_COST -> "💵"
        OperationType.EDIT_TARGET_RATIO -> "🎯"
        OperationType.EDIT_ASSET_TYPE -> "🏷️"

        // 数据操作 - 使用数据相关图标
        OperationType.CLEAR_DATA -> "🧹"
        OperationType.EXPORT_DATA -> "📤"
        OperationType.IMPORT_DATA -> "📥"

        // 设置操作
        OperationType.SETTINGS_CHANGE -> "⚙️"

        // 导航操作 - 使用导航相关图标
        OperationType.PAGE_VIEW -> "👁️"
        OperationType.PAGE_SWITCH -> "🔄"

        // 交互操作 - 使用交互相关图标
        OperationType.BUTTON_CLICK -> "👆"
        OperationType.ITEM_CLICK -> "📋"
        OperationType.CHART_INTERACTION -> "📈"

        // 系统操作
        OperationType.SEARCH -> "🔍"
        OperationType.FILTER -> "🔖"
        OperationType.SORT -> "📊"
        OperationType.REFRESH -> "🔄"
        OperationType.EXPAND_COLLAPSE -> "📂"

        // 其他
        OperationType.OTHER -> "📝"
    }
}

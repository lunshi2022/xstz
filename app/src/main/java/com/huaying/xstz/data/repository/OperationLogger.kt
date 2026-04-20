package com.huaying.xstz.data.repository

import com.huaying.xstz.data.entity.OperationLog
import com.huaying.xstz.data.entity.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 操作日志记录器
 * 用于全局记录用户操作，支持异步记录不阻塞UI
 */
object OperationLogger {
    private var repository: OperationLogRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    fun init(repository: OperationLogRepository) {
        this.repository = repository
    }
    
    /**
     * 记录操作日志
     */
    fun log(
        type: OperationType,
        title: String,
        description: String = "",
        targetId: Long? = null,
        targetName: String? = null
    ) {
        val repo = repository ?: return
        scope.launch {
            try {
                repo.logOperation(type, title, description, targetId, targetName)
            } catch (e: Exception) {
                // 记录失败不影响主流程
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 记录页面查看
     */
    fun logPageView(pageName: String, extraInfo: String = "") {
        log(
            type = OperationType.VIEW_DETAIL,
            title = "查看页面",
            description = if (extraInfo.isNotEmpty()) "$pageName - $extraInfo" else pageName
        )
    }
    
    /**
     * 记录点击操作
     */
    fun logClick(actionName: String, target: String = "") {
        log(
            type = OperationType.OTHER,
            title = "点击: $actionName",
            description = if (target.isNotEmpty()) "目标: $target" else ""
        )
    }
    
    /**
     * 记录按钮点击
     */
    fun logButtonClick(buttonName: String, location: String = "") {
        log(
            type = OperationType.OTHER,
            title = "点击按钮: $buttonName",
            description = if (location.isNotEmpty()) "位置: $location" else ""
        )
    }
    
    /**
     * 记录列表项点击
     */
    fun logItemClick(itemName: String, itemType: String = "") {
        log(
            type = OperationType.VIEW_DETAIL,
            title = "查看: $itemName",
            description = if (itemType.isNotEmpty()) "类型: $itemType" else ""
        )
    }
    
    /**
     * 记录设置变更
     */
    fun logSettingChange(settingName: String, oldValue: String, newValue: String) {
        log(
            type = OperationType.SETTINGS_CHANGE,
            title = "修改设置: $settingName",
            description = "$oldValue → $newValue"
        )
    }
    
    /**
     * 记录底部导航切换
     */
    fun logNavigationSwitch(from: String, to: String) {
        log(
            type = OperationType.OTHER,
            title = "切换页面",
            description = "$from → $to"
        )
    }
    
    /**
     * 记录图表交互
     */
    fun logChartInteraction(action: String, chartType: String = "") {
        log(
            type = OperationType.VIEW_CHART,
            title = "图表操作: $action",
            description = if (chartType.isNotEmpty()) "图表类型: $chartType" else ""
        )
    }
    
    /**
     * 记录搜索操作
     */
    fun logSearch(keyword: String, searchType: String = "") {
        log(
            type = OperationType.OTHER,
            title = "搜索",
            description = if (searchType.isNotEmpty()) "类型: $searchType, 关键词: $keyword" else "关键词: $keyword"
        )
    }
    
    /**
     * 记录刷新操作
     */
    fun logRefresh(refreshType: String = "") {
        log(
            type = OperationType.OTHER,
            title = "刷新数据",
            description = if (refreshType.isNotEmpty()) "类型: $refreshType" else ""
        )
    }
    
    /**
     * 记录展开/折叠操作
     */
    fun logExpandCollapse(itemName: String, isExpand: Boolean) {
        log(
            type = OperationType.OTHER,
            title = if (isExpand) "展开" else "折叠",
            description = itemName
        )
    }
    
    /**
     * 记录排序操作
     */
    fun logSort(sortBy: String, sortOrder: String = "") {
        log(
            type = OperationType.OTHER,
            title = "排序",
            description = "按 $sortBy ${if (sortOrder.isNotEmpty()) "($sortOrder)" else ""}"
        )
    }
    
    /**
     * 记录筛选操作
     */
    fun logFilter(filterType: String, filterValue: String = "") {
        log(
            type = OperationType.OTHER,
            title = "筛选",
            description = if (filterValue.isNotEmpty()) "$filterType: $filterValue" else filterType
        )
    }
    
    /**
     * 记录对话框操作
     */
    fun logDialog(dialogName: String, action: String) {
        log(
            type = OperationType.OTHER,
            title = "对话框: $dialogName",
            description = "操作: $action"
        )
    }
    
    /**
     * 记录输入操作
     */
    fun logInput(inputField: String, inputType: String = "文本") {
        log(
            type = OperationType.OTHER,
            title = "输入: $inputField",
            description = "类型: $inputType"
        )
    }
    
    /**
     * 记录滑动操作
     */
    fun logScroll(location: String, direction: String) {
        log(
            type = OperationType.OTHER,
            title = "滑动",
            description = "位置: $location, 方向: $direction"
        )
    }
    
    /**
     * 记录长按操作
     */
    fun logLongPress(target: String, location: String = "") {
        log(
            type = OperationType.OTHER,
            title = "长按: $target",
            description = if (location.isNotEmpty()) "位置: $location" else ""
        )
    }
    
    /**
     * 记录返回操作
     */
    fun logBack(from: String) {
        log(
            type = OperationType.OTHER,
            title = "返回",
            description = "从: $from"
        )
    }
}

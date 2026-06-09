package com.huaying.xstz.ui.navigation

/**
 * 路由常量集中管理
 */
object Route {
    // 底部导航页
    const val HOME = "home"
    const val CHARTS = "charts"
    const val REBALANCE = "rebalance"
    const val SETTINGS = "settings"

    // 子页面
    const val ADD_FUND = "addFund"
    const val TARGET_ALLOCATION = "targetAllocation"
    const val OPERATION_LOG = "operationLog"
    const val ABOUT = "about"
    const val GUIDE = "guide"

    // 带参数的子页面（路由模式，用于NavHost注册）
    const val FUND_DETAIL = "fundDetail/{fundId}"
    const val TRANSACTION_HISTORY = "transactionHistory/{fundId}"

    // 构建带参数的路由（用于navigate调用）
    fun fundDetail(fundId: Long) = "fundDetail/$fundId"
    fun transactionHistory(fundId: Long) = "transactionHistory/$fundId"

    // 所有底部导航路由
    val MAIN_ROUTES = listOf(HOME, CHARTS, REBALANCE, SETTINGS)
}

/**
 * 导航方向枚举
 */
enum class NavigationDirection {
    FORWARD,   // 向前（从左到右）
    BACKWARD,  // 向后（从右到左）
    NONE       // 无方向（非底部导航切换）
}

/**
 * 根据路由判断导航方向
 *
 * @param fromRoute 来源路由
 * @param toRoute 目标路由
 * @param navItemsOrder 导航项顺序列表
 * @return 导航方向
 */
fun getNavigationDirection(
    fromRoute: String?,
    toRoute: String?,
    navItemsOrder: List<String>
): NavigationDirection {
    if (fromRoute == null || toRoute == null) return NavigationDirection.NONE

    // 只在底部导航项之间判断方向
    val fromIndex = navItemsOrder.indexOf(fromRoute)
    val toIndex = navItemsOrder.indexOf(toRoute)

    if (fromIndex == -1 || toIndex == -1) return NavigationDirection.NONE

    return when {
        toIndex > fromIndex -> NavigationDirection.FORWARD
        toIndex < fromIndex -> NavigationDirection.BACKWARD
        else -> NavigationDirection.NONE
    }
}

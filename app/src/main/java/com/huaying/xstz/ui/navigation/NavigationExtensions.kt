package com.huaying.xstz.ui.navigation

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

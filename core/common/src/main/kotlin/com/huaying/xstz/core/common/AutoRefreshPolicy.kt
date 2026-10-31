package com.huaying.xstz.core.common

/**
 * 自动刷新决策。
 *
 * 方案 §5.1「行情刷新」三条硬约束的判定集中在这里，做成纯函数——
 * 不依赖时钟、不依赖协程、不碰网络，因此可以完整穷举测试：
 *
 * 1. **仅交易时段**：午休、收盘、周末、节假日一律不刷。这些时段行情不变，
 *    刷了也是空跑接口、还白白消耗用户流量。
 * 2. **固定频率**：前台按用户设定（3s/5s），由调用方传入 [foregroundIntervalMs]。
 * 3. **后台降频**：应用退到后台后拉长到 [backgroundIntervalMs]，用户看不见时
 *    没必要高频打接口。
 *
 * 注意返回 false 有两种语义：**现在不该刷**（非交易时段）和
 * **还没到下次刷的时间**（间隔未到）。两者调度器都据此"继续等"，
 * 所以一个布尔足够，不必区分。
 */
object AutoRefreshPolicy {

    /**
     * @param status      当前市场状态（来自 [MarketClock]）
     * @param foreground  应用是否前台（来自 [ProcessLifecycleOwner]）
     * @param nowMs       当前墙钟毫秒（注入以便测试，生产用 [System.currentTimeMillis]）
     * @param lastAttemptMs 上一次尝试刷新的墙钟毫秒（0 表示从未尝试，立即刷）
     * @param foregroundIntervalMs 前台刷新间隔
     * @param backgroundIntervalMs 后台降频间隔
     */
    fun shouldRefresh(
        status: MarketStatus,
        foreground: Boolean,
        nowMs: Long,
        lastAttemptMs: Long,
        foregroundIntervalMs: Long,
        backgroundIntervalMs: Long,
    ): Boolean {
        // 非交易时段：无论前后台都不刷
        if (status != MarketStatus.TRADING) return false
        val interval = if (foreground) foregroundIntervalMs else backgroundIntervalMs
        // lastAttemptMs == 0 视为"还没刷过"，立即允许一次，避免冷启动后一直干等
        if (lastAttemptMs == 0L) return true
        return (nowMs - lastAttemptMs) >= interval
    }
}

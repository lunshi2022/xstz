package com.huaying.xstz.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRefreshPolicyTest {

    @Test
    fun `非交易时段一律不刷`() {
        val statuses = listOf(
            MarketStatus.PRE_OPEN,
            MarketStatus.LUNCH_BREAK,
            MarketStatus.CLOSED,
            MarketStatus.WEEKEND,
            MarketStatus.HOLIDAY,
        )
        statuses.forEach { s ->
            assertFalse(
                "状态 $s 不应触发刷新",
                AutoRefreshPolicy.shouldRefresh(
                    status = s,
                    foreground = true,
                    nowMs = 100_000,
                    lastAttemptMs = 0,
                    foregroundIntervalMs = 3000,
                    backgroundIntervalMs = 30_000,
                ),
            )
        }
    }

    @Test
    fun `交易时段-前台-首次立即刷`() {
        assertTrue(
            AutoRefreshPolicy.shouldRefresh(
                status = MarketStatus.TRADING,
                foreground = true,
                nowMs = 100_000,
                lastAttemptMs = 0, // 从未尝试
                foregroundIntervalMs = 3000,
                backgroundIntervalMs = 30_000,
            ),
        )
    }

    @Test
    fun `交易时段-前台-间隔未到不刷`() {
        val last = 100_000L
        assertFalse(
            AutoRefreshPolicy.shouldRefresh(
                status = MarketStatus.TRADING,
                foreground = true,
                nowMs = last + 2999, // 差 1ms 到 3s
                lastAttemptMs = last,
                foregroundIntervalMs = 3000,
                backgroundIntervalMs = 30_000,
            ),
        )
    }

    @Test
    fun `交易时段-前台-间隔到了才刷`() {
        val last = 100_000L
        assertTrue(
            AutoRefreshPolicy.shouldRefresh(
                status = MarketStatus.TRADING,
                foreground = true,
                nowMs = last + 3000,
                lastAttemptMs = last,
                foregroundIntervalMs = 3000,
                backgroundIntervalMs = 30_000,
            ),
        )
    }

    @Test
    fun `交易时段-后台-用降频间隔`() {
        val last = 100_000L
        // 前台间隔 3s 已到，但后台降频 30s 未到 → 不该刷
        assertFalse(
            AutoRefreshPolicy.shouldRefresh(
                status = MarketStatus.TRADING,
                foreground = false,
                nowMs = last + 3000,
                lastAttemptMs = last,
                foregroundIntervalMs = 3000,
                backgroundIntervalMs = 30_000,
            ),
        )
        // 后台降频 30s 到了 → 刷
        assertTrue(
            AutoRefreshPolicy.shouldRefresh(
                status = MarketStatus.TRADING,
                foreground = false,
                nowMs = last + 30_000,
                lastAttemptMs = last,
                foregroundIntervalMs = 3000,
                backgroundIntervalMs = 30_000,
            ),
        )
    }
}

package com.huaying.xstz.ui.assetoverview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交易时段纯决策逻辑单元测试
 * 时钟（SystemClock）与节假日数据源依赖留在公开方法中，此处覆盖分钟数决策边界
 */
class TradingTimeCheckerTest {

    private val checker = TradingTimeChecker()

    // ==================== 市场状态边界（GMT+8 分钟数） ====================

    @Test
    fun marketStatus_beforeMorningOpen() {
        assertEquals("未开盘", checker.marketStatusForTradingDay(9 * 60))          // 09:00
        assertEquals("未开盘", checker.marketStatusForTradingDay(9 * 60 + 14))     // 09:14
    }

    @Test
    fun marketStatus_morningSession() {
        assertEquals("交易中", checker.marketStatusForTradingDay(9 * 60 + 15))     // 09:15 起点（含）
        assertEquals("交易中", checker.marketStatusForTradingDay(10 * 60))         // 10:00
        assertEquals("交易中", checker.marketStatusForTradingDay(11 * 60 + 30))    // 11:30 终点（含）
    }

    @Test
    fun marketStatus_lunchBreak() {
        assertEquals("午间休盘", checker.marketStatusForTradingDay(11 * 60 + 31))  // 11:31
        assertEquals("午间休盘", checker.marketStatusForTradingDay(12 * 60))       // 12:00
        assertEquals("午间休盘", checker.marketStatusForTradingDay(12 * 60 + 59))  // 12:59
    }

    @Test
    fun marketStatus_afternoonSession() {
        assertEquals("交易中", checker.marketStatusForTradingDay(13 * 60))         // 13:00 起点（含）
        assertEquals("交易中", checker.marketStatusForTradingDay(14 * 60 + 30))    // 14:30
        assertEquals("交易中", checker.marketStatusForTradingDay(15 * 60))         // 15:00 终点（含）
    }

    @Test
    fun marketStatus_afterClose() {
        assertEquals("已收盘", checker.marketStatusForTradingDay(15 * 60 + 1))     // 15:01
        assertEquals("已收盘", checker.marketStatusForTradingDay(23 * 60 + 59))    // 23:59
    }

    // ==================== 是否交易时段 ====================

    @Test
    fun tradingTime_sessionBoundaries() {
        assertFalse(checker.tradingTimeForTradingDay(9 * 60 + 14))
        assertTrue(checker.tradingTimeForTradingDay(9 * 60 + 15))
        assertTrue(checker.tradingTimeForTradingDay(11 * 60 + 30))
        assertFalse(checker.tradingTimeForTradingDay(11 * 60 + 31))
        assertFalse(checker.tradingTimeForTradingDay(12 * 60 + 59))
        assertTrue(checker.tradingTimeForTradingDay(13 * 60))
        assertTrue(checker.tradingTimeForTradingDay(15 * 60))
        assertFalse(checker.tradingTimeForTradingDay(15 * 60 + 1))
    }

    @Test
    fun tradingTime_outsideSessions() {
        assertFalse(checker.tradingTimeForTradingDay(0))
        assertFalse(checker.tradingTimeForTradingDay(8 * 60))
        assertFalse(checker.tradingTimeForTradingDay(16 * 60))
        assertFalse(checker.tradingTimeForTradingDay(24 * 60 - 1))
    }
}

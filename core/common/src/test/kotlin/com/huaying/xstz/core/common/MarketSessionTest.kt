package com.huaying.xstz.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MarketSessionTest {

    private fun status(h: Int, m: Int) = MarketSession.statusAt(LocalTime.of(h, m))

    @Test
    fun `九点十四分未开盘`() {
        assertEquals(MarketStatus.PRE_OPEN, status(9, 14))
    }

    @Test
    fun `九点十五分进入交易`() {
        assertEquals(MarketStatus.TRADING, status(9, 15))
    }

    @Test
    fun `十一点半仍算交易中`() {
        assertEquals(MarketStatus.TRADING, status(11, 30))
    }

    @Test
    fun `十一点三十一分进入午休`() {
        assertEquals(MarketStatus.LUNCH_BREAK, status(11, 31))
    }

    @Test
    fun `十二点五十九分仍是午休`() {
        assertEquals(MarketStatus.LUNCH_BREAK, status(12, 59))
    }

    @Test
    fun `十三点恢复交易`() {
        assertEquals(MarketStatus.TRADING, status(13, 0))
    }

    @Test
    fun `十五点整仍算交易中`() {
        assertEquals(MarketStatus.TRADING, status(15, 0))
    }

    @Test
    fun `十五点零一分已收盘`() {
        assertEquals(MarketStatus.CLOSED, status(15, 1))
    }

    @Test
    fun `凌晨属于未开盘`() {
        assertEquals(MarketStatus.PRE_OPEN, status(0, 0))
    }

    @Test
    fun `深夜属于已收盘`() {
        assertEquals(MarketStatus.CLOSED, status(23, 59))
    }

    @Test
    fun `isTrading 与 status 判定一致`() {
        assertTrue(MarketSession.isTrading(LocalTime.of(10, 0)))
        assertFalse(MarketSession.isTrading(LocalTime.of(12, 0)))
        assertFalse(MarketSession.isTrading(LocalTime.of(16, 0)))
    }
}

class MarketClockTest {

    @Test
    fun `周六返回周末休市`() {
        // 2026-10-31 是周六
        val instant = java.time.LocalDateTime.of(2026, 10, 31, 10, 0)
            .atZone(MarketSession.ZONE).toInstant()
        val clock = MarketClock(now = { instant })
        assertEquals(MarketStatus.WEEKEND, clock.currentStatus())
    }

    @Test
    fun `工作日交易时段返回交易中`() {
        // 2026-10-30 是周五
        val instant = java.time.LocalDateTime.of(2026, 10, 30, 10, 0)
            .atZone(MarketSession.ZONE).toInstant()
        val clock = MarketClock(now = { instant })
        assertEquals(MarketStatus.TRADING, clock.currentStatus())
    }

    @Test
    fun `日历标记假日时返回节假日休市`() {
        val instant = java.time.LocalDateTime.of(2026, 10, 30, 10, 0)
            .atZone(MarketSession.ZONE).toInstant()
        val alwaysHoliday = TradingCalendar { false }
        val clock = MarketClock(calendar = alwaysHoliday, now = { instant })
        assertEquals(MarketStatus.HOLIDAY, clock.currentStatus())
    }

    @Test
    fun `周末优先于日历判定`() {
        val instant = java.time.LocalDateTime.of(2026, 10, 31, 10, 0)
            .atZone(MarketSession.ZONE).toInstant()
        val alwaysTrading = TradingCalendar { true }
        val clock = MarketClock(calendar = alwaysTrading, now = { instant })
        // 周六即使日历说"是交易日"，也只应报周末休市
        assertEquals(MarketStatus.WEEKEND, clock.currentStatus())
    }
}

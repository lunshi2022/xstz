package com.huaying.xstz.feature.charts

import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.NetValueSnapshot
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组合维度状态测试。
 *
 * 这些用例锁的是日历与收益走势页**最容易算错**的地方：
 *   1. 区间收益率的分母是首日总资产，不是本金（本金在区间内会因申赎变化）
 *   2. 年化波动要用 dayProfit 算，不能用总资产差值（否则申赎会被当成波动）
 *   3. 日历的月度聚合必须按"年月"过滤，不能只按 dayOfMonth——
 *      跨月时 15 号会有两个
 *   4. 日历的月份独立于区间，翻月不能改区间
 */
class PortfolioStatsTest {

    private fun snap(
        date: String,
        total: Double,
        principal: Double,
        dayProfit: Double,
        stock: Double = 0.0,
        bond: Double = 0.0,
        cash: Double = 0.0,
    ) = NetValueSnapshot(
        epochDay = LocalDate.parse(date).toEpochDay(),
        totalValue = Money.fromYuan(total),
        principal = Money.fromYuan(principal),
        dayProfit = Money.fromYuan(dayProfit),
        stockValue = Money.fromYuan(stock),
        bondValue = Money.fromYuan(bond),
        cashValue = Money.fromYuan(cash),
    )

    @Test
    fun `区间收益率以首日总资产为基数`() {
        // 首日总资产 10000、本金 8000；末日总资产 11000、本金 9000（中途追加）
        // 若拿本金当分母会是 (11000-9000)/9000 = 22.2%，无法解释
        val state = ChartsUiState(
            snapshots = listOf(
                snap("2026-03-02", total = 10_000.0, principal = 8_000.0, dayProfit = 0.0),
                snap("2026-03-03", total = 11_000.0, principal = 9_000.0, dayProfit = 500.0),
            ),
        )
        // 区间累计收益 = 末 profit - 首 profit = (11000-9000) - (10000-8000) = 0
        assertEquals(0.0, state.portfolioProfit.toDouble(), 1.0)
    }

    @Test
    fun `年化波动用当日盈亏而不是总资产差值`() {
        // 三天：净值没变化，只是第二天追加了一倍资金。
        // 用总资产差值算会得到巨大波动；用 dayProfit（全程 0）应得到 0。
        val state = ChartsUiState(
            snapshots = listOf(
                snap("2026-03-02", total = 10_000.0, principal = 10_000.0, dayProfit = 0.0),
                snap("2026-03-03", total = 20_000.0, principal = 20_000.0, dayProfit = 0.0),
                snap("2026-03-04", total = 20_000.0, principal = 20_000.0, dayProfit = 0.0),
            ),
        )
        assertEquals(0.0, state.portfolioVolatility, 1e-9)
    }

    @Test
    fun `日历按月聚合不会把不同月的同一天混在一起`() {
        // 2 月 15 日与 3 月 15 日都存在，切到 3 月时只能看到 3 月那天
        val snaps = listOf(
            snap("2026-02-15", total = 100.0, principal = 100.0, dayProfit = 10.0),
            snap("2026-03-15", total = 200.0, principal = 200.0, dayProfit = 20.0),
        )
        val march = ChartsUiState(
            snapshots = snaps,
            calendarMonthState = YearMonth.of(2026, 3),
        )
        assertEquals(1, march.monthPnl.size)
        assertEquals(20.0, march.monthPnl[15]!!.dayProfit.toYuan(), 1e-6)

        val feb = march.copy(calendarMonthState = YearMonth.of(2026, 2))
        assertEquals(10.0, feb.monthPnl[15]!!.dayProfit.toYuan(), 1e-6)
    }

    @Test
    fun `当月盈亏只统计当月`() {
        val state = ChartsUiState(
            snapshots = listOf(
                snap("2026-03-30", total = 100.0, principal = 100.0, dayProfit = 10.0),
                snap("2026-03-31", total = 100.0, principal = 100.0, dayProfit = -4.0),
                snap("2026-04-01", total = 100.0, principal = 100.0, dayProfit = 99.0),
            ),
            calendarMonthState = YearMonth.of(2026, 3),
        )
        assertEquals(6.0, state.monthProfit / 10_000.0, 1e-6)
        assertEquals(1, state.monthWinDays)
        assertEquals(1, state.monthLossDays)
    }

    @Test
    fun `盈利与亏损天数分别计数`() {
        val state = ChartsUiState(
            snapshots = listOf(
                snap("2026-03-02", 100.0, 100.0, 5.0),
                snap("2026-03-03", 100.0, 100.0, -3.0),
                snap("2026-03-04", 100.0, 100.0, 0.0),
                snap("2026-03-05", 100.0, 100.0, 8.0),
            ),
        )
        assertEquals(2, state.winDays)
        assertEquals(1, state.lossDays)
    }

    @Test
    fun `资产配置只列出有市值的类别`() {
        val state = ChartsUiState(
            snapshots = listOf(
                snap("2026-03-02", 10_000.0, 10_000.0, 0.0, stock = 7_000.0, bond = 3_000.0),
                snap("2026-03-03", 10_000.0, 10_000.0, 0.0, stock = 8_000.0, bond = 2_000.0),
            ),
        )
        val types = state.allocationSeries.map { it.type }
        // 只有股票和债券有市值，商品与现金不出现
        assertEquals(listOf(AssetType.STOCK, AssetType.BOND), types)
    }

    @Test
    fun `配置占比之和为一`() {
        val s = snap(
            "2026-03-02", total = 10_000.0, principal = 10_000.0, dayProfit = 0.0,
            stock = 5_000.0, bond = 3_000.0, cash = 2_000.0,
        )
        val state = ChartsUiState(
            snapshots = listOf(s, s.copy(epochDay = s.epochDay + 1)),
        )
        val sum = AssetType.entries.sumOf { state.latestRatio(it) }
        assertEquals(1.0, sum, 1e-9)
    }

    @Test
    fun `快照不足两天时视为无数据`() {
        val state = ChartsUiState(snapshots = listOf(snap("2026-03-02", 100.0, 100.0, 0.0)))
        assertTrue(!state.hasSnapshot)
    }

    @Test
    fun `最大回撤按总资产计算`() {
        val state = ChartsUiState(
            snapshots = listOf(
                snap("2026-03-02", 10_000.0, 10_000.0, 0.0),
                snap("2026-03-03", 12_000.0, 10_000.0, 2_000.0),
                snap("2026-03-04", 9_000.0, 10_000.0, -3_000.0),
            ),
        )
        // 从 12000 回撤到 9000 = -25%
        assertEquals(-0.25, state.portfolioMaxDrawdown, 1e-6)
    }
}

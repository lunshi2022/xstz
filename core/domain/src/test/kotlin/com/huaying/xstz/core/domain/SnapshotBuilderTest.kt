package com.huaying.xstz.core.domain

import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.InstrumentKind
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快照重算器测试。
 *
 * 这些用例锁的是**最容易算错的三件事**：
 *   1. 卖出后剩余成本要按比例结转（不能变成负数，也不能把已实现收益算进本金）
 *   2. 当日盈亏要逐只算涨跌再相加，不能用总资产的差值（追加资金会被算成盈利）
 *   3. 买入当天没有"昨日净值"，当日盈亏必须是 0
 */
class SnapshotBuilderTest {

    private fun holding(
        id: Long = 1,
        code: String = "000001",
        name: String = "测试基金",
        type: AssetType = AssetType.STOCK,
    ) = Holding(
        fundId = id,
        code = code,
        name = name,
        type = type,
        kind = InstrumentKind.FUND,
        shares = Shares.ZERO,
        cost = Money.ZERO,
        nav = Nav.ZERO,
        prevNav = Nav.ZERO,
        targetRatio = 0.0,
    )

    private fun nav(day: Long, value: Double) = NavPoint(day, Nav.fromDouble(value))

    private fun buy(
        id: Long = 1,
        day: Long,
        shares: Double,
        price: Double,
        fee: Double = 0.0,
    ) = Trade(
        fundId = id,
        type = TradeType.BUY,
        shares = Shares.fromDouble(shares),
        nav = Nav.fromDouble(price),
        fee = Money.fromYuan(fee),
        amount = Money.fromYuan(shares * price),
        epochDay = day,
    )

    private fun sell(
        id: Long = 1,
        day: Long,
        shares: Double,
        price: Double,
        fee: Double = 0.0,
    ) = Trade(
        fundId = id,
        type = TradeType.SELL,
        shares = Shares.fromDouble(shares),
        nav = Nav.fromDouble(price),
        fee = Money.fromYuan(fee),
        amount = Money.fromYuan(shares * price),
        epochDay = day,
    )

    @Test
    fun `买入后总资产等于份额乘净值`() {
        val h = holding()
        val trades = listOf(buy(day = 10, shares = 1000.0, price = 2.0))
        val navs = mapOf(1L to listOf(nav(10, 2.0), nav(11, 2.5)))

        val snaps = SnapshotBuilder.build(listOf(h), trades, navs, 10, 11)

        assertEquals(2, snaps.size)
        // 第 10 天：1000 份 × 2.0 = 2000
        assertEquals(2000.0, snaps[0].totalValue.toYuan(), 1e-6)
        // 第 11 天：1000 份 × 2.5 = 2500
        assertEquals(2500.0, snaps[1].totalValue.toYuan(), 1e-6)
    }

    @Test
    fun `本金等于买入金额含手续费`() {
        val h = holding()
        val trades = listOf(buy(day = 10, shares = 1000.0, price = 2.0, fee = 5.0))
        val navs = mapOf(1L to listOf(nav(10, 2.0)))

        val snaps = SnapshotBuilder.build(listOf(h), trades, navs, 10, 10)

        assertEquals(2005.0, snaps[0].principal.toYuan(), 1e-6)
        assertEquals(-5.0, snaps[0].profit.toYuan(), 1e-6)
    }

    @Test
    fun `买入当天当日盈亏为零`() {
        val h = holding()
        val trades = listOf(buy(day = 10, shares = 1000.0, price = 2.0))
        val navs = mapOf(1L to listOf(nav(10, 2.0), nav(11, 2.5)))

        val snaps = SnapshotBuilder.build(listOf(h), trades, navs, 10, 11)

        // 建仓当天没有"昨日净值"可比，盈亏是 0
        assertEquals(0.0, snaps[0].dayProfit.toYuan(), 1e-6)
        // 次日：1000 × (2.5 - 2.0) = 500
        assertEquals(500.0, snaps[1].dayProfit.toYuan(), 1e-6)
    }

    @Test
    fun `卖出后剩余成本按比例结转`() {
        val h = holding()
        val trades = listOf(
            buy(day = 10, shares = 1000.0, price = 2.0),
            sell(day = 11, shares = 500.0, price = 3.0),
        )
        val navs = mapOf(1L to listOf(nav(11, 3.0)))

        val snaps = SnapshotBuilder.build(listOf(h), trades, navs, 11, 11)

        // 原成本 2000，卖掉一半结转 1000，剩 1000
        assertEquals(1000.0, snaps[0].principal.toYuan(), 1e-6)
        // 市值 500 份 × 3.0 = 1500
        assertEquals(1500.0, snaps[0].totalValue.toYuan(), 1e-6)
        // 持仓浮盈 = 1500 - 1000 = 500
        assertEquals(500.0, snaps[0].profit.toYuan(), 1e-6)
    }

    @Test
    fun `全部卖出后成本为零而不是负数`() {
        val h = holding()
        val trades = listOf(
            buy(day = 10, shares = 1000.0, price = 2.0),
            sell(day = 11, shares = 1000.0, price = 3.0),
        )
        val navs = mapOf(1L to listOf(nav(11, 3.0)))

        val snaps = SnapshotBuilder.build(listOf(h), trades, navs, 11, 11)

        assertEquals(0.0, snaps[0].principal.toYuan(), 1e-6)
        assertEquals(0.0, snaps[0].totalValue.toYuan(), 1e-6)
        // 当日盈亏要算进卖出兑现的 1000：卖在 3.0、成本 2.0，1000 份赚 1000。
        // 这笔钱当天就兑现了，日历上那天必须是红的。
        assertEquals(1000.0, snaps[0].dayProfit.toYuan(), 1e-6)
    }

    @Test
    fun `卖出当日盈亏包含已实现部分`() {
        val h = holding()
        val trades = listOf(
            buy(day = 10, shares = 1000.0, price = 2.0),
            sell(day = 11, shares = 1000.0, price = 3.0),
        )
        // 第 11 天净值 3.0，第 10 天 2.0
        val navs = mapOf(1L to listOf(nav(10, 2.0), nav(11, 3.0)))

        val snaps = SnapshotBuilder.build(listOf(h), trades, navs, 10, 11)

        // 第 11 天：持仓已清空，未实现部分为 0，但已实现 1000
        assertEquals(1000.0, snaps[1].dayProfit.toYuan(), 1e-6)
    }

    @Test
    fun `卖出手续费从已实现盈亏中扣除`() {
        val h = holding()
        val trades = listOf(
            buy(day = 10, shares = 1000.0, price = 2.0, fee = 10.0),
            sell(day = 11, shares = 1000.0, price = 3.0, fee = 20.0),
        )
        val navs = mapOf(1L to listOf(nav(11, 3.0)))

        val snaps = SnapshotBuilder.build(listOf(h), trades, navs, 11, 11)

        // 卖出 3000，结转成本 2010，手续费 20 → 已实现 = 3000 - 2010 - 20 = 970
        assertEquals(970.0, snaps[0].dayProfit.toYuan(), 1e-6)
    }

    @Test
    fun `当日盈亏不把追加资金算成盈利`() {
        // 第一天持仓 1000 份 @2.0 = 2000
        // 第二天追加买入 1000 份 @2.0（本金变成 4000，市值变成 4000）
        // 净值没变，市值翻倍——如果用差值法，会显示"赚了 2000"，实际是用户自己充了 2000
        val h = holding()
        val trades = listOf(
            buy(day = 10, shares = 1000.0, price = 2.0),
            buy(day = 11, shares = 1000.0, price = 2.0),
        )
        val navs = mapOf(1L to listOf(nav(10, 2.0), nav(11, 2.0)))

        val snaps = SnapshotBuilder.build(listOf(h), trades, navs, 10, 11)

        // 当日盈亏必须是 0（净值没变）
        assertEquals(0.0, snaps[1].dayProfit.toYuan(), 1e-6)
        // 而总资产确实翻倍了——这正是差值法会算错的地方
        assertEquals(4000.0, snaps[1].totalValue.toYuan(), 1e-6)
        assertEquals(4000.0, snaps[1].principal.toYuan(), 1e-6)
    }

    @Test
    fun `多只持仓的当日盈亏逐只累加`() {
        val h1 = holding(id = 1, code = "AAA")
        val h2 = holding(id = 2, code = "BBB")
        val trades = listOf(
            buy(id = 1, day = 10, shares = 1000.0, price = 2.0),
            buy(id = 2, day = 10, shares = 500.0, price = 4.0),
        )
        val navs = mapOf(
            1L to listOf(nav(10, 2.0), nav(11, 2.2)),
            2L to listOf(nav(10, 4.0), nav(11, 3.8)),
        )

        val snaps = SnapshotBuilder.build(listOf(h1, h2), trades, navs, 10, 11)

        // 第一只涨 0.2 × 1000 = +200；第二只跌 0.2 × 500 = -100；合计 +100
        assertEquals(100.0, snaps[1].dayProfit.toYuan(), 1e-6)
        assertEquals(2200.0 + 1900.0, snaps[1].totalValue.toYuan(), 1e-6)
    }

    @Test
    fun `四类资产市值分别归集`() {
        val stock = holding(id = 1, type = AssetType.STOCK)
        val bond = holding(id = 2, type = AssetType.BOND)
        val commodity = holding(id = 3, type = AssetType.COMMODITY)
        val cash = holding(id = 4, type = AssetType.CASH)
        val trades = listOf(
            buy(id = 1, day = 10, shares = 100.0, price = 10.0),
            buy(id = 2, day = 10, shares = 100.0, price = 20.0),
            buy(id = 3, day = 10, shares = 100.0, price = 30.0),
            buy(id = 4, day = 10, shares = 100.0, price = 40.0),
        )
        val navs = mapOf(
            1L to listOf(nav(10, 10.0)),
            2L to listOf(nav(10, 20.0)),
            3L to listOf(nav(10, 30.0)),
            4L to listOf(nav(10, 40.0)),
        )

        val snap = SnapshotBuilder.build(
            listOf(stock, bond, commodity, cash), trades, navs, 10, 10
        ).first()

        assertEquals(1000.0, snap.stockValue.toYuan(), 1e-6)
        assertEquals(2000.0, snap.bondValue.toYuan(), 1e-6)
        assertEquals(3000.0, snap.commodityValue.toYuan(), 1e-6)
        assertEquals(4000.0, snap.cashValue.toYuan(), 1e-6)
        assertEquals(10000.0, snap.totalValue.toYuan(), 1e-6)
        // 占比可算
        assertEquals(0.1, snap.ratioOf(AssetType.STOCK), 1e-6)
    }

    @Test
    fun `空持仓返回空列表`() {
        assertTrue(SnapshotBuilder.build(emptyList(), emptyList(), emptyMap(), 0, 10).isEmpty())
    }

    @Test
    fun `无净值时跳过该持仓而不是算成零市值`() {
        // 持仓有交易但完全没有净值记录——此时无法算市值
        val h = holding()
        val trades = listOf(buy(day = 10, shares = 1000.0, price = 2.0))

        val snaps = SnapshotBuilder.build(listOf(h), trades, emptyMap(), 10, 10)

        // 快照仍然生成（交易日由交易日期决定），但市值为 0 而不是被当成"亏光了"
        assertEquals(1, snaps.size)
        assertEquals(0.0, snaps[0].totalValue.toYuan(), 1e-6)
    }

    @Test
    fun `今日快照用当前持仓直接算`() {
        val h = holding().copy(
            shares = Shares.fromDouble(1000.0),
            cost = Money.fromYuan(2000.0),
            nav = Nav.fromDouble(2.5),
            prevNav = Nav.fromDouble(2.4),
        )

        val snap = SnapshotBuilder.buildToday(listOf(h), 20)

        assertEquals(20L, snap.epochDay)
        assertEquals(2500.0, snap.totalValue.toYuan(), 1e-6)
        assertEquals(2000.0, snap.principal.toYuan(), 1e-6)
        assertEquals(100.0, snap.dayProfit.toYuan(), 1e-6)
        assertEquals(500.0, snap.profit.toYuan(), 1e-6)
        assertEquals(0.25, snap.profitRate, 1e-6)
    }
}

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
 * 新增交易类型（再平衡 / 追加资金 / 取出资金）的重放测试。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么这组测试必须存在
 *
 * 方案阶段 3 第 5 项要求「五类流水」。新增的三种类型如果只是
 * 加上枚举值、`when` 里塞个 `else -> pos` 蒙混过关，编译能过、
 * 界面能显示，但**持仓与收益会全错**：
 *   * 再平衡被当成"无操作" → 调仓后份额不变，市值直接算错
 *   * 追加资金被当成买入 → 凭空多出一份持仓
 *
 * 这类错误不会崩溃，只会让数字慢慢偏掉，是最难发现的一类缺陷。
 * ══════════════════════════════════════════════════════════════════
 */
/** Money 没有 toDouble()（避免隐式精度转换），测试里用这个只读扩展取值比较 */
private val Money.yuan: Double get() = micros / 10_000.0

class TradeTypeReplayTest {

    private fun holding() = Holding(
        fundId = 1,
        code = "000001",
        name = "测试标的",
        type = AssetType.STOCK,
        kind = InstrumentKind.STOCK,
        shares = Shares.ZERO,
        cost = Money.ZERO,
        nav = Nav.ZERO,
        prevNav = Nav.ZERO,
        targetRatio = 1.0,
    )

    private fun trade(
        type: TradeType,
        day: Long,
        shares: Double,
        price: Double,
        fee: Double = 0.0,
    ) = Trade(
        fundId = 1,
        type = type,
        shares = Shares.fromDouble(shares),
        nav = Nav.fromDouble(price),
        // 减仓时 amount 取份额与价格的乘积（负份额时自然为负），
        // 与「shares 带符号」的语义约定一致
        amount = Money.fromYuan(shares * price),
        fee = Money.fromYuan(fee),
        epochDay = day,
    )

    /** 再平衡加仓：份额与成本都应增加，等价于一次买入 */
    @Test
    fun `再平衡加仓等价于买入`() {
        val trades = listOf(trade(TradeType.REBALANCE, day = 100, shares = 100.0, price = 2.0))
        val snap = SnapshotBuilder.build(
            holdings = listOf(holding()),
            navs = mapOf(1L to listOf(NavPoint(100, Nav.fromDouble(2.0)))),
            trades = trades,
            fromDay = 100,
            toDay = 100,
        ).first()

        // 份额 100 × 净值 2.0 = 200
        assertEquals(200.0, snap.totalValue.yuan, 0.01)
        // 本金 = 成本 = 200
        assertEquals(200.0, snap.principal.yuan, 0.01)
    }

    /** 再平衡减仓：份额减少、成本按比例结转，与卖出同规则 */
    @Test
    fun `再平衡减仓按比例结转成本`() {
        val trades = listOf(
            // 先建仓 200 份 @ 2.0 = 成本 400
            trade(TradeType.BUY, day = 100, shares = 200.0, price = 2.0),
            // 再平衡减仓 80 份（份额为负表示减仓）
            trade(TradeType.REBALANCE, day = 101, shares = -80.0, price = 2.5),
        )
        val snaps = SnapshotBuilder.build(
            holdings = listOf(holding()),
            navs = mapOf(1L to listOf(
                NavPoint(100, Nav.fromDouble(2.0)),
                NavPoint(101, Nav.fromDouble(2.5)),
            )),
            trades = trades,
            fromDay = 100,
            toDay = 101,
        )
        val last = snaps.last()

        // 剩余份额 120，净值 2.5 → 市值 300
        assertEquals(300.0, last.totalValue.yuan, 0.01)

        // 成本结转：400 × (80/200) = 160，剩余成本 240
        // 本金按剩余成本计，不应把已实现收益算进本金
        assertEquals(240.0, last.principal.yuan, 0.01)
    }

    /**
     * 追加资金 / 取出资金：作用于**现金持仓**，不改变标的份额。
     *
     * ═══════════════════════════════════════════════════════════
     * 这里更正了一个建模误解（记录在此以免重犯）
     *
     * 本项目没有独立的"现金池"字段——现金是以 **AssetType.CASH 的持仓**
     * 表示的，净值恒为 1.0（见 HoldingRepositories 的行情刷新分支：
     * `if (entity.type == AssetType.CASH.name) updateQuote(id, 10_000L, ...)`）。
     *
     * 因此 cashValue 只可能来自 CASH 持仓的市值，
     * **不会**从交易流水的 cashFlow 汇总而来。SnapshotBuilder 里
     * 那句"现金的增减由外层按 day 汇总 cashFlow 处理"的注释是过时的，
     * 实际实现并没这样做（也不需要）。
     *
     * 结论：ADD_CASH/WITHDRAW 在 applyTrade 里"不改变标的持仓"是**正确**的，
     * 因为它们的语义是"动现金持仓"，而现金持仓自己也会有对应的流水
     * （或由调用方直接改 CASH 持仓的份额）。
     * ═══════════════════════════════════════════════════════════
     */
    @Test
    fun `追加资金不改变标的份额`() {
        val trades = listOf(
            trade(TradeType.BUY, day = 100, shares = 100.0, price = 2.0),
            Trade(
                fundId = 1,
                type = TradeType.ADD_CASH,
                shares = Shares.ZERO,
                nav = Nav.ZERO,
                amount = Money.fromYuan(500.0),
                epochDay = 101,
            ),
        )
        val snaps = SnapshotBuilder.build(
            holdings = listOf(holding()),
            trades = trades,
            navs = mapOf(1L to listOf(
                NavPoint(100, Nav.fromDouble(2.0)),
                NavPoint(101, Nav.fromDouble(2.0)),
            )),
            fromDay = 100,
            toDay = 101,
        )
        val last = snaps.last()

        // 份额仍是 100，净值 2.0 → 市值 200。
        // 若 ADD_CASH 被误当成买入，这里会变成 300（份额 150）
        assertEquals(200.0, last.totalValue.yuan, 0.01)
    }

    /** 现金持仓本身的市值能正确进入 cashValue（这验证的是 CASH 分支，不是流水） */
    @Test
    fun `现金持仓市值计入现金类`() {
        val cashHolding = holding().copy(
            fundId = 2,
            code = "CASH",
            name = "现金",
            type = AssetType.CASH,
            kind = InstrumentKind.CASH,
        )
        val snaps = SnapshotBuilder.build(
            holdings = listOf(cashHolding),
            trades = listOf(
                // 现金持仓：净值恒 1.0，买入 500 份 = 500 元
                Trade(
                    fundId = 2,
                    type = TradeType.BUY,
                    shares = Shares.fromDouble(500.0),
                    nav = Nav.fromDouble(1.0),
                    amount = Money.fromYuan(500.0),
                    epochDay = 100,
                )
            ),
            navs = mapOf(2L to listOf(NavPoint(100, Nav.fromDouble(1.0)))),
            fromDay = 100,
            toDay = 100,
        ).first()

        assertEquals(500.0, snaps.cashValue.yuan, 0.01)
        assertEquals(500.0, snaps.totalValue.yuan, 0.01)
    }

    /**
     * 五类流水的 displayName 必须各不相同。
     *
     * 旧版 P2-10 的缺陷就是 `ADD_FUNDS` 被映射成"买入"，
     * 用户看到错误类型。这条测试防止新增类型后出现显示名冲突。
     */
    /**
     * 五类流水的 displayName 必须各不相同。
     *
     * 旧版 P2-10 的缺陷就是 `ADD_FUNDS` 被映射成"买入"，
     * 用户看到错误类型。这条测试防止新增类型后出现显示名冲突。
     */
    @Test
    fun `六类交易显示名互不重复`() {
        val names = TradeType.entries.map { it.displayName }
        assertEquals(
            "显示名重复会让用户无法区分交易类型：$names",
            names.size,
            names.toSet().size,
        )
    }

    /** 现金类交易的判定必须准确，下游靠它决定是否更新持仓 */
    @Test
    fun `现金类交易判定正确`() {
        assertEquals(
            setOf(TradeType.ADD_CASH, TradeType.WITHDRAW),
            TradeType.entries.filter { it.isCashFlow }.toSet(),
        )
    }
}

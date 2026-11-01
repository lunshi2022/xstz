package com.huaying.xstz.core.domain

import com.huaying.xstz.core.model.ActionType
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.RebalanceStrategy
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.ThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RebalanceEngineTest {

    private fun holding(id: Long, valueYuan: Double, target: Double, type: AssetType = AssetType.STOCK) =
        Holding(
            fundId = id,
            code = "00000$id",
            name = "基金$id",
            type = type,
            shares = Shares.fromDouble(valueYuan),
            cost = Money.fromYuan(valueYuan),
            nav = Nav.fromDouble(1.0),
            prevNav = Nav.fromDouble(1.0),
            targetRatio = target,
        )

    @Test
    fun `空组合产生空方案`() {
        val plan = RebalanceEngine.plan(emptyList(), RebalanceStrategy.BALANCED)
        assertTrue(plan.items.isEmpty())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `完全对齐时全部持有不动`() {
        val list = listOf(holding(1, 5_000.0, 0.5), holding(2, 5_000.0, 0.5))
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.BALANCED, thresholdPct = 0.01)
        assertTrue(plan.items.all { it.action == ActionType.HOLD })
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `均衡策略买卖金额相等`() {
        val list = listOf(holding(1, 8_000.0, 0.5), holding(2, 2_000.0, 0.5))
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.BALANCED, thresholdPct = 0.05)
        assertEquals(plan.totalBuy.micros, plan.totalSell.micros)
        assertEquals(0L, plan.cashDelta.micros)
    }

    @Test
    fun `均衡策略下超配卖出低配买入`() {
        val list = listOf(holding(1, 8_000.0, 0.5), holding(2, 2_000.0, 0.5))
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.BALANCED, thresholdPct = 0.05)
        val sell = plan.items.first { it.fundId == 1L }
        val buy = plan.items.first { it.fundId == 2L }
        assertEquals(ActionType.SELL, sell.action)
        assertEquals(ActionType.BUY, buy.action)
        // 目标各 5000，差额各 3000
        assertEquals(-3_000.0, sell.delta.toYuan(), 0.02)
        assertEquals(3_000.0, buy.delta.toYuan(), 0.02)
    }

    @Test
    fun `仅卖出策略不产生买入`() {
        val list = listOf(holding(1, 8_000.0, 0.5), holding(2, 2_000.0, 0.5))
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.SELL_ONLY, thresholdPct = 0.05)
        assertTrue(plan.items.none { it.action == ActionType.BUY })
        assertEquals(3_000.0, plan.totalSell.toYuan(), 0.02)
        assertTrue(plan.cashDelta.isZero.not())
    }

    @Test
    fun `仅卖出策略不动现金`() {
        val list = listOf(
            holding(1, 8_000.0, 0.4),
            holding(2, 2_000.0, 0.4),
            holding(3, 0.0, 0.2, type = AssetType.CASH),
        )
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.SELL_ONLY, thresholdPct = 0.01)
        val cash = plan.items.first { it.fundId == 3L }
        assertEquals(ActionType.HOLD, cash.action)
    }

    @Test
    fun `仅卖出策略也会卖超配的现金类资产`() {
        // 复现样本场景：货币基金（CASH 类型）占 40%，目标 30%，明显超配。
        // SELL_ONLY 必须与 BALANCED 一致地把它卖掉，而不是报"无偏离"。
        val list = listOf(
            holding(1, 6_000.0, 0.30),
            holding(2, 6_000.0, 0.30),
            holding(3, 8_000.0, 0.30, type = AssetType.CASH),
        )
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.SELL_ONLY, thresholdPct = 0.01)
        val cash = plan.items.first { it.fundId == 3L }
        assertEquals(ActionType.SELL, cash.action)
        assertTrue(plan.totalSell.micros > 0L)
        // 只卖不买：现金净流入为正
        assertTrue(plan.cashDelta.micros > 0L)
    }

    @Test
    fun `完全对齐策略允许买入大于卖出`() {
        val list = listOf(holding(1, 9_000.0, 0.5), holding(2, 1_000.0, 0.5))
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.FULL, thresholdPct = 0.05)
        // 目标各 5000：卖 4000 买 4000，仍然相等（因为总额不变）
        assertEquals(4_000.0, plan.totalSell.toYuan(), 0.02)
        assertEquals(4_000.0, plan.totalBuy.toYuan(), 0.02)
    }

    @Test
    fun `低于阈值的不动手`() {
        // 目标 50/50，实际 50.5/49.5，偏离 0.5 个百分点 < 阈值 5%
        val list = listOf(holding(1, 5_050.0, 0.5), holding(2, 4_950.0, 0.5))
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.BALANCED, thresholdPct = 0.05)
        assertTrue(plan.items.all { it.action == ActionType.HOLD })
    }

    @Test
    fun `目标之和超过一时按比例压缩`() {
        val list = listOf(holding(1, 5_000.0, 0.8), holding(2, 5_000.0, 0.4))
        val targets = RebalanceEngine.normalizeTargets(list)
        assertEquals(1.0, targets.values.sum(), 1e-9)
        assertEquals(0.8 / 1.2, targets.getValue(1L), 1e-9)
    }

    @Test
    fun `目标之和不足一时保留原值`() {
        val list = listOf(holding(1, 5_000.0, 0.4), holding(2, 5_000.0, 0.4))
        val targets = RebalanceEngine.normalizeTargets(list)
        assertEquals(0.4, targets.getValue(1L), 1e-9)
        assertEquals(0.8, targets.values.sum(), 1e-9)
    }

    @Test
    fun `负目标被压到零`() {
        val list = listOf(holding(1, 1_000.0, -0.2), holding(2, 1_000.0, 0.5))
        val targets = RebalanceEngine.normalizeTargets(list)
        assertEquals(0.0, targets.getValue(1L), 1e-9)
    }

    @Test
    fun `买入差额换份额向下取整`() {
        val delta = Money.fromYuan(1_000.0)
        val nav = Nav.fromDouble(3.3333)
        val shares = RebalanceEngine.sharesToTrade(delta, nav)
        // 1000 / 3.3333 = 300.003 → 向下取整 300.00
        assertEquals(300.0, shares.toDouble(), 0.001)
        assertTrue(shares.toDouble() * nav.toDouble() <= 1_000.01)
    }

    @Test
    fun `净值为零时不产生份额`() {
        assertEquals(0.0, RebalanceEngine.sharesToTrade(Money.fromYuan(100.0), Nav.ZERO).toDouble(), 0.0)
    }

    @Test
    fun `相对阈值模式下高偏向项也能被识别`() {
        val list = listOf(holding(1, 9_000.0, 0.5), holding(2, 1_000.0, 0.5))
        val plan = RebalanceEngine.plan(
            list,
            RebalanceStrategy.BALANCED,
            thresholdPct = 0.2,
            mode = ThresholdMode.RELATIVE,
        )
        // 相对偏离 (0.9-0.5)/0.5 = 0.8 > 0.2 → 要动
        assertTrue(plan.items.any { it.action != ActionType.HOLD })
    }

    @Test
    fun `方案的总市值等于输入总市值`() {
        val list = listOf(holding(1, 7_000.0, 0.5), holding(2, 3_000.0, 0.5))
        val plan = RebalanceEngine.plan(list, RebalanceStrategy.BALANCED)
        assertEquals(10_000.0, plan.totalValue.toYuan(), 0.01)
    }
}

package com.huaying.xstz.core.domain

import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.ThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldingMathTest {

    private fun holding(
        id: Long,
        valueYuan: Double,
        costYuan: Double = valueYuan,
        target: Double = 0.0,
        navYuan: Double = 1.0,
        prevNavYuan: Double = navYuan,
        type: AssetType = AssetType.STOCK,
        shares: Double = valueYuan / navYuan,
    ) = Holding(
        fundId = id,
        code = "00000$id",
        name = "基金$id",
        type = type,
        shares = Shares.fromDouble(shares),
        cost = Money.fromYuan(costYuan),
        nav = Nav.fromDouble(navYuan),
        prevNav = Nav.fromDouble(prevNavYuan),
        targetRatio = target,
    )

    @Test
    fun `空组合汇总全为零`() {
        val s = HoldingMath.summarize(emptyList())
        assertEquals(0L, s.totalMarketValue.micros)
        assertEquals(0L, s.totalCost.micros)
        assertEquals(0, s.holdingCount)
    }

    @Test
    fun `单持仓汇总等于自身`() {
        val h = holding(1, 10_000.0, 9_000.0)
        val s = HoldingMath.summarize(listOf(h))
        assertEquals(Money.fromYuan(10_000.0), s.totalMarketValue)
        assertEquals(Money.fromYuan(9_000.0), s.totalCost)
        assertEquals(1, s.holdingCount)
    }

    @Test
    fun `多持仓汇总不因小数累加漂移`() {
        // 0.1 + 0.2 在 Double 下会得到 0.30000000000000004，定点必须精确
        val a = holding(1, 0.1, shares = 0.1)
        val b = holding(2, 0.2, shares = 0.2)
        val s = HoldingMath.summarize(listOf(a, b))
        assertEquals(3000L, s.totalMarketValue.micros)
    }

    @Test
    fun `收益率为零成本时返回零而不是无穷`() {
        val h = holding(1, 1_000.0, costYuan = 0.0)
        assertEquals(0.0, h.profitRate, 1e-9)
    }

    @Test
    fun `当日收益按昨日净值计算`() {
        val h = holding(1, valueYuan = 1_100.0, navYuan = 1.1, prevNavYuan = 1.0)
        // 份额 1000，净值涨 0.1 → 当日收益 100
        assertEquals(100.0, h.dayProfit.toYuan(), 0.01)
    }

    @Test
    fun `资产类型占比之和为一`() {
        val list = listOf(
            holding(1, 6_000.0, type = AssetType.STOCK),
            holding(2, 3_000.0, type = AssetType.BOND),
            holding(3, 1_000.0, type = AssetType.CASH),
        )
        val sum = AssetType.entries.sumOf { HoldingMath.ratioOfType(list, it) }
        assertEquals(1.0, sum, 1e-9)
    }

    @Test
    fun `零份额持仓不参与占比分母`() {
        val active = holding(1, 1_000.0)
        val empty = holding(2, 0.0, shares = 0.0)
        val ratios = HoldingMath.displayRatios(listOf(active, empty))
        assertEquals(1.0, ratios[1L]!!, 1e-9)
        assertEquals(0.0, ratios[2L]!!, 1e-9)
    }

    @Test
    fun `相对偏离在目标为零且当前非零时为无穷`() {
        assertEquals(Double.MAX_VALUE, HoldingMath.deviationRatio(0.3, 0.0), 0.0)
        assertEquals(0.0, HoldingMath.deviationRatio(0.0, 0.0), 0.0)
    }

    @Test
    fun `相对偏离按目标比例计算`() {
        // 目标 20%，实际 25% → (0.25-0.20)/0.20 = 0.25
        assertEquals(0.25, HoldingMath.deviationRatio(0.25, 0.20), 1e-9)
    }

    @Test
    fun `绝对偏离等于占比之差`() {
        assertEquals(0.05, HoldingMath.deviationAbsolute(0.25, 0.20), 1e-9)
        assertEquals(0.05, HoldingMath.deviationAbsolute(0.15, 0.20), 1e-9)
    }

    @Test
    fun `超过阈值判定需要再平衡`() {
        val list = listOf(
            holding(1, 8_000.0, target = 0.5),
            holding(2, 2_000.0, target = 0.5),
        )
        // 实际 80/20，偏离 30 个百分点，阈值 5% → 需要
        assertTrue(HoldingMath.needsRebalance(list, 0.05, ThresholdMode.ABSOLUTE))
        // 阈值 31% → 不需要
        assertFalse(HoldingMath.needsRebalance(list, 0.31, ThresholdMode.ABSOLUTE))
    }

    @Test
    fun `目标为零的持仓不触发再平衡`() {
        // 两只都未设目标（关注中、尚未配置仓位），无论占比怎么分布都不该报警
        val list = listOf(
            holding(1, 9_000.0, target = 0.0),
            holding(2, 1_000.0, target = 0.0),
        )
        assertFalse(HoldingMath.needsRebalance(list, 0.01, ThresholdMode.ABSOLUTE))
        assertFalse(HoldingMath.hasSevereDeviation(list, 0.01, ThresholdMode.ABSOLUTE))
    }

    @Test
    fun `设了目标的持仓即使有目标为零的同伴也要报警`() {
        // 回归防护：不能因为组合里存在 target=0 的项，就把其他项的偏离一起豁免
        val list = listOf(
            holding(1, 1_000.0, target = 0.0),
            holding(2, 1_000.0, target = 1.0),
        )
        assertTrue(HoldingMath.needsRebalance(list, 0.01, ThresholdMode.ABSOLUTE))
    }

    @Test
    fun `严重偏离是阈值的两倍`() {
        val list = listOf(
            holding(1, 9_000.0, target = 0.5),
            holding(2, 1_000.0, target = 0.5),
        )
        // 偏离 40 个百分点；阈值 15% 的两倍是 30% → 严重
        assertTrue(HoldingMath.hasSevereDeviation(list, 0.15, ThresholdMode.ABSOLUTE))
        // 阈值 25% 的两倍是 50% → 不严重
        assertFalse(HoldingMath.hasSevereDeviation(list, 0.25, ThresholdMode.ABSOLUTE))
    }

    @Test
    fun `空组合不触发任何告警`() {
        assertFalse(HoldingMath.needsRebalance(emptyList(), 0.01, ThresholdMode.ABSOLUTE))
        assertFalse(HoldingMath.hasSevereDeviation(emptyList(), 0.01, ThresholdMode.ABSOLUTE))
    }
}

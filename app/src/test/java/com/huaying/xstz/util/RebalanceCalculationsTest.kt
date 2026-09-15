package com.huaying.xstz.util

import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 再平衡核心计算单元测试
 * 覆盖：强制再平衡、智能分配、现金账户吸收差额、阈值内不交易等核心路径
 */
class RebalanceCalculationsTest {

    private fun createFund(
        id: Long = 1,
        code: String = "000001",
        name: String = "测试基金",
        type: AssetType = AssetType.STOCK,
        holdingQuantity: Double = 1000.0,
        totalCost: Double = 10000.0,
        currentPrice: Double = 12.0,
        targetRatio: Double = 0.25
    ): Fund {
        return Fund(
            id = id, code = code, name = name, type = type,
            holdingQuantity = holdingQuantity, totalCost = totalCost,
            currentPrice = currentPrice, targetRatio = targetRatio
        )
    }

    // ==================== 空输入 ====================

    @Test
    fun calculateAdjustments_emptyFunds_returnsEmpty() {
        val result = calculateAdjustments(
            funds = emptyList(), totalAssets = 10000.0,
            strategy = RebalanceStrategy.FULL_REBALANCE,
            newFundVal = 0.0, thresholdPct = 5f, thresholdMode = 0
        )
        assertTrue(result.isEmpty())
    }

    // ==================== 强制再平衡（FULL_REBALANCE） ====================

    @Test
    fun fullRebalance_target75vs25_sellsOverweightBuysUnderweight() {
        // 两只基金各 10000 元，目标 75%/25%：卖出 A 5000，买入 B 5000，净变动 0 立即收敛
        val fundA = createFund(id = 1, name = "基金A", holdingQuantity = 1000.0, currentPrice = 10.0, targetRatio = 0.75)
        val fundB = createFund(id = 2, name = "基金B", holdingQuantity = 2000.0, currentPrice = 5.0, targetRatio = 0.25)

        val result = calculateAdjustments(
            funds = listOf(fundA, fundB), totalAssets = 20000.0,
            strategy = RebalanceStrategy.FULL_REBALANCE,
            newFundVal = 0.0, thresholdPct = 5f, thresholdMode = 0
        )

        assertEquals(2, result.size)
        val itemA = result.first { it.fundId == 1L }
        val itemB = result.first { it.fundId == 2L }
        // 强制再平衡忽略阈值，直接补齐到目标值（忽略 100 股取整前的微小误差按金额断言）
        assertEquals(5000.0, itemA.adjustmentAmount, 0.01)
        assertEquals(-5000.0, itemB.adjustmentAmount, 0.01)
        assertEquals(500.0, itemA.changeQuantity, 0.01)
        assertEquals(-1000.0, itemB.changeQuantity, 0.01)
    }

    @Test
    fun fullRebalance_ignoresThreshold() {
        // 当前占比在阈值内（55% vs 目标 50%，阈值 5%），但强制再平衡仍产生交易
        val fund = createFund(id = 1, holdingQuantity = 1100.0, currentPrice = 10.0, targetRatio = 0.5)
        val cash = createFund(id = 2, type = AssetType.CASH, holdingQuantity = 9000.0, currentPrice = 1.0, targetRatio = 0.5)

        val result = calculateAdjustments(
            funds = listOf(fund, cash), totalAssets = 20000.0,
            strategy = RebalanceStrategy.FULL_REBALANCE,
            newFundVal = 0.0, thresholdPct = 5f, thresholdMode = 0
        )

        val stockItem = result.first { it.fundId == 1L }
        assertEquals(-1000.0, stockItem.adjustmentAmount, 0.01)
        // 现金账户吸收反向差额
        val cashItem = result.first { it.fundId == 2L }
        assertEquals(1000.0, cashItem.adjustmentAmount, 0.01)
    }

    // ==================== 智能策略（SMART） ====================

    @Test
    fun smartBalanced_newCash_distributedByGap() {
        // 两只基金各持 10000 且偏离在阈值内，投入 10000 元按缺口均分
        // （股数需为 100 股最小交易单位的整数倍：A 500 股×10 元、B 1000 股×5 元）
        val fundA = createFund(id = 1, holdingQuantity = 1000.0, currentPrice = 10.0, targetRatio = 0.5)
        val fundB = createFund(id = 2, holdingQuantity = 2000.0, currentPrice = 5.0, targetRatio = 0.5)

        val result = calculateAdjustments(
            funds = listOf(fundA, fundB), totalAssets = 20000.0,
            strategy = RebalanceStrategy.SMART,
            newFundVal = 10000.0, thresholdPct = 5f, thresholdMode = 0
        )

        assertEquals(2, result.size)
        assertEquals(5000.0, result.first { it.fundId == 1L }.adjustmentAmount, 0.01)
        assertEquals(5000.0, result.first { it.fundId == 2L }.adjustmentAmount, 0.01)
        assertEquals(500.0, result.first { it.fundId == 1L }.changeQuantity, 0.01)
        assertEquals(1000.0, result.first { it.fundId == 2L }.changeQuantity, 0.01)
    }

    @Test
    fun smart_withinThreshold_noNewCash_noTrade() {
        // 当前占比 55% vs 目标 50%，比例阈值模式 5% 内且无新资金 → 不产生任何交易
        val fund = createFund(id = 1, holdingQuantity = 1100.0, currentPrice = 10.0, targetRatio = 0.5)
        val cash = createFund(id = 2, type = AssetType.CASH, holdingQuantity = 9000.0, currentPrice = 1.0, targetRatio = 0.5)

        val result = calculateAdjustments(
            funds = listOf(fund, cash), totalAssets = 20000.0,
            strategy = RebalanceStrategy.SMART,
            newFundVal = 0.0, thresholdPct = 5f, thresholdMode = 1
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun smart_belowCritical_fundedByNewCash() {
        // 基金A 目标 75% 仅持 50%（低于下界 75%-2.5%=72.5%，为临界缺口），新资金优先补临界缺口
        val fundA = createFund(id = 1, holdingQuantity = 1000.0, currentPrice = 10.0, targetRatio = 0.75)
        val fundB = createFund(id = 2, holdingQuantity = 3060.0, currentPrice = 5.0, targetRatio = 0.25)

        val result = calculateAdjustments(
            funds = listOf(fundA, fundB), totalAssets = 25300.0,
            strategy = RebalanceStrategy.SMART,
            newFundVal = 6000.0, thresholdPct = 5f, thresholdMode = 0
        )

        // A 是唯一临界缺口且新资金不足以补齐全部缺口：6000 元应全额流向 A
        val itemA = result.firstOrNull { it.fundId == 1L }
        val itemB = result.firstOrNull { it.fundId == 2L }
        assertTrue(itemA != null && itemA.adjustmentAmount > 0)
        // B 在阈值内且偏高时不应买入
        if (itemB != null) {
            assertTrue(itemB.adjustmentAmount <= 0.01)
        }
    }

    // ==================== 现金账户（CASH） ====================

    @Test
    fun fullRebalance_cashAccountAbsorbsDifference() {
        // 股票基金欠配 2000，现金账户超配 2000：强制再平衡时现金账户吸收差额
        val stock = createFund(id = 1, holdingQuantity = 1000.0, currentPrice = 10.0, targetRatio = 0.6)
        val cash = createFund(id = 2, type = AssetType.CASH, holdingQuantity = 10000.0, currentPrice = 1.0, targetRatio = 0.4)

        val result = calculateAdjustments(
            funds = listOf(stock, cash), totalAssets = 20000.0,
            strategy = RebalanceStrategy.FULL_REBALANCE,
            newFundVal = 0.0, thresholdPct = 5f, thresholdMode = 0
        )

        assertEquals(2000.0, result.first { it.fundId == 1L }.adjustmentAmount, 0.01)
        assertEquals(-2000.0, result.first { it.fundId == 2L }.adjustmentAmount, 0.01)
        // 现金账户的 changeQuantity 与金额一致（1 元面值）
        assertEquals(-2000.0, result.first { it.fundId == 2L }.changeQuantity, 0.01)
    }

    // ==================== 结果排序 ====================

    @Test
    fun result_itemsSortedByInputOrder() {
        val fundA = createFund(id = 1, holdingQuantity = 1000.0, currentPrice = 10.0, targetRatio = 0.75)
        val fundB = createFund(id = 2, holdingQuantity = 2000.0, currentPrice = 5.0, targetRatio = 0.25)

        val result = calculateAdjustments(
            funds = listOf(fundA, fundB), totalAssets = 20000.0,
            strategy = RebalanceStrategy.FULL_REBALANCE,
            newFundVal = 0.0, thresholdPct = 5f, thresholdMode = 0
        )

        assertEquals(1L, result.first().fundId)
        assertEquals(2L, result.last().fundId)
    }
}

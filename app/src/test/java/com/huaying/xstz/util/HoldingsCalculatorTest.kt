package com.huaying.xstz.util

import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import org.junit.Assert.*
import org.junit.Test

class HoldingsCalculatorTest {

    private fun createFund(
        id: Long = 1,
        code: String = "000001",
        name: String = "测试基金",
        type: AssetType = AssetType.STOCK,
        holdingQuantity: Double = 1000.0,
        totalCost: Double = 10000.0,
        currentPrice: Double = 12.0,
        preClosePrice: Double = 11.5,
        changePercent: Double = 4.35,
        targetRatio: Double = 0.25
    ): Fund {
        return Fund(
            id = id, code = code, name = name, type = type,
            holdingQuantity = holdingQuantity, totalCost = totalCost,
            currentPrice = currentPrice, preClosePrice = preClosePrice,
            changePercent = changePercent, targetRatio = targetRatio
        )
    }

    // ==================== calculateTotalAssets ====================

    @Test
    fun calculateTotalAssets_emptyList_returnsZero() {
        assertEquals(0.0, HoldingsCalculator.calculateTotalAssets(emptyList()), 0.001)
    }

    @Test
    fun calculateTotalAssets_singleFund_returnsMarketValue() {
        val fund = createFund(holdingQuantity = 1000.0, currentPrice = 12.0)
        assertEquals(12000.0, HoldingsCalculator.calculateTotalAssets(listOf(fund)), 0.001)
    }

    @Test
    fun calculateTotalAssets_multipleFunds_returnsSum() {
        val fund1 = createFund(id = 1, holdingQuantity = 1000.0, currentPrice = 10.0)
        val fund2 = createFund(id = 2, holdingQuantity = 500.0, currentPrice = 20.0)
        assertEquals(20000.0, HoldingsCalculator.calculateTotalAssets(listOf(fund1, fund2)), 0.001)
    }

    // ==================== calculateTotalCost ====================

    @Test
    fun calculateTotalCost_emptyList_returnsZero() {
        assertEquals(0.0, HoldingsCalculator.calculateTotalCost(emptyList()), 0.001)
    }

    @Test
    fun calculateTotalCost_multipleFunds_returnsSum() {
        val fund1 = createFund(id = 1, totalCost = 10000.0)
        val fund2 = createFund(id = 2, totalCost = 15000.0)
        assertEquals(25000.0, HoldingsCalculator.calculateTotalCost(listOf(fund1, fund2)), 0.001)
    }

    // ==================== calculateCurrentValue ====================

    @Test
    fun calculateCurrentValue_normalFund_returnsQuantityTimesPrice() {
        val fund = createFund(holdingQuantity = 1000.0, currentPrice = 12.0)
        assertEquals(12000.0, HoldingsCalculator.calculateCurrentValue(fund), 0.001)
    }

    @Test
    fun calculateCurrentValue_zeroQuantity_returnsZero() {
        val fund = createFund(holdingQuantity = 0.0, currentPrice = 12.0)
        assertEquals(0.0, HoldingsCalculator.calculateCurrentValue(fund), 0.001)
    }

    // ==================== calculateCurrentRatio ====================

    @Test
    fun calculateCurrentRatio_zeroTotalAssets_returnsZero() {
        val fund = createFund()
        assertEquals(0.0, HoldingsCalculator.calculateCurrentRatio(fund, 0.0), 0.001)
    }

    @Test
    fun calculateCurrentRatio_normalCase_returnsPercentage() {
        val fund = createFund(holdingQuantity = 1000.0, currentPrice = 10.0)
        assertEquals(20.0, HoldingsCalculator.calculateCurrentRatio(fund, 50000.0), 0.001)
    }

    // ==================== calculateCostPrice ====================

    @Test
    fun calculateCostPrice_normalFund_returnsCostDivQuantity() {
        val fund = createFund(totalCost = 10000.0, holdingQuantity = 1000.0)
        assertEquals(10.0, HoldingsCalculator.calculateCostPrice(fund), 0.001)
    }

    @Test
    fun calculateCostPrice_zeroQuantity_returnsZero() {
        val fund = createFund(totalCost = 10000.0, holdingQuantity = 0.0)
        assertEquals(0.0, HoldingsCalculator.calculateCostPrice(fund), 0.001)
    }

    // ==================== calculateReturnRate ====================

    @Test
    fun calculateReturnRate_positiveReturn_returnsPositivePercentage() {
        assertEquals(50.0, HoldingsCalculator.calculateReturnRate(15000.0, 10000.0), 0.001)
    }

    @Test
    fun calculateReturnRate_negativeReturn_returnsNegativePercentage() {
        assertEquals(-20.0, HoldingsCalculator.calculateReturnRate(8000.0, 10000.0), 0.001)
    }

    @Test
    fun calculateReturnRate_zeroCost_returnsZero() {
        assertEquals(0.0, HoldingsCalculator.calculateReturnRate(10000.0, 0.0), 0.001)
    }

    // ==================== calculateTodayReturn ====================

    @Test
    fun calculateTodayReturn_withPreClosePrice_usesPreCloseAsBase() {
        val fund = createFund(holdingQuantity = 1000.0, preClosePrice = 10.0, changePercent = 5.0)
        assertEquals(500.0, HoldingsCalculator.calculateTodayReturn(fund), 0.001)
    }

    @Test
    fun calculateTodayReturn_withoutPreClosePrice_usesCurrentPriceAsBase() {
        val fund = createFund(holdingQuantity = 1000.0, currentPrice = 12.0, preClosePrice = 0.0, changePercent = 5.0)
        assertEquals(600.0, HoldingsCalculator.calculateTodayReturn(fund), 0.001)
    }

    @Test
    fun calculateTodayReturn_batchCalculation_returnsSum() {
        val fund1 = createFund(id = 1, holdingQuantity = 1000.0, preClosePrice = 10.0, changePercent = 5.0)
        val fund2 = createFund(id = 2, holdingQuantity = 500.0, preClosePrice = 20.0, changePercent = -2.0)
        assertEquals(300.0, HoldingsCalculator.calculateTodayReturn(listOf(fund1, fund2)), 0.001)
    }

    // ==================== calculateMarketValueByType ====================

    @Test
    fun calculateMarketValueByType_filtersCorrectly() {
        val stockFund = createFund(id = 1, type = AssetType.STOCK, holdingQuantity = 1000.0, currentPrice = 10.0)
        val bondFund = createFund(id = 2, type = AssetType.BOND, holdingQuantity = 500.0, currentPrice = 20.0)
        val funds = listOf(stockFund, bondFund)

        assertEquals(10000.0, HoldingsCalculator.calculateMarketValueByType(funds, AssetType.STOCK), 0.001)
        assertEquals(10000.0, HoldingsCalculator.calculateMarketValueByType(funds, AssetType.BOND), 0.001)
        assertEquals(0.0, HoldingsCalculator.calculateMarketValueByType(funds, AssetType.CASH), 0.001)
    }

    // ==================== calculateDeviation ====================

    @Test
    fun calculateDeviation_zeroTotalAssets_returnsZeroZero() {
        val fund = createFund()
        val (deviation, target) = HoldingsCalculator.calculateDeviation(fund, 0.0)
        assertEquals(0.0, deviation, 0.001)
        assertEquals(0.0, target, 0.001)
    }

    @Test
    fun calculateDeviation_normalCase_returnsCorrectDeviationAndTarget() {
        val fund = createFund(holdingQuantity = 1000.0, currentPrice = 12.0, targetRatio = 0.25)
        val (deviation, target) = HoldingsCalculator.calculateDeviation(fund, 40000.0)
        assertEquals(5.0, deviation, 0.1)
        assertEquals(25.0, target, 0.001)
    }

    // ==================== calculateEffectiveDeviationRatio ====================

    @Test
    fun calculateEffectiveDeviationRatio_percentageMode_returnsMaxOfRatioAndMin() {
        val result = HoldingsCalculator.calculateEffectiveDeviationRatio(0.25, 20.0f, 0)
        assertEquals(0.05, result, 0.001)
    }

    @Test
    fun calculateEffectiveDeviationRatio_percentageMode_smallTarget_usesMinAbsDeviation() {
        val result = HoldingsCalculator.calculateEffectiveDeviationRatio(0.01, 20.0f, 0)
        assertEquals(AppConstants.MIN_ABS_DEVIATION, result, 0.001)
    }

    @Test
    fun calculateEffectiveDeviationRatio_percentagePointMode_returnsThresholdOver100() {
        val result = HoldingsCalculator.calculateEffectiveDeviationRatio(0.25, 5.0f, 1)
        assertEquals(0.05, result, 0.001)
    }

    // ==================== calculateEffectiveDeviationPercent ====================

    @Test
    fun calculateEffectiveDeviationPercent_percentageMode_returnsMaxOfProductAndMin() {
        val result = HoldingsCalculator.calculateEffectiveDeviationPercent(25.0, 20.0, 0)
        assertEquals(5.0, result, 0.001)
    }

    @Test
    fun calculateEffectiveDeviationPercent_percentagePointMode_returnsThresholdDirectly() {
        val result = HoldingsCalculator.calculateEffectiveDeviationPercent(25.0, 5.0, 1)
        assertEquals(5.0, result, 0.001)
    }

    // ==================== calculateNonCashTotalRatio ====================

    @Test
    fun calculateNonCashTotalRatio_excludesCashFunds() {
        val stockFund = createFund(id = 1, type = AssetType.STOCK, targetRatio = 0.4)
        val bondFund = createFund(id = 2, type = AssetType.BOND, targetRatio = 0.3)
        val cashFund = createFund(id = 3, type = AssetType.CASH, targetRatio = 0.3)
        assertEquals(70.0, HoldingsCalculator.calculateNonCashTotalRatio(listOf(stockFund, bondFund, cashFund)), 0.001)
    }

    // ==================== calculateCashRatio ====================

    @Test
    fun calculateCashRatio_returns100MinusNonCash() {
        val stockFund = createFund(id = 1, type = AssetType.STOCK, targetRatio = 0.4)
        val bondFund = createFund(id = 2, type = AssetType.BOND, targetRatio = 0.3)
        assertEquals(30.0, HoldingsCalculator.calculateCashRatio(listOf(stockFund, bondFund)), 0.001)
    }

    @Test
    fun calculateCashRatio_nonCashOver100_returnsZero() {
        val stockFund = createFund(id = 1, type = AssetType.STOCK, targetRatio = 0.6)
        val bondFund = createFund(id = 2, type = AssetType.BOND, targetRatio = 0.5)
        assertEquals(0.0, HoldingsCalculator.calculateCashRatio(listOf(stockFund, bondFund)), 0.001)
    }

    // ==================== hasSevereDeviation ====================

    @Test
    fun hasSevereDeviation_zeroTotalAssets_returnsFalse() {
        val fund = createFund()
        assertFalse(HoldingsCalculator.hasSevereDeviation(listOf(fund), 0.0))
    }

    @Test
    fun hasSevereDeviation_largeDeviation_returnsTrue() {
        val fund = createFund(holdingQuantity = 1000.0, currentPrice = 12.0, targetRatio = 0.25)
        assertTrue(HoldingsCalculator.hasSevereDeviation(listOf(fund), 12000.0))
    }

    @Test
    fun hasSevereDeviation_smallDeviation_returnsFalse() {
        val fund = createFund(holdingQuantity = 1000.0, currentPrice = 12.0, targetRatio = 0.25)
        assertFalse(HoldingsCalculator.hasSevereDeviation(listOf(fund), 48000.0))
    }

    // ==================== calculateFundDisplayRatios ====================

    @Test
    fun calculateFundDisplayRatios_zeroTotalAssets_returnsEmptyMap() {
        val fund = createFund()
        assertTrue(HoldingsCalculator.calculateFundDisplayRatios(listOf(fund), 0.0).isEmpty())
    }

    @Test
    fun calculateFundDisplayRatios_normalCase_returnsRoundedRatios() {
        val fund1 = createFund(id = 1, holdingQuantity = 1000.0, currentPrice = 10.0)
        val fund2 = createFund(id = 2, holdingQuantity = 500.0, currentPrice = 20.0)
        val ratios = HoldingsCalculator.calculateFundDisplayRatios(listOf(fund1, fund2), 20000.0)
        assertEquals(50.0, ratios[fund1]!!, 0.01)
        assertEquals(50.0, ratios[fund2]!!, 0.01)
    }

    // ==================== isNeedRebalance ====================

    @Test
    fun isNeedRebalance_withinThreshold_returnsFalse() {
        assertFalse(HoldingsCalculator.isNeedRebalance(
            deviation = 3.0, targetRatio = 25.0, threshold = 20.0, thresholdMode = 0
        ))
    }

    @Test
    fun isNeedRebalance_exceedsThreshold_returnsTrue() {
        assertTrue(HoldingsCalculator.isNeedRebalance(
            deviation = 8.0, targetRatio = 25.0, threshold = 20.0, thresholdMode = 0
        ))
    }

    @Test
    fun isNeedRebalance_percentagePointMode_withinThreshold_returnsFalse() {
        assertFalse(HoldingsCalculator.isNeedRebalance(
            deviation = 3.0, targetRatio = 25.0, threshold = 5.0, thresholdMode = 1
        ))
    }

    // ==================== getFundStatus ====================

    @Test
    fun getFundStatus_zeroTotalAssets_returnsNormal() {
        val fund = createFund()
        assertEquals("正常", HoldingsCalculator.getFundStatus(fund, 0.0, 20.0, 0))
    }

    @Test
    fun getFundStatus_smallDeviation_returnsNormal() {
        val fund = createFund(holdingQuantity = 1000.0, currentPrice = 12.0, targetRatio = 0.25)
        assertEquals("正常", HoldingsCalculator.getFundStatus(fund, 48000.0, 20.0, 0))
    }
}

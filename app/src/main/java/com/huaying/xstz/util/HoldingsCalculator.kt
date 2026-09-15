package com.huaying.xstz.util

import com.huaying.xstz.data.entity.Fund
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * 持仓计算工具类
 * 整合所有持仓相关的计算逻辑，供资产总览、基金详情、再平衡、目标占比等界面共享使用
 */
object HoldingsCalculator {

    // ==================== 基础计算 ====================

    /** 计算总资产市值（currentPrice为0时使用成本价回退，避免市值算为0） */
    fun calculateTotalAssets(funds: List<Fund>): Double {
        return funds.sumOf { fund ->
            val price = if (fund.currentPrice > 0) fund.currentPrice
                        else if (fund.holdingQuantity > 0) fund.totalCost / fund.holdingQuantity
                        else 0.0
            fund.holdingQuantity * price
        }
    }

    /** 计算总成本 */
    fun calculateTotalCost(funds: List<Fund>): Double {
        return funds.sumOf { it.totalCost }
    }

    /** 计算单基金当前市值（currentPrice为0时使用成本价回退） */
    fun calculateCurrentValue(fund: Fund): Double {
        val price = if (fund.currentPrice > 0) fund.currentPrice
                    else if (fund.holdingQuantity > 0) fund.totalCost / fund.holdingQuantity
                    else 0.0
        return fund.holdingQuantity * price
    }

    /** 计算单基金当前占比（百分比，如 25.0 表示 25%） */
    fun calculateCurrentRatio(fund: Fund, totalAssets: Double): Double {
        if (totalAssets <= 0) return 0.0
        val currentValue = calculateCurrentValue(fund)
        return currentValue / totalAssets * AppConstants.PERCENTAGE_BASE
    }

    /** 计算成本价格 */
    fun calculateCostPrice(fund: Fund): Double {
        return if (fund.holdingQuantity > 0) fund.totalCost / fund.holdingQuantity else 0.0
    }

    /** 计算收益率（百分比） */
    fun calculateReturnRate(currentValue: Double, cost: Double): Double {
        return if (cost > 0) (currentValue - cost) / cost * AppConstants.PERCENTAGE_BASE else 0.0
    }

    /** 计算今日收益 */
    fun calculateTodayReturn(fund: Fund): Double {
        val basePrice = if (fund.preClosePrice > 0) fund.preClosePrice else fund.currentPrice
        return fund.holdingQuantity * basePrice * fund.changePercent / AppConstants.PERCENTAGE_BASE
    }

    /** 批量计算今日收益 */
    fun calculateTodayReturn(funds: List<Fund>): Double {
        return funds.sumOf { calculateTodayReturn(it) }
    }

    /** 按资产类型计算市值（currentPrice为0时使用成本价回退） */
    fun calculateMarketValueByType(funds: List<Fund>, type: com.huaying.xstz.data.entity.AssetType): Double {
        return funds.filter { it.type == type }.sumOf { calculateCurrentValue(it) }
    }

    // ==================== 偏离度与再平衡状态 ====================

    private const val MIN_TRADE_UNIT = 100.0
    private const val MIN_DEVIATION_FOR_NORMAL = 5.0
    private const val SEVERE_THRESHOLD_MULTIPLIER = 2.0
    private const val MIN_THRESHOLD_BASE = 0.5

    /** 计算偏离度（返回 偏离度百分比, 目标比例百分比） */
    fun calculateDeviation(fund: Fund, totalAssets: Double): Pair<Double, Double> {
        if (totalAssets == 0.0) return 0.0 to 0.0
        val currentRatio = calculateCurrentRatio(fund, totalAssets)
        val targetRatioPercent = fund.targetRatio * AppConstants.PERCENTAGE_BASE
        return (currentRatio - targetRatioPercent) to targetRatioPercent
    }

    /**
     * 计算有效偏离阈值（比例值，0-1范围）
     * @param targetRatio 目标比例（0-1范围）
     * @param thresholdPct 阈值百分比数值（如20.0表示20%）
     * @param thresholdMode 0=百分比模式，1=百分点模式
     * @return 有效偏离阈值（比例值，0-1范围）
     */
    fun calculateEffectiveDeviationRatio(targetRatio: Double, thresholdPct: Float, thresholdMode: Int): Double {
        return if (thresholdMode == 0) {
            max(targetRatio * (thresholdPct / AppConstants.PERCENTAGE_BASE), AppConstants.MIN_ABS_DEVIATION)
        } else {
            (thresholdPct / AppConstants.PERCENTAGE_BASE).toDouble()
        }
    }

    /**
     * 计算有效偏离阈值（百分比值，0-100范围）
     * @param targetRatioPercent 目标比例百分比（如25.0表示25%）
     * @param thresholdPct 阈值百分比数值（如20.0表示20%）
     * @param thresholdMode 0=百分比模式，1=百分点模式
     * @return 有效偏离阈值（百分比值，0-100范围）
     */
    fun calculateEffectiveDeviationPercent(targetRatioPercent: Double, thresholdPct: Double, thresholdMode: Int): Double {
        return if (thresholdMode == 0) {
            max(targetRatioPercent * (thresholdPct / AppConstants.PERCENTAGE_BASE), MIN_THRESHOLD_BASE)
        } else {
            thresholdPct
        }
    }

    /** 判断是否需要再平衡 */
    fun isNeedRebalance(
        deviation: Double,
        targetRatio: Double,
        fund: Fund? = null,
        totalAssets: Double = 0.0,
        threshold: Double,
        thresholdMode: Int
    ): Boolean {
        val effectiveThreshold = if (thresholdMode == 0) {
            max(targetRatio * (threshold / AppConstants.PERCENTAGE_BASE), MIN_THRESHOLD_BASE)
        } else {
            threshold
        }

        if (abs(deviation) <= effectiveThreshold) return false

        if (fund != null && fund.currentPrice > 0 && totalAssets > 0) {
            val deviationValue = totalAssets * (abs(deviation) / AppConstants.PERCENTAGE_BASE)
            val deviationShares = deviationValue / fund.currentPrice
            if (deviationShares < MIN_TRADE_UNIT) {
                return false
            }
        }

        return true
    }

    /** 获取基金再平衡状态文本 */
    fun getFundStatus(
        fund: Fund,
        totalAssets: Double,
        thresholdPct: Double,
        thresholdMode: Int,
        estimatedLiquidity: Double = 0.0
    ): String {
        if (totalAssets == 0.0) return "正常"

        val currentRatio = calculateCurrentRatio(fund, totalAssets)
        val targetRatioPercent = fund.targetRatio * AppConstants.PERCENTAGE_BASE
        val deviation = abs(currentRatio - targetRatioPercent)

        val normalThreshold = if (thresholdMode == 0) {
            max(targetRatioPercent * (thresholdPct / AppConstants.PERCENTAGE_BASE), MIN_THRESHOLD_BASE)
        } else {
            thresholdPct
        }
        val severeThreshold = normalThreshold * SEVERE_THRESHOLD_MULTIPLIER

        val status = when {
            deviation > severeThreshold -> "严重偏离"
            deviation > normalThreshold -> "需平衡"
            else -> "正常"
        }

        if (status == "正常") return "正常"

        if (fund.currentPrice > 0) {
            val deviationValue = totalAssets * (deviation / AppConstants.PERCENTAGE_BASE)
            val deviationShares = deviationValue / fund.currentPrice

            if (deviationShares < MIN_TRADE_UNIT && deviation < MIN_DEVIATION_FOR_NORMAL) {
                return "正常"
            }

            val realDeviation = currentRatio - targetRatioPercent
            if (realDeviation < 0) {
                val costToBuy100 = fund.currentPrice * MIN_TRADE_UNIT
                if (estimatedLiquidity < costToBuy100) {
                    return status
                }
            }
        }

        return status
    }

    /** 计算可卖出流动性 */
    fun calculateLiquidity(funds: List<Fund>, totalAssets: Double): Double {
        if (totalAssets <= 0) return 0.0

        var liquidity = 0.0
        funds.forEach { fund ->
            val (deviationPct, _) = calculateDeviation(fund, totalAssets)
            if (deviationPct > 0 && fund.currentPrice > 0) {
                val sellValue = totalAssets * (deviationPct / AppConstants.PERCENTAGE_BASE)
                val sellShares = sellValue / fund.currentPrice
                if (sellShares >= MIN_TRADE_UNIT) {
                    val sellableUnits = (sellShares / MIN_TRADE_UNIT).toInt()
                    liquidity += sellableUnits * MIN_TRADE_UNIT * fund.currentPrice
                }
            }
        }
        return liquidity
    }

    /** 计算各基金当前占比（四舍五入到小数点后2位） */
    fun calculateFundDisplayRatios(funds: List<Fund>, totalAssets: Double): Map<Fund, Double> {
        if (totalAssets <= 0) return emptyMap()
        return funds.associateWith { fund ->
            val currentValue = fund.holdingQuantity * fund.currentPrice
            round(currentValue / totalAssets * AppConstants.PERCENTAGE_BASE * 100.0) / 100.0
        }
    }

    /** 计算非现金基金目标占比总和（百分比） */
    fun calculateNonCashTotalRatio(funds: List<Fund>): Double {
        return funds.filter { it.type != com.huaying.xstz.data.entity.AssetType.CASH }
            .sumOf { it.targetRatio * AppConstants.PERCENTAGE_BASE }
    }

    /** 计算现金占比（百分比，自动 = 100 - 非现金总和） */
    fun calculateCashRatio(funds: List<Fund>): Double {
        return (AppConstants.PERCENTAGE_BASE - calculateNonCashTotalRatio(funds))
            .coerceAtLeast(AppConstants.ZERO_DOUBLE)
    }

    /** 判断是否存在严重偏离 */
    fun hasSevereDeviation(funds: List<Fund>, totalAssets: Double): Boolean {
        if (totalAssets <= 0) return false
        return funds.any { fund ->
            val currentRatio = calculateCurrentRatio(fund, totalAssets)
            val targetRatio = fund.targetRatio * AppConstants.PERCENTAGE_BASE
            val deviation = abs(currentRatio - targetRatio)
            deviation > AppConstants.SEVERE_DEVIATION_THRESHOLD
        }
    }
}

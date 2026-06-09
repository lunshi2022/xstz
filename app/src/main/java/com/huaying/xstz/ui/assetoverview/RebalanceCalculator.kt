package com.huaying.xstz.ui.assetoverview

import com.huaying.xstz.data.entity.Fund
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * 再平衡计算工具类
 * 负责计算基金偏离度、再平衡状态等纯计算逻辑
 */
object RebalanceCalculator {

    private const val MIN_TRADE_UNIT = 100.0
    private const val MIN_DEVIATION_FOR_NORMAL = 5.0
    private const val SEVERE_THRESHOLD_MULTIPLIER = 2.0
    private const val MIN_THRESHOLD_BASE = 0.5

    fun calculateDeviation(fund: Fund, totalAssets: Double): Pair<Double, Double> {
        if (totalAssets == 0.0) return 0.0 to 0.0

        val currentValue = fund.holdingQuantity * fund.currentPrice
        val currentRatio = currentValue / totalAssets * 100
        val targetRatioPercent = fund.targetRatio * 100

        return (currentRatio - targetRatioPercent) to targetRatioPercent
    }

    fun isNeedRebalance(
        deviation: Double,
        targetRatio: Double,
        fund: Fund? = null,
        totalAssets: Double = 0.0,
        threshold: Double,
        thresholdMode: Int
    ): Boolean {
        val effectiveThreshold = if (thresholdMode == 0) {
            max(targetRatio * (threshold / 100.0), MIN_THRESHOLD_BASE)
        } else {
            threshold
        }

        if (abs(deviation) <= effectiveThreshold) return false

        if (fund != null && fund.currentPrice > 0 && totalAssets > 0) {
            val deviationValue = totalAssets * (abs(deviation) / 100.0)
            val deviationShares = deviationValue / fund.currentPrice
            if (deviationShares < MIN_TRADE_UNIT) {
                return false
            }
        }

        return true
    }

    fun getFundStatus(
        fund: Fund,
        totalAssets: Double,
        thresholdPct: Double,
        thresholdMode: Int,
        estimatedLiquidity: Double
    ): String {
        if (totalAssets == 0.0) return "正常"

        val currentValue = fund.holdingQuantity * fund.currentPrice
        val currentRatio = currentValue / totalAssets * 100
        val targetRatioPercent = fund.targetRatio * 100
        val deviation = abs(currentRatio - targetRatioPercent)

        val normalThreshold = if (thresholdMode == 0) {
            max(targetRatioPercent * (thresholdPct / 100.0), MIN_THRESHOLD_BASE)
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
            val deviationValue = totalAssets * (deviation / 100.0)
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

    fun calculateLiquidity(funds: List<Fund>, totalAssets: Double): Double {
        if (totalAssets <= 0) return 0.0

        var liquidity = 0.0
        funds.forEach { fund ->
            val (deviationPct, _) = calculateDeviation(fund, totalAssets)
            if (deviationPct > 0 && fund.currentPrice > 0) {
                val sellValue = totalAssets * (deviationPct / 100.0)
                val sellShares = sellValue / fund.currentPrice
                if (sellShares >= MIN_TRADE_UNIT) {
                    val sellableUnits = (sellShares / MIN_TRADE_UNIT).toInt()
                    liquidity += sellableUnits * MIN_TRADE_UNIT * fund.currentPrice
                }
            }
        }
        return liquidity
    }

    fun calculateFundDisplayRatios(funds: List<Fund>, totalAssets: Double): Map<Fund, Double> {
        if (totalAssets <= 0) return emptyMap()
        return funds.associateWith { fund ->
            val currentValue = fund.holdingQuantity * fund.currentPrice
            round(currentValue / totalAssets * 100.0 * 100.0) / 100.0
        }
    }
}

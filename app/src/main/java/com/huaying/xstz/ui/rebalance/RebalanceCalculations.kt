package com.huaying.xstz.ui.rebalance

import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.util.AppConstants
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

data class AdjustmentItem(
    val fundId: Long,
    val fundName: String,
    val assetType: AssetType,
    val currentRatio: Double,
    val targetRatio: Double,
    val idealRatio: Double,
    val adjustmentAmount: Double,
    val changeQuantity: Double
)

enum class RebalanceStrategy(val displayName: String) {
    SMART("智能买卖"),
    FULL_REBALANCE("强制再平衡")
}

fun calculateAdjustments(
    funds: List<Fund>,
    totalAssets: Double,
    strategy: RebalanceStrategy,
    newFundVal: Double,
    thresholdPct: Float,
    thresholdMode: Int
): List<AdjustmentItem> {
    val hasCashFund = funds.any { it.type == AssetType.CASH }

    var effectiveFutureTotal = totalAssets + newFundVal
    var adjustments: List<AdjustmentItem> = emptyList()

    val maxIterations = if (hasCashFund) AppConstants.REBALANCE_CASH_ITERATIONS else AppConstants.REBALANCE_MAX_ITERATIONS

    for (i in 0 until maxIterations) {
        adjustments = internalCalculateAdjustments(
            funds,
            totalAssets,
            effectiveFutureTotal,
            strategy,
            newFundVal,
            thresholdPct,
            thresholdMode
        )

        if (hasCashFund) break

        val netStockChange = adjustments.sumOf { it.adjustmentAmount }
        val realFutureTotal = totalAssets + netStockChange

        if (abs(realFutureTotal - effectiveFutureTotal) < AppConstants.REBALANCE_CONVERGENCE_THRESHOLD) {
            break
        }

        effectiveFutureTotal = realFutureTotal
    }

    return adjustments
}

private fun internalCalculateAdjustments(
    funds: List<Fund>,
    totalAssets: Double,
    futureTotal: Double,
    strategy: RebalanceStrategy,
    newFundVal: Double,
    thresholdPct: Float,
    thresholdMode: Int
): List<AdjustmentItem> {
    if (funds.isEmpty()) {
        return emptyList()
    }

    val targetRatios = funds.associate { it.id to it.targetRatio }
    val normTargets = targetRatios

    val fundAllocations = mutableMapOf<Long, Double>()

    val safeThreshold = if (thresholdMode == 0) {
        thresholdPct.coerceIn(
            AppConstants.THRESHOLD_PERCENT_MIN.toFloat(),
            AppConstants.THRESHOLD_PERCENT_MAX.toFloat()
        )
    } else {
        thresholdPct.coerceIn(
            AppConstants.THRESHOLD_POINT_MIN.toFloat(),
            AppConstants.THRESHOLD_POINT_MAX.toFloat()
        )
    }

    val deviations = funds.associate { fund ->
        val targetRatio = targetRatios[fund.id] ?: 0.0
        val finalDev: Double = if (thresholdMode == 0) {
            max(targetRatio * (safeThreshold / AppConstants.PERCENTAGE_BASE), AppConstants.MIN_ABS_DEVIATION)
        } else {
            (safeThreshold / AppConstants.PERCENTAGE_BASE).toDouble()
        }
        fund.id to finalDev
    }

    data class FundGap(val fundId: Long, val gap: Double, val isOutsideThreshold: Boolean)
    val gaps = funds.map { fund ->
        val targetVal = futureTotal * (normTargets[fund.id] ?: 0.0)
        val currentVal = fund.holdingQuantity * fund.currentPrice

        val currentRatio = if (futureTotal > 0) currentVal / futureTotal else 0.0
        val normTarget = normTargets[fund.id] ?: 0.0
        val allowedDev = deviations[fund.id] ?: 0.0
        val isOutside = abs(currentRatio - normTarget) > allowedDev

        FundGap(fund.id, targetVal - currentVal, isOutside)
    }

    if (strategy == RebalanceStrategy.FULL_REBALANCE) {
        gaps.forEach { fundAllocations[it.fundId] = it.gap }
    } else {
        if (newFundVal >= 0) {
            val lowerBoundGaps = gaps.map { gap ->
                val fund = funds.find { it.id == gap.fundId }!!
                val normTarget = normTargets[fund.id] ?: 0.0
                val allowedDev = deviations[fund.id] ?: 0.0
                val lowerBound = normTarget - allowedDev
                val currentRatio = if (futureTotal > 0) (fund.holdingQuantity * fund.currentPrice) / futureTotal else 0.0

                val isBelowLower = currentRatio < lowerBound
                gap to isBelowLower
            }

            val criticalGaps = lowerBoundGaps.filter { it.second }.map { it.first }
            val otherPosGaps = lowerBoundGaps.filter { !it.second && it.first.gap > 0 }.map { it.first }

            var remaining = newFundVal

            val totalCritical = criticalGaps.sumOf { it.gap }
            if (remaining >= totalCritical && totalCritical > 0) {
                criticalGaps.forEach { fundAllocations[it.fundId] = it.gap }
                remaining -= totalCritical

                val totalOther = otherPosGaps.sumOf { it.gap }
                if (remaining >= totalOther && totalOther > 0) {
                    otherPosGaps.forEach { fundAllocations[it.fundId] = it.gap }
                    remaining -= totalOther

                    funds.forEach { fund ->
                        val ratio = normTargets[fund.id] ?: 0.0
                        fundAllocations[fund.id] = (fundAllocations[fund.id] ?: 0.0) + remaining * ratio
                    }
                } else if (totalOther > 0) {
                    otherPosGaps.forEach { item ->
                        fundAllocations[item.fundId] = remaining * (item.gap / totalOther)
                    }
                } else {
                    funds.forEach { fund ->
                        val ratio = normTargets[fund.id] ?: 0.0
                        fundAllocations[fund.id] = (fundAllocations[fund.id] ?: 0.0) + remaining * ratio
                    }
                }
            } else if (totalCritical > 0) {
                criticalGaps.forEach { item ->
                    fundAllocations[item.fundId] = remaining * (item.gap / totalCritical)
                }
            } else {
                val totalPosGap = gaps.filter { it.gap > 0 }.sumOf { it.gap }
                if (remaining >= totalPosGap && totalPosGap > 0) {
                    gaps.filter { it.gap > 0 }.forEach { fundAllocations[it.fundId] = it.gap }
                    remaining -= totalPosGap
                    funds.forEach { fund ->
                        val ratio = normTargets[fund.id] ?: 0.0
                        fundAllocations[fund.id] = (fundAllocations[fund.id] ?: 0.0) + remaining * ratio
                    }
                } else if (totalPosGap > 0) {
                    gaps.filter { it.gap > 0 }.forEach { item ->
                        fundAllocations[item.fundId] = remaining * (item.gap / totalPosGap)
                    }
                } else {
                    funds.forEach { fund ->
                        val ratio = normTargets[fund.id] ?: 0.0
                        fundAllocations[fund.id] = remaining * ratio
                    }
                }
            }
        } else {
            val negGaps = gaps.filter { it.gap < 0 }
            val totalNegGap = negGaps.sumOf { it.gap }
            var remaining = newFundVal

            if (remaining <= totalNegGap && totalNegGap < 0) {
                negGaps.forEach { fundAllocations[it.fundId] = it.gap }
                remaining -= totalNegGap
                funds.forEach { fund ->
                    val ratio = normTargets[fund.id] ?: 0.0
                    fundAllocations[fund.id] = (fundAllocations[fund.id] ?: 0.0) + remaining * ratio
                }
            } else if (totalNegGap < 0) {
                negGaps.forEach { item ->
                    fundAllocations[item.fundId] = remaining * (item.gap / totalNegGap)
                }
            } else {
                funds.forEach { fund ->
                    val ratio = normTargets[fund.id] ?: 0.0
                    fundAllocations[fund.id] = remaining * ratio
                }
            }
        }
    }

    val items = mutableListOf<AdjustmentItem>()

    val cashFunds = funds.filter { it.type == AssetType.CASH }
    val nonCashFunds = funds.filter { it.type != AssetType.CASH }

    data class Plan(val fund: Fund, val rawShares: Double, var plannedShares: Double, val price: Double)

    val plans = nonCashFunds.mapNotNull { fund ->
        val rawAmount = fundAllocations[fund.id] ?: 0.0
        if (fund.currentPrice > 0) {
            val rawShares = rawAmount / fund.currentPrice
            var planned = round(rawShares / AppConstants.MIN_TRADE_UNIT_DOUBLE) * AppConstants.MIN_TRADE_UNIT_DOUBLE

            if (planned < 0 && abs(planned) > fund.holdingQuantity) {
                val maxSellable = (fund.holdingQuantity / AppConstants.MIN_TRADE_UNIT_DOUBLE).toInt() * AppConstants.MIN_TRADE_UNIT_DOUBLE
                planned = -maxSellable
            }
            Plan(fund, rawShares, planned, fund.currentPrice)
        } else null
    }

    val existingCash = cashFunds.sumOf { it.holdingQuantity * it.currentPrice }

    fun calculateProjectedCash(): Double {
        val nonCashUsed = plans.sumOf { it.plannedShares * it.price }
        return existingCash + newFundVal - nonCashUsed
    }

    var projectedCash = calculateProjectedCash()

    if (projectedCash < -1.0) {
        while (projectedCash < -1.0) {
            val candidates = plans.filter { plan ->
                if (plan.plannedShares > 0) true
                else abs(plan.plannedShares - AppConstants.MIN_TRADE_UNIT_DOUBLE) <= plan.fund.holdingQuantity
            }

            if (candidates.isEmpty()) break

            val bestCandidate = candidates.minByOrNull { plan ->
                val currentDev = abs(plan.plannedShares - plan.rawShares)
                val nextDev = abs((plan.plannedShares - AppConstants.MIN_TRADE_UNIT_DOUBLE) - plan.rawShares)
                nextDev - currentDev
            }

            if (bestCandidate != null) {
                bestCandidate.plannedShares -= AppConstants.MIN_TRADE_UNIT_DOUBLE
                projectedCash += AppConstants.MIN_TRADE_UNIT_DOUBLE * bestCandidate.price
            } else {
                break
            }
        }
    }

    var actualNonCashUsed = 0.0
    plans.forEach { plan ->
        val finalShares = plan.plannedShares
        val finalAmount = finalShares * plan.price

        if (abs(finalAmount) > 0.01) {
            actualNonCashUsed += finalAmount

            val currentVal = plan.fund.holdingQuantity * plan.fund.currentPrice
            val currentRatio = if (totalAssets > 0) currentVal / totalAssets * AppConstants.PERCENTAGE_BASE else 0.0
            val targetVal = currentVal + finalAmount
            val targetRatio = if (futureTotal > 0) targetVal / futureTotal * AppConstants.PERCENTAGE_BASE else 0.0

            items.add(AdjustmentItem(
                fundId = plan.fund.id,
                fundName = plan.fund.name,
                assetType = plan.fund.type,
                currentRatio = currentRatio,
                targetRatio = targetRatio,
                idealRatio = (normTargets[plan.fund.id] ?: 0.0) * AppConstants.PERCENTAGE_BASE,
                adjustmentAmount = finalAmount,
                changeQuantity = finalShares
            ))
        }
    }

    val totalCashChange = newFundVal - actualNonCashUsed

    if (abs(totalCashChange) > 0.01 && cashFunds.isNotEmpty()) {
        val targetCashFund = cashFunds.first()
        val currentVal = targetCashFund.holdingQuantity * targetCashFund.currentPrice
        val currentRatio = if (totalAssets > 0) currentVal / totalAssets * AppConstants.PERCENTAGE_BASE else 0.0
        val targetVal = currentVal + totalCashChange
        val targetRatio = if (futureTotal > 0) targetVal / futureTotal * AppConstants.PERCENTAGE_BASE else 0.0

        items.add(AdjustmentItem(
            fundId = targetCashFund.id,
            fundName = targetCashFund.name,
            assetType = targetCashFund.type,
            currentRatio = currentRatio,
            targetRatio = targetRatio,
            idealRatio = (normTargets[targetCashFund.id] ?: 0.0) * AppConstants.PERCENTAGE_BASE,
            adjustmentAmount = totalCashChange,
            changeQuantity = totalCashChange
        ))
    }

    items.sortBy { item -> funds.indexOfFirst { it.id == item.fundId } }

    return items
}

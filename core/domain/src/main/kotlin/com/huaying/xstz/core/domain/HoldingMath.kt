package com.huaying.xstz.core.domain

import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.PortfolioSummary
import com.huaying.xstz.core.model.ThresholdMode
import kotlin.math.abs

/**
 * 持仓计算内核。
 *
 * 全部是纯函数：入参 -> 出参，不碰数据库、不碰时钟、不碰协程。
 * 旧版把同样的逻辑写成 244 行的 object，且强耦合数据库实体，导致 38 个测试里
 * 一大半在手工拼假实体。这里只依赖不可变的 [Holding]，测试可以一行一个 case。
 */
object HoldingMath {

    /** 组合汇总 */
    fun summarize(holdings: List<Holding>): PortfolioSummary {
        if (holdings.isEmpty()) return PortfolioSummary()
        var marketValue = 0L
        var cost = 0L
        var dayProfit = 0L
        var activeCount = 0
        for (h in holdings) {
            marketValue += h.marketValue.micros
            cost += h.cost.micros
            dayProfit += h.dayProfit.micros
            if (!h.shares.isZero) activeCount++
        }
        return PortfolioSummary(
            totalMarketValue = Money(marketValue),
            totalCost = Money(cost),
            dayProfit = Money(dayProfit),
            holdingCount = activeCount,
        )
    }

    /** 某资产大类的市值合计 */
    fun valueOfType(holdings: List<Holding>, type: AssetType): Money =
        Money(holdings.filter { it.type == type }.sumOf { it.marketValue.micros })

    /** 某资产大类的实际占比 */
    fun ratioOfType(holdings: List<Holding>, type: AssetType): Double {
        val total = summarize(holdings).totalMarketValue
        if (total.isZero) return 0.0
        return valueOfType(holdings, type).micros.toDouble() / total.micros
    }

    /**
     * 每个持仓的展示占比，key 为 fundId。
     * 份额为 0 的持仓（刚添加还没买入）不参与分母，否则会把其他持仓的占比整体压低。
     */
    fun displayRatios(holdings: List<Holding>): Map<Long, Double> {
        val total = holdings.filter { !it.shares.isZero }.sumOf { it.marketValue.micros }
        if (total == 0L) return holdings.associate { it.fundId to 0.0 }
        return holdings.associate { h ->
            h.fundId to if (h.shares.isZero) 0.0 else h.marketValue.micros.toDouble() / total
        }
    }

    /**
     * 相对偏离 = (当前占比 - 目标占比) / 目标占比。
     *
     * 目标为 0 时：当前也接近 0 → 0；否则返回 [Double.MAX_VALUE]，
     * 表示"多出了本不该存在的仓位"。返回无穷而不是抛异常，是因为这个值会直接进排序，
     * 无穷天然排在最前面，正好是"最该处理的那一项"。
     */
    fun deviationRatio(currentRatio: Double, targetRatio: Double): Double = when {
        targetRatio <= 0.0 -> if (abs(currentRatio) < 1e-9) 0.0 else Double.MAX_VALUE
        else -> (currentRatio - targetRatio) / targetRatio
    }

    /** 绝对偏离（占比之差），用于 ABSOLUTE 阈值模式 */
    fun deviationAbsolute(currentRatio: Double, targetRatio: Double): Double =
        abs(currentRatio - targetRatio)

    /**
     * 按模式计算偏离值，两种模式的返回值量纲不同（一个百分点、一个比例），
     * 但都直接与用户设定的阈值比较，所以调用方只需关心"是否 >= 阈值"。
     */
    fun deviation(
        currentRatio: Double,
        targetRatio: Double,
        mode: ThresholdMode,
    ): Double = when (mode) {
        ThresholdMode.ABSOLUTE -> deviationAbsolute(currentRatio, targetRatio)
        ThresholdMode.RELATIVE -> deviationRatio(currentRatio, targetRatio)
    }

    /**
     * 是否需要再平衡：任一持仓偏离超过阈值即 true。
     * 目标占比为 0 的持仓不参与判定——它们要么是"关注列表"，要么是待清理项，
     * 不应该让整个组合永远处于"需要再平衡"状态。
     */
    fun needsRebalance(
        holdings: List<Holding>,
        thresholdPct: Double,
        mode: ThresholdMode,
    ): Boolean = holdings.any { h ->
        val current = currentRatioOf(h, holdings)
        h.targetRatio > 0.0 && deviation(current, h.targetRatio, mode) >= thresholdPct
    }

    /** 是否存在严重偏离（阈值的 2 倍），用于首页告警 */
    fun hasSevereDeviation(
        holdings: List<Holding>,
        thresholdPct: Double,
        mode: ThresholdMode,
    ): Boolean = holdings.any { h ->
        val current = currentRatioOf(h, holdings)
        h.targetRatio > 0.0 && deviation(current, h.targetRatio, mode) >= thresholdPct * 2
    }

    /** 单个持仓在组合中的实际占比 */
    fun currentRatioOf(holding: Holding, holdings: List<Holding>): Double {
        val total = holdings.filter { !it.shares.isZero }.sumOf { it.marketValue.micros }
        if (total == 0L) return 0.0
        return holding.marketValue.micros.toDouble() / total
    }
}

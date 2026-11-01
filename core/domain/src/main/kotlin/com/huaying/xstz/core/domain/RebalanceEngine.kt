package com.huaying.xstz.core.domain

import com.huaying.xstz.core.model.ActionType
import com.huaying.xstz.core.model.AdjustmentItem
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.RebalancePlan
import com.huaying.xstz.core.model.RebalanceStrategy
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.ThresholdMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * 再平衡引擎。
 *
 * 三种策略的语义：
 *
 *   SELL_ONLY  只卖出超配项，卖出所得转入现金，不主动买入。
 *              适合"不想再投钱、只想降风险"的人。
 *
 *   BALANCED   卖出超配、买入低配，买卖金额相等，总市值不变。
 *              最容易算错的一种：直接按差额下单会让买入总额大于卖出总额，等于偷偷加仓。
 *
 *   FULL       所有项都拉回目标权重，差额由外部资金补足或转入现金。
 *
 * 为什么不用旧版的迭代收敛：
 *   旧实现用一个最多 N 次的循环去逼近，收敛阈值是魔法数，且没有测试证明收敛。
 *   这里改成闭式解——先算差额，再按策略做一次配平。结果确定、可穷举测试、无收敛问题。
 */
object RebalanceEngine {

    private const val MICRO = 10_000.0

    /** 中间态：一条持仓的目标与差额 */
    private data class Draft(
        val holding: Holding,
        val targetRatio: Double,
        val targetValue: Money,
        val delta: Money,
        val currentRatio: Double,
    )

    fun plan(
        holdings: List<Holding>,
        strategy: RebalanceStrategy,
        thresholdPct: Double = 0.05,
        mode: ThresholdMode = ThresholdMode.ABSOLUTE,
        externalCash: Money = Money.ZERO,
        now: Long = System.currentTimeMillis(),
    ): RebalancePlan {
        if (holdings.isEmpty()) {
            return RebalancePlan(strategy, emptyList(), Money.ZERO, Money.ZERO, now)
        }

        val targets = normalizeTargets(holdings)
        val totalValue = Money(holdings.sumOf { it.marketValue.micros }) + externalCash

        // 1. 每条持仓的目标市值与差额
        val drafts = holdings.map { h ->
            val targetRatio = targets.getValue(h.fundId)
            val targetValue = Money((totalValue.micros * targetRatio).roundToLong())
            val currentRatio = if (totalValue.isZero) 0.0
            else h.marketValue.micros.toDouble() / totalValue.micros
            Draft(h, targetRatio, targetValue, targetValue - h.marketValue, currentRatio)
        }

        // 2. 只有偏离超过阈值的才动手
        val actionable = drafts.filter { d -> d.targetRatio > 0.0 && exceeds(d, thresholdPct, mode) }

        // 3. 按策略配平资金
        val deltas = when (strategy) {
            RebalanceStrategy.SELL_ONLY -> sellOnly(actionable)
            RebalanceStrategy.BALANCED -> balanced(actionable)
            RebalanceStrategy.FULL -> actionable.associate { it.holding.fundId to it.delta }
        }

        // 4. 组装
        val items = drafts.map { d ->
            val delta = deltas[d.holding.fundId] ?: Money.ZERO
            AdjustmentItem(
                fundId = d.holding.fundId,
                code = d.holding.code,
                name = d.holding.name,
                type = d.holding.type,
                currentValue = d.holding.marketValue,
                currentRatio = d.currentRatio,
                targetRatio = d.targetRatio,
                targetValue = d.targetValue,
                delta = delta,
                deviation = HoldingMath.deviationRatio(d.currentRatio, d.targetRatio),
                action = when {
                    delta.micros > 0 -> ActionType.BUY
                    delta.micros < 0 -> ActionType.SELL
                    else -> ActionType.HOLD
                },
            )
        }

        val buyTotal = items.filter { it.action == ActionType.BUY }.sumOf { it.delta.micros }
        val sellTotal = items.filter { it.action == ActionType.SELL }.sumOf { -it.delta.micros }

        return RebalancePlan(
            strategy = strategy,
            items = items,
            totalValue = totalValue,
            cashDelta = Money(sellTotal - buyTotal),
            generatedAt = now,
        )
    }

    private fun exceeds(d: Draft, thresholdPct: Double, mode: ThresholdMode): Boolean {
        val dev = when (mode) {
            ThresholdMode.ABSOLUTE -> abs(d.currentRatio - d.targetRatio)
            ThresholdMode.RELATIVE -> HoldingMath.deviationRatio(d.currentRatio, d.targetRatio)
        }
        return dev >= thresholdPct
    }

    /**
     * 目标权重处理。
     *
     * 用户填的目标经常加起来不等于 1（只填 80%，剩下 20% 是现金）——
     * 这种情况**保留原值**，差额隐含就是现金。
     * 只有总和超过 1（如误填 120%）才按比例压回 100%，
     * 否则目标市值之和会凭空大于总资产，方案根本不成立。
     */
    internal fun normalizeTargets(holdings: List<Holding>): Map<Long, Double> {
        val sanitized = holdings.associate { it.fundId to it.targetRatio.coerceAtLeast(0.0) }
        val sum = sanitized.values.sum()
        if (sum <= 1.0 + 1e-9) return sanitized
        return sanitized.mapValues { (_, v) -> v / sum }
    }

    /**
     * SELL_ONLY：只保留卖出，买入归零。
     *
     * 注意：这里**不**对现金类资产做特殊处理。
     * 旧实现里有一句 `&& !type.isCashLike`，本意是"应急金不动"，但样本里的货币基金
     * 也归在 CASH 类型下、且目标只有 5%（实际 22%）——它正是"现金类超配"的典型场景，
     * 用户恰恰希望在 SELL_ONLY 下把它卖掉、把现金腾出来。那句守卫会让 SELL_ONLY 与
     * BALANCED 行为不一致：BALANCED 会卖超配的货币基金，SELL_ONLY 却不动，于是 SELL_ONLY
     * 下"需卖出"变成 0、顶部还报"所有持仓都在阈值内"，与事实矛盾（基金明明超配 17pp）。
     *
     * 真正"不该卖"的是 value 为 0、低于目标的应急金——它 delta 为正，本就不会被卖，
     * 所以去掉守卫后行为正确，且 `仅卖出策略不动现金` 用例（现金持仓 value=0）依然成立。
     */
    private fun sellOnly(actionable: List<Draft>): Map<Long, Money> =
        actionable.associate { d ->
            val sell = if (d.delta.isNegative) d.delta else Money.ZERO
            d.holding.fundId to sell
        }

    /**
     * BALANCED：买卖配平，总市值不变。
     *
     * 规则（业务约束，不是优化技巧）：
     *   * 买入总额 ≤ 卖出总额，用户不需要额外掏钱
     *   * 卖出总额也不超过买入需求，否则用户被动减仓
     * 因此两侧同时按较小侧等比缩放。
     */
    private fun balanced(actionable: List<Draft>): Map<Long, Money> {
        var buyDemand = 0L
        var sellSupply = 0L
        for (d in actionable) {
            if (d.delta.micros > 0) buyDemand += d.delta.micros else sellSupply += -d.delta.micros
        }
        if (buyDemand == 0L) {
            return actionable.associate { it.holding.fundId to it.delta }
        }
        val scale = if (buyDemand <= sellSupply) {
            // 卖得多买得少：把卖出缩到与买入相等，多出的仓位留着（用户不想被动减仓那么多）
            buyDemand.toDouble() / sellSupply
        } else {
            // 买得多卖得少：按可用卖出资金等比缩减买入
            sellSupply.toDouble() / buyDemand
        }
        return actionable.associate { d ->
            val scaled = if (d.delta.micros > 0) {
                if (buyDemand <= sellSupply) d.delta.micros else (d.delta.micros * scale).roundToLong()
            } else {
                if (buyDemand <= sellSupply) (d.delta.micros * scale).roundToLong() else d.delta.micros
            }
            d.holding.fundId to Money(scaled)
        }
    }

    /**
     * 差额换算成可下单份额。
     *
     * 量纲必须盯死：delta 是 Money（1e-4 元），nav 是 Nav（1e-4 元/份），
     * 两者单位相同，直接相除就是"份"的实数，**不能再乘 1e4**。
     * （早期版本多乘了一次 MICRO，结果 1000 元算出 300 万份，这里留注释防回归。）
     *
     * 向下取整到 0.01 份：宁可少买一点，也不要因为四舍五入超出可用资金导致下单失败。
     */
    fun sharesToTrade(delta: Money, nav: Nav): Shares {
        if (nav.micros <= 0L || delta.isZero) return Shares.ZERO
        val rawShares = delta.micros.toDouble() / nav.micros.toDouble()
        val floored = floor(rawShares * 100.0) / 100.0
        return Shares.fromDouble(floored)
    }
}

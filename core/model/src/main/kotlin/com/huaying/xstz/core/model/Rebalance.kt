package com.huaying.xstz.core.model

/** 再平衡策略：三种用户心智不同的调仓哲学 */
enum class RebalanceStrategy(val displayName: String, val description: String) {
    /** 只卖出超配，不主动加仓——保守，适合不想追加资金的人 */
    SELL_ONLY("仅卖出", "只削减超配项，不做买入，不追加资金"),

    /** 超配卖出、低配买入，总仓位不变——最常用 */
    BALANCED("均衡调仓", "卖出超配、买入低配，总投入不变"),

    /** 按目标权重全额重建，允许追加资金到低配项 */
    FULL("完全对齐", "所有资产都拉回目标权重，可能需要追加资金"),
}

/** 调仓动作 */
enum class ActionType { BUY, SELL, HOLD }

/**
 * 单条调仓建议。
 *
 * 之所以把「偏离」与「建议动作」分开放：
 * 偏离是事实（客观），动作是策略产物（主观）。混在一起会导致换策略时历史记录失去意义。
 */
data class AdjustmentItem(
    val fundId: Long,
    val code: String,
    val name: String,
    val type: AssetType,
    /** 当前市值 */
    val currentValue: Money,
    /** 当前占比 */
    val currentRatio: Double,
    /** 目标占比 */
    val targetRatio: Double,
    /** 目标市值 = 总资产 × 目标占比 */
    val targetValue: Money,
    /** 差额 = 目标 - 当前；正数表示需要买入 */
    val delta: Money,
    /** 偏离度 = (当前 - 目标) / 目标 */
    val deviation: Double,
    val action: ActionType,
) {
    /** 是否超过阈值需要操作 */
    fun exceedsThreshold(thresholdPct: Double): Boolean = kotlin.math.abs(deviation) >= thresholdPct
}

/**
 * 一份调仓方案。
 *
 * @param totalValue 参与再平衡的总资产（现金也算，否则权重永远加不到 100%）
 * @param cashDelta 方案执行后的现金变化：卖出净流入为正、买入净流出为负
 */
data class RebalancePlan(
    val strategy: RebalanceStrategy,
    val items: List<AdjustmentItem>,
    val totalValue: Money,
    val cashDelta: Money,
    val generatedAt: Long,
) {
    val buyItems: List<AdjustmentItem> get() = items.filter { it.action == ActionType.BUY }
    val sellItems: List<AdjustmentItem> get() = items.filter { it.action == ActionType.SELL }

    val totalBuy: Money
        get() = buyItems.fold(Money.ZERO) { acc, i -> acc + i.delta }

    val totalSell: Money
        get() = sellItems.fold(Money.ZERO) { acc, i -> acc + i.delta.abs() }

    val isEmpty: Boolean get() = items.none { it.action != ActionType.HOLD }
}

/** 目标仓位一条 */
data class TargetAllocation(
    val fundId: Long,
    val code: String,
    val name: String,
    val ratio: Double,
)

/** 偏离告警阈值模式 */
enum class ThresholdMode {
    /** 绝对偏离：|当前 - 目标| >= 阈值 个百分点 */
    ABSOLUTE,
    /** 相对偏离：(当前 - 目标) / 目标 >= 阈值 */
    RELATIVE,
}

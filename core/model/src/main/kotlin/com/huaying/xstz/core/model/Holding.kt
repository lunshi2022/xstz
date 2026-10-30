package com.huaying.xstz.core.model


/**
 * 一笔基金/股票持仓。
 *
 * 与旧版的关键差异：
 *   1. 份额、成本用定点类型（Shares / Money），不再用 Double → 跨端计算结果一致
 *   2. 成本单价由 totalCost / quantity 推导，不单独存储 → 不可能出现"改份额忘改单价"的脏数据
 *   3. 现金（现金类基金）用 type = CASH 表达，净值恒为 1，市值 == 份额
 */
data class Holding(
    val fundId: Long,
    val code: String,
    val name: String,
    val type: AssetType,
    /** 持有份额 */
    val shares: Shares,
    /** 累计投入本金（买入金额之和，不含已实现盈亏） */
    val cost: Money,
    /** 当前净值 */
    val nav: Nav,
    /** 上一交易日净值，用于算当日涨跌 */
    val prevNav: Nav,
    /** 目标占比 0.0~1.0 */
    val targetRatio: Double,
) {
    /** 当前市值 = 净值 × 份额 */
    val marketValue: Money = nav * shares

    /** 累计盈亏 = 市值 - 成本 */
    val profit: Money = marketValue - cost

    /** 累计收益率；成本为 0 时返回 0 而不是无穷 */
    val profitRate: Double
        get() = if (cost.isZero) 0.0 else profit.micros.toDouble() / cost.micros

    /** 当日盈亏 = (今日净值 - 昨日净值) × 份额 */
    val dayProfit: Money
        get() = if (prevNav.isZero) Money.ZERO else (nav - prevNav) * shares

    /** 当日涨跌幅 */
    val dayChangeRate: Double
        get() = if (prevNav.isZero) 0.0 else (nav.micros - prevNav.micros).toDouble() / prevNav.micros

    /** 成本单价 = 成本 / 份额；份额为 0 时返回 0 */
    val costPrice: Money
        get() = if (shares.isZero) Money.ZERO
        else Money((cost.micros.toDouble() / shares.toDouble()).toLong())
}

/**
 * 组合汇总。总市值、总成本、当日盈亏、总收益率。
 *
 * 注意：这里所有字段都是**派生值**，不落库。
 * 旧版把这些写进数据库，导致每次改一笔交易要重算并回写多张表，是数据不一致的主因。
 */
data class PortfolioSummary(
    val totalMarketValue: Money = Money.ZERO,
    val totalCost: Money = Money.ZERO,
    val dayProfit: Money = Money.ZERO,
    val holdingCount: Int = 0,
) {
    val totalProfit: Money get() = totalMarketValue - totalCost

    val totalProfitRate: Double
        get() = if (totalCost.isZero) 0.0 else totalProfit.micros.toDouble() / totalCost.micros

    val dayProfitRate: Double
        get() {
            val yesterday = totalMarketValue - dayProfit
            return if (yesterday.isZero) 0.0 else dayProfit.micros.toDouble() / yesterday.micros
        }

    /** 某资产类别占比 */
    fun ratioOf(value: Money): Double =
        if (totalMarketValue.isZero) 0.0
        else value.micros.toDouble() / totalMarketValue.micros
}

/** 交易方向 */
enum class TradeType {
    BUY,
    SELL,
    DIVIDEND;

    val displayName: String
        get() = when (this) {
            BUY -> "买入"
            SELL -> "卖出"
            DIVIDEND -> "分红"
        }
}

/**
 * 一笔交易记录。
 *
 * 手续费单独存，不从金额里扣——审计时需要区分"我付了多少"和"券商赚了多少"。
 */
data class Trade(
    val id: Long = 0L,
    val fundId: Long,
    val type: TradeType,
    val shares: Shares,
    val nav: Nav,
    val fee: Money = Money.ZERO,
    /** 成交金额 = 份额 × 净值（不含费） */
    val amount: Money = nav * shares,
    /** 交易日期，epochDay */
    val epochDay: Long,
    val note: String = "",
    val createdAt: Long = 0L,
) {
    /** 含费实际收支：买入是支出（正），卖出是收入（正），分红是收入（正） */
    val cashFlow: Money
        get() = when (type) {
            TradeType.BUY -> amount + fee
            TradeType.SELL -> amount - fee
            TradeType.DIVIDEND -> amount - fee
        }
}

/** 净值点位 */
data class NavPoint(
    val epochDay: Long,
    val nav: Nav,
    val accumulatedNav: Nav = nav,
)

/** 日历上的单日盈亏 */
data class DailyPnl(
    val epochDay: Long,
    val profit: Money,
    val marketValue: Money,
)

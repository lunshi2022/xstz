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
    /**
     * 标的类型（A股 / ETF / 债券 / 基金 / 现金）。
     *
     * 与 [type] 分开存：type 用于配置比例与再平衡，kind 用于展示标签和选择行情接口。
     * 没有它就无法区分"510300 沪深300ETF"和"110022 易方达消费"——
     * 两者 type 都是 STOCK，但一个走场内行情、一个走场外估值。
     */
    val kind: InstrumentKind = InstrumentKind.FUND,
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

/**
 * 交易方向。
 *
 * 五类（方案阶段 3 第 5 项「买入/卖出/再平衡/追加/取出 五类流水」）：
 *
 * | 类型 | 语义 | 对持仓的影响 |
 * |---|---|---|
 * | [BUY] | 买入，用现金换取份额 | 份额↑ 成本↑ 现金↓ |
 * | [SELL] | 卖出，份额换回现金 | 份额↓ 成本↓ 现金↑ |
 * | [DIVIDEND] | 分红，份额不变、现金增加 | 份额不变 成本↓（或不变）现金↑ |
 * | [REBALANCE] | 再平衡产生的调仓，本质是买卖但需单独归因 | 同 BUY/SELL，按 `shares` 符号判定方向 |
 * | [ADD_CASH] | 追加资金（转入），不影响任何标的份额 | 仅现金↑ |
 * | [WITHDRAW] | 取出资金（转出） | 仅现金↓ |
 *
 * 为什么 [REBALANCE] 不合并进 BUY/SELL：
 *   再平衡的买卖是**算法驱动**的，用户看流水时最想区分"我自己买的"
 *   和"系统建议我调仓的"。合并后这个信息永久丢失，且无法事后还原。
 *   旧版 P2-10 的教训正是这类：`ADD_FUNDS` 被映射成"买入"，
 *   用户看到错误的类型——归因信息的价值就在这里。
 *
 * 为什么 [ADD_CASH]/[WITHDRAW] 是独立类型而非"买/卖一个现金标的"：
 *   追加资金不是交易，没有对手方、没有价格、不产生手续费。
 *   强行建模成交易会让持仓表里出现一条奇怪的"现金持仓"，
 *   并污染收益率计算的分母。
 */
enum class TradeType {
    BUY,
    SELL,
    DIVIDEND,
    REBALANCE,
    ADD_CASH,
    WITHDRAW;

    val displayName: String
        get() = when (this) {
            BUY -> "买入"
            SELL -> "卖出"
            DIVIDEND -> "分红"
            REBALANCE -> "再平衡"
            ADD_CASH -> "追加资金"
            WITHDRAW -> "取出资金"
        }

    /** 是否为现金进出（不涉及标的份额） */
    val isCashFlow: Boolean
        get() = this == ADD_CASH || this == WITHDRAW

    /** 是否为买入方向（份额增加）。REBALANCE 需按金额符号判定，不在此列 */
    val isInflow: Boolean
        get() = this == BUY
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
    /**
     * 含费实际收支（**统一为"现金流入为正"**）。
     *
     * 符号约定很关键：下游的持仓与快照重放全靠它判断现金增减。
     * 旧版这里对 BUY 返回正数（把支出当正），语义混乱，
     * 导致任何用到 cashFlow 的地方都要额外记一个"买入要取反"的心智负担。
     *
     * 这里统一为会计口径：
     *   * 钱进来（卖出、分红、追加、再平衡减仓）→ 正
     *   * 钱出去（买入、再平衡加仓、取出）    → 负
     *
     * [TradeType.REBALANCE] 的金额符号由 [amount] 的正负携带
     * （加仓为负、减仓为正），所以直接返回 amount - fee。
     */
    val cashFlow: Money
        get() = when (type) {
            TradeType.BUY -> -(amount + fee)
            TradeType.SELL -> amount - fee
            TradeType.DIVIDEND -> amount - fee
            TradeType.REBALANCE -> amount - fee
            TradeType.ADD_CASH -> amount
            TradeType.WITHDRAW -> -amount
        }
}

/** 净值点位 */
data class NavPoint(
    val epochDay: Long,
    val nav: Nav,
    val accumulatedNav: Nav = nav,
)

/**
 * 组合级每日快照。
 *
 * 方案 §5.1「净值记录」的数据模型：**按日快照总资产/本金/收益/收益率/四类资产市值，
 * 作为图表与日历的唯一数据源**。
 *
 * 与单只基金的 [NavPoint] 的分工：
 *   * [NavPoint] 是**因**——某只标的在某天的净值是多少
 *   * 本类是**果**——整个组合在某天值多少钱、赚了多少
 *
 * 派生量（累计收益、收益率）不落库，由 [profit] 与 [profitRate] 现场算——
 * 与 [PortfolioSummary] 同一原则：派生值存两处必然出现不一致。
 */
data class NetValueSnapshot(
    val epochDay: Long,
    /** 当日总资产 */
    val totalValue: Money,
    /** 当日累计本金 */
    val principal: Money,
    /** 当日盈亏（不含申赎影响） */
    val dayProfit: Money,
    /** 股票类市值 */
    val stockValue: Money = Money.ZERO,
    /** 债券类市值 */
    val bondValue: Money = Money.ZERO,
    /** 商品类市值 */
    val commodityValue: Money = Money.ZERO,
    /** 现金类市值 */
    val cashValue: Money = Money.ZERO,
) {
    /** 累计收益 = 总资产 - 本金 */
    val profit: Money get() = totalValue - principal

    /** 累计收益率；本金为 0 时返回 0 而不是无穷 */
    val profitRate: Double
        get() = if (principal.isZero) 0.0 else profit.micros.toDouble() / principal.micros

    /** 当日收益率：以昨收市值为基数 */
    val dayProfitRate: Double
        get() {
            val yesterday = totalValue - dayProfit
            return if (yesterday.isZero) 0.0 else dayProfit.micros.toDouble() / yesterday.micros
        }

    /** 某一资产大类的市值 */
    fun valueOf(type: AssetType): Money = when (type) {
        AssetType.STOCK -> stockValue
        AssetType.BOND -> bondValue
        AssetType.COMMODITY -> commodityValue
        AssetType.CASH -> cashValue
    }

    /** 某一资产大类的占比 */
    fun ratioOf(type: AssetType): Double {
        if (totalValue.isZero) return 0.0
        return valueOf(type).micros.toDouble() / totalValue.micros
    }
}

/** 日历上的单日盈亏 */
data class DailyPnl(
    val epochDay: Long,
    val profit: Money,
    val marketValue: Money,
)

package com.huaying.xstz.core.domain

import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.NetValueSnapshot
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType

/**
 * 组合快照重算器。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么需要这个
 * ══════════════════════════════════════════════════════════════════
 * 方案 §5.1 要求「净值记录：按日快照（总资产/本金/收益/收益率/四类市值），
 * 作为图表与日历唯一数据源」。而图表与日历要回答的是**组合级**的问题：
 *
 *   * 「我的组合这个月赚了多少？」——需要每天的总资产与本金
 *   * 「3 月 15 号那天是赚是亏？」——需要每天的当日盈亏
 *
 * 但持仓表 `holdings` 只存**当前**状态（份额、成本、最新净值），
 * 净值表 `nav_history` 只存**单只标的**的净值。两个表都推不出组合的历史：
 *
 *   1. **份额会变**。3 月买过、5 月卖过，那 3 月的总资产按今天的份额算是错的。
 *   2. **成本会变**。卖出一部分要按比例结转成本，净���表里没有成本信息。
 *   3. **当日盈亏不能靠差值**。`今天总资产 - 昨天总资产` 里混着追加/取出的资金，
 *      追加 1 万会被算成"赚了 1 万"。
 *
 * 所以必须**从流水重放**：交易流水 `trades` 记录了每一次份额与成本的变化，
 * 把它按日期回放，配合每日的净值，就能还原出任意一天的真实持仓状态。
 *
 * ══════════════════════════════════════════════════════════════════
 * 回放算法
 * ══════════════════════════════════════════════════════════════════
 * 对每个交易日 D：
 *
 *   1. 先把 D 当天及之前的所有交易按时间顺序作用到"当前状态"上，
 *      得到 D 收盘时的（份额，成本）
 *   2. 用 D 当天的净值算出每只持仓的市值
 *   3. 合计出总资产、本金、四类市值
 *   4. 当日盈亏 = Σ(份额 × (今日净值 - 昨日净值))
 *      —— 注意是**逐只算涨跌再相加**，而不是总资产的差值。
 *      这样追加/取出资金不会被算成盈亏。
 *
 * 复杂度 O(交易数 + 净值点数)，一天几百条流水、几年净值都是毫秒级。
 */
object SnapshotBuilder {

    /**
     * 单只标的在某一时刻的状态。
     *
     * [realizedProfit] 只在卖出时有值，记录这次卖出兑现的盈亏；
     * 它是"当日盈亏"的已实现部分，与净值涨跌的未实现部分相加才是当天真实盈亏。
     */
    private data class Position(
        val holdingId: Long,
        val shares: Shares = Shares.ZERO,
        val cost: Money = Money.ZERO,
        val realizedProfit: Money? = null,
    )

    /**
     * 从交易流水与净值历史重算出每日组合快照。
     *
     * @param holdings 当前持仓（提供 code / type / 名称）
     * @param trades   全部交易流水，按日期升序
     * @param navs     每只标的的净值历史
     * @param fromDay  起始日（含）
     * @param toDay    结束日（含）
     * @return 按日期升序的每日快照
     */
    fun build(
        holdings: List<Holding>,
        trades: List<Trade>,
        navs: Map<Long, List<NavPoint>>,
        fromDay: Long,
        toDay: Long,
    ): List<NetValueSnapshot> {
        if (holdings.isEmpty() || toDay < fromDay) return emptyList()

        val holdingById = holdings.associateBy { it.fundId }
        val tradesByDay = trades.groupBy { it.epochDay }.toSortedMap()

        // 所有可能出现在区间内的交易日：净值表出现过的日期 ∪ 有交易的日期
        val tradingDays = buildSet {
            navs.values.forEach { points -> points.forEach { add(it.epochDay) } }
            addAll(tradesByDay.keys)
        }.filter { it in fromDay..toDay }.sorted()

        if (tradingDays.isEmpty()) return emptyList()

        // 状态回放器：逐日推进，维护每只标的的 (份额, 成本)
        val positions = HashMap<Long, Position>()
        // 当日已实现盈亏：key 是交易日。由卖出交易产生
        val realizedByDay = HashMap<Long, Long>()
        var tradeCursor = 0
        val sortedTrades = trades.sortedWith(compareBy({ it.epochDay }, { it.id }))

        val out = ArrayList<NetValueSnapshot>(tradingDays.size)

        for (day in tradingDays) {
            // --- 1. 把截至当天的交易作用到状态上 ---
            while (tradeCursor < sortedTrades.size && sortedTrades[tradeCursor].epochDay <= day) {
                val t = sortedTrades[tradeCursor]
                tradeCursor++
                val pos = positions.getOrPut(t.fundId) { Position(t.fundId) }
                val applied = applyTrade(pos, t)
                positions[t.fundId] = applied
                // 卖出的已实现盈亏计入当天
                applied.realizedProfit?.let {
                    realizedByDay[day] = (realizedByDay[day] ?: 0L) + it.micros
                }
            }

            // --- 2. 逐只算市值 ---
            var totalValue = 0L
            var principal = 0L
            var unrealizedProfit = 0L
            var stock = 0L
            var bond = 0L
            var commodity = 0L
            var cash = 0L

            for (holding in holdings) {
                val pos = positions[holding.fundId] ?: continue
                if (pos.shares.isZero) continue

                val points = navs[holding.fundId].orEmpty()
                // 净值兜底：用最后已知净值。
                // 场外基金节假日不更新净值，若某天没有净值点就跳过该持仓，
                // 那天的组合市值会凭空少一块，收益曲线出现一个不该有的坑。
                val today = navOn(points, day) ?: continue
                val yesterday = points.lastOrNull { it.epochDay < day }?.nav

                val marketValue = today * pos.shares
                totalValue += marketValue.micros
                principal += pos.cost.micros

                // 未实现盈亏：份额 × (今日净值 - 昨日净值)
                // 昨天没有净值（首日建仓）时为 0——建仓当天没有"涨跌"可言
                if (yesterday != null && !yesterday.isZero) {
                    val delta = today - yesterday
                    unrealizedProfit += (delta * pos.shares).micros
                }

                when (holding.type) {
                    AssetType.STOCK -> stock += marketValue.micros
                    AssetType.BOND -> bond += marketValue.micros
                    AssetType.COMMODITY -> commodity += marketValue.micros
                    AssetType.CASH -> cash += marketValue.micros
                }
            }

            out += NetValueSnapshot(
                epochDay = day,
                totalValue = Money(totalValue),
                principal = Money(principal),
                // 当日盈亏 = 未实现（净值涨跌）+ 已实现（卖出兑现）
                //
                // 两个都要有：只看未实现的话，"今天全部卖出"这一天会显示 0 盈亏，
                // 而用户当天实实在在赚了钱——日历上那天应该是红的。
                dayProfit = Money(unrealizedProfit + (realizedByDay[day] ?: 0L)),
                stockValue = Money(stock),
                bondValue = Money(bond),
                commodityValue = Money(commodity),
                cashValue = Money(cash),
            )
        }

        return out
    }

    /**
     * 把一笔交易作用到持仓状态上。
     *
     * 卖出的成本按**份额比例结转**，与 TradeRepositoryImpl 保持一致：
     * 卖在高位不该让剩余成本变成负数，也不该把已实现的收益算进本金。
     *
     * @return 作用后的新状态；[Position.realizedProfit] 在卖出时携带当日已实现盈亏
     */
    private fun applyTrade(pos: Position, trade: Trade): Position = when (trade.type) {
        TradeType.BUY -> pos.copy(
            shares = pos.shares + trade.shares,
            cost = pos.cost + trade.amount + trade.fee,
        )

        TradeType.SELL -> {
            val ratio = if (pos.shares.isZero) 0.0
            else trade.shares.toDouble() / pos.shares.toDouble()
            val costReleased = Money((pos.cost.micros * ratio).toLong())
            // 已实现盈亏 = 卖出金额 - 结转成本 - 手续费
            // 手续费是真实支出，必须从收益里扣掉
            val realized = trade.amount - costReleased - trade.fee
            pos.copy(
                shares = pos.shares - trade.shares,
                cost = pos.cost - costReleased,
                realizedProfit = realized,
            )
        }

        // 分红不影响份额与成本：份额不变，成本不变（分红是额外收入）
        TradeType.DIVIDEND -> pos

        /*
         * 再平衡：按份额变化方向复用买入/卖出的记账规则。
         *
         * ⚠️ 语义约定（方案未明确规定，此处按最小假设确定并记录）：
         *   本分支假定 `trade.shares` 是**增量**（与 BUY/SELL 一致），
         *   不是"调仓后的目标持仓"。
         *
         * 选增量语义的理由：
         *   1. 流水表的本质是"记录发生过的动作"，不是"记录状态"。
         *      存目标持仓等于在流水里塞快照，一旦漏记一笔就无法回放。
         *   2. 与既有 BUY/SELL 语义统一，避免同一张表里两种含义。
         *   3. 减仓时 amount 与 shares 的正负能互相校验（都是带符号的）。
         *
         * 因此加仓判定看 shares 为正，减仓看 shares 为负——
         * 用的是符号而不是与 pos.shares 比大小，因为增量可能很小，
         * 比大小会把"减仓 100 份"误判成加仓（当只持有 50 份时）。
         *
         * 归因上仍是 REBALANCE（流水里显示"再平衡"），
         * 但持仓重放必须还原成份额的增减，否则成本结转会错。
         */
        TradeType.REBALANCE -> if (trade.shares.micros >= 0) {
            // 加仓：与 BUY 同规则
            pos.copy(
                shares = pos.shares + trade.shares,
                cost = pos.cost + trade.amount + trade.fee,
            )
        } else {
            // 减仓：与 SELL 同规则（按比例结转成本、计已实现盈亏）
            // 注意 shares 是 Shares 而非 Money——两者都是 @JvmInline value class，
            // 单位同为 1e-4 但类型不同，混用编译器会拦下（这正是分类型的价值）
            val soldShares = Shares(-trade.shares.micros)
            val ratio = if (pos.shares.isZero) 0.0
            else soldShares.toDouble() / pos.shares.toDouble()
            val costReleased = Money((pos.cost.micros * ratio).toLong())
            val realized = trade.amount - costReleased - trade.fee
            pos.copy(
                shares = pos.shares - soldShares,
                cost = pos.cost - costReleased,
                realizedProfit = realized,
            )
        }

        /*
         * 追加/取出资金：与标的持仓完全无关。
         *
         * 这两个类型作用于**现金持仓**（AssetType.CASH，净值恒 1.0），
         * 不是独立的"现金池"字段——本项目没有那个字段。
         * 现金的增减通过改动 CASH 持仓的份额体现，
         * 所以这里对非现金标的而言"不改变持仓"是正确行为。
         *
         * 注意：早期版本的注释写成"由外层汇总 cashFlow 处理"，
         * 与实现不符（外层没有这段逻辑，也不需要）。
         * 那段注释已按实际实现更正，避免后续维护者去找一个不存在的代码。
         */
        TradeType.ADD_CASH, TradeType.WITHDRAW -> pos
    }

    /**
     * 只用当前持仓与当前净值，算出"今天"的快照。
     *
     * 用于实时写入：每次刷新行情后调一次，把当天的组合状态落到表里。
     * 不需要重放历史——历史由 [build] 一次性算出来。
     */
    fun buildToday(
        holdings: List<Holding>,
        epochDay: Long,
    ): NetValueSnapshot {
        var totalValue = 0L
        var principal = 0L
        var dayProfit = 0L
        var stock = 0L
        var bond = 0L
        var commodity = 0L
        var cash = 0L

        for (h in holdings) {
            val mv = h.marketValue
            totalValue += mv.micros
            principal += h.cost.micros
            dayProfit += h.dayProfit.micros
            when (h.type) {
                AssetType.STOCK -> stock += mv.micros
                AssetType.BOND -> bond += mv.micros
                AssetType.COMMODITY -> commodity += mv.micros
                AssetType.CASH -> cash += mv.micros
            }
        }

        return NetValueSnapshot(
            epochDay = epochDay,
            totalValue = Money(totalValue),
            principal = Money(principal),
            dayProfit = Money(dayProfit),
            stockValue = Money(stock),
            bondValue = Money(bond),
            commodityValue = Money(commodity),
            cashValue = Money(cash),
        )
    }

    /**
     * 净值缺失时用最后已知净值兜底。
     *
     * 场外基金节假日不更新净值，如果某天该基金没有净值点，
     * 就沿用上一个交易日的净值——市值为常量，当日盈亏为 0。
     */
    fun navOn(points: List<NavPoint>, day: Long): Nav? =
        points.lastOrNull { it.epochDay <= day }?.nav
}

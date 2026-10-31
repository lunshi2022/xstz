package com.huaying.xstz.core.data.repository

import com.huaying.xstz.core.database.toModel
import com.huaying.xstz.core.database.toEntity
import com.huaying.xstz.core.database.dao.HoldingDao
import com.huaying.xstz.core.database.dao.TransactionDao
import com.huaying.xstz.core.database.dao.TradeDao
import com.huaying.xstz.core.database.entity.OperationLogEntity
import com.huaying.xstz.core.database.entity.TradeEntity
import com.huaying.xstz.core.domain.HoldingMath
import com.huaying.xstz.core.domain.SnapshotBuilder
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.NavRepository
import com.huaying.xstz.core.domain.repository.RefreshResult
import com.huaying.xstz.core.domain.repository.SnapshotRepository
import com.huaying.xstz.core.domain.repository.SnapshotWriter
import com.huaying.xstz.core.domain.repository.TimeRepository
import com.huaying.xstz.core.domain.repository.TradeRepository
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.DataSource
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.InstrumentKind
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.NetValueSnapshot
import com.huaying.xstz.core.model.PortfolioSummary
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType
import com.huaying.xstz.core.network.QuoteSource
import com.huaying.xstz.core.network.SampleData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 持仓仓储实现。
 *
 * 一条原则：**行情刷新与持仓写入走不同语句**。
 * 旧版用 `@Update` 整行覆盖来更新净值，结果在"用户正在录入交易"和"定时刷新行情"
 * 同时发生时，会把刚录入的份额冲掉——这类竞态在真机上极难复现，但一定会发生。
 */
@Singleton
class HoldingRepositoryImpl @Inject constructor(
    private val holdingDao: HoldingDao,
    private val tradeDao: TradeDao,
    private val transactionDao: TransactionDao,
    private val quoteSource: QuoteSource,
    private val navRepository: NavRepository,
) : HoldingRepository {

    override fun observeHoldings(): Flow<List<Holding>> =
        holdingDao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeSummary(): Flow<PortfolioSummary> =
        observeHoldings().map { HoldingMath.summarize(it) }

    override suspend fun getHolding(id: Long): Holding? =
        holdingDao.getById(id)?.toModel()

    override suspend fun addHolding(
        code: String,
        name: String,
        type: AssetType,
        kind: InstrumentKind,
    ): Long {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val sample = SampleData.forCode(code, name, type)
        // 按**用户选定的类型**查一次，不再按代码段猜：
        // kind 来自候选列表，是用户点选的结果，比任何正则都准。
        val live = if (kind == InstrumentKind.CASH) null else runCatching {
            quoteSource.quote(code, kind)
        }.getOrNull()?.takeIf { it.source == DataSource.LIVE }?.value

        val id = holdingDao.insert(
            com.huaying.xstz.core.database.entity.HoldingEntity(
                code = code,
                name = name.ifBlank { live?.name ?: sample.name },
                type = type.name,
                kind = kind.name,
                sharesMicros = 0,
                costMicros = 0,
                navMicros = if (kind == InstrumentKind.CASH) 10_000L
                else Nav.fromDouble(live?.estimatedNav?.toDouble() ?: 0.0).micros,
                prevNavMicros = if (kind == InstrumentKind.CASH) 10_000L
                else Nav.fromDouble(live?.nav?.toDouble() ?: 0.0).micros,
                targetRatio = 0.0,
                navEpochDay = if (live != null || kind == InstrumentKind.CASH) today else 0,
                sortOrder = holdingDao.count(),
                createdAt = now,
                updatedAt = now,
            )
        )
        // 落一个净值起点，走势页添加后立刻有数据可画
        if (live != null) {
            navRepository.saveHistory(
                id,
                listOf(NavPoint(today, Nav.fromDouble(live.estimatedNav.toDouble()))),
            )
        }
        transactionDao.insertLog(
            OperationLogEntity(
                action = "ADD_HOLDING",
                detail = "添加${kind.displayName} $code ${name.ifBlank { live?.name ?: sample.name }}",
                createdAt = now,
            )
        )
        return id
    }

    override suspend fun candidates(code: String): List<com.huaying.xstz.core.model.QuoteCandidate> {
        if (code.length != 6 || code.any { !it.isDigit() }) return emptyList()
        // 清单交给数据源去查：它会同时问场内与场外两个接口，
        // 这样 000001 能同时拿到「平安银行」和「华夏成长」两条，由用户挑
        return runCatching { quoteSource.candidates(code) }.getOrDefault(emptyList())
    }

    /**
     * 名称 / 代码联想搜索。
     *
     * 不查缓存、不查样例：搜索结果是"此刻存在什么"，
     * 缓存会让已下架的标的永远搜得到；样例更糟，会为任意关键词编出标的名。
     */
    override suspend fun search(keyword: String): List<com.huaying.xstz.core.model.QuoteCandidate> {
        val q = keyword.trim()
        if (q.isEmpty()) return emptyList()
        return runCatching { quoteSource.search(q) }.getOrDefault(emptyList())
    }

    override suspend fun updateTargetRatio(id: Long, ratio: Double) {
        holdingDao.updateTarget(id, ratio.coerceIn(0.0, 1.0), System.currentTimeMillis())
    }

    override suspend fun deleteHolding(id: Long) {
        // 先删流水再删持仓：外键方向决定顺序，反了会留下孤儿流水
        tradeDao.deleteByHolding(id)
        holdingDao.deleteById(id)
        transactionDao.insertLog(
            OperationLogEntity(
                action = "DELETE_HOLDING",
                detail = "删除持仓 #$id 及其全部流水",
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * 灌入示例持仓。
     *
     * 用 SampleData 里那套确定性曲线（同 code 每次生成同样的净值序列），
     * 所以截图和手工验收可以复现，不会每次看到不同的图形。
     *
     * 净值历史一次性写 180 天，让走势图有足够的数据可画。
     */
    override suspend fun clearAllHoldings(): Int {
        val all = holdingDao.getAll()
        if (all.isEmpty()) return 0
        // 先删流水再删持仓：外键方向决定顺序，反了会留下孤儿流水
        all.forEach { tradeDao.deleteByHolding(it.id) }
        holdingDao.deleteAll()
        transactionDao.insertLog(
            OperationLogEntity(
                action = "CLEAR_ALL_HOLDINGS",
                detail = "清空 ${all.size} 只持仓及其全部流水",
                createdAt = System.currentTimeMillis(),
            )
        )
        return all.size
    }

    override suspend fun exportHoldingsCsv(): String {
        val all = holdingDao.getAll()
        val sb = StringBuilder()
        // UTF-8 BOM：Excel 没有 BOM 会把中文读成乱码
        sb.append('\uFEFF')
        sb.append("代码,名称,资产类别,标的类型,份额,成本(元),最新净值,市值(元),目标占比,累计盈亏(元),收益率\n")
        all.forEach { h ->
            val shares = h.sharesMicros / 10_000.0
            val cost = h.costMicros / 10_000.0
            val nav = h.navMicros / 10_000.0
            val marketValue = nav * shares
            val profit = marketValue - cost
            val rate = if (cost > 0.0) profit / cost else 0.0
            sb.append(
                listOf(
                    h.code,
                    h.name,
                    h.type,
                    h.kind,
                    fmt(shares, 4),
                    fmt(cost, 2),
                    fmt(nav, 4),
                    fmt(marketValue, 2),
                    "${fmt(h.targetRatio * 100.0, 2)}%",
                    fmt(profit, 2),
                    "${fmt(rate * 100.0, 2)}%",
                ).joinToString(",") { csvCell(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    /** 固定 Locale.US 格式化：默认 Locale 在部分语言下小数点是逗号，会直接破坏 CSV */
    private fun fmt(value: Double, digits: Int): String =
        java.lang.String.format(java.util.Locale.US, "%.${digits}f", value)

    /** CSV 单元格转义：含逗号/引号/换行时加双引号，内部引号翻倍（RFC 4180） */
    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    override suspend fun seedSampleData(): Int {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        var count = 0

        for ((index, s) in SampleData.funds.withIndex()) {
            val existing = holdingDao.getByCode(s.code)
            val id = existing?.id ?: holdingDao.insert(
                com.huaying.xstz.core.database.entity.HoldingEntity(
                    code = s.code,
                    name = s.name,
                    type = s.type.name,
                    kind = InstrumentKind.FUND.name,
                    sharesMicros = 0,
                    costMicros = 0,
                    navMicros = 0,
                    prevNavMicros = 0,
                    targetRatio = s.targetRatio,
                    navEpochDay = 0,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                )
            )

            holdingDao.updateQuote(
                id = id,
                navMicros = Nav.fromDouble(s.nav).micros,
                prevNavMicros = Nav.fromDouble(s.prevNav).micros,
                epochDay = today,
                now = now,
            )
            // 份额与成本要单独写：updateQuote 刻意不碰这两个字段
            val holding = holdingDao.getById(id)
            if (holding != null) {
                holdingDao.update(
                    holding.copy(
                        sharesMicros = Shares.fromDouble(s.shares).micros,
                        costMicros = Money.fromYuan(s.costPerShare * s.shares).micros,
                        updatedAt = now,
                    )
                )
            }

            // 180 天净值历史
            val points = s.history.mapIndexed { i, value ->
                NavPoint(epochDay = today - (s.history.size - 1 - i), nav = Nav.fromDouble(value))
            }
            navRepository.saveHistory(id, points)

            /*
             * 补一笔建仓交易。
             *
             * 这一步是必须的，而不是"顺手加个流水"：组合快照是从**交易流水**重放出来的
             * （见 SnapshotBuilder 的说明——历史份额无法从当前持仓反推）。
             * 只写持仓不写流水，收益走势与日历就永远是空的，
             * 示例数据的意义（验证图表、日历、配色到底对不对）也就没了。
             *
             * 买入价取历史序列的**第一天**净值，日期也取那一天：
             * 这样从建仓首日到今天，份额始终不变，每日盈亏完全由净值波动产生，
             * 得到的曲线才是"真实持有体验"，不会混入申赎造成的台阶。
             */
            if (points.isNotEmpty() && s.shares > 0.0) {
                val firstPoint = points.first()
                val buyShares = Shares.fromDouble(s.shares)
                tradeDao.insert(
                    TradeEntity(
                        holdingId = id,
                        type = TradeType.BUY.name,
                        sharesMicros = buyShares.micros,
                        navMicros = firstPoint.nav.micros,
                        feeMicros = 0L,
                        amountMicros = (firstPoint.nav * buyShares).micros,
                        epochDay = firstPoint.epochDay,
                        note = "示例建仓",
                        createdAt = now,
                    )
                )
            }
            count++
        }

        transactionDao.insertLog(
            OperationLogEntity(
                action = "SEED_SAMPLE",
                detail = "载入 $count 只示例基金及其净值历史",
                createdAt = now,
            )
        )
        return count
    }

    /**
     * 刷新全部持仓行情。
     *
     * 降级链：真实接口 → 内存缓存 → 离线样例。
     * 即使全线失败也返回成功（用样例填充），保证界面永远有内容，
     * 但通过 [RefreshResult.source] 明确告知调用方数据来源。
     */
    override suspend fun refreshQuotes(): RefreshResult {
        val holdings = holdingDao.getAll()
        if (holdings.isEmpty()) {
            // 空组合不是"异常"，不需要往界面上抛一句话。
            // 之前返回 "没有持仓"，结果首页底部多出一行无意义的灰字（截图为证）。
            return RefreshResult(0, 0, DataSource.LIVE, null)
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        var updated = 0
        var failed = 0
        var lastSource = DataSource.LIVE
        val failures = ArrayList<String>()

        for (entity in holdings) {
            if (entity.type == AssetType.CASH.name) {
                // 现金类不抓行情，净值恒为 1
                holdingDao.updateQuote(entity.id, 10_000L, 10_000L, today, now)
                updated++
                continue
            }
            val kind = runCatching { InstrumentKind.valueOf(entity.kind) }.getOrDefault(InstrumentKind.FUND)
            val result = runCatching { quoteSource.quote(entity.code, kind) }.getOrNull()
            val estimate = result?.value
            if (estimate == null) {
                failed++
                failures += entity.code
                continue
            }
            lastSource = result!!.source
            holdingDao.updateQuote(
                id = entity.id,
                navMicros = estimate.estimatedNav.micros,
                prevNavMicros = estimate.nav.micros,
                epochDay = today,
                now = now,
            )
            // 顺带把当天净值落进历史表，图表才有数据可画
            navRepository.saveHistory(
                entity.id,
                listOf(NavPoint(today, estimate.estimatedNav)),
            )
            updated++
        }

        val message = when {
            failed == 0 -> null
            updated == 0 -> "全部 ${failed} 只基金行情获取失败，已切换到离线数据"
            else -> "有 ${failed} 只基金未更新：${failures.take(3).joinToString("、")}"
        }
        return RefreshResult(updated, failed, lastSource, message)
    }
}

/**
 * 快照写入器。
 *
 * 方案 §5.1 要求「净值记录」是图表与日历的唯一数据源，
 * 所以每次刷新行情后都要把当天的组合状态落进 `net_value_snapshot`。
 *
 * 它做两件事：
 *   1. **写入当天**——用当前持仓与当前净值直接算（[SnapshotBuilder.buildToday]）
 *   2. **补齐历史**——用交易流水重放出过去每一天（[SnapshotBuilder.build]）
 *
 * 第 2 步不能省：用户可能是今天才第一次打开这个应用，
 * 但过去三个月记了几十笔交易。只写今天的话，收益走势图只有一天数据，
 * 日历一片空白——那就等于没做。
 */
@Singleton
class SnapshotWriterImpl @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val tradeRepository: TradeRepository,
    private val navRepository: NavRepository,
    private val snapshotRepository: SnapshotRepository,
    private val quoteSource: QuoteSource,
    private val time: TimeRepository,
) : SnapshotWriter {

    /**
     * 刷新后调用：写入当天并回填历史。
     *
     * 两步的分工：
     *   * **当天**每次都写。用的是当前持仓 + 当前净值，最简单也最不容易错。
     *   * **历史**只在"快照表还没有覆盖到昨天"时补。补齐之后就不再重放，
     *     否则每次刷新都要重算几百天，白白耗电。
     */
    override suspend fun writeTodayAndBackfill() {
        // "今天"必须用可信时间算：用户改系统时间后，LocalDate.now() 会跟着偏，
        // 导致快照被写到错误的（未来/过去）日期，伪造收益曲线。
        // 可信时间走 SNTP 偏移 + elapsedRealtime 锚定，改系统钟不影响它。
        val today = java.time.LocalDate
            .ofInstant(
                java.time.Instant.ofEpochMilli(time.trustedNowMs()),
                ASIA_SHANGHAI,
            )
            .toEpochDay()
        val holdings = holdingRepository.observeHoldings().firstOrNull().orEmpty()
        if (holdings.isEmpty()) return

        // ---------- 1. 当天 ----------
        runCatching {
            snapshotRepository.save(SnapshotBuilder.buildToday(holdings, today))
        }

        // ---------- 2. 历史是否已补齐 ----------
        // 判据：表里已有早于今天的记录，说明历史已经覆盖过。
        // 之前这里写成 `latest < today` 就 return，逻辑是反的——
        // 结果只要写过一次今天，历史就永远补不上。
        val latest = runCatching { snapshotRepository.latestDay() }.getOrNull()
        val earliest = runCatching { snapshotRepository.earliestDay() }.getOrNull()
        if (latest != null && earliest != null && earliest < today) return

        val trades = runCatching { tradeRepository.observeAll().firstOrNull().orEmpty() }
            .getOrDefault(emptyList())
        if (trades.isEmpty()) return

        // ---------- 3. 回放区间 ----------
        // 起点取"最早交易"和"一年前"的较晚者：用户要求回填最近一年，
        // 但若第一笔交易发生在半年前，从半年前开始即可——再往前没有持仓，全是零。
        val earliestTrade = trades.minOf { it.epochDay }
        val fromDay = maxOf(earliestTrade, today - BACKFILL_DAYS)

        // ---------- 4. 取净值 ----------
        // 关键：本地 nav_history 通常是空的（行情刷新只写"最新净值"到 holdings，
        // 不写历史表），必须**从网络拉**再落库，否则回放出来的市值全是 0。
        val navs = buildMap {
            holdings.forEach { h ->
                val fetched = runCatching {
                    quoteSource.history(h.code, h.kind, fromDay)
                }.getOrDefault(emptyList())
                if (fetched.isNotEmpty()) {
                    // 落库：下次回填或其他页面要用，不必再拉一次
                    runCatching { navRepository.saveHistory(h.fundId, fetched) }
                    put(h.fundId, fetched)
                } else {
                    // 网络失败时退回本地已有历史（可能为空，那就跳过这只）
                    val local = runCatching {
                        navRepository.history(h.fundId, fromDay, today)
                    }.getOrDefault(emptyList())
                    if (local.isNotEmpty()) put(h.fundId, local)
                }
            }
        }
        if (navs.isEmpty()) return

        val history = SnapshotBuilder.build(holdings, trades, navs, fromDay, today)
        runCatching { snapshotRepository.saveAll(history) }
    }

    private companion object {
        /** 回填窗口：最近一年（用户确认的口径） */
        const val BACKFILL_DAYS = 365L

        /** 资产与交易日历统一按上海时区，避免设备时区干扰 */
        val ASIA_SHANGHAI = java.time.ZoneId.of("Asia/Shanghai")
    }
}

/**
 * 交易仓储。
 *
 * 「记一笔交易」是最容易写错的写路径：要同时更新持仓份额、累计成本、
 * 写流水、写日志，四件事必须原子。这里统一走 [TransactionDao.commitTrade]。
 */
@Singleton
class TradeRepositoryImpl @Inject constructor(
    private val tradeDao: TradeDao,
    private val holdingDao: HoldingDao,
    private val transactionDao: TransactionDao,
) : TradeRepository {

    override fun observeAll(): Flow<List<Trade>> =
        tradeDao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeByHolding(holdingId: Long): Flow<List<Trade>> =
        tradeDao.observeByHolding(holdingId).map { list -> list.map { it.toModel() } }

    override suspend fun recordTrade(trade: Trade): Long {
        val holding = holdingDao.getById(trade.fundId)
            ?: error("持仓不存在：${trade.fundId}")

        val currentShares = Shares(holding.sharesMicros)
        val currentCost = Money(holding.costMicros)

        val (newShares, newCost) = when (trade.type) {
            TradeType.BUY -> {
                // 买入：份额增加，成本累加（含手续费，手续费也是我的成本）
                currentShares + trade.shares to (currentCost + trade.amount + trade.fee)
            }
            TradeType.SELL -> {
                // 卖出：份额减少，成本按比例扣减。
                // 关键：不能直接减 trade.amount，否则"卖在高位"会让剩余成本变成负数。
                // 正确做法是按卖出份额占总份额的比例结转成本。
                val ratio = if (currentShares.isZero) 0.0
                else trade.shares.toDouble() / currentShares.toDouble()
                val costReleased = Money((currentCost.micros * ratio).toLong())
                (currentShares - trade.shares) to (currentCost - costReleased)
            }
            TradeType.DIVIDEND -> {
                // 分红：份额不变，现金入账。若是红利再投则由调用方转成 BUY 处理。
                currentShares to currentCost
            }
            TradeType.REBALANCE -> {
                // 再平衡：按 shares 的符号复用买卖规则（语义约定见 TradeType 注释：
                // shares 是**增量**，正为加仓、负为减仓）。
                // 必须与 SnapshotBuilder.applyTrade 保持一致，
                // 否则实时写入的快照与历史重放的结果会分叉。
                if (trade.shares.micros >= 0) {
                    currentShares + trade.shares to (currentCost + trade.amount + trade.fee)
                } else {
                    val soldShares = Shares(-trade.shares.micros)
                    val ratio = if (currentShares.isZero) 0.0
                    else soldShares.toDouble() / currentShares.toDouble()
                    val costReleased = Money((currentCost.micros * ratio).toLong())
                    (currentShares - soldShares) to (currentCost - costReleased)
                }
            }
            TradeType.ADD_CASH, TradeType.WITHDRAW -> {
                // 追加/取出资金：不作用于标的份额。
                //
                // 这两个类型本不该绑定到某个 holdingId——它们操作的是组合现金池。
                // 若调用方仍按标的提交，这里保持持仓不变而不是报错：
                // 报错会让"记录一笔资金转入"这件事直接失败，而用户的意图是合法的。
                // 现金的增减在快照里由 cashFlow 汇总体现。
                currentShares to currentCost
            }
        }

        if (newShares.isNegative) error("卖出份额超过持有份额")

        val now = System.currentTimeMillis()
        return transactionDao.commitTrade(
            holdingId = holding.id,
            newSharesMicros = newShares.micros,
            newCostMicros = newCost.micros.coerceAtLeast(0L),
            trade = trade.toEntity(),
            log = OperationLogEntity(
                action = "TRADE_${trade.type.name}",
                detail = "${trade.type.displayName} ${holding.name} " +
                    "${trade.shares.toDouble()} 份 @ ${trade.nav.toDouble()}",
                createdAt = now,
            ),
            now = now,
        )
    }

    override suspend fun deleteTrade(id: Long) {
        // 删除流水不自动回滚持仓：回滚需要重放全部流水，
        // 这里保持"流水可删、持仓不动"，并在日志里留痕，避免静默改数
        tradeDao.deleteById(id)
        transactionDao.insertLog(
            OperationLogEntity(
                action = "DELETE_TRADE",
                detail = "删除交易流水 #$id（持仓不变）",
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}

@Singleton
class NavRepositoryImpl @Inject constructor(
    private val navDao: com.huaying.xstz.core.database.dao.NavHistoryDao,
) : NavRepository {

    override suspend fun history(holdingId: Long, fromDay: Long, toDay: Long): List<NavPoint> =
        navDao.range(holdingId, fromDay, toDay).map { it.toModel() }

    override suspend fun saveHistory(holdingId: Long, points: List<NavPoint>) {
        if (points.isEmpty()) return
        navDao.upsertAll(points.map { it.toEntity(holdingId) })
    }

    override suspend fun latestDay(holdingId: Long): Long? = navDao.latestDay(holdingId)
}

/**
 * 组合快照仓储实现。
 *
 * 只做存取，**不含重算逻辑**——重算是 [SnapshotBuilder] 的纯函数职责，
 * 放这里会让"读"和"算"耦在一起，没法单测。
 */
@Singleton
class SnapshotRepositoryImpl @Inject constructor(
    private val snapshotDao: com.huaying.xstz.core.database.dao.NetValueSnapshotDao,
) : SnapshotRepository {

    override suspend fun range(fromDay: Long, toDay: Long): List<NetValueSnapshot> =
        snapshotDao.range(fromDay, toDay).map { it.toModel() }

    override suspend fun all(): List<NetValueSnapshot> =
        snapshotDao.range(Long.MIN_VALUE, Long.MAX_VALUE).map { it.toModel() }

    override suspend fun recent(limit: Int): List<NetValueSnapshot> =
        snapshotDao.recent(limit).map { it.toModel() }

    override suspend fun latestDay(): Long? = snapshotDao.latestDay()

    override suspend fun earliestDay(): Long? = snapshotDao.earliestDay()

    override suspend fun save(snapshot: NetValueSnapshot) {
        snapshotDao.upsert(snapshot.toEntity())
    }

    override suspend fun saveAll(snapshots: List<NetValueSnapshot>) {
        if (snapshots.isEmpty()) return
        snapshotDao.upsertAll(snapshots.map { it.toEntity() })
    }
}

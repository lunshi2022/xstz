package com.huaying.xstz.core.data.repository

import com.huaying.xstz.core.database.toModel
import com.huaying.xstz.core.database.toEntity
import com.huaying.xstz.core.database.dao.HoldingDao
import com.huaying.xstz.core.database.dao.TransactionDao
import com.huaying.xstz.core.database.dao.TradeDao
import com.huaying.xstz.core.database.entity.OperationLogEntity
import com.huaying.xstz.core.database.entity.TradeEntity
import com.huaying.xstz.core.domain.HoldingMath
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.NavRepository
import com.huaying.xstz.core.domain.repository.RefreshResult
import com.huaying.xstz.core.domain.repository.TradeRepository
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.DataSource
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.PortfolioSummary
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType
import com.huaying.xstz.core.network.QuoteSource
import com.huaying.xstz.core.network.SampleData
import kotlinx.coroutines.flow.Flow
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

    override suspend fun addHolding(code: String, name: String, type: AssetType): Long {
        val now = System.currentTimeMillis()
        val sample = SampleData.forCode(code, name, type)
        val id = holdingDao.insert(
            com.huaying.xstz.core.database.entity.HoldingEntity(
                code = code,
                name = name.ifBlank { sample.name },
                type = type.name,
                sharesMicros = 0,
                costMicros = 0,
                navMicros = 0,
                prevNavMicros = 0,
                targetRatio = 0.0,
                navEpochDay = 0,
                sortOrder = holdingDao.count(),
                createdAt = now,
                updatedAt = now,
            )
        )
        transactionDao.insertLog(
            OperationLogEntity(
                action = "ADD_HOLDING",
                detail = "添加基金 $code ${name.ifBlank { sample.name }}",
                createdAt = now,
            )
        )
        return id
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
     * 刷新全部持仓行情。
     *
     * 降级链：真实接口 → 内存缓存 → 离线样例。
     * 即使全线失败也返回成功（用样例填充），保证界面永远有内容，
     * 但通过 [RefreshResult.source] 明确告知调用方数据来源。
     */
    override suspend fun refreshQuotes(): RefreshResult {
        val holdings = holdingDao.getAll()
        if (holdings.isEmpty()) {
            return RefreshResult(0, 0, DataSource.LIVE, "没有持仓")
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
            val result = runCatching { quoteSource.quote(entity.code) }.getOrNull()
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

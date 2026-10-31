package com.huaying.xstz.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.huaying.xstz.core.database.entity.HoldingEntity
import com.huaying.xstz.core.database.entity.NavPointEntity
import com.huaying.xstz.core.database.entity.NetValueSnapshotEntity
import com.huaying.xstz.core.database.entity.OperationLogEntity
import com.huaying.xstz.core.database.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldingDao {

    @Query("SELECT * FROM holdings ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<HoldingEntity>

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun getById(id: Long): HoldingEntity?

    @Query("SELECT * FROM holdings WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): HoldingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: HoldingEntity): Long

    @Update
    suspend fun update(entity: HoldingEntity)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM holdings")
    suspend fun deleteAll()

    /**
     * 只更新行情字段。
     * 单独开这条语句，是为了让"抓行情"这条高频路径不触碰份额与成本，
     * 避免并发刷新把用户刚录入的持仓覆盖掉。
     */
    @Query(
        """
        UPDATE holdings
        SET nav_micros = :navMicros,
            prev_nav_micros = :prevNavMicros,
            nav_epoch_day = :epochDay,
            updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun updateQuote(id: Long, navMicros: Long, prevNavMicros: Long, epochDay: Long, now: Long)

    @Query("UPDATE holdings SET target_ratio = :ratio, updated_at = :now WHERE id = :id")
    suspend fun updateTarget(id: Long, ratio: Double, now: Long)

    @Query("SELECT COALESCE(SUM(target_ratio), 0.0) FROM holdings")
    suspend fun sumTargetRatio(): Double

    /**
     * 三个聚合查询。
     *
     * 它们存在的意义是**证明 SQLCipher 下 SQL 聚合正确**——旧版 P0-2 的返修点。
     * 旧实现把金额做列级加密存成 TEXT，`SUM(holdingQuantity * currentPrice)`
     * 变成对密文字符串做算术，SQLite 静默返回错误值而不报错。
     *
     * 注意全部用 COALESCE 包住：`SUM` 在空集合上返回 NULL，
     * Room 映射到非空 Long 会直接崩，包了才安全。
     *
     * `SUM(shares_micros * nav_micros)` 用 INTEGER 运算：
     * 两者都是 ×1e4 定点，乘积是 ×1e8，所以下面再 `/ 10000` 回到 ×1e4。
     * 必须显式写乘法顺序（先乘后除），先除会丢精度。
     */
    @Query("SELECT COALESCE(SUM(shares_micros), 0) FROM holdings")
    suspend fun sumSharesMicros(): Long

    @Query("SELECT COALESCE(SUM(cost_micros), 0) FROM holdings")
    suspend fun sumCostMicros(): Long

    @Query("SELECT COALESCE(SUM(shares_micros * nav_micros / 10000), 0) FROM holdings")
    suspend fun sumMarketValueMicros(): Long

    @Query("SELECT COUNT(*) FROM holdings")
    suspend fun count(): Int
}

@Dao
interface TradeDao {

    @Query("SELECT * FROM trades ORDER BY epoch_day DESC, id DESC")
    fun observeAll(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE holding_id = :holdingId ORDER BY epoch_day DESC, id DESC")
    fun observeByHolding(holdingId: Long): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE holding_id = :holdingId ORDER BY epoch_day DESC, id DESC")
    suspend fun getByHolding(holdingId: Long): List<TradeEntity>

    @Query("SELECT * FROM trades ORDER BY epoch_day ASC, id ASC")
    suspend fun getAllAscending(): List<TradeEntity>

    @Query("SELECT * FROM trades")
    suspend fun getAll(): List<TradeEntity>

    @Insert
    suspend fun insert(entity: TradeEntity): Long

    @Insert
    suspend fun insertAll(list: List<TradeEntity>)

    @Query("DELETE FROM trades WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trades WHERE holding_id = :holdingId")
    suspend fun deleteByHolding(holdingId: Long)

    @Query("DELETE FROM trades")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM trades WHERE holding_id = :holdingId")
    suspend fun countByHolding(holdingId: Long): Int
}

@Dao
interface NavHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(points: List<NavPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(point: NavPointEntity)

    @Query(
        """
        SELECT * FROM nav_history
        WHERE holding_id = :holdingId AND epoch_day BETWEEN :fromDay AND :toDay
        ORDER BY epoch_day ASC
        """
    )
    suspend fun range(holdingId: Long, fromDay: Long, toDay: Long): List<NavPointEntity>

    @Query("SELECT * FROM nav_history WHERE holding_id = :holdingId ORDER BY epoch_day ASC")
    suspend fun all(holdingId: Long): List<NavPointEntity>

    @Query("SELECT * FROM nav_history WHERE epoch_day = :epochDay")
    suspend fun byDay(epochDay: Long): List<NavPointEntity>

    @Query("SELECT * FROM nav_history WHERE epoch_day >= :fromDay ORDER BY epoch_day ASC")
    suspend fun since(fromDay: Long): List<NavPointEntity>

    @Query("SELECT MAX(epoch_day) FROM nav_history WHERE holding_id = :holdingId")
    suspend fun latestDay(holdingId: Long): Long?

    @Query("DELETE FROM nav_history WHERE holding_id = :holdingId")
    suspend fun clearForHolding(holdingId: Long)

    @Query("DELETE FROM nav_history")
    suspend fun deleteAll()
}

@Dao
interface OperationLogDao {

    @Query("SELECT * FROM operation_logs ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<OperationLogEntity>>

    @Query("SELECT * FROM operation_logs ORDER BY created_at DESC LIMIT :limit")
    suspend fun recent(limit: Int = 200): List<OperationLogEntity>

    @Query("SELECT * FROM operation_logs ORDER BY created_at DESC")
    suspend fun all(): List<OperationLogEntity>

    @Insert
    suspend fun insert(entity: OperationLogEntity): Long

    @Insert
    suspend fun insertAll(list: List<OperationLogEntity>)

    @Query("DELETE FROM operation_logs WHERE created_at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM operation_logs")
    suspend fun clear()
}

@Dao
interface NetValueSnapshotDao {

    /**
     * 写入/覆盖某一天的组合快照。
     *
     * 用 REPLACE 而不是 ABORT：同一天可能重算多次（刷新行情、重新计算），
     * 快照是**派生数据**，覆盖是正确的，冲突报错才是错的。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: NetValueSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<NetValueSnapshotEntity>)

    /** 区间读取，图表与日历都按这个查 */
    @Query(
        """
        SELECT * FROM net_value_snapshot
        WHERE epoch_day BETWEEN :fromDay AND :toDay
        ORDER BY epoch_day ASC
        """
    )
    suspend fun range(fromDay: Long, toDay: Long): List<NetValueSnapshotEntity>

    @Query("SELECT * FROM net_value_snapshot ORDER BY epoch_day ASC")
    fun observeAll(): Flow<List<NetValueSnapshotEntity>>

    @Query("SELECT * FROM net_value_snapshot ORDER BY epoch_day DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NetValueSnapshotEntity>

    @Query("SELECT MAX(epoch_day) FROM net_value_snapshot")
    suspend fun latestDay(): Long?

    @Query("SELECT MIN(epoch_day) FROM net_value_snapshot")
    suspend fun earliestDay(): Long?

    @Query("SELECT COUNT(*) FROM net_value_snapshot")
    suspend fun count(): Int

    @Query("DELETE FROM net_value_snapshot")
    suspend fun deleteAll()
}

/**
 * 跨表事务边界。
 *
 * 单独成类，是因为「一次操作动多张表」必须原子。
 * 旧版把这类逻辑散落在各个 Repository 的协程里，
 * 一旦中途抛异常就会出现「流水记了、持仓没变」的静默不一致。
 */
@Dao
abstract class TransactionDao {

    @Query(
        """
        UPDATE holdings
        SET shares_micros = :sharesMicros, cost_micros = :costMicros, updated_at = :now
        WHERE id = :holdingId
        """
    )
    abstract suspend fun updatePosition(
        holdingId: Long,
        sharesMicros: Long,
        costMicros: Long,
        now: Long,
    )

    @Insert
    abstract suspend fun insertTrade(trade: TradeEntity): Long

    @Insert
    abstract suspend fun insertLog(log: OperationLogEntity): Long

    @Insert
    abstract suspend fun insertHoldings(list: List<HoldingEntity>)

    @Insert
    abstract suspend fun insertTrades(list: List<TradeEntity>)

    @Insert
    abstract suspend fun insertLogs(list: List<OperationLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertNavPoints(list: List<NavPointEntity>)

    /**
     * 记一笔交易：更新持仓 + 写流水 + 记日志，三件事要么全成要么全败。
     */
    @Transaction
    open suspend fun commitTrade(
        holdingId: Long,
        newSharesMicros: Long,
        newCostMicros: Long,
        trade: TradeEntity,
        log: OperationLogEntity,
        now: Long,
    ): Long {
        updatePosition(holdingId, newSharesMicros, newCostMicros, now)
        insertLog(log)
        return insertTrade(trade)
    }

    /**
     * 备份恢复：先清空，再写持仓并**捕获新自增 id**，最后按 code→id 映射回填流水与净值。
     *
     * 为什么不能一次性批量插入再关联：批量插入拿不到逐条 id，
     * 而恢复后 id 必然与备份时不同。用 code 作为关联键是这个流程能正确工作的前提。
     */
    @Transaction
    open suspend fun replaceAllWithCodeMapping(
        holdings: List<HoldingEntity>,
        trades: (Map<String, Long>) -> List<TradeEntity>,
        navPoints: (Map<String, Long>) -> List<NavPointEntity>,
        logs: List<OperationLogEntity>,
        clearAll: suspend () -> Unit,
    ) {
        clearAll()

        val codeToId = LinkedHashMap<String, Long>(holdings.size)
        for (h in holdings) {
            val newId = insertOneHolding(h)
            codeToId[h.code] = newId
        }

        val tradeEntities = trades(codeToId)
        if (tradeEntities.isNotEmpty()) insertTrades(tradeEntities)

        val navEntities = navPoints(codeToId)
        if (navEntities.isNotEmpty()) upsertNavPoints(navEntities)

        if (logs.isNotEmpty()) insertLogs(logs)
    }

    @Insert
    abstract suspend fun insertOneHolding(holding: HoldingEntity): Long

    /**
     * 通用整体替换（不涉及 code 映射的场景，例如内部测试或将来导入外部数据）。
     */
    @Transaction
    open suspend fun replaceAll(
        holdings: List<HoldingEntity>,
        trades: List<TradeEntity>,
        navPoints: List<NavPointEntity>,
        logs: List<OperationLogEntity>,
        clearAll: suspend () -> Unit,
    ) {
        clearAll()
        insertHoldings(holdings)
        if (trades.isNotEmpty()) insertTrades(trades)
        if (navPoints.isNotEmpty()) upsertNavPoints(navPoints)
        if (logs.isNotEmpty()) insertLogs(logs)
    }
}

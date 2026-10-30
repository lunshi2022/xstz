package com.huaying.xstz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 持仓表。
 *
 * 三个刻意的设计决定：
 *   1. **不做列级加密**。旧版把 Double 加密成 TEXT，直接导致 SQL 无法聚合
 *      （`SUM(holdingQuantity * currentPrice)` 报类型错误），只能退回应用层全量重算。
 *      新版改为整库级保护（设备加密 + 应用沙箱），敏感数据靠系统隔离，不靠字段加密。
 *      备份走口令派生密钥的 AES-GCM，与库内存储解耦。
 *
 *   2. **金额和份额用 Long 定点**（1e-4）。SQL 聚合精确，跨进程序列化不丢精度。
 *
 *   3. **成本单价不落库**，由 totalCost / shares 推导。旧版单独存 costPrice，
 *      改份额忘改单价就会产生自相矛盾的记录。
 */
@Entity(
    tableName = "holdings",
    indices = [Index(value = ["code"], unique = true)],
)
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** AssetType.name，字符串存储便于跨版本演进时容忍新枚举 */
    @ColumnInfo(name = "type")
    val type: String,

    /** 份额 × 1e4 */
    @ColumnInfo(name = "shares_micros")
    val sharesMicros: Long = 0,

    /** 累计成本 × 1e4 */
    @ColumnInfo(name = "cost_micros")
    val costMicros: Long = 0,

    /** 最新净值 × 1e4 */
    @ColumnInfo(name = "nav_micros")
    val navMicros: Long = 0,

    /** 昨收净值 × 1e4 */
    @ColumnInfo(name = "prev_nav_micros")
    val prevNavMicros: Long = 0,

    /** 目标占比 0.0~1.0 */
    @ColumnInfo(name = "target_ratio")
    val targetRatio: Double = 0.0,

    /** 净值对应的交易日 epochDay，0 表示从未更新 */
    @ColumnInfo(name = "nav_epoch_day")
    val navEpochDay: Long = 0,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0,
)

/**
 * 交易流水。
 *
 * 只追加、不修改（除删除）。任何"改交易"操作都实现为「删旧 + 插新」并留审计记录，
 * 这样持仓可以由流水完整重放出来，是数据可恢复性的最后一道保险。
 */
@Entity(
    tableName = "trades",
    indices = [
        Index(value = ["holding_id"]),
        Index(value = ["epoch_day"]),
    ],
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "holding_id")
    val holdingId: Long,

    /** TradeType.name */
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "shares_micros")
    val sharesMicros: Long,

    @ColumnInfo(name = "nav_micros")
    val navMicros: Long,

    @ColumnInfo(name = "fee_micros")
    val feeMicros: Long = 0,

    @ColumnInfo(name = "amount_micros")
    val amountMicros: Long,

    @ColumnInfo(name = "epoch_day")
    val epochDay: Long,

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0,
)

/**
 * 净值历史。复合主键 (holding_id, epoch_day)：
 * 同一只基金每天只应有一个净值，重复写入直接覆盖，天然幂等。
 */
@Entity(
    tableName = "nav_history",
    primaryKeys = ["holding_id", "epoch_day"],
    indices = [Index(value = ["epoch_day"])],
)
data class NavPointEntity(
    @ColumnInfo(name = "holding_id")
    val holdingId: Long,

    @ColumnInfo(name = "epoch_day")
    val epochDay: Long,

    @ColumnInfo(name = "nav_micros")
    val navMicros: Long,

    @ColumnInfo(name = "acc_nav_micros")
    val accNavMicros: Long,
)

/**
 * 操作日志。
 * 与旧版的区别：新增 `payload` 字段记录变更前后快照，用于"误操作回滚"。
 * 旧版只记文字描述，出事后无法自动恢复。
 */
@Entity(
    tableName = "operation_logs",
    indices = [Index(value = ["created_at"])],
)
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 动作类型：ADD_HOLDING / EDIT_TRADE / REBALANCE / RESTORE ... */
    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "detail")
    val detail: String,

    /** JSON 快照，可为空（纯查询类操作不留快照） */
    @ColumnInfo(name = "payload")
    val payload: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0,
)

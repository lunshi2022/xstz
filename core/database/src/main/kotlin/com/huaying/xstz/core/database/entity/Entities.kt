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
 *      新版改为 **SQLCipher 整库加密**（见 `XstzDatabase.create`）：字段在 SQLite
 *      眼里仍是普通 INTEGER/REAL，聚合、索引、ORDER BY 照常工作，
 *      而库文件在磁盘上是密文。口令来自 Keystore 包裹的随机 DEK。
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

    /**
     * InstrumentKind.name（STOCK/ETF/BOND/FUND/CASH）。
     *
     * 默认 FUND 是为了让 v1 库升级上来的旧记录有个明确归属：
     * 旧版只管理基金，所以升级后都记作 FUND 是准确的历史事实，不是猜测。
     */
    @ColumnInfo(name = "kind", defaultValue = "FUND")
    val kind: String = "FUND",

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
 * 组合级每日快照。
 *
 * 这是方案 §5.1「净值记录」的数据落地：**按日快照总资产/本金/收益/收益率/四类市值，
 * 作为图表与日历的唯一数据源**（REBUILD_PLAN §2 模块 6、附录 B）。
 *
 * 为什么必须单独建表，而不能靠 `nav_history` 拼出来：
 *
 *   `nav_history` 是**单只标的**的净值序列（holding_id, epoch_day, nav）。
 *   它回答不了三个问题：
 *     1. 某一天整个组合值多少钱——需要把当天的每只持仓按**当时的份额**加权，
 *        而份额会随交易变动，净值表里没有份额；
 *     2. 某一天的**本金**是多少——成本随买入卖出结转，净值表里没有成本；
 *     3. 某一天的**当日盈亏**是多少——必须区分"净值涨跌"与"申赎导致的市值变化"，
 *        两者混在一个差值里就会把追加资金算成盈利。
 *
 * 换句话说：`nav_history` 是因，`net_value_snapshot` 是果。
 * 有了它，收益走势图直接按 totalValue 出曲线，日历直接按 dayProfit 上色，
 * 都不需要临时做重算——而临时重算的致命缺陷是**历史份额无从还原**
 * （见 SnapshotBuilder 的说明）。
 *
 * 表名沿用方案附录 B 的 `net_value_snapshot`；字段 `commodityValue` 而非旧版的 `goldValue`。
 */
@Entity(tableName = "net_value_snapshot")
data class NetValueSnapshotEntity(
    /** 交易日 epochDay，天然唯一——同一天重算直接覆盖，幂等 */
    @PrimaryKey
    @ColumnInfo(name = "epoch_day")
    val epochDay: Long,

    /** 当日总资产（所有持仓市值之和） */
    @ColumnInfo(name = "total_value_micros")
    val totalValueMicros: Long,

    /** 当日累计本金（所有持仓成本之和） */
    @ColumnInfo(name = "principal_micros")
    val principalMicros: Long,

    /**
     * 当日盈亏。
     *
     * 单独存而不是用 `totalValue - 前一天 totalValue`：
     * 追加资金 / 取出资金会改变总市值但不是盈亏。
     * 差值法会把"今天追加了 1 万"显示成"今天赚了 1 万"，这是最难被发现的一类错账。
     */
    @ColumnInfo(name = "day_profit_micros")
    val dayProfitMicros: Long,

    /** 四类资产市值，用于资产配置维度 */
    @ColumnInfo(name = "stock_value_micros")
    val stockValueMicros: Long = 0,

    @ColumnInfo(name = "bond_value_micros")
    val bondValueMicros: Long = 0,

    @ColumnInfo(name = "commodity_value_micros")
    val commodityValueMicros: Long = 0,

    @ColumnInfo(name = "cash_value_micros")
    val cashValueMicros: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0,
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

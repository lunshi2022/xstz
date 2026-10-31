package com.huaying.xstz.core.database

import com.huaying.xstz.core.database.entity.HoldingEntity
import com.huaying.xstz.core.database.entity.NavPointEntity
import com.huaying.xstz.core.database.entity.NetValueSnapshotEntity
import com.huaying.xstz.core.database.entity.OperationLogEntity
import com.huaying.xstz.core.database.entity.TradeEntity
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.InstrumentKind
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.NetValueSnapshot
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType

/**
 * 实体 ↔ 模型的映射。
 *
 * 单独成文件的原因：这是**唯一允许单位换算发生的地方**。
 * 定点 Long 与业务类型之间的转换若散落各处，迟早有人在 UI 层
 * 直接读 `sharesMicros` 当份额用，然后差 1 万倍。
 *
 * 用顶层函数而非 object 成员：object 内的扩展函数必须 `with(obj){}` 才能调用，
 * 会让每个调用点都多一层包装，收益为零。
 */

fun HoldingEntity.toModel(): Holding = Holding(
    fundId = id,
    code = code,
    name = name,
    type = runCatching { AssetType.valueOf(type) }.getOrDefault(AssetType.STOCK),
    // 用 this.kind 而不是 kind：后者在 lambda 里会解析成「参数名」，
    // data class 的默认值表达式里容易写成自引用
    kind = runCatching { InstrumentKind.valueOf(this.kind) }.getOrDefault(InstrumentKind.FUND),
    shares = Shares(sharesMicros),
    cost = Money(costMicros),
    nav = Nav(navMicros),
    prevNav = Nav(prevNavMicros),
    targetRatio = targetRatio,
)

fun TradeEntity.toModel(): Trade = Trade(
    id = id,
    fundId = holdingId,
    type = runCatching { TradeType.valueOf(type) }.getOrDefault(TradeType.BUY),
    shares = Shares(sharesMicros),
    nav = Nav(navMicros),
    fee = Money(feeMicros),
    amount = Money(amountMicros),
    epochDay = epochDay,
    note = note,
    createdAt = createdAt,
)

fun Trade.toEntity(): TradeEntity = TradeEntity(
    id = id,
    holdingId = fundId,
    type = type.name,
    sharesMicros = shares.micros,
    navMicros = nav.micros,
    feeMicros = fee.micros,
    amountMicros = amount.micros,
    epochDay = epochDay,
    note = note,
    createdAt = if (createdAt != 0L) createdAt else System.currentTimeMillis(),
)

fun NavPointEntity.toModel(): NavPoint = NavPoint(
    epochDay = epochDay,
    nav = Nav(navMicros),
    accumulatedNav = Nav(accNavMicros),
)

fun NavPoint.toEntity(holdingId: Long): NavPointEntity = NavPointEntity(
    holdingId = holdingId,
    epochDay = epochDay,
    navMicros = nav.micros,
    accNavMicros = accumulatedNav.micros,
)

fun OperationLogEntity.toDomain(): OperationRecord = OperationRecord(
    id = id,
    action = action,
    detail = detail,
    payload = payload,
    createdAt = createdAt,
)

fun NetValueSnapshotEntity.toModel(): NetValueSnapshot = NetValueSnapshot(
    epochDay = epochDay,
    totalValue = Money(totalValueMicros),
    principal = Money(principalMicros),
    dayProfit = Money(dayProfitMicros),
    stockValue = Money(stockValueMicros),
    bondValue = Money(bondValueMicros),
    commodityValue = Money(commodityValueMicros),
    cashValue = Money(cashValueMicros),
)

fun NetValueSnapshot.toEntity(now: Long = System.currentTimeMillis()): NetValueSnapshotEntity =
    NetValueSnapshotEntity(
        epochDay = epochDay,
        totalValueMicros = totalValue.micros,
        principalMicros = principal.micros,
        dayProfitMicros = dayProfit.micros,
        stockValueMicros = stockValue.micros,
        bondValueMicros = bondValue.micros,
        commodityValueMicros = commodityValue.micros,
        cashValueMicros = cashValue.micros,
        createdAt = now,
    )

/** 操作日志的领域视图（不进 core:model，避免模型层被日志概念污染） */
data class OperationRecord(
    val id: Long,
    val action: String,
    val detail: String,
    val payload: String?,
    val createdAt: Long,
)

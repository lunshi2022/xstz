package com.huaying.xstz.core.data.repository

import com.huaying.xstz.core.data.backup.BackupCrypto
import com.huaying.xstz.core.database.dao.HoldingDao
import com.huaying.xstz.core.database.dao.NavHistoryDao
import com.huaying.xstz.core.database.dao.OperationLogDao
import com.huaying.xstz.core.database.dao.TradeDao
import com.huaying.xstz.core.database.dao.TransactionDao
import com.huaying.xstz.core.database.entity.HoldingEntity
import com.huaying.xstz.core.database.entity.NavPointEntity
import com.huaying.xstz.core.database.entity.OperationLogEntity
import com.huaying.xstz.core.database.entity.TradeEntity
import com.huaying.xstz.core.domain.repository.BackupRepository
import com.huaying.xstz.core.domain.repository.ImportResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份 / 恢复。
 *
 * 与旧版的三点本质差异：
 *   1. **密钥来自口令，不来自 Keystore**。旧版密钥不可导出，换机必死。
 *   2. **备份内容带 schema 版本**。将来加字段时能判断"这份备份能不能导"，
 *      而不是靠 try/catch 撞运气。
 *   3. **跨表关联用 code 而不是自增 id**。恢复后自增 id 必然重新分配，
 *      用 id 关联会把流水挂到错误的基金上——这是备份功能最隐蔽的坑。
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val holdingDao: HoldingDao,
    private val tradeDao: TradeDao,
    private val navDao: NavHistoryDao,
    private val logDao: OperationLogDao,
    private val transactionDao: TransactionDao,
) : BackupRepository {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true // 向前兼容：旧版本读取新备份时忽略不认识的字段
        encodeDefaults = true
    }

    override suspend fun export(passphrase: CharArray): String {
        val holdings = holdingDao.getAll()
        val idToCode = holdings.associate { it.id to it.code }

        val cutoff = java.time.LocalDate.now().minusYears(3).toEpochDay()
        val snapshot = BackupSnapshot(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = "2.0.0",
            holdings = holdings.map { it.toDto() },
            trades = tradeDao.getAll().mapNotNull { t ->
                idToCode[t.holdingId]?.let { t.toDto(it) }
            },
            navPoints = navDao.since(cutoff).mapNotNull { n ->
                idToCode[n.holdingId]?.let { n.toDto(it) }
            },
        )

        val plaintext = json.encodeToString(BackupSnapshot.serializer(), snapshot)
            .toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(plaintext, passphrase)
        // Base64 包一层：备份要能经剪贴板/邮件/聊天工具传递，
        // 直接给二进制会在这些通道里被破坏
        val body = java.util.Base64.getEncoder().encodeToString(encrypted)

        logDao.insert(
            OperationLogEntity(
                action = "BACKUP_EXPORT",
                detail = "导出备份：${snapshot.holdings.size} 条持仓、${snapshot.trades.size} 条流水",
                createdAt = System.currentTimeMillis(),
            )
        )
        return body
    }

    override suspend fun import(payload: String, passphrase: CharArray): ImportResult {
        val raw = runCatching { java.util.Base64.getDecoder().decode(payload.trim()) }
            .getOrElse { throw IllegalArgumentException("备份内容不是有效的 Base64，可能复制不完整") }

        val plaintext = BackupCrypto.decrypt(raw, passphrase)
        val snapshot = runCatching {
            json.decodeFromString(BackupSnapshot.serializer(), String(plaintext, Charsets.UTF_8))
        }.getOrElse { throw IllegalArgumentException("备份内容解析失败，文件可能已损坏") }

        if (snapshot.schemaVersion > SCHEMA_VERSION) {
            throw IllegalArgumentException(
                "这份备份来自更新版本的应用（v${snapshot.schemaVersion}），请先升级后再恢复"
            )
        }

        // 用 code 重建关联：先清空并写入持仓，拿到新的自增 id，再把流水与净值挂上去
        val holdingsByCode = snapshot.holdings
            .distinctBy { it.code }
            .associateBy { it.code }

        val holdingEntities = holdingsByCode.values.map { it.toEntity()}.toList()

        // 这里必须逐个插入以获取新 id，不能批量——批量插入拿不到 id 回填
        transactionDao.replaceAllWithCodeMapping(
            holdings = holdingEntities,
            trades = { codeToId ->
                snapshot.trades.mapNotNull { t ->
                    codeToId[t.holdingCode]?.let { t.toEntity(it) }
                }
            },
            navPoints = { codeToId ->
                snapshot.navPoints.mapNotNull { n ->
                    codeToId[n.holdingCode]?.let { n.toEntity(it) }
                }
            },
            logs = listOf(
                OperationLogEntity(
                    action = "BACKUP_IMPORT",
                    detail = "恢复备份：${snapshot.holdings.size} 条持仓、${snapshot.trades.size} 条流水",
                    createdAt = System.currentTimeMillis(),
                )
            ),
            clearAll = {
                tradeDao.deleteAll()
                navDao.deleteAll()
                holdingDao.deleteAll()
            },
        )

        return ImportResult(
            holdings = snapshot.holdings.size,
            trades = snapshot.trades.size,
            navPoints = snapshot.navPoints.size,
        )
    }

    override suspend fun lastBackupTime(): Long =
        logDao.recent(50).firstOrNull { it.action == "BACKUP_EXPORT" }?.createdAt ?: 0L

    // ---------------- 序列化 DTO ----------------
    // 刻意不复用 Room 实体：实体带 @PrimaryKey 与列名注解，直接序列化会把存储细节
    // 钉进备份格式，将来改列名就毁掉旧备份。

    @Serializable
    private data class BackupSnapshot(
        val schemaVersion: Int,
        val exportedAt: Long,
        val appVersion: String,
        val holdings: List<HoldingDto>,
        val trades: List<TradeDto>,
        val navPoints: List<NavDto>,
    )

    @Serializable
    private data class HoldingDto(
        val code: String,
        val name: String,
        val type: String,
        val sharesMicros: Long,
        val costMicros: Long,
        val navMicros: Long,
        val prevNavMicros: Long,
        val targetRatio: Double,
        val navEpochDay: Long,
        val sortOrder: Int,
        val createdAt: Long,
        val updatedAt: Long,
    )

    @Serializable
    private data class TradeDto(
        /** 关联键：基金代码，不是自增 id */
        val holdingCode: String,
        val type: String,
        val sharesMicros: Long,
        val navMicros: Long,
        val feeMicros: Long,
        val amountMicros: Long,
        val epochDay: Long,
        val note: String,
        val createdAt: Long,
    )

    @Serializable
    private data class NavDto(
        val holdingCode: String,
        val epochDay: Long,
        val navMicros: Long,
        val accNavMicros: Long,
    )

    private fun HoldingEntity.toDto() = HoldingDto(
        code, name, type, sharesMicros, costMicros, navMicros, prevNavMicros,
        targetRatio, navEpochDay, sortOrder, createdAt, updatedAt,
    )

    private fun TradeEntity.toDto(code: String) = TradeDto(
        code, type, sharesMicros, navMicros, feeMicros, amountMicros, epochDay, note, createdAt,
    )

    private fun NavPointEntity.toDto(code: String) = NavDto(
        code, epochDay, navMicros, accNavMicros,
    )

    private fun HoldingDto.toEntity() = HoldingEntity(
        code = code, name = name, type = type,
        sharesMicros = sharesMicros, costMicros = costMicros,
        navMicros = navMicros, prevNavMicros = prevNavMicros,
        targetRatio = targetRatio, navEpochDay = navEpochDay,
        sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun TradeDto.toEntity(holdingId: Long) = TradeEntity(
        holdingId = holdingId,
        type = type, sharesMicros = sharesMicros, navMicros = navMicros,
        feeMicros = feeMicros, amountMicros = amountMicros,
        epochDay = epochDay, note = note, createdAt = createdAt,
    )

    private fun NavDto.toEntity(holdingId: Long) = NavPointEntity(
        holdingId = holdingId, epochDay = epochDay,
        navMicros = navMicros, accNavMicros = accNavMicros,
    )

    companion object {
        /** 备份格式版本。加字段不一定要改它；改语义必须改。 */
        const val SCHEMA_VERSION = 1
    }
}

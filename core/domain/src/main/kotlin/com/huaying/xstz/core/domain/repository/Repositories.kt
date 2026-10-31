package com.huaying.xstz.core.domain.repository

import com.huaying.xstz.core.model.DataSource
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.PortfolioSummary
import com.huaying.xstz.core.model.Sourced
import com.huaying.xstz.core.model.Trade
import kotlinx.coroutines.flow.Flow

/**
 * 仓储接口都定义在 domain 层，实现放在 data 层。
 *
 * 这样 feature 模块只依赖 domain，不认识 Room / OkHttp，
 * 换存储、换数据源都不用碰 UI 代码——旧版正是缺这一层，
 * 导致每个 ViewModel 都直接 import 了 Room 的 DAO。
 */
interface HoldingRepository {
    fun observeHoldings(): Flow<List<Holding>>
    fun observeSummary(): Flow<PortfolioSummary>
    suspend fun getHolding(id: Long): Holding?
    suspend fun addHolding(code: String, name: String, type: com.huaying.xstz.core.model.AssetType): Long
    suspend fun updateTargetRatio(id: Long, ratio: Double)
    suspend fun deleteHolding(id: Long)
    suspend fun refreshQuotes(): RefreshResult

    /**
     * 灌入一组示例持仓，用于首次体验与 UI 验收。
     *
     * 放在仓储接口而不是 UI 层：示例数据要写库、要触发净值历史，
     * 这些属于数据职责，UI 只负责触发。
     *
     * @return 实际写入的持仓数量
     */
    suspend fun seedSampleData(): Int
}

data class RefreshResult(
    val updated: Int,
    val failed: Int,
    val source: DataSource,
    val message: String?,
)

interface TradeRepository {
    fun observeAll(): Flow<List<Trade>>
    fun observeByHolding(holdingId: Long): Flow<List<Trade>>
    suspend fun recordTrade(trade: Trade): Long
    suspend fun deleteTrade(id: Long)
}

interface NavRepository {
    suspend fun history(holdingId: Long, fromDay: Long, toDay: Long): List<NavPoint>
    suspend fun saveHistory(holdingId: Long, points: List<NavPoint>)
    suspend fun latestDay(holdingId: Long): Long?
}

interface BackupRepository {
    suspend fun export(passphrase: CharArray): String
    suspend fun import(payload: String, passphrase: CharArray): ImportResult
    suspend fun lastBackupTime(): Long
}

data class ImportResult(
    val holdings: Int,
    val trades: Int,
    val navPoints: Int,
)

interface SettingsRepository {
    fun observeThemeMode(): Flow<Int>
    suspend fun setThemeMode(mode: Int)
    fun observePrivacyMode(): Flow<Boolean>
    suspend fun setPrivacyMode(enabled: Boolean)
    fun observeThreshold(): Flow<Double>
    suspend fun setThreshold(value: Double)
    fun observeThresholdMode(): Flow<Int>
    suspend fun setThresholdMode(mode: Int)
    fun observeReducedTransparency(): Flow<Boolean>
    suspend fun setReducedTransparency(enabled: Boolean)
    suspend fun isOnboardingDone(): Boolean
    suspend fun setOnboardingDone(done: Boolean)
    /** 最近一次成功刷新行情的时间，用于首页"数据新鲜度" */
    fun observeLastSync(): Flow<Long>
    suspend fun setLastSync(timestamp: Long)

    companion object {
        /*
         * 常量定义在接口而不是实现类：feature 模块只依赖 domain，
         * 若从 SettingsRepositoryImpl 取值就必须依赖 core:data，
         * 分层会立刻破掉（已踩过这个坑）。
         */
        const val THEME_AUTO = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val THRESHOLD_MODE_ABSOLUTE = 0
        const val THRESHOLD_MODE_RELATIVE = 1

        /** 默认 5% 偏离触发提醒，基金组合再平衡的常见经验值 */
        const val DEFAULT_THRESHOLD = 0.05
    }
}

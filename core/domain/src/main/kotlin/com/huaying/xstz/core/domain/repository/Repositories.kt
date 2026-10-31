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
    suspend fun addHolding(
        code: String,
        name: String,
        type: com.huaying.xstz.core.model.AssetType,
        kind: com.huaying.xstz.core.model.InstrumentKind,
    ): Long
    suspend fun updateTargetRatio(id: Long, ratio: Double)
    suspend fun deleteHolding(id: Long)
    suspend fun refreshQuotes(): RefreshResult

    /**
     * 查询一个代码可能对应的全部标的，用于添加页的候选列表。
     *
     * 返回空列表表示**确实没有**（网络失败、代码不存在、或只有离线样例可用）。
     * 刻意不返回样例数据：样例会为任意 6 位数字编出一个名字，
     * 用户输入 999999 也会看到"某某指数"，那是欺骗。
     *
     * 为什么是列表而不是单个：A 股代码段与基金代码段重叠（000001 既是平安银行
     * 也是华夏成长），任何优先级规则都在替用户做决定，只能让他自己选。
     */
    suspend fun candidates(code: String): List<com.huaying.xstz.core.model.QuoteCandidate>

    /**
     * 按名称或代码联想搜索，用于添加页的输入联想。
     *
     * 与 [candidates] 的分工：
     *   * [candidates] 问"这个代码是什么"，输入是完整 6 位代码，返回该代码的全部可能归属
     *   * [search] 问"叫什么的东西有哪些"，输入可以是"茅台""沪深300""110022"
     *
     * 为什么必须有它：代码是记不住的。要求用户为加茅台去查 600519，
     * 等于把"记得股票代码"当成使用的先决条件。
     *
     * 搜索结果**不含价格**（一次可能返回 20 条，逐条查行情要 20 个请求），
     * 价格在用户选中后再查。返回空列表表示没搜到，不是"网络失败"——
     * 两者在界面上要区分：前者提示"换个关键词"，后者提示"网络异常"。
     */
    suspend fun search(keyword: String): List<com.huaying.xstz.core.model.QuoteCandidate>

    /**
     * 灌入一组示例持仓，用于首次体验与 UI 验收。
     *
     * 放在仓储接口而不是 UI 层：示例数据要写库、要触发净值历史，
     * 这些属于数据职责，UI 只负责触发。
     *
     * @return 实际写入的持仓数量
     */
    suspend fun seedSampleData(): Int

    /**
     * 清空全部持仓及其流水。**破坏性操作**，UI 层必须二次确认才能调用。
     * @return 实际删除的持仓数
     */
    suspend fun clearAllHoldings(): Int

    /**
     * 导出当前持仓为 CSV 文本（RFC 4180，带 UTF-8 BOM）。
     *
     * 带 BOM 的理由：没有 BOM 时 Excel 会把中文读成乱码。这个 CSV 是要给用户
     * 拿到表格软件里看的数据，不能让他一打开就是一堆问号。
     */
    suspend fun exportHoldingsCsv(): String
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

/**
 * 组合级每日快照仓储。
 *
 * 方案 §5.1「净值记录」的对外接口。它是**收益走势图**与**日历视图**的
 * 唯一数据源——这两个页面不直接碰 holdings 或 nav_history，只读这里。
 */
interface SnapshotRepository {
    /** 区间读取（图表按区间查） */
    suspend fun range(fromDay: Long, toDay: Long): List<com.huaying.xstz.core.model.NetValueSnapshot>

    /** 全部读取（日历按月查时可先看总量） */
    suspend fun all(): List<com.huaying.xstz.core.model.NetValueSnapshot>

    /** 最近若干条（倒序取用） */
    suspend fun recent(limit: Int): List<com.huaying.xstz.core.model.NetValueSnapshot>

    /** 最后一条快照的日期；没有快照时返回 null */
    suspend fun latestDay(): Long?

    /** 最早一条快照的日期；没有快照时返回 null。判断历史是否已回填要看它。 */
    suspend fun earliestDay(): Long?

    /**
     * 写入/覆盖某一天的快照。
     *
     * 同一天重复写是幂等的：快照是派生数据，覆盖比报错正确。
     */
    suspend fun save(snapshot: com.huaying.xstz.core.model.NetValueSnapshot)

    /** 批量写入 */
    suspend fun saveAll(snapshots: List<com.huaying.xstz.core.model.NetValueSnapshot>)
}

/**
 * 快照写入用例。
 *
 * 放在 domain 而不是 data，是因为 feature 层要用它，
 * 而 feature 不应该认识 `core:data` 的实现类
 * （已经踩过一次：设置页直接依赖 SettingsRepositoryImpl，
 *  导致常量只能上移才把分层补回来）。
 *
 * 实现见 `core:data` 的 `SnapshotWriterImpl`。
 */
interface SnapshotWriter {
    /**
     * 写入当天的组合快照，并在需要时回填历史。
     *
     * 回填逻辑：从交易流水重放过去每一天——只写今天的话，
     * 用户第一次打开应用时收益走势图只有一天数据、日历一片空白。
     */
    suspend fun writeTodayAndBackfill()
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

    /**
     * 系统级「减弱动效」：无障碍「移除动画」（Secure）与开发者选项
     * 「动画程序时长缩放」（Global）任一为 0 即视为开启。
     * 这是系统无障碍开关，不是用户偏好——App 必须尊重它，否则在已开启
     * 减弱动效的设备上仍播放位移/缩放转场，会被判为无障碍违规。
     */
    fun observeReducedMotion(): Flow<Boolean>

    /**
     * 系统级「高对比度文字」（Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST，
     * API 29+）。开启时，主题把最弱的一档文字（textTertiary / flat）进一步
     * 加深（浅色）或提亮（深色），把已满足 WCAG AA 的对比度再往上推一档，
     * 服务对对比度敏感的用户。这是系统无障碍开关，App 必须尊重。
     */
    fun observeHighContrast(): Flow<Boolean>

    /**
     * 引导完成状态的**响应式**查询。
     *
     * 不能退化成 `flow { emit(isOnboardingDone()) }` 这种一次性 Flow：
     * completeOnboarding() 正是在 App 运行期间写这个值，一次性 Flow 发完即结束，
     * 结果是点了「跳过」/「开始使用」后 UI 永远停在引导页
     * （真机实测：adb 点击与手动点击都退不出去）。必须是 DataStore 的持续流，
     * 写完立刻重组。
     */
    fun observeOnboardingDone(): Flow<Boolean>

    suspend fun isOnboardingDone(): Boolean
    suspend fun setOnboardingDone(done: Boolean)

    /**
     * 初始投资日期（**epochDay**，不是毫秒）。0 表示未设置。
     *
     * 为什么存 epochDay 而不是时间戳：这是一个"日期"而非"时刻"。存毫秒会引入
     * 时区偏移——同一时刻在不同时区落到不同日期，跨时区恢复备份时日期会漂一天。
     *
     * 用途是给收益一个时间基准：没有它就无法回答"这笔收益花了多久赚到"，
     * 也就无从计算年化。方案 §5.2 把它列为设置项。
     */
    fun observeInitialInvestDay(): Flow<Long>
    suspend fun setInitialInvestDay(epochDay: Long)

    /** 最近一次成功刷新行情的时间，用于首页"数据新鲜度" */
    fun observeLastSync(): Flow<Long>
    suspend fun setLastSync(timestamp: Long)

    /** 自动刷新间隔（毫秒）。仅交易时段 + 前台时按此节奏刷新 */
    fun observeRefreshIntervalMs(): Flow<Long>
    suspend fun setRefreshIntervalMs(ms: Long)

    /**
     * 后台降频开关。
     *
     * 降频逻辑本身在 `AutoRefreshPolicy`（后台走 `REFRESH_INTERVAL_BACKGROUND`），
     * 此前是恒定生效、用户无法干预。这里补的是用户开关：
     * 关掉后后台仍按前台频率刷，换来更实时的数据，代价是耗电与流量。
     */
    fun observeBackgroundThrottle(): Flow<Boolean>
    suspend fun setBackgroundThrottle(enabled: Boolean)

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

        /** 自动刷新：高频（3s）与常规（5s）两档，对应设置页「流畅 / 省电」 */
        const val REFRESH_INTERVAL_FAST = 3000L
        const val REFRESH_INTERVAL_NORMAL = 5000L
        const val DEFAULT_REFRESH_INTERVAL = REFRESH_INTERVAL_NORMAL

        /**
         * 后台降频间隔：前台按 [DEFAULT_REFRESH_INTERVAL] 刷，
         * 退到后台后拉长到这一步，避免用户看不见时仍高频打接口。
         */
        const val REFRESH_INTERVAL_BACKGROUND = 30_000L
    }
}

/**
 * 一条操作日志（排障用，非业务数据）。
 *
 * 只在 Repository 层写入（方案第 282 行的降级决策）：
 * 旧版 35 处埋点侵入 UI 导致 Composable 里到处是 log 调用，
 * 重构后 UI 只留页面曝光与显式操作，数据变更由仓库自身记录。
 */
data class OperationLogEntry(
    val id: Long,
    val action: String,
    val detail: String,
    val createdAt: Long,
)

/** 操作日志仓库：查询与清理。写入发生在各业务仓库内部，不经过本接口 */
interface OperationLogRepository {
    /** 最近 [limit] 条，新的在前 */
    fun observeRecent(limit: Int = 200): Flow<List<OperationLogEntry>>

    /** 清理 [before] 之前的日志。返回删除条数 */
    suspend fun purgeBefore(before: Long): Int

    /** 清空。仅设置页「清空操作日志」入口使用 */
    suspend fun clear()
}

/**
 * 可信时间仓库（方案 §5.1「时间防篡改」）。
 *
 * 提供「不被用户改系统时间影响」的墙钟毫秒，供快照写盘、收益归属等
 * 依赖"今天"的环节使用。底层用 SNTP 校时 + `elapsedRealtime` 锚定，
 * 详见 [com.huaying.xstz.core.common.TrustedClock]。
 */
interface TimeRepository {
    /** 当前可信毫秒时间；未校时退回系统墙钟（不比原生更差） */
    fun trustedNowMs(): Long

    /** 是否已至少成功校时一次 */
    val isCalibrated: Boolean

    /**
     * 尝试按 SNTP(阿里云) → SNTP(pool.ntp.org) → HTTP(淘宝) 三级降级校时。
     * 返回是否成功拿到任一个时间源。失败（全超时）时保留上一次偏移，
     * 不会把已校准状态清零。
     */
    suspend fun resync(): Boolean
}

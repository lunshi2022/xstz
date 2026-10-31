package com.huaying.xstz.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.common.MarketClock
import com.huaying.xstz.core.domain.AppForegroundProvider
import com.huaying.xstz.core.domain.HoldingMath
import com.huaying.xstz.core.domain.QuoteRefreshScheduler
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.SettingsRepository
import com.huaying.xstz.core.domain.repository.SnapshotWriter
import com.huaying.xstz.core.domain.repository.TimeRepository
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.DataSource
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.PortfolioSummary
import com.huaying.xstz.core.model.ThresholdMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OverviewUiState(
    val summary: PortfolioSummary = PortfolioSummary(),
    val holdings: List<Holding> = emptyList(),
    val ratios: Map<Long, Double> = emptyMap(),
    val typeRatios: Map<AssetType, Double> = emptyMap(),
    val privacy: Boolean = false,
    val source: DataSource = DataSource.NONE,
    val lastSync: Long = 0L,
    val refreshing: Boolean = false,
    val message: String? = null,
    val needsRebalance: Boolean = false,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = holdings.isEmpty()
}

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val settings: SettingsRepository,
    private val snapshotWriter: SnapshotWriter,
    private val marketClock: MarketClock,
    private val foreground: AppForegroundProvider,
    private val time: TimeRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    // 默认 NONE 而非 LIVE：全新安装、一条行情都没有时不能宣称"实时"
    private val source = MutableStateFlow(DataSource.NONE)

    /** 刷新频率随设置变化：3s/5s 两档由用户在设置页切换 */
    private val refreshIntervalMs: StateFlow<Long> = settings
        .observeRefreshIntervalMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_REFRESH_INTERVAL)

    /**
     * 后台实际刷新间隔：开关打开时用后台专用间隔，关闭时退回前台间隔。
     * 做成派生流而非一次性读取，用户拨开关后下一拍决策即生效。
     */
    private val backgroundIntervalMs: StateFlow<Long> = combine(
        settings.observeBackgroundThrottle(),
        refreshIntervalMs,
    ) { throttle, foregroundMs ->
        if (throttle) SettingsRepository.REFRESH_INTERVAL_BACKGROUND else foregroundMs
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.REFRESH_INTERVAL_BACKGROUND)

    val state: StateFlow<OverviewUiState> = combine(
        holdingRepository.observeHoldings(),
        settings.observePrivacyMode(),
        settings.observeLastSync(),
        settings.observeThreshold(),
        settings.observeThresholdMode(),
    ) { holdings, privacy, lastSync, threshold, thresholdMode ->
        val summary = HoldingMath.summarize(holdings)
        OverviewUiState(
            summary = summary,
            holdings = holdings,
            ratios = HoldingMath.displayRatios(holdings),
            typeRatios = AssetType.entries.associateWith { HoldingMath.ratioOfType(holdings, it) },
            privacy = privacy,
            lastSync = lastSync,
            needsRebalance = HoldingMath.needsRebalance(
                holdings,
                threshold,
                if (thresholdMode == 1) ThresholdMode.RELATIVE else ThresholdMode.ABSOLUTE,
            ),
            loading = false,
        )
    }.combine(refreshing) { s, r -> s.copy(refreshing = r) }
        .combine(message) { s, m -> s.copy(message = m) }
        // 持仓为空时强制降级为 NONE：没有任何标的就没有"数据来源"可言，
        // 沿用上一次刷新留下的 LIVE 会让人误以为界面上的 ¥0.00 是实时行情
        .combine(source) { s, src ->
            s.copy(source = if (s.holdings.isEmpty()) DataSource.NONE else src)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    init {
        // 首次进入自动刷新一次行情，让用户立刻看到真实数据
        refresh()
        // 启动自动刷新调度器：仅交易时段 + 前台按设定频率、后台降频。
        // 与上面的 refresh() 互斥——两路都走同一个 refreshing 锁，不会叠加打接口
        QuoteRefreshScheduler(
            marketClock = marketClock,
            foreground = foreground.isForeground,
            refreshIntervalMs = refreshIntervalMs,
            backgroundIntervalMs = backgroundIntervalMs,
            onRefresh = { refresh() },
        ).start(viewModelScope)
        // best-effort 校时：成功一次后，可信时间此后由 elapsedRealtime 锚定，
        // 即便之后断网也不会被改系统时间带偏。不阻塞界面。
        viewModelScope.launch { time.resync() }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val result = runCatching { holdingRepository.refreshQuotes() }
            refreshing.value = false
            result.onSuccess {
                source.value = it.source
                message.value = it.message
                settings.setLastSync(System.currentTimeMillis())
                // 快照是图表与日历的唯一数据源（方案 §5.1）。
                // 放在刷新之后写：这样快照里的净值与界面上显示的是同一批数据。
                runCatching { snapshotWriter.writeTodayAndBackfill() }
            }.onFailure {
                message.value = "刷新失败：${it.message ?: "网络异常"}"
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    /**
     * 载入示例持仓。
     *
     * 存在的理由不是"演示炫技"，而是空界面下无法验证任何东西：
     * 列表行高、占比环形图、数字对齐、涨跌配色全都要有数据才看得出对错。
     */
    fun seedSamples() {
        viewModelScope.launch {
            refreshing.value = true
            val result = runCatching { holdingRepository.seedSampleData() }
            refreshing.value = false
            result.onSuccess { n ->
                /*
                 * 必须把来源标成 SAMPLE。
                 * 示例基金的代码（900001 等）在真实接口里不存在，
                 * 若仍显示"实时"，等于告诉用户这是真实行情 —— 这是不能接受的误导。
                 */
                source.value = DataSource.SAMPLE
                message.value = "已载入 $n 只示例基金（离线样例数据，非真实行情）"
            }.onFailure {
                message.value = "载入失败：${it.message ?: "未知错误"}"
            }
        }
    }

    fun togglePrivacy() {
        viewModelScope.launch {
            settings.setPrivacyMode(!state.value.privacy)
        }
    }
}

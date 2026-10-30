package com.huaying.xstz.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.HoldingMath
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.SettingsRepository
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
    val source: DataSource = DataSource.LIVE,
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
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val source = MutableStateFlow(DataSource.LIVE)

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
        .combine(source) { s, src -> s.copy(source = src) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    init {
        // 首次进入自动刷新一次行情，让用户立刻看到真实数据
        refresh()
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
            }.onFailure {
                message.value = "刷新失败：${it.message ?: "网络异常"}"
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    fun togglePrivacy() {
        viewModelScope.launch {
            settings.setPrivacyMode(!state.value.privacy)
        }
    }
}

package com.huaying.xstz.feature.rebalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.HoldingMath
import com.huaying.xstz.core.domain.RebalanceEngine
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.SettingsRepository
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.RebalancePlan
import com.huaying.xstz.core.model.RebalanceStrategy
import com.huaying.xstz.core.model.ThresholdMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RebalanceUiState(
    val holdings: List<Holding> = emptyList(),
    val plan: RebalancePlan? = null,
    val strategy: RebalanceStrategy = RebalanceStrategy.BALANCED,
    val thresholdPct: Double = 0.05,
    val thresholdMode: ThresholdMode = ThresholdMode.ABSOLUTE,
    val totalValue: Money = Money.ZERO,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = holdings.isEmpty()
    val hasAction: Boolean get() = plan?.isEmpty == false
}

@HiltViewModel
class RebalanceViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val strategy = MutableStateFlow(RebalanceStrategy.BALANCED)

    /** 阈值改动即时落库，避免退出页面就丢 */
    fun setStrategy(s: RebalanceStrategy) {
        strategy.value = s
    }

    fun setThreshold(pct: Double) {
        viewModelScope.launch { settings.setThreshold(pct) }
    }

    fun setThresholdMode(mode: ThresholdMode) {
        viewModelScope.launch {
            settings.setThresholdMode(
                if (mode == ThresholdMode.RELATIVE) 1 else 0
            )
        }
    }

    val state: StateFlow<RebalanceUiState> = combine(
        holdingRepository.observeHoldings(),
        strategy,
        settings.observeThreshold(),
        settings.observeThresholdMode(),
    ) { holdings, s, threshold, modeInt ->
        val mode = if (modeInt == 1) ThresholdMode.RELATIVE else ThresholdMode.ABSOLUTE
        // 纯函数计算，没有副作用，可以在组合过程中直接调用
        val plan = if (holdings.isEmpty()) null else RebalanceEngine.plan(
            holdings = holdings,
            strategy = s,
            thresholdPct = threshold,
            mode = mode,
        )
        RebalanceUiState(
            holdings = holdings,
            plan = plan,
            strategy = s,
            thresholdPct = threshold,
            thresholdMode = mode,
            totalValue = HoldingMath.summarize(holdings).totalMarketValue,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RebalanceUiState())
}

package com.huaying.xstz.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.TradeRepository
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 交易历史的分组：同一交易日的流水归到一起 */
data class TradeDayGroup(
    val epochDay: Long,
    val trades: List<Trade>,
) {
    /**
     * 当日净现金流入。
     *
     * 用 `cashFlow` 汇总而非 `amount`：手续费是真实支出，
     * 只算 amount 会让"今天到底花了多少钱"偏小。
     */
    val netCashFlow: Money
        get() = trades.fold(Money.ZERO) { acc, t -> acc + t.cashFlow }
}

data class TradeHistoryUiState(
    val groups: List<TradeDayGroup> = emptyList(),
    val holdings: Map<Long, Holding> = emptyMap(),
    val loading: Boolean = true,
    /** 当前筛选的类型；null = 全部 */
    val filter: TradeType? = null,
    /** 锁定到单一标的时显示标的名；null = 全部标的 */
    val lockedHoldingName: String? = null,
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    val totalCount: Int get() = groups.sumOf { it.trades.size }
}

/**
 * 交易历史页。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么按日分组而不是平铺
 *
 * 平铺 200 条流水后，用户实际上只能做两件事：翻到底部看最早的，
 * 或者找某一天。前者用不上列表，后者需要"日期"这个抓手。
 * 按日分组给了日期锚点，且能在每组头部显示当日净流入——
 * 这是用户真正关心的数字（今天投进去多少钱）。
 *
 * 筛选用类型而非关键字：流水里没有可搜的文本字段（备注通常为空），
 * 按类型筛选是唯一有意义的维度。
 *
 * 入口有两个：从再平衡页进来看**全部**标的，从持仓详情进来
 * 只看**该标的**（锁定后不可切换——要切换回详情页选别的）。
 * ══════════════════════════════════════════════════════════════════
 */
@HiltViewModel
class TradeHistoryViewModel @Inject constructor(
    private val tradeRepository: TradeRepository,
    holdingRepository: HoldingRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<TradeType?>(null)
    private val holdingFilter = MutableStateFlow<Long?>(null)

    val state: StateFlow<TradeHistoryUiState> =
        combine(
            tradeRepository.observeAll(),
            holdingRepository.observeHoldings(),
            filter,
            holdingFilter,
        ) { trades, holdings, type, holdingId ->
            // 标的过滤必须在类型过滤之前：锁定标的后用户关心的
            // 是"这只基金的全部流水"，类型筛选是进一步的收窄
            val byHolding = if (holdingId == null) trades else trades.filter { it.fundId == holdingId }
            val filtered = if (type == null) byHolding else byHolding.filter { it.type == type }

            TradeHistoryUiState(
                groups = filtered
                    .groupBy { it.epochDay }
                    // 最近的日期在前
                    .toList()
                    .sortedByDescending { it.first }
                    .map { (day, list) ->
                        TradeDayGroup(
                            epochDay = day,
                            trades = list.sortedByDescending { it.createdAt },
                        )
                    },
                holdings = holdings.associateBy { it.fundId },
                loading = false,
                filter = type,
                lockedHoldingName = holdingId?.let { id ->
                    holdings.firstOrNull { it.fundId == id }?.name
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TradeHistoryUiState(),
        )

    fun setFilter(type: TradeType?) {
        filter.value = type
    }

    /** 锁定到单一标的；null 表示查看全部 */
    fun setHoldingFilter(holdingId: Long?) {
        holdingFilter.value = holdingId
    }

    fun deleteTrade(id: Long) {
        viewModelScope.launch { tradeRepository.deleteTrade(id) }
    }
}

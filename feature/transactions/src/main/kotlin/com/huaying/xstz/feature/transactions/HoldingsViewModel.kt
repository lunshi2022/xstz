package com.huaying.xstz.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.HoldingMath
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.NavRepository
import com.huaying.xstz.core.domain.repository.TradeRepository
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Nav
import com.huaying.xstz.core.model.PortfolioSummary
import com.huaying.xstz.core.model.Shares
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 持仓列表的排序口径。三个都按"从高到低"，符合看盘时先看大额的直觉。 */
enum class HoldingSort(val label: String) {
    MARKET_VALUE("市值"),
    DAY_CHANGE("当日"),
    PROFIT("盈亏"),
}

/** 按资产类别分组后的一个分组，带组内小计 */
data class HoldingGroup(
    val type: AssetType,
    val holdings: List<Holding>,
    val marketValue: Money,
    val dayProfit: Money,
    val profit: Money,
    /** 该组占组合总市值的比例 */
    val ratio: Double,
)

data class HoldingsUiState(
    val holdings: List<Holding> = emptyList(),
    val summary: PortfolioSummary = PortfolioSummary(),
    val ratios: Map<Long, Double> = emptyMap(),
    /** fundId → 最近 30 个净值点，供热力线用 */
    val sparklines: Map<Long, List<Double>> = emptyMap(),
    val sort: HoldingSort = HoldingSort.MARKET_VALUE,
    val grouped: Boolean = true,
    val groups: List<HoldingGroup> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = holdings.isEmpty()
}

@HiltViewModel
class HoldingsViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val tradeRepository: TradeRepository,
    private val navRepository: NavRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(HoldingSort.MARKET_VALUE)
    private val grouped = MutableStateFlow(true)
    private val sparklines = MutableStateFlow<Map<Long, List<Double>>>(emptyMap())

    init {
        // 走势线随持仓变化重算。用 collectLatest：持仓快速增删时，
        // 上一次还在跑的 5 次数据库查询会被取消，不会堆积。
        viewModelScope.launch {
            holdingRepository.observeHoldings().collectLatest { list ->
                if (list.isEmpty()) {
                    sparklines.value = emptyMap()
                    return@collectLatest
                }
                val today = LocalDate.now().toEpochDay()
                val map = buildMap {
                    list.forEach { h ->
                        val pts = runCatching {
                            navRepository.history(h.fundId, today - SPARKLINE_DAYS, today)
                        }.getOrDefault(emptyList())
                        // 少于 2 个点画不出线，直接不放进 map，卡片按"无数据"处理
                        if (pts.size >= 2) put(h.fundId, pts.map { it.nav.toDouble() })
                    }
                }
                sparklines.value = map
            }
        }
    }

    val state: StateFlow<HoldingsUiState> = combine(
        holdingRepository.observeHoldings(),
        sort,
        grouped,
        sparklines,
        message,
    ) { holdings, sortMode, isGrouped, sparks, msg ->
        val summary = HoldingMath.summarize(holdings)
        val ratios = HoldingMath.displayRatios(holdings)
        HoldingsUiState(
            holdings = sorted(holdings, sortMode),
            summary = summary,
            ratios = ratios,
            sparklines = sparks,
            sort = sortMode,
            grouped = isGrouped,
            groups = if (isGrouped) group(holdings, sortMode, summary) else emptyList(),
            loading = false,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoldingsUiState())

    private fun sorted(list: List<Holding>, mode: HoldingSort): List<Holding> = when (mode) {
        HoldingSort.MARKET_VALUE -> list.sortedByDescending { it.marketValue.micros }
        HoldingSort.DAY_CHANGE -> list.sortedByDescending { it.dayChangeRate }
        HoldingSort.PROFIT -> list.sortedByDescending { it.profitRate }
    }

    /**
     * 按资产类别分组。
     *
     * 组的顺序不按枚举定义，而按**组内市值降序**——组合里股票占 51% 时，
     * 把"债券"排在第一位是误导。空组不出现。
     */
    private fun group(
        holdings: List<Holding>,
        mode: HoldingSort,
        summary: PortfolioSummary,
    ): List<HoldingGroup> =
        holdings.groupBy { it.type }
            .map { (type, items) ->
                val value = items.fold(Money.ZERO) { acc, h -> acc + h.marketValue }
                val day = items.fold(Money.ZERO) { acc, h -> acc + h.dayProfit }
                val profit = items.fold(Money.ZERO) { acc, h -> acc + h.profit }
                HoldingGroup(
                    type = type,
                    holdings = sorted(items, mode),
                    marketValue = value,
                    dayProfit = day,
                    profit = profit,
                    ratio = summary.ratioOf(value),
                )
            }
            .sortedByDescending { it.marketValue.micros }

    fun setSort(mode: HoldingSort) {
        sort.value = mode
    }

    fun toggleGrouped() {
        grouped.value = !grouped.value
    }

    /**
     * 录入一笔交易。
     *
     * 校验三件事：份额为正、净值合理、卖出不超过持有。
     * 第三条在仓储层还有一道兜底，但在这里先拦能给用户更清楚的提示。
     */
    fun recordTrade(
        holding: Holding,
        type: TradeType,
        sharesText: String,
        navText: String,
        feeText: String,
    ) {
        val shares = sharesText.toDoubleOrNull()
        val nav = navText.toDoubleOrNull()
        val fee = feeText.toDoubleOrNull() ?: 0.0

        // 用提前 return 而不是 when，这样 Kotlin 能做智能转换，
        // 后面就不必写 shares!! 这种在 lambda 里还可能失效的断言
        if (shares == null || shares <= 0.0) {
            message.value = "份额必须大于 0"
            return
        }
        if (nav == null || nav <= 0.0) {
            message.value = "净值必须大于 0"
            return
        }
        if (fee < 0.0) {
            message.value = "手续费不能为负"
            return
        }
        if (type == TradeType.SELL && Shares.fromDouble(shares) > holding.shares) {
            message.value = "卖出份额超过持有 ${holding.shares.toDouble()}"
            return
        }

        viewModelScope.launch {
            runCatching {
                tradeRepository.recordTrade(
                    Trade(
                        fundId = holding.fundId,
                        type = type,
                        shares = Shares.fromDouble(shares),
                        nav = Nav.fromDouble(nav),
                        fee = Money.fromYuan(fee),
                        amount = Money.fromYuan(shares * nav),
                        epochDay = LocalDate.now().toEpochDay(),
                        note = "",
                    )
                )
            }.onSuccess { message.value = "${type.displayName}成功" }
                .onFailure { message.value = "${type.displayName}失败：${it.message}" }
        }
    }

    fun deleteHolding(holding: Holding) {
        viewModelScope.launch {
            runCatching { holdingRepository.deleteHolding(holding.fundId) }
                .onSuccess { message.value = "已删除 ${holding.name}" }
                .onFailure { message.value = "删除失败：${it.message}" }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private companion object {
        /** 卡片内迷你线取最近 30 天：短到能反映"最近"，长到不会一根直线 */
        const val SPARKLINE_DAYS = 30L
    }
}

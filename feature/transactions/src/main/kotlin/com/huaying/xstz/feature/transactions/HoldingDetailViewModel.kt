package com.huaying.xstz.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.NavRepository
import com.huaying.xstz.core.domain.repository.TradeRepository
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.Trade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.sqrt

/** 详情页的净值区间。比走势页少一档"全部"——详情页要给明细与流水留空间。 */
enum class DetailRange(val label: String, val days: Long) {
    M1("1月", 30),
    M3("3月", 90),
    M6("6月", 180),
    Y1("1年", 365),
}

data class HoldingDetailUiState(
    val holding: Holding? = null,
    val points: List<NavPoint> = emptyList(),
    val range: DetailRange = DetailRange.M3,
    val trades: List<Trade> = emptyList(),
    val loading: Boolean = true,
) {
    val hasChart: Boolean get() = points.size >= 2

    /** 区间涨跌：相对区间首日 */
    val rangeChange: Double
        get() = if (points.size < 2 || points.first().nav.isZero) 0.0
        else (points.last().nav.micros - points.first().nav.micros).toDouble() / points.first().nav.micros

    val highest: NavPoint? get() = points.maxByOrNull { it.nav.micros }
    val lowest: NavPoint? get() = points.minByOrNull { it.nav.micros }

    /** 区间最大回撤，与走势页同口径 */
    val maxDrawdown: Double
        get() {
            if (points.size < 2) return 0.0
            var peak = points.first().nav.micros
            var worst = 0.0
            points.forEach { p ->
                if (p.nav.micros > peak) peak = p.nav.micros
                if (peak > 0L) {
                    val dd = (p.nav.micros - peak).toDouble() / peak
                    if (dd < worst) worst = dd
                }
            }
            return worst
        }

    /** 年化波动率：日收益率标准差 × √252 */
    val annualVolatility: Double
        get() {
            if (points.size < 3) return 0.0
            val returns = points.zipWithNext { a, b ->
                if (a.nav.isZero) 0.0
                else (b.nav.micros - a.nav.micros).toDouble() / a.nav.micros
            }
            val mean = returns.average()
            val variance = returns.sumOf { (it - mean) * (it - mean) } / returns.size
            return sqrt(variance) * sqrt(TRADING_DAYS_PER_YEAR)
        }

    private companion object {
        const val TRADING_DAYS_PER_YEAR = 252.0
    }
}

/**
 * 持仓详情。
 *
 * 不复用 HoldingsViewModel：详情页需要额外的净值区间与流水流，
 * 塞进列表页会让那个 StateFlow 的组合爆炸（持仓 × 排序 × 分组 ×
 * 走势线 × 区间 × 流水），而列表页根本用不到后两项。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HoldingDetailViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val navRepository: NavRepository,
    private val tradeRepository: TradeRepository,
) : ViewModel() {

    private val fundId = MutableStateFlow<Long?>(null)
    private val range = MutableStateFlow(DetailRange.M3)

    private val chartFlow: Flow<List<NavPoint>> = combine(fundId, range) { id, r -> id to r }
        .flatMapLatest { (id, r) ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                val today = LocalDate.now().toEpochDay()
                flow { emit(navRepository.history(id, today - r.days, today)) }
            }
        }

    private val tradesFlow: Flow<List<Trade>> = fundId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else tradeRepository.observeByHolding(id)
    }

    /**
     * 持仓本身也要跟着变：录完一笔交易后市值、份额、成本必须立刻刷新。
     * 直接观察全量再过滤，比单独写一个 getById 的 Flow 简单，且不存在
     * "写入后订阅收不到"的问题——列表流本身就是 Room 的 invalidation 驱动。
     */
    private val holdingFlow: Flow<Holding?> = fundId.flatMapLatest { id ->
        if (id == null) {
            flowOf<Holding?>(null)
        } else {
            holdingRepository.observeHoldings()
                .map { list -> list.firstOrNull { it.fundId == id } }
        }
    }

    val state: StateFlow<HoldingDetailUiState> = combine(
        holdingFlow,
        chartFlow,
        range,
        tradesFlow,
    ) { holding, points, r, trades ->
        HoldingDetailUiState(
            holding = holding,
            points = points,
            range = r,
            trades = trades,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoldingDetailUiState())

    fun load(id: Long) {
        if (fundId.value != id) fundId.value = id
    }

    fun setRange(r: DetailRange) {
        range.value = r
    }

    /** 下拉刷新：只刷这一只也要走全量接口（接口没有单只批量），失败静默 */
    fun refreshQuotes() {
        viewModelScope.launch {
            runCatching { holdingRepository.refreshQuotes() }
        }
    }
}

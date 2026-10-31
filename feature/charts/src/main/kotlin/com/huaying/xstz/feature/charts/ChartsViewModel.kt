package com.huaying.xstz.feature.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.NavRepository
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.NavPoint
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
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** 时间范围 */
enum class ChartRange(val label: String, val days: Long) {
    M1("1月", 30),
    M3("3月", 90),
    M6("6月", 180),
    Y1("1年", 365),
    ALL("全部", 3650),
}

data class ChartsUiState(
    val holdings: List<Holding> = emptyList(),
    val selectedId: Long? = null,
    val points: List<NavPoint> = emptyList(),
    val range: ChartRange = ChartRange.M6,
    val loading: Boolean = true,
) {
    val selected: Holding? get() = holdings.firstOrNull { it.fundId == selectedId }

    /** 区间涨跌幅：末值相对首值 */
    val changeRate: Double
        get() = if (points.size < 2 || points.first().nav.isZero) 0.0
        else (points.last().nav.micros - points.first().nav.micros).toDouble() / points.first().nav.micros

    val highest: NavPoint? get() = points.maxByOrNull { it.nav.micros }
    val lowest: NavPoint? get() = points.minByOrNull { it.nav.micros }

    val hasData: Boolean get() = points.size >= 2
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val navRepository: NavRepository,
) : ViewModel() {

    /** 用户手动选中的基金；null 表示"跟随第一只" */
    private val manualSelection = MutableStateFlow<Long?>(null)
    private val range = MutableStateFlow(ChartRange.M6)

    /** 把持仓、选中项、范围三者一起纳入，这样"自动选中第一只"不需要额外的一帧 */
    private val query = combine(
        holdingRepository.observeHoldings(),
        manualSelection,
        range,
    ) { holdings, manual, r -> Triple(holdings, manual, r) }

    private val pointsFlow: Flow<Pair<List<Holding>, List<NavPoint>>> =
        query.flatMapLatest { (holdings, manual, r) ->
            // 手动选择优先；否则取第一只持仓
            val effectiveId = manual ?: holdings.firstOrNull()?.fundId
            if (effectiveId == null) {
                flowOf(holdings to emptyList())
            } else {
                val today = LocalDate.now().toEpochDay()
                flow {
                    val pts = navRepository.history(effectiveId, today - r.days, today)
                    emit(holdings to pts)
                }
            }
        }

    val state: StateFlow<ChartsUiState> = combine(
        pointsFlow,
        manualSelection,
        range,
    ) { (holdings, points), manual, r ->
        ChartsUiState(
            holdings = holdings,
            selectedId = manual ?: holdings.firstOrNull()?.fundId,
            points = points,
            range = r,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    fun select(fundId: Long) {
        manualSelection.value = fundId
    }

    fun setRange(r: ChartRange) {
        range.value = r
    }
}

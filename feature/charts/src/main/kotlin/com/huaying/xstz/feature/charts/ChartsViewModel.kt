package com.huaying.xstz.feature.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.HoldingMath
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.NavRepository
import com.huaying.xstz.core.domain.repository.SnapshotRepository
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.NetValueSnapshot
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
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.sqrt

/** 时间范围 */
enum class ChartRange(val label: String, val days: Long) {
    M1("1月", 30),
    M3("3月", 90),
    M6("6月", 180),
    Y1("1年", 365),
    ALL("全部", 3650),
}

/**
 * 图表维度。
 *
 * 方案 §2 模块7 要求"收益走势 / 资产配置双维度"，模块8 要求"日历视图"。
 * 三者回答不同问题，但**共用同一份快照表**：
 *   * [PROFIT] 「我赚了多少」——纵轴是累计收益金额，看的是结果
 *   * [ALLOCATION]「钱压在哪」——纵轴是各类资产占比，看的是结构
 *   * [CALENDAR]「哪天赚哪天亏」——按日格子，看的是节奏
 *
 * 日历放在这里而不是新开一个 Tab：它和收益走势读的是同一张表、回答的是
 * 同一类问题（"过去发生了什么"），做成第六个 Tab 会让底部导航变拥挤，
 * 也不符合方案 §8.4 定的五项导航结构。
 */
enum class ChartDimension(val label: String) {
    PROFIT("收益走势"),
    ALLOCATION("资产配置"),
    CALENDAR("盈亏日历"),
    SINGLE("单只标的"),
    ;

    /**
     * 是否读组合快照表。
     *
     * 前三个是组合级（net_value_snapshot），[SINGLE] 是标的级（nav_history）。
     * 两者的纵轴含义、可算指标、空数据判据全都不同，所以必须显式区分——
     * 早期版本把"组合"与"标的"混在一个分支里，导致切到标的视图时
     * 组合的空快照被当成"没有数据"。
     */
    val isPortfolio: Boolean get() = this != SINGLE
}

/** 资产配置图上的一条序列 */
data class AllocationSeries(
    val type: AssetType,
    val snapshots: List<NetValueSnapshot>,
)

data class ChartsUiState(
    val holdings: List<Holding> = emptyList(),
    val selectedId: Long? = null,
    val points: List<NavPoint> = emptyList(),
    val range: ChartRange = ChartRange.M6,
    val dimension: ChartDimension = ChartDimension.PROFIT,
    /** 组合级：按日快照 */
    val snapshots: List<NetValueSnapshot> = emptyList(),
    /** 日历当前查看的月份。与 [range] 独立，理由见 [calendarMonth] */
    val calendarMonthState: YearMonth? = null,
    val loading: Boolean = true,
) {
    val selected: Holding? get() = holdings.firstOrNull { it.fundId == selectedId }

    // ---------- 标的级指标（原有） ----------

    /** 区间涨跌幅：末值相对首值 */
    val changeRate: Double
        get() = if (points.size < 2 || points.first().nav.isZero) 0.0
        else (points.last().nav.micros - points.first().nav.micros).toDouble() / points.first().nav.micros

    val highest: NavPoint? get() = points.maxByOrNull { it.nav.micros }
    val lowest: NavPoint? get() = points.minByOrNull { it.nav.micros }

    val hasData: Boolean get() = points.size >= 2

    // ---------- 组合级指标（新增） ----------

    val hasSnapshot: Boolean get() = snapshots.size >= 2

    /** 区间内的首个 / 末个快照 */
    val firstSnapshot: NetValueSnapshot? get() = snapshots.firstOrNull()
    val lastSnapshot: NetValueSnapshot? get() = snapshots.lastOrNull()

    /** 区间累计收益（末快照的累计收益 - 首快照的累计收益） */
    val portfolioProfit: Long
        get() {
            val first = firstSnapshot ?: return 0L
            val last = lastSnapshot ?: return 0L
            return last.profit.micros - first.profit.micros
        }

    /**
     * 区间收益率。
     *
     * 基数是"首日总资产"，不是"本金"——本金在区间内会因申赎变化，
     * 拿它当分母会让"这个月赚了多少"变成一个无法解释的数。
     */
    val portfolioProfitRate: Double
        get() {
            val first = firstSnapshot ?: return 0.0
            if (first.totalValue.isZero) return 0.0
            return portfolioProfit.toDouble() / first.totalValue.micros
        }

    /** 区间内累计的当日盈亏之和（用于核对：应接近 portfolioProfit） */
    val sumDayProfit: Long get() = snapshots.sumOf { it.dayProfit.micros }

    /** 资产配置：按类型拆成四条序列，只保留有市值的类型 */
    val allocationSeries: List<AllocationSeries>
        get() {
            if (snapshots.isEmpty()) return emptyList()
            return AssetType.entries.mapNotNull { type ->
                val nonZero = snapshots.any { it.valueOf(type).micros > 0 }
                if (!nonZero) null else AllocationSeries(type, snapshots)
            }
        }

    /** 最新一日的配置占比，用于图例 */
    fun latestRatio(type: AssetType): Double = lastSnapshot?.ratioOf(type) ?: 0.0

    /** 区间内的最大回撤（组合级，按总资产算） */
    val portfolioMaxDrawdown: Double
        get() {
            if (snapshots.size < 2) return 0.0
            var peak = snapshots.first().totalValue.micros
            var worst = 0.0
            snapshots.forEach { s ->
                val v = s.totalValue.micros
                if (v > peak) peak = v
                if (peak > 0L) {
                    val dd = (v - peak).toDouble() / peak
                    if (dd < worst) worst = dd
                }
            }
            return worst
        }

    /**
     * 组合级年化波动：用**每日收益率**算标准差。
     *
     * 注意不能用"总资产的日变化率"——那会把申购赎回当成波动。
     * 这里用 dayProfit / 昨收市值，与日历页的口径一致。
     */
    val portfolioVolatility: Double
        get() {
            if (snapshots.size < 3) return 0.0
            val returns = snapshots.mapNotNull { s ->
                val yesterday = s.totalValue.micros - s.dayProfit.micros
                if (yesterday <= 0L) null else s.dayProfit.micros.toDouble() / yesterday
            }
            if (returns.size < 2) return 0.0
            val mean = returns.average()
            val variance = returns.sumOf { (it - mean) * (it - mean) } / returns.size
            return sqrt(variance) * sqrt(TRADING_DAYS_PER_YEAR)
        }

    /** 盈利天数 / 总天数，直观回答"这段行情好不好做" */
    val winDays: Int get() = snapshots.count { it.dayProfit.micros > 0 }
    val lossDays: Int get() = snapshots.count { it.dayProfit.micros < 0 }

    /** 最大单日盈利 / 亏损 */
    val bestDay: NetValueSnapshot? get() = snapshots.maxByOrNull { it.dayProfit.micros }
    val worstDay: NetValueSnapshot? get() = snapshots.minByOrNull { it.dayProfit.micros }

    // ---------- 日历 ----------

    /**
     * 日历用的一整个月的数据。
     *
     * 注意：日历的"月"由 [calendarMonth] 单独控制，**不受 [range] 限制**——
     * 用户点"1月"只想看走势图的一个月，但翻看日历时会想回看更早的月份。
     * 两件事共用 range 会让"翻上个月"变成"改区间"，语义完全错位。
     */
    val calendarMonth: YearMonth get() = calendarMonthState ?: YearMonth.now()

    /** 当月每日盈亏，key 是当月内的日（1~31） */
    val monthPnl: Map<Int, NetValueSnapshot>
        get() {
            val ym = calendarMonth
            return snapshots.mapNotNull { s ->
                val d = LocalDate.ofEpochDay(s.epochDay)
                if (d.year == ym.year && d.monthValue == ym.monthValue) d.dayOfMonth to s else null
            }.toMap()
        }

    /** 当月合计盈亏 */
    val monthProfit: Long get() = snapshotsInMonth().sumOf { it.dayProfit.micros }

    /** 当月盈利天数 / 亏损天数 */
    val monthWinDays: Int get() = snapshotsInMonth().count { it.dayProfit.micros > 0 }
    val monthLossDays: Int get() = snapshotsInMonth().count { it.dayProfit.micros < 0 }

    /** 当月单日极值，用于确定格子配色强度 */
    val monthMaxAbsProfit: Long
        get() = snapshotsInMonth().maxOfOrNull { kotlin.math.abs(it.dayProfit.micros) } ?: 0L

    /** 落在 [calendarMonth] 内的快照 */
    private fun snapshotsInMonth(): List<NetValueSnapshot> {
        val ym = calendarMonth
        return snapshots.filter {
            val d = LocalDate.ofEpochDay(it.epochDay)
            d.year == ym.year && d.monthValue == ym.monthValue
        }
    }

    // ---------- 标的级风险指标（原有，保留） ----------

    /**
     * 最大回撤：从任一历史高点到其后最低点的最大跌幅，恒为负或零。
     */
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

    /**
     * 年化波动率：日收益率标准差 × √252。
     */
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

    /** 区间振幅：(最高 - 最低) / 最低。衡量波动幅度，与方向无关。 */
    val amplitude: Double
        get() {
            val hi = highest ?: return 0.0
            val lo = lowest ?: return 0.0
            if (lo.nav.isZero) return 0.0
            return (hi.nav.micros - lo.nav.micros).toDouble() / lo.nav.micros
        }

    /** 交易天数（数据点数，不是自然日跨度） */
    val tradingDays: Int get() = points.size

    private companion object {
        const val TRADING_DAYS_PER_YEAR = 252.0
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val holdingRepository: HoldingRepository,
    private val navRepository: NavRepository,
    private val snapshotRepository: SnapshotRepository,
) : ViewModel() {

    /** 用户手动选中的基金；null 表示"跟随第一只" */
    private val manualSelection = MutableStateFlow<Long?>(null)
    private val range = MutableStateFlow(ChartRange.M6)
    private val dimension = MutableStateFlow(ChartDimension.PROFIT)

    /** 日历当前看的月份。null 表示"跟随最新快照所在的月" */
    private val month = MutableStateFlow<YearMonth?>(null)

    /** 四个输入一起进流，避免"切维度要等两帧" */
    private val query = combine(
        holdingRepository.observeHoldings(),
        combine(manualSelection, range, dimension) { manual, r, d -> Triple(manual, r, d) },
        month,
    ) { holdings, (manual, r, d), m -> Query(holdings, manual, r, d, m) }

    private data class Query(
        val holdings: List<Holding>,
        val manual: Long?,
        val range: ChartRange,
        val dimension: ChartDimension,
        val month: YearMonth?,
    )

    private data class Loaded(
        val query: Query,
        val points: List<NavPoint>,
        val snapshots: List<NetValueSnapshot>,
    )

    private val loadedFlow: Flow<Loaded> = query.flatMapLatest { q ->
        val today = LocalDate.now().toEpochDay()
        flow {
            if (q.holdings.isEmpty()) {
                emit(Loaded(q, emptyList(), emptyList()))
                return@flow
            }

            // 日历要能翻到 range 之外的月份，所以它的查询窗口取"整年"，
            // 而不是跟着 range 走——否则点"1月"就翻不到上个月。
            val from = when {
                q.dimension == ChartDimension.CALENDAR -> {
                    val ym = q.month ?: YearMonth.from(LocalDate.now())
                    // 往回多看 12 个月，够翻一年
                    ym.minusMonths(12).atDay(1).toEpochDay()
                }
                else -> today - q.range.days
            }

            val snaps = if (q.dimension.isPortfolio) {
                runCatching { snapshotRepository.range(from, today) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val pts = if (!q.dimension.isPortfolio) {
                val id = q.manual ?: q.holdings.first().fundId
                runCatching { navRepository.history(id, today - q.range.days, today) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            emit(Loaded(q, pts, snaps))
        }
    }

    val state: StateFlow<ChartsUiState> = loadedFlow
        .combine(combine(manualSelection, range, dimension, month) { m, r, d, mo -> Quad(m, r, d, mo) }) { loaded, q ->
            ChartsUiState(
                holdings = loaded.query.holdings,
                // 手动选中优先；切换维度不影响选择
                selectedId = q.manual ?: loaded.query.holdings.firstOrNull()?.fundId,
                points = loaded.points,
                range = q.range,
                dimension = q.dimension,
                snapshots = loaded.snapshots,
                calendarMonthState = q.month,
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    private data class Quad(
        val manual: Long?,
        val range: ChartRange,
        val dimension: ChartDimension,
        val month: YearMonth?,
    )

    fun select(fundId: Long) {
        manualSelection.value = fundId
    }

    fun setRange(r: ChartRange) {
        range.value = r
    }

    fun setDimension(d: ChartDimension) {
        dimension.value = d
    }

    /** 日历翻月。切到日历时自动定位到最新有数据的月份 */
    fun shiftMonth(delta: Long) {
        val current = month.value ?: YearMonth.from(LocalDate.now())
        month.value = current.plusMonths(delta)
    }
}

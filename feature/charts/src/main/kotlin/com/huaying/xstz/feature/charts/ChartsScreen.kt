package com.huaying.xstz.feature.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.model.NetValueSnapshot
import com.huaying.xstz.core.ui.AllocationStackChart
import com.huaying.xstz.core.ui.EmptyState
import com.huaying.xstz.core.ui.HairlineDivider
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.NavChart
import com.huaying.xstz.core.ui.ProfitAreaChart
import com.huaying.xstz.core.ui.SectionHeader
import com.huaying.xstz.core.ui.SolidCard

/**
 * 走势页。
 *
 * 图表按 ADR-010 全自绘，不引任何图表库。
 *
 * 两个维度 × 三种标的视图（方案 §2 模块7「收益走势 / 资产配置双维度」）：
 *   * 收益走势：组合级 —— 读快照表，画累计收益，可跨零点
 *   * 资产配置：组合级 —— 读快照表，画各类资产占比的堆叠面积
 *   * 单只标的：标的级 —— 读 nav_history，画净值曲线 + 区间风险指标
 *
 * 为什么"单只标的"不算第三个维度：它的数据源完全不同（nav_history 而非
 * net_value_snapshot），纵轴含义也不同（净值 vs 收益/占比）。
 * 放进同一个维度枚举里会让"切维度"这件简单的事实变得含混。
 */
@Composable
fun ChartsScreen(
    state: ChartsUiState,
    onSelect: (Long) -> Unit,
    onRangeChange: (ChartRange) -> Unit,
    onDimensionChange: (ChartDimension) -> Unit,
    onShiftMonth: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette

    // 十字准星选中的点。切换维度/基金/区间时自动清空——
    // 否则索引会指向另一条序列上的错误位置。
    var scrub by remember(state.selectedId, state.range, state.dimension) {
        mutableStateOf<Int?>(null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LargeTitleHeader(
            title = "走势",
            subtitle = "组合收益 · 全部本地数据",
        )

        if (state.holdings.isEmpty()) {
            EmptyState(
                title = "没有可绘制的数据",
                hint = "先在持仓页添加标的并录入交易",
            )
            return@Column
        }

        // ---------- 维度切换 ----------
        DimensionTabs(current = state.dimension, onSelect = onDimensionChange)

        // ---------- 时间范围 ----------
        RangeTabs(current = state.range, onSelect = onRangeChange)

        when {
            state.dimension == ChartDimension.PROFIT -> ProfitView(
                state = state,
                scrub = scrub,
                onScrubChange = { scrub = it },
            )

            state.dimension == ChartDimension.ALLOCATION -> AllocationView(
                state = state,
                scrub = scrub,
                onScrubChange = { scrub = it },
            )

            state.dimension == ChartDimension.CALENDAR -> CalendarView(
                state = state,
                onShiftMonth = onShiftMonth,
            )

            else -> SingleInstrumentView(
                state = state,
                scrub = scrub,
                onScrubChange = { scrub = it },
                onSelect = onSelect,
            )
        }

        SectionHeader(
            title = "数据说明",
            subtitle = if (state.dimension.isPortfolio)
                "组合维度取自每日快照，首次加载会回填最近一年"
            else
                "标的历史取自行情接口；示例数据为确定性生成的演示曲线",
        )
    }
}

// ==================================================================
// 维度与区间切换
// ==================================================================

@Composable
private fun DimensionTabs(current: ChartDimension, onSelect: (ChartDimension) -> Unit) {
    val palette = XstzTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.glassFill, SquircleShape(14.dp, 2.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ChartDimension.entries.forEach { d ->
            val selected = d == current
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (selected) palette.accent.copy(alpha = 0.20f) else Color.Transparent,
                        SquircleShape(10.dp, 2.4f),
                    )
                    .clickable { onSelect(d) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.label,
                    style = XstzType.caption,
                    color = if (selected) palette.accent else palette.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RangeTabs(current: ChartRange, onSelect: (ChartRange) -> Unit) {
    val palette = XstzTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.glassFill, SquircleShape(14.dp, 2.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ChartRange.entries.forEach { r ->
            val selected = r == current
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (selected) palette.accent.copy(alpha = 0.20f) else Color.Transparent,
                        SquircleShape(10.dp, 2.4f),
                    )
                    .clickable { onSelect(r) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    r.label,
                    style = XstzType.caption,
                    color = if (selected) palette.accent else palette.textSecondary,
                )
            }
        }
    }
}

// ==================================================================
// 组合 · 收益走势
// ==================================================================

@Composable
private fun ProfitView(
    state: ChartsUiState,
    scrub: Int?,
    onScrubChange: (Int?) -> Unit,
) {
    val palette = XstzTheme.palette
    val snaps = state.snapshots

    if (snaps.size < 2) {
        SolidCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 16.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text("还没有组合收益数据", style = XstzType.headline, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "录入至少一笔交易后刷新汇总，系统会回放历史并生成每日快照",
                    style = XstzType.caption,
                    color = palette.textTertiary,
                )
            }
        }
        return
    }

    val scrubbed: NetValueSnapshot? = scrub?.let { snaps.getOrNull(it) }

    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 16.dp,
    ) {
        Column {
            val displayProfit = scrubbed?.profit ?: snaps.last().profit
            val displayRate = if (scrubbed != null) {
                val base = scrubbed.principal.micros
                if (base == 0L) 0.0 else scrubbed.profit.micros.toDouble() / base
            } else {
                state.portfolioProfitRate
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (scrubbed != null) formatDate(scrubbed.epochDay) else "累计收益",
                        style = XstzType.callout,
                        color = palette.textSecondary,
                    )
                    Text(
                        Formats.signedCurrency(displayProfit.toYuan()),
                        style = XstzType.numericL,
                        color = palette.change(displayProfit.micros.toDouble()),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formats.percent(displayRate),
                        style = XstzType.numericM,
                        color = palette.change(displayRate),
                    )
                    Text(
                        if (scrubbed != null) "累计收益率" else "${state.range.label}区间",
                        style = XstzType.caption2,
                        color = palette.textTertiary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            ProfitAreaChart(
                values = snaps.map { it.profit.micros },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                upColor = palette.up,
                downColor = palette.down,
                gridColor = palette.divider,
                zeroAxisColor = palette.textTertiary.copy(alpha = 0.5f),
                scrubIndex = scrub,
                onScrubChange = onScrubChange,
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDate(snaps.first().epochDay), style = XstzType.caption2, color = palette.textTertiary)
                Text(
                    formatDate(snaps[snaps.size / 2].epochDay),
                    style = XstzType.caption2,
                    color = palette.textTertiary,
                )
                Text(formatDate(snaps.last().epochDay), style = XstzType.caption2, color = palette.textTertiary)
            }
        }
    }

    // ---------- 组合统计 ----------
    SectionHeader(title = "区间统计", subtitle = "${snaps.size} 个交易日")
    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 16.dp,
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell(
                    label = "区间盈亏",
                    value = Formats.signedCurrency(state.portfolioProfit.toDouble() / 10_000.0),
                    color = palette.change(state.portfolioProfit.toDouble()),
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "最大回撤",
                    value = Formats.percent(state.portfolioMaxDrawdown),
                    color = palette.down,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell(
                    label = "年化波动",
                    value = Formats.percent(state.portfolioVolatility),
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "盈利 / 亏损",
                    value = "${state.winDays} / ${state.lossDays}",
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell(
                    label = "单日最佳",
                    value = state.bestDay?.let { Formats.signedCurrency(it.dayProfit.toYuan()) } ?: "—",
                    color = palette.up,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "单日最差",
                    value = state.worstDay?.let { Formats.signedCurrency(it.dayProfit.toYuan()) } ?: "—",
                    color = palette.down,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // ---------- 每日明细 ----------
    SectionHeader(
        title = "每日快照",
        subtitle = "最近 ${minOf(DETAIL_ROWS, snaps.size)} 个交易日",
    )
    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 0.dp,
    ) {
        SnapshotDetailList(snaps = snaps)
    }
}

// ==================================================================
// 组合 · 资产配置
// ==================================================================

@Composable
private fun AllocationView(
    state: ChartsUiState,
    scrub: Int?,
    onScrubChange: (Int?) -> Unit,
) {
    val palette = XstzTheme.palette
    val snaps = state.snapshots
    val series = state.allocationSeries

    if (snaps.size < 2 || series.isEmpty()) {
        SolidCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 16.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text("还没有配置数据", style = XstzType.headline, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "配置图需要至少两天的快照；先录入交易并刷新一次",
                    style = XstzType.caption,
                    color = palette.textTertiary,
                )
            }
        }
        return
    }

    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 16.dp,
    ) {
        Column {
            val refSnapshot = scrub?.let { snaps.getOrNull(it) } ?: snaps.last()
            Text(
                if (scrub != null) formatDate(refSnapshot.epochDay) else "当前配置",
                style = XstzType.callout,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                Formats.currency(refSnapshot.totalValue.toYuan()),
                style = XstzType.numericL,
                color = palette.textPrimary,
            )

            Spacer(Modifier.height(14.dp))

            AllocationStackChart(
                // 按稳定性自下而上堆叠：现金垫底、股票在最上。
                // 顺序固定比按当日占比排序更好——排序会随时间跳跃，看着像数据在闪
                series = series.map { palette.asset(it.type) },
                ratios = series.map { s -> s.snapshots.map { it.ratioOf(s.type).toFloat() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                gridColor = palette.divider,
                scrubIndex = scrub,
                onScrubChange = onScrubChange,
            )

            Spacer(Modifier.height(12.dp))

            // 图例：每类资产一行，占比 + 金额
            series.forEach { s ->
                val value = refSnapshot.valueOf(s.type)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(palette.asset(s.type), CircleShape)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        s.type.displayName,
                        style = XstzType.caption,
                        color = palette.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Formats.ratio(refSnapshot.ratioOf(s.type)),
                        style = XstzType.numericXS,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        Formats.currency(value.toYuan()),
                        style = XstzType.numericXS,
                        color = palette.textTertiary,
                        modifier = Modifier.width(90.dp),
                    )
                }
                HairlineDivider()
            }
        }
    }

    // 配置变化：首末对比，回答"这段区间我调仓了吗"
    SectionHeader(title = "配置变化", subtitle = "区间首日 → 末日")
    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 16.dp,
    ) {
        Column {
            val first = snaps.first()
            series.forEachIndexed { i, s ->
                val before = first.ratioOf(s.type)
                val after = snaps.last().ratioOf(s.type)
                val delta = after - before
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(9.dp).background(palette.asset(s.type), CircleShape)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        s.type.displayName,
                        style = XstzType.caption,
                        color = palette.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${Formats.ratio(before)} → ${Formats.ratio(after)}",
                        style = XstzType.numericXS,
                        color = palette.textTertiary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        // 占比变化用百分点表示，不用"%"——从 20% 到 30% 是 +10 个百分点，
                        // 写成 +50% 会让用户误以为赚了一半
                        "${if (delta >= 0) "+" else ""}${String.format("%.1f", delta * 100)}pp",
                        style = XstzType.numericXS,
                        color = palette.change(delta),
                        modifier = Modifier.width(58.dp),
                    )
                }
                if (i != series.lastIndex) HairlineDivider()
            }
        }
    }
}

// ==================================================================
// 组合 · 盈亏日历
// ==================================================================

/**
 * 月历视图（方案 §2 模块8：月历每日盈亏 + 节假日标注 + 当日明细）。
 *
 * 三个设计决定：
 *
 * 1. **格子颜色按当日盈亏相对当月极值的比例着色**，而不是绝对金额。
 *    绝对金额着色会让「一个月赚 8000」和「赚 80」的日历看起来一模一样——
 *    要么全是深色要么全是浅色。按比例着色才能看出"哪天最猛"。
 *
 * 2. **非交易日不填色**，只显示日期数字（灰）。场外基金周末不更新净值，
 *    若给这些日子填 0 盈亏的浅灰，会被误读成"那天不赚不亏"，
 *    实际是"那天根本没有行情"。两者语义完全不同。
 *
 * 3. **节假日标注**用日期数字下方的圆点，中国节假日不放假的市场规则
 *    比公历周末复杂（调休），这里不硬编码节假日表——
 *    判据统一为"该日有无快照"，这比维护一张会过期的节假日表更可靠。
 */
@Composable
private fun CalendarView(
    state: ChartsUiState,
    onShiftMonth: (Long) -> Unit,
) {
    val palette = XstzTheme.palette
    val ym = state.calendarMonth
    val pnl = state.monthPnl

    if (state.snapshots.isEmpty()) {
        SolidCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 16.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text("还没有盈亏记录", style = XstzType.headline, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "录入交易并刷新后，系统会按日生成快照供日历展示",
                    style = XstzType.caption,
                    color = palette.textTertiary,
                )
            }
        }
        return
    }

    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 16.dp,
    ) {
        Column {
            // ---------- 月份导航 ----------
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(palette.glassFill, CircleShape)
                        .clickable { onShiftMonth(-1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", style = XstzType.headline, color = palette.textSecondary)
                }
                Text(
                    "${ym.year} 年 ${ym.monthValue} 月",
                    style = XstzType.headline,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .background(palette.glassFill, CircleShape)
                        .clickable { onShiftMonth(1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("›", style = XstzType.headline, color = palette.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 当月汇总 ----------
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("当月盈亏", style = XstzType.caption2, color = palette.textTertiary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        Formats.signedCurrency(state.monthProfit / 10_000.0),
                        style = XstzType.numericM,
                        color = palette.change(state.monthProfit.toDouble()),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("盈利 / 亏损", style = XstzType.caption2, color = palette.textTertiary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${state.monthWinDays} / ${state.monthLossDays}",
                        style = XstzType.numericM,
                        color = palette.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- 星期表头 ----------
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                    Text(
                        w,
                        style = XstzType.caption2,
                        color = palette.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ---------- 日格 ----------
            // 周一为第一天：国内日历习惯，与 java.time 的 Mon=1 一致
            val firstDayOffset = ym.atDay(1).dayOfWeek.value - 1
            val daysInMonth = ym.lengthOfMonth()
            val maxAbs = state.monthMaxAbsProfit

            val totalCells = firstDayOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        val dayNumber = cellIndex - firstDayOffset + 1
                        if (dayNumber in 1..daysInMonth) {
                            DayCell(
                                day = dayNumber,
                                snapshot = pnl[dayNumber],
                                maxAbs = maxAbs,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (r != rows - 1) Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(10.dp))

            // 图例
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(palette.down.copy(alpha = 0.55f), CircleShape))
                Spacer(Modifier.size(4.dp))
                Text("亏", style = XstzType.caption2, color = palette.textTertiary)
                Spacer(Modifier.size(12.dp))
                Box(Modifier.size(8.dp).background(palette.glassFill, CircleShape))
                Spacer(Modifier.size(4.dp))
                Text("无行情", style = XstzType.caption2, color = palette.textTertiary)
                Spacer(Modifier.size(12.dp))
                Box(Modifier.size(8.dp).background(palette.up.copy(alpha = 0.55f), CircleShape))
                Spacer(Modifier.size(4.dp))
                Text("盈", style = XstzType.caption2, color = palette.textTertiary)
            }
        }
    }

    // ---------- 当月逐日明细 ----------
    val monthRows = state.snapshots.filter {
        val d = java.time.LocalDate.ofEpochDay(it.epochDay)
        d.year == ym.year && d.monthValue == ym.monthValue
    }.reversed()
    if (monthRows.isNotEmpty()) {
        SectionHeader(title = "当月明细", subtitle = "${monthRows.size} 个交易日")
        SolidCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            contentPadding = 0.dp,
        ) {
            Column {
                monthRows.forEachIndexed { index, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Formats.shortDate(s.epochDay),
                            style = XstzType.numericXS,
                            color = palette.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "周${Formats.weekday(s.epochDay)}",
                            style = XstzType.caption2,
                            color = palette.textTertiary,
                            modifier = Modifier.weight(0.8f),
                        )
                        Text(
                            Formats.currency(s.totalValue.toYuan()),
                            style = XstzType.numericXS,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1.3f),
                        )
                        Text(
                            Formats.signedCurrency(s.dayProfit.toYuan()),
                            style = XstzType.numericXS,
                            color = palette.change(s.dayProfit.micros.toDouble()),
                            modifier = Modifier.weight(1.2f),
                        )
                    }
                    if (index != monthRows.lastIndex) HairlineDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

/**
 * 单个日格。
 *
 * 颜色强度 = |当日盈亏| / 当月最大 |盈亏|，映射到 0.10~0.55 的透明度区间。
 * 下限 0.10 保证"有行情但波动很小"的日子仍能看出填过色，
 * 上限 0.55 避免遮挡格子里的数字。
 */
@Composable
private fun DayCell(
    day: Int,
    snapshot: NetValueSnapshot?,
    maxAbs: Long,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette

    val alpha = when {
        snapshot == null || maxAbs == 0L -> 0f
        else -> {
            val ratio = kotlin.math.abs(snapshot.dayProfit.micros).toFloat() / maxAbs
            /*
             * 实测校准：初版用 0.10 + ratio*0.45（封顶 0.55），截图反推得到的
             * 实际 alpha 只有 0.076~0.152——日格几乎看不见，"日历"退化成
             * 一排淡灰数字。原因是内容层卡片底色是 #F7F9FC（很浅），
             * 10% 的红叠上去只改变 2 个色阶，人眼分辨不出来。
             *
             * 改为 0.20 起、封顶 0.72：
             *   * 下限 0.20 保证最弱的一天也有可辨识的底色（约 6~7 个色阶）
             *   * 上限 0.72 仍能让深色文字保持对比度（红底上黑字约 4.8:1）
             * 强度差仍然按 |当日盈亏| / 当月最大 |盈亏| 线性映射，
             * 所以"哪天赚得多"依然一眼可分。
             */
            (0.20f + ratio * 0.52f).coerceIn(0.20f, 0.72f)
        }
    }
    val fill = when {
        snapshot == null -> Color.Transparent
        snapshot.dayProfit.micros > 0 -> palette.up.copy(alpha = alpha)
        snapshot.dayProfit.micros < 0 -> palette.down.copy(alpha = alpha)
        else -> palette.glassFill
    }
    val textColor = when {
        snapshot == null -> palette.textTertiary
        snapshot.dayProfit.micros != 0L -> palette.textPrimary
        else -> palette.textSecondary
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .height(44.dp)
            .background(fill, SquircleShape(9.dp, 2.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.toString(),
                style = XstzType.caption2,
                color = textColor,
            )
            if (snapshot != null && snapshot.dayProfit.micros != 0L) {
                Text(
                    // 格子里只放金额的绝对值 + 一位小数的"元"，不放符号：
                    // 宽度只够 4~5 个字符，带上符号与千分位会溢出
                    compactAmount(snapshot.dayProfit.micros),
                    style = XstzType.caption2,
                    color = palette.change(snapshot.dayProfit.micros.toDouble()),
                )
            }
        }
    }
}

/** 把万分之元压成格子能放下的短文本：0.3万 / 128 / -45 */
private fun compactAmount(micros: Long): String {
    val yuan = micros / 10_000.0
    val abs = kotlin.math.abs(yuan)
    return when {
        abs >= 10_000 -> String.format("%.0f万", yuan / 10_000)
        abs >= 1_000 -> String.format("%.0f", yuan)
        else -> String.format("%.0f", yuan)
    }
}

// ==================================================================
// 标的级视图（原有功能，保留）
// ==================================================================

@Composable
private fun SingleInstrumentView(
    state: ChartsUiState,
    scrub: Int?,
    onScrubChange: (Int?) -> Unit,
    onSelect: (Long) -> Unit,
) {
    val palette = XstzTheme.palette
    val scrubbed: NavPoint? = scrub?.let { state.points.getOrNull(it) }

    // 标的切换 chips
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.holdings.forEach { h ->
            val selected = h.fundId == state.selectedId
            Box(
                Modifier
                    .background(
                        if (selected) palette.accent.copy(alpha = 0.18f) else palette.glassFill,
                        SquircleShape(12.dp, 2.6f),
                    )
                    .clickable { onSelect(h.fundId) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    h.name,
                    style = XstzType.caption,
                    color = if (selected) palette.accent else palette.textSecondary,
                )
            }
        }
    }

    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 16.dp,
    ) {
        Column {
            val displayNav = scrubbed?.nav ?: state.points.lastOrNull()?.nav
            val displayRate = scrubbed?.let { p ->
                if (state.points.isEmpty() || state.points.first().nav.isZero) 0.0
                else (p.nav.micros - state.points.first().nav.micros).toDouble() /
                    state.points.first().nav.micros
            } ?: state.changeRate

            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (scrubbed != null) formatDate(scrubbed.epochDay)
                        else state.selected?.name ?: "",
                        style = XstzType.callout,
                        color = palette.textSecondary,
                    )
                    Text(
                        displayNav?.let { Formats.nav(it.toDouble()) } ?: "—",
                        style = XstzType.numericL,
                        color = palette.textPrimary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formats.percent(displayRate),
                        style = XstzType.numericM,
                        color = palette.change(displayRate),
                    )
                    Text(
                        if (scrubbed != null) "较区间首日" else "${state.range.label}区间",
                        style = XstzType.caption2,
                        color = palette.textTertiary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (state.hasData) {
                NavChart(
                    points = state.points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    lineColor = palette.change(state.changeRate),
                    gridColor = palette.divider,
                    scrubIndex = scrub,
                    onScrubChange = onScrubChange,
                )

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDate(state.points.first().epochDay), style = XstzType.caption2, color = palette.textTertiary)
                    Text(
                        formatDate(state.points[state.points.size / 2].epochDay),
                        style = XstzType.caption2,
                        color = palette.textTertiary,
                    )
                    Text(formatDate(state.points.last().epochDay), style = XstzType.caption2, color = palette.textTertiary)
                }

                Spacer(Modifier.height(10.dp))
                HairlineDivider()
                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "最低 ${state.lowest?.let { Formats.nav(it.nav.toDouble()) } ?: "—"}",
                        style = XstzType.caption2,
                        color = palette.textTertiary,
                    )
                    Text(
                        "最高 ${state.highest?.let { Formats.nav(it.nav.toDouble()) } ?: "—"}",
                        style = XstzType.caption2,
                        color = palette.textTertiary,
                    )
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(190.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "净值数据不足，至少需要 2 个交易日",
                        style = XstzType.caption,
                        color = palette.textTertiary,
                    )
                }
            }
        }
    }

    if (state.hasData) {
        SectionHeader(title = "区间统计")
        SolidCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            contentPadding = 16.dp,
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCell("最大回撤", Formats.percent(state.maxDrawdown), palette.down, Modifier.weight(1f))
                    StatCell("年化波动", Formats.percent(state.annualVolatility), palette.textPrimary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCell("区间振幅", Formats.percent(state.amplitude), palette.textPrimary, Modifier.weight(1f))
                    StatCell("交易天数", "${state.tradingDays} 天", palette.textSecondary, Modifier.weight(1f))
                }
            }
        }

        SectionHeader(
            title = "净值明细",
            subtitle = "最近 ${minOf(DETAIL_ROWS, state.points.size)} 个交易日",
        )
        SolidCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            contentPadding = 0.dp,
        ) {
            NavDetailList(points = state.points)
        }
    }
}

// ==================================================================
// 公共小组件
// ==================================================================

@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = XstzTheme.palette
    Column(modifier) {
        Text(label, style = XstzType.caption2, color = palette.textTertiary)
        Spacer(Modifier.height(3.dp))
        Text(value, style = XstzType.numericS, color = color)
    }
}

/** 组合快照明细表：倒序（最新在前），日期 / 总资产 / 当日盈亏 / 累计收益率 */
@Composable
private fun SnapshotDetailList(snaps: List<NetValueSnapshot>) {
    val palette = XstzTheme.palette
    val rows = snaps.takeLast(DETAIL_ROWS).reversed()
    Column {
        rows.forEachIndexed { index, s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatDate(s.epochDay),
                    style = XstzType.numericXS,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formats.currency(s.totalValue.toYuan()),
                    style = XstzType.numericXS,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    Formats.signedCurrency(s.dayProfit.toYuan()),
                    style = XstzType.numericXS,
                    color = palette.change(s.dayProfit.micros.toDouble()),
                    modifier = Modifier.weight(1.1f),
                )
                Text(
                    Formats.percent(s.profitRate),
                    style = XstzType.numericXS,
                    color = palette.change(s.profitRate),
                    modifier = Modifier.weight(0.9f),
                )
            }
            if (index != rows.lastIndex) {
                HairlineDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/** 标的净值明细表：倒序，日期 / 净值 / 日涨跌 */
@Composable
private fun NavDetailList(points: List<NavPoint>) {
    val palette = XstzTheme.palette
    val rows = points.takeLast(DETAIL_ROWS).reversed()
    Column {
        rows.forEachIndexed { index, p ->
            val originalIndex = points.lastIndex - index
            val prev = points.getOrNull(originalIndex - 1)
            val dayRate = if (prev == null || prev.nav.isZero) null
            else (p.nav.micros - prev.nav.micros).toDouble() / prev.nav.micros

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatDate(p.epochDay),
                    style = XstzType.numericXS,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formats.nav(p.nav.toDouble()),
                    style = XstzType.numericXS,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    dayRate?.let { Formats.percent(it) } ?: "—",
                    style = XstzType.numericXS,
                    color = if (dayRate != null) palette.change(dayRate) else palette.textTertiary,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index != rows.lastIndex) {
                HairlineDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/** 明细表最多显示多少行 */
private const val DETAIL_ROWS = 15

private fun formatDate(epochDay: Long): String = Formats.shortDate(epochDay)

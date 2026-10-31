package com.huaying.xstz.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.ui.ChangeText
import com.huaying.xstz.core.ui.EmptyState
import com.huaying.xstz.core.ui.HairlineDivider
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.SectionHeader
import com.huaying.xstz.core.ui.SolidCard
import com.huaying.xstz.core.ui.Sparkline

/**
 * 持仓页。
 *
 * 与概览页的分工：概览看"总量与结构"，这里看"每一只的细节并能操作"。
 *
 * 三层信息设计（截图验收后补的）：
 *   1. 顶部汇总卡给出市值/当日/累计/成本四个总量，带分隔线分组
 *   2. 明细区可按资产类别分组，组头带小计与占比——只看单只基金
 *      无法回答"我在股票上到底占多少"，分组能直接回答
 *   3. 每只基金卡片内嵌 30 天迷你走势线：+13% 可能是稳步上行，
 *      也可能是暴跌后反弹，光看百分比无法区分
 */
@Composable
fun HoldingsScreen(
    state: HoldingsUiState,
    onOpenAdd: () -> Unit,
    onOpenDetail: (Holding) -> Unit,
    onSortChange: (HoldingSort) -> Unit,
    onToggleGrouped: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitleHeader(
                title = "持仓",
                subtitle = "共 ${state.holdings.size} 只 · 点击可录入交易",
                trailing = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "添加基金",
                        tint = palette.accent,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(onClick = onOpenAdd),
                    )
                },
            )
        }

        // ---------- 汇总 ----------
        item {
            SummaryCard(state = state)
        }

        if (!state.isEmpty) {
            // ---------- 明细标题 + 分组开关 ----------
            item {
                SectionHeader(
                    title = "明细",
                    action = {
                        GroupToggle(checked = state.grouped, onClick = onToggleGrouped)
                    },
                )
            }
            // ---------- 排序 ----------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoldingSort.entries.forEach { s ->
                        SortChip(
                            label = s.label,
                            selected = s == state.sort,
                            onClick = { onSortChange(s) },
                        )
                    }
                }
            }
        }

        if (state.isEmpty) {
            item {
                EmptyState(
                    title = "还没有持仓",
                    hint = "点右上角 + 添加基金，再录入买入交易",
                )
            }
        } else if (state.grouped) {
            state.groups.forEach { group ->
                item(key = "group-${group.type}") {
                    GroupHeader(group = group)
                }
                items(group.holdings, key = { it.fundId }) { h ->
                    HoldingDetailCard(
                        holding = h,
                        ratio = state.ratios[h.fundId] ?: 0.0,
                        spark = state.sparklines[h.fundId],
                        onClick = { onOpenDetail(h) },
                    )
                }
            }
        } else {
            items(state.holdings, key = { it.fundId }) { h ->
                HoldingDetailCard(
                    holding = h,
                    ratio = state.ratios[h.fundId] ?: 0.0,
                    spark = state.sparklines[h.fundId],
                    onClick = { onOpenDetail(h) },
                )
            }
        }

        if (state.message != null) {
            item {
                Text(
                    text = state.message,
                    style = XstzType.caption,
                    color = palette.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismissMessage)
                        .padding(top = 4.dp),
                )
            }
        }
    }


}

/**
 * 汇总卡。
 *
 * 主数字（市值）给最大字号独占一行，其余三个量并排在下半部。
 * 这样"我总共有多少"一眼可见，细节留到第二眼——旧版把所有数字
 * 用同样字号堆在一起，等于没有重点。
 *
 * 容器用 SolidCard 而非 GlassPanel：它是内容层（会随列表滚动），
 * 依 VISUAL_ACCEPTANCE §4 红线 R3 不得使用玻璃。
 */
@Composable
private fun SummaryCard(state: HoldingsUiState) {
    val palette = XstzTheme.palette
    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp,
    ) {
        Column {
            Text("持仓市值", style = XstzType.caption, color = palette.textTertiary)
            Spacer(Modifier.height(2.dp))
            Text(
                Formats.currency(state.summary.totalMarketValue.toYuan()),
                style = XstzType.numericL,
                color = palette.textPrimary,
            )

            Spacer(Modifier.height(14.dp))
            HairlineDivider()
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCell(
                    label = "当日",
                    value = Formats.signedCurrency(state.summary.dayProfit.toYuan()),
                    rate = state.summary.dayProfitRate,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "累计",
                    value = Formats.signedCurrency(state.summary.totalProfit.toYuan()),
                    rate = state.summary.totalProfitRate,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "成本",
                    value = Formats.currency(state.summary.totalCost.toYuan()),
                    rate = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    rate: Double?,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    Column(modifier) {
        Text(label, style = XstzType.caption2, color = palette.textTertiary)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = XstzType.numericXS,
            color = if (rate != null) palette.change(rate) else palette.textSecondary,
        )
        if (rate != null) {
            Text(
                Formats.percent(rate),
                style = XstzType.caption2,
                color = palette.change(rate).copy(alpha = 0.85f),
            )
        }
    }
}

/** 分组开关：图标 + 文字，选中态用强调色底 */
@Composable
private fun GroupToggle(checked: Boolean, onClick: () -> Unit) {
    val palette = XstzTheme.palette
    Row(
        modifier = Modifier
            .background(
                if (checked) palette.accent.copy(alpha = 0.16f) else palette.glassFill,
                SquircleShape(10.dp, 2.6f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Checklist,
            contentDescription = null,
            tint = if (checked) palette.accent else palette.textTertiary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            "分组",
            style = XstzType.caption2,
            color = if (checked) palette.accent else palette.textSecondary,
        )
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = XstzTheme.palette
    Box(
        Modifier
            .background(
                if (selected) palette.accent.copy(alpha = 0.16f) else palette.glassFill,
                SquircleShape(9.dp, 2.5f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = XstzType.caption2,
            color = if (selected) palette.accent else palette.textTertiary,
        )
    }
}

/**
 * 分组头。
 *
 * 一行承载四件事：类别名、只数、组内市值、占组合比例。
 * 下面一行是组内当日与累计盈亏——这两项是分组真正的价值所在：
 * "股票今天赚了多少"比"某只基金今天赚了多少"更接近决策。
 */
@Composable
private fun GroupHeader(group: HoldingGroup) {
    val palette = XstzTheme.palette
    val accent = palette.asset(group.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 15.dp)
                .background(accent, SquircleShape(2.dp, 2.4f)),
        )
        Spacer(Modifier.size(7.dp))
        // 用 groupName（"股票型"）而不是 displayName（"股票"）：
        // 组里可能有"基金"徽章的股票型基金，两个词不加区分就会读成矛盾
        Text(group.type.groupName, style = XstzType.callout, color = palette.textPrimary)
        Spacer(Modifier.size(6.dp))
        Text(
            "${group.holdings.size} 只 · ${Formats.ratio(group.ratio)}",
            style = XstzType.caption2,
            color = palette.textTertiary,
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Formats.currencyValue(group.marketValue.toYuan()),
                style = XstzType.numericXS,
                color = palette.textSecondary,
            )
            Text(
                "当日 ${if (group.dayProfit.isNegative) "" else "+"}${Formats.currencyValue(group.dayProfit.toYuan())}",
                style = XstzType.caption2,
                color = palette.change(group.dayProfit.micros.toDouble()),
            )
        }
    }
}

@Composable
private fun HoldingDetailCard(
    holding: Holding,
    ratio: Double,
    spark: List<Double>?,
    onClick: () -> Unit,
) {
    val palette = XstzTheme.palette
    val accent = palette.asset(holding.type)
    // 列表行属内容层，不能用玻璃（VISUAL_ACCEPTANCE §4 红线 R3）
    SolidCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        cornerRadius = 20.dp,
        contentPadding = 14.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 类型胶囊而不是单个字：这个应用同时管 A股/ETF/债券/基金，
                // "股"这一个字无法区分 600519 和 510300（都是 STOCK）
                Box(
                    Modifier
                        .background(accent.copy(alpha = 0.16f), SquircleShape(9.dp, 2.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(holding.kind.displayName, style = XstzType.caption2, color = accent)
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(holding.name, style = XstzType.callout, color = palette.textPrimary)
                    Text(
                        "${holding.code} · 占比 ${Formats.ratio(ratio)}",
                        style = XstzType.caption,
                        color = palette.textTertiary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formats.currency(holding.marketValue.toYuan()),
                        style = XstzType.numericS,
                        color = palette.textPrimary,
                    )
                    ChangeText(value = holding.profitRate, style = XstzType.numericXS)
                }
            }

            // 迷你走势线：数据不足时不占位，避免出现一条空白横槽
            if (spark != null && spark.size >= 2) {
                Spacer(Modifier.height(10.dp))
                Sparkline(
                    values = spark,
                    lineColor = palette.change(holding.profitRate),
                    height = 30.dp,
                )
            }

            Spacer(Modifier.height(10.dp))

            // 四个关键数字：份额 / 成本 / 成本单价 / 当日涨跌
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniMetric("份额", Formats.quantity(holding.shares.toDouble()))
                MiniMetric("成本", Formats.currencyValue(holding.cost.toYuan()))
                MiniMetric("成本单价", Formats.nav(holding.costPrice.toYuan()))
                MiniMetric(
                    "当日",
                    Formats.percent(holding.dayChangeRate),
                    color = palette.change(holding.dayChangeRate),
                )
            }

            if (holding.shares.isZero) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "尚未录入买入交易，点卡片录入",
                    style = XstzType.caption,
                    color = palette.accent,
                )
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    val palette = XstzTheme.palette
    Column {
        Text(label, style = XstzType.caption2, color = palette.textTertiary)
        // 标签与数值之间留 2dp：实测贴在一起时"份额"和"12,500.00"会黏成一行
        Spacer(Modifier.height(2.dp))
        Text(value, style = XstzType.numericXS, color = color ?: palette.textSecondary)
    }
}

package com.huaying.xstz.feature.rebalance

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.ActionType
import com.huaying.xstz.core.model.AdjustmentItem
import com.huaying.xstz.core.model.RebalanceStrategy
import com.huaying.xstz.core.ui.SolidCard
import com.huaying.xstz.core.ui.EmptyState
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.SectionHeader
import kotlin.math.roundToInt

/**
 * 再平衡页。
 *
 * 只做一件事：把 [com.huaying.xstz.core.domain.RebalanceEngine] 的结果如实呈现。
 * 页面本身不参与任何计算——这是刻意的，算法要能被单测覆盖，UI 只负责展示，
 * 避免出现"界面上算一遍、测试里算一遍、两边不一致"的经典问题。
 */
@Composable
fun RebalanceScreen(
    state: RebalanceUiState,
    onStrategyChange: (RebalanceStrategy) -> Unit,
    onThresholdChange: (Double) -> Unit,
    onOpenTargetAllocation: () -> Unit,
    onOpenTradeHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitleHeader(
                title = "再平衡",
                subtitle = if (state.hasAction) "有偏离需要处理" else "当前结构与目标一致",
            )
        }

        // ---------- 入口 ----------
        // 两个二级页的入口放在标题下第一屏可见处：
        // 「目标仓位」是再平衡的输入，「交易历史」是它的执行结果，
        // 二者与再平衡是同一件事的三个环节，不该藏进设置
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryButton(
                    label = "设置目标仓位",
                    onClick = onOpenTargetAllocation,
                    modifier = Modifier.weight(1f),
                )
                EntryButton(
                    label = "交易历史",
                    onClick = onOpenTradeHistory,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.isEmpty) {
            item {
                EmptyState(
                    title = "没有可调仓的持仓",
                    hint = "先在持仓页添加基金并录入交易",
                )
            }
            return@LazyColumn
        }

        // ---------- 策略 ----------
        item {
            SectionHeader(title = "调仓策略", subtitle = state.strategy.description)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RebalanceStrategy.entries.forEach { s ->
                    val selected = s == state.strategy
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                if (selected) palette.accent.copy(alpha = 0.18f) else palette.glassFill,
                                SquircleShape(12.dp, 2.6f),
                            )
                            .clickable { onStrategyChange(s) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.displayName,
                            style = XstzType.caption,
                            color = if (selected) palette.accent else palette.textSecondary,
                        )
                    }
                }
            }
        }

        // ---------- 阈值 ----------
        item {
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("偏离阈值", style = XstzType.caption, color = palette.textTertiary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${(state.thresholdPct * 100).roundToInt()}%",
                            style = XstzType.numericS,
                            color = palette.accent,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // 用一组离散按钮代替 Slider：档位比连续滑动更容易点准，
                    // 且阈值本来就没有"22.7%" 这种精度需求
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.01, 0.03, 0.05, 0.10, 0.20).forEach { v ->
                            val selected = kotlin.math.abs(state.thresholdPct - v) < 1e-6
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) palette.accent.copy(alpha = 0.20f) else palette.divider,
                                        SquircleShape(9.dp, 2.4f),
                                    )
                                    .clickable { onThresholdChange(v) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${(v * 100).roundToInt()}%",
                                    style = XstzType.caption2,
                                    color = if (selected) palette.accent else palette.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 汇总 ----------
        item {
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = 16.dp,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryCell("需买入", state.plan?.totalBuy?.let { Formats.currency(it.toYuan()) } ?: "—", palette.up, Modifier.weight(1f))
                    SummaryCell("需卖出", state.plan?.totalSell?.let { Formats.currency(it.toYuan()) } ?: "—", palette.down, Modifier.weight(1f))
                    SummaryCell(
                        "执行后现金",
                        state.plan?.cashDelta?.let { Formats.signedCurrency(it.toYuan()) } ?: "—",
                        palette.textPrimary,
                        Modifier.weight(1f),
                    )
                }
            }
        }

        // ---------- 明细 ----------
        item { SectionHeader(title = "调仓明细", subtitle = "按偏离程度排序") }

        val items = state.plan?.items
            ?.filter { it.action != ActionType.HOLD }
            ?.sortedByDescending { kotlin.math.abs(it.delta.micros) }
            .orEmpty()

        if (items.isEmpty()) {
            item {
                Text(
                    "所有持仓都在阈值内，暂时不需要调仓",
                    style = XstzType.footnote,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            items(items, key = { it.fundId }) { item ->
                AdjustmentCard(item)
            }
        }
    }
}

@Composable
private fun EntryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    Box(
        modifier
            .background(palette.backgroundElevated, SquircleShape(12.dp, 2.6f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = XstzType.caption, color = palette.accent)
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {    val palette = XstzTheme.palette
    Column(modifier) {
        Text(label, style = XstzType.caption2, color = palette.textTertiary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = XstzType.numericS, color = color)
    }
}

@Composable
private fun AdjustmentCard(item: AdjustmentItem) {
    val palette = XstzTheme.palette
    val isBuy = item.action == ActionType.BUY
    val accent = if (isBuy) palette.up else palette.down

    SolidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        contentPadding = 13.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(accent.copy(alpha = 0.16f), SquircleShape(8.dp, 2.4f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    if (isBuy) "买入" else "卖出",
                    style = XstzType.caption2,
                    color = accent,
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = XstzType.callout, color = palette.textPrimary)
                Text(
                    "当前 ${Formats.ratio(item.currentRatio)} → 目标 ${Formats.ratio(item.targetRatio)}",
                    style = XstzType.caption2,
                    color = palette.textTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // 卖出的金额必须带负号：之前用 abs() 后统一加 "+"，
                    // 界面上出现绿色 "+¥16,127.26"，看起来像收入而不是减仓
                    Formats.signedCurrency(
                        if (isBuy) item.delta.abs().toYuan() else -item.delta.abs().toYuan()
                    ),
                    style = XstzType.numericS,
                    color = accent,
                )
                Text(
                    if (item.deviation == Double.MAX_VALUE) "目标为 0"
                    else "偏离 ${Formats.percent(item.deviation)}",
                    style = XstzType.caption2,
                    color = palette.textTertiary,
                )
            }
        }
    }
}

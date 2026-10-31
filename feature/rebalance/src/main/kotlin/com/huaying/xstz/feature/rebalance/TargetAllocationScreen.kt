package com.huaying.xstz.feature.rebalance

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Spacing
import com.huaying.xstz.core.ui.ButtonVariant
import com.huaying.xstz.core.ui.GlassButton
import com.huaying.xstz.core.ui.SolidCard

/**
 * 目标仓位设置页。
 *
 * 这一页解决一个具体痛点：配平多个标的的目标占比到正好 100%。
 * 逐个弹输入框改是行不通的——改 A 就必须改 B，用户在弹窗里
 * 看不到合计变化，只能盲调。
 *
 * 所以整页围绕「合计」设计：
 *   * 顶部常驻合计条，实时显示 100.0% / 98.2% 与差值
 *   * 每个标的一条滑块，手指拖动即时看到合计变动
 *   * 一键"按现有比例配平"，把零头按比例摊到各标的
 */
@Composable
fun TargetAllocationScreen(
    onBack: () -> Unit,
    viewModel: TargetAllocationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasChanges by viewModel.hasChanges.collectAsStateWithLifecycle()
    val palette = XstzTheme.palette

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(
                text = "返回",
                onClick = onBack,
                variant = ButtonVariant.Text,
            )
            Spacer(Modifier.weight(1f))
            GlassButton(
                text = if (state.saving) "保存中…" else "保存",
                onClick = { viewModel.save() },
                variant = ButtonVariant.Primary,
                enabled = hasChanges && !state.saving,
            )
        }

        // 合计条：这一页的核心信息
        AllocationSummary(state)

        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "还没有持仓，先添加标的再设置目标仓位",
                    style = XstzType.body,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        // 快捷操作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            GlassButton(
                text = "按现有比例配平",
                onClick = viewModel::normalizeToFull,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = "全部清零",
                onClick = viewModel::clearAll,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.screenEdge,
                end = Spacing.screenEdge,
                top = Spacing.xs,
                bottom = Spacing.tabBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(state.holdings, key = { it.fundId }) { h ->
                TargetRow(
                    name = h.name,
                    code = h.code,
                    ratio = h.targetRatio,
                    onRatioChange = { viewModel.setRatio(h.fundId, it) },
                )
            }
        }
    }
}

@Composable
private fun AllocationSummary(state: TargetAllocationUiState) {
    val palette = XstzTheme.palette
    // 颜色语义：正好 100% 用中性色，超配红、不足橙。
    // 这里刻意不用"下跌绿"表示不足——配比不足不是亏损，用下跌色会误导。
    val statusColor = when {
        state.isBalanced -> palette.textPrimary
        state.deltaRatio > 0 -> palette.up
        else -> palette.accent
    }

    SolidCard(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "目标占比合计",
                    style = XstzType.footnote,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatPct(state.sumRatio),
                    style = XstzType.numericL,
                    color = statusColor,
                )
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = when {
                    state.isBalanced -> "已配平"
                    state.deltaRatio > 0 ->
                        "超出 ${formatPct(state.deltaRatio)}，再平衡结果会偏大"
                    else ->
                        "还差 ${formatPct(-state.deltaRatio)} 未分配"
                },
                style = XstzType.caption,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun TargetRow(
    name: String,
    code: String,
    ratio: Double,
    onRatioChange: (Double) -> Unit,
) {
    val palette = XstzTheme.palette
    SolidCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = XstzType.subhead, color = palette.textPrimary)
                    Text(text = code, style = XstzType.caption2, color = palette.textTertiary)
                }
                Text(
                    text = formatPct(ratio),
                    style = XstzType.numericS,
                    color = palette.textPrimary,
                )
            }
            Slider(
                value = ratio.toFloat(),
                onValueChange = { onRatioChange(it.toDouble()) },
                valueRange = 0f..1f,
                // 40 段 = 2.5% 一档。太细手指拖不准，太粗配不平
                steps = 39,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.divider,
                ),
                modifier = Modifier.height(32.dp),
            )
        }
    }
}

/** 统一的百分比格式：保留一位小数，避免 0.30000000000000004 这类浮点噪声上屏 */
private fun formatPct(v: Double): String {
    val pct = v * 100.0
    val rounded = kotlin.math.round(pct * 10) / 10
    return "${if (rounded == rounded.toLong().toDouble()) rounded.toLong() else rounded}%"
}

package com.huaying.xstz.feature.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.glass.GlassLevel
import com.huaying.xstz.core.designsystem.glass.GlassPanel
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.ui.ChangeText
import com.huaying.xstz.core.ui.EmptyState
import com.huaying.xstz.core.ui.GlassPrimaryButton
import com.huaying.xstz.core.ui.HairlineDivider
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.SectionHeader
import com.huaying.xstz.core.ui.SourceBadge
import dev.chrisbanes.haze.HazeState

/**
 * 资产概览。
 *
 * 信息层级刻意做成三层，与 iOS 的金融类首屏一致：
 *   1. 总资产（最大字号，唯一焦点）
 *   2. 当日盈亏 + 累计收益（次一级，并排）
 *   3. 配置结构（环形图）+ 持仓列表（可下钻）
 *
 * 隐私模式只遮挡数字不遮挡结构，这样截图分享时既能给别人看配置、又不泄露金额。
 */
@Composable
fun OverviewScreen(
    state: OverviewUiState,
    hazeState: HazeState?,
    onRefresh: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onOpenHolding: (Long) -> Unit,
    onSeedSamples: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitleHeader(
                title = "概览",
                subtitle = if (state.lastSync > 0) "更新于 ${Formats.freshness(state.lastSync)}" else "尚未同步",
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SourceBadge(state.source)
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新行情",
                            tint = palette.textSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable(enabled = !state.refreshing, onClick = onRefresh),
                        )
                    }
                },
            )
        }

        // ---------- 总资产 ----------
        item {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                state = hazeState,
                cornerRadius = 28.dp,
                level = GlassLevel.Thick,
                contentPadding = 20.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("总资产", style = XstzType.footnote, color = palette.textSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (state.privacy) "显示金额" else "隐藏金额",
                            style = XstzType.caption,
                            color = palette.accent,
                            modifier = Modifier.clickable(onClick = onTogglePrivacy),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.privacy) "¥ ****" else Formats.currency(state.summary.totalMarketValue.toYuan()),
                        style = XstzType.numericXL,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        MetricColumn(
                            label = "当日盈亏",
                            value = if (state.privacy) "****"
                            else Formats.signedCurrency(state.summary.dayProfit.toYuan()),
                            valueColor = palette.change(state.summary.dayProfit.micros.toDouble()),
                            sub = if (state.privacy) "" else Formats.percent(state.summary.dayProfitRate),
                            subColor = palette.change(state.summary.dayProfitRate),
                        )
                        MetricColumn(
                            label = "累计收益",
                            value = if (state.privacy) "****"
                            else Formats.signedCurrency(state.summary.totalProfit.toYuan()),
                            valueColor = palette.change(state.summary.totalProfit.micros.toDouble()),
                            sub = if (state.privacy) "" else Formats.percent(state.summary.totalProfitRate),
                            subColor = palette.change(state.summary.totalProfitRate),
                        )
                    }
                }
            }
        }

        // ---------- 配置结构 ----------
        item {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                state = hazeState,
                cornerRadius = 24.dp,
                level = GlassLevel.Regular,
                contentPadding = 16.dp,
            ) {
                Column {
                    SectionHeader(
                        title = "资产配置",
                        subtitle = if (state.needsRebalance) "有持仓偏离目标，建议再平衡" else "结构与目标一致",
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AllocationRing(
                            ratios = state.typeRatios,
                            modifier = Modifier.size(120.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            AssetType.entries.forEach { type ->
                                val ratio = state.typeRatios[type] ?: 0.0
                                if (ratio <= 0.0001) return@forEach
                                LegendRow(
                                    color = palette.asset(type),
                                    label = type.displayName,
                                    value = Formats.ratio(ratio),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 持仓 ----------
        item {
            SectionHeader(
                title = "持仓",
                subtitle = if (state.holdings.isEmpty()) null else "共 ${state.holdings.size} 只",
            )
        }

        if (state.holdings.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有持仓",
                    hint = "添加第一只基金后，这里会显示实时市值与盈亏",
                    action = {
                        GlassPrimaryButton(
                            text = "载入示例数据",
                            onClick = onSeedSamples,
                            modifier = Modifier.fillMaxWidth(0.6f),
                        )
                    },
                )
            }
        } else {
            items(state.holdings, key = { it.fundId }) { holding ->
                HoldingRow(
                    holding = holding,
                    ratio = state.ratios[holding.fundId] ?: 0.0,
                    privacy = state.privacy,
                    hazeState = hazeState,
                    onClick = { onOpenHolding(holding.fundId) },
                )
            }
        }

        if (state.message != null) {
            item {
                Text(
                    text = state.message,
                    style = XstzType.caption,
                    color = if (state.source == com.huaying.xstz.core.model.DataSource.SAMPLE) {
                        androidx.compose.ui.graphics.Color(0xFFD9902B)
                    } else palette.textTertiary,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    sub: String,
    subColor: Color,
) {
    val palette = XstzTheme.palette
    Column {
        Text(label, style = XstzType.caption, color = palette.textTertiary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = XstzType.numericM, color = valueColor)
        if (sub.isNotEmpty()) {
            Text(sub, style = XstzType.numericXS, color = subColor)
        }
    }
}

/**
 * 环形配置图，全自绘。
 *
 * 为什么不用现成图表库：这个环要跟超椭圆卡片、玻璃材质、涨跌色系统一致，
 * 引入第三方库后半径、描边、留白全部对不齐，反而更难调。
 * 自绘 40 行，样式完全可控。
 */
@Composable
fun AllocationRing(
    ratios: Map<AssetType, Double>,
    modifier: Modifier = Modifier,
    strokeRatio: Float = 0.16f,
) {
    val palette = XstzTheme.palette
    val motion = XstzTheme.motion
    val total = ratios.values.sum().coerceAtLeast(0.0001)

    val progress by animateFloatAsState(
        targetValue = if (motion.animationsEnabled) 1f else 1f,
        animationSpec = tween(900),
        label = "ring",
    )

    Canvas(modifier) {
        val stroke = size.minDimension * strokeRatio
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        val hasData = ratios.values.any { it > 0.0005 }
        if (!hasData) {
            // 空态：只画一圈极淡的细线占位。
            // 之前用 divider.copy(alpha=0.5f) —— copy 会把原本 10% 的黑提升到 50%，
            // 截图上就成了一圈突兀的深灰粗环。这里直接用 divider 本身，并收细到 0.22。
            drawArc(
                color = palette.divider,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke * 0.22f, cap = StrokeCap.Butt),
            )
            return@Canvas
        }

        // 底环：即使没有数据也能看出"这里是一个环"
        drawArc(
            color = palette.divider,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )

        var start = -90f
        AssetType.entries.forEach { type ->
            val ratio = (ratios[type] ?: 0.0) / total
            if (ratio <= 0.0005) return@forEach
            val sweep = (ratio * 360f * progress).toFloat()
            drawArc(
                color = palette.asset(type),
                startAngle = start,
                // 留 1.5° 缝隙：色块之间有一道细缝，比直接相接更容易分辨边界
                sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    val palette = XstzTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, SquircleShape(4.dp, 2.0f))
        )
        Spacer(Modifier.size(8.dp))
        Text(label, style = XstzType.footnote, color = palette.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = XstzType.numericXS, color = palette.textPrimary)
    }
}

@Composable
private fun HoldingRow(
    holding: Holding,
    ratio: Double,
    privacy: Boolean,
    hazeState: HazeState?,
    onClick: () -> Unit,
) {
    val palette = XstzTheme.palette
    GlassPanel(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        state = hazeState,
        cornerRadius = 20.dp,
        level = GlassLevel.Thin,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(palette.asset(holding.type).copy(alpha = 0.16f), SquircleShape(12.dp, 2.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    holding.name.take(1),
                    style = XstzType.footnote,
                    color = palette.asset(holding.type),
                )
            }
            Spacer(Modifier.size(12.dp))
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
                    if (privacy) "****" else Formats.currency(holding.marketValue.toYuan()),
                    style = XstzType.numericS,
                    color = palette.textPrimary,
                )
                ChangeText(
                    value = holding.profitRate,
                    style = XstzType.numericXS,
                    privacy = privacy,
                )
            }
        }
    }
}

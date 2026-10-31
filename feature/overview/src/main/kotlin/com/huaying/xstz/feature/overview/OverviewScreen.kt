package com.huaying.xstz.feature.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Motion
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.ui.SolidCard
import com.huaying.xstz.core.ui.ChangeText
import com.huaying.xstz.core.ui.EmptyState
import com.huaying.xstz.core.ui.GlassPrimaryButton
import com.huaying.xstz.core.ui.HairlineDivider
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.SectionHeader
import com.huaying.xstz.core.ui.SourceBadge

/** 相对时间文案的重算节拍。freshness 最小粒度为分钟，15 秒足够把误差压在半分钟内 */
private const val TICK_MS = 15_000L

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: OverviewUiState,
    onRefresh: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onOpenHolding: (Long) -> Unit,
    onSeedSamples: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette

    /*
     * 相对时间必须自己走。
     *
     * Compose 只在状态变化时重组，而 lastSync 在刷新之外不会变——于是
     * "更新于 X"会被冻结在首次求值那一刻。实测：静置 132 秒文案纹丝不动，
     * 强制重组后立刻变成"1 分钟前"，证明数据其实很旧，只是文案没老化。
     *
     * 对用户而言这等同虚报新鲜度：盯着看十分钟，界面仍宣称行情是"刚刚"拉的。
     *
     * 两个踩过的坑都写在这里，别再踩：
     * 1. 不能用 Stateless +「等别的 state 变了顺带更新」——必须主动打拍。
     * 2. nowMs 必须在**屏级 scope** 读取并就地算出字符串，再传给 LazyColumn
     *    的 item。若把 nowMs 直接读进 item lambda，更新只 invalidate 那个
     *    item 的局部 scope，LazyColumn 不会重新组合它，实测照样冻结在旧值。
     * produceState 随 composable 离开自动取消，页面不可见时不空转。
     */
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val freshnessText = remember(state.lastSync, nowMs) { Formats.freshness(state.lastSync, nowMs) }

    /*
     * 下拉刷新：方案 §5.1 P0 第 2 项把「手动下拉刷新」与自动刷新并列要求，
     * 此前整仓没有任何 pullRefresh 实现——自动刷新再好，用户想"现在立刻拉一次"
     * 时没有手势入口，这是功能缺失而不只是没测。
     *
     * isRefreshing 直接接 ViewModel 的 refreshing 锁：下拉触发的 onRefresh
     * 与自动刷新共用同一把互斥锁，不会叠加打接口。
     */
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            /*
             * bottom 必须留够悬浮 Tab 栏的高度。
             *
             * 实测：120.dp 时第 4 个持仓被 Tab 栏压住，滚到底也看不全（截图为证）。
             * Tab 栏自身约 62dp + 底部外边距 20dp + 手势导航区 ~48dp ≈ 130dp，
             * 再留 40dp 呼吸空间 → 170dp。
             */
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            LargeTitleHeader(
                title = "概览",
                // 三态而非两态：无持仓时说"暂无持仓"，
                // 否则会显示"更新于 刚刚"，暗示刚刚刷新到了（并不存在的）数据
                subtitle = when {
                    state.holdings.isEmpty() -> "暂无持仓"
                    state.lastSync > 0 -> "更新于 $freshnessText"
                    else -> "尚未同步"
                },
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
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 28.dp,
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
                    // 本金必须与盈亏并列：只有市值与收益时，用户无法回答"我到底投了多少"，
                    // 也容易把累计收益误读成本金（方案 §5.1 P0 第 1 项明确列举"本金"）。
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        MetricColumn(
                            label = "本金",
                            value = if (state.privacy) Formats.PRIVACY_MASK
                            else Formats.currency(state.summary.totalCost.toYuan()),
                            valueColor = palette.textPrimary,
                            sub = "",
                            subColor = palette.textTertiary,
                        )
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
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                contentPadding = 16.dp,
            ) {
                Column {
                    SectionHeader(
                        title = "资产配置",
                        // 用"可查看差额"而非"建议再平衡"：验收清单 §11 合规项要求
                        // 文案不得出现投资建议类表述。这里只提示存在偏离，
                        // 不替用户做任何买卖决定。
                        subtitle = if (state.needsRebalance) "有持仓偏离目标，可查看再平衡差额" else "结构与目标一致",
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
        // 入环动画从 0 扫到 1；关闭动效时跳过动画直接到终值。
        // （旧写法 1f else 1f 是空操作，animationsEnabled 实际没起作用）
        targetValue = 1f,
        animationSpec = if (motion.animationsEnabled) {
            tween(Motion.TAB_MS, easing = Motion.iosEaseOut)
        } else {
            snap()
        },
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
    onClick: () -> Unit,
) {
    val palette = XstzTheme.palette
    SolidCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        cornerRadius = 20.dp,
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
                // 偏离度与状态：方案 §5.1 要求持仓列表给出「市值·占比·偏离度·状态」。
                // 之前只有一句汇总式提示"有持仓偏离目标"，用户看不出是**哪一只**偏了。
                // 偏离用百分点（pp）而非百分比：占比之差是两个百分数相减，写成 % 会差一个数量级。
                val target = holding.targetRatio
                val devPp = (ratio - target) * 100.0
                if (target > 0.0001) {
                    val status = when {
                        devPp >= 1.0 -> "超配"
                        devPp <= -1.0 -> "低配"
                        else -> "正常"
                    }
                    Text(
                        "目标 ${Formats.ratio(target)} · 偏离 ${Formats.percentagePoints(devPp)} · $status",
                        style = XstzType.caption2,
                        color = if (status == "正常") palette.textTertiary else palette.accent,
                    )
                } else {
                    Text("未设目标", style = XstzType.caption2, color = palette.textTertiary)
                }
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

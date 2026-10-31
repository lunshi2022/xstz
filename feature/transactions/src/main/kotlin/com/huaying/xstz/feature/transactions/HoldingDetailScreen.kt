package com.huaying.xstz.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType
import com.huaying.xstz.core.ui.SolidCard
import com.huaying.xstz.core.ui.HairlineDivider
import com.huaying.xstz.core.ui.MeshBackdrop
import com.huaying.xstz.core.ui.NavChart
import com.huaying.xstz.core.ui.SectionHeader
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 持仓详情页。
 *
 * 为什么要有独立页面：列表页每行只有四个数字，回答不了
 * "这只基金我拿了多久、赚了多少、中间最难受时回撤多少、我都在什么价位买卖过"。
 * 这四个问题正是决定"要不要继续拿"的依据。
 *
 * 页面结构自上而下：
 *   1. 市值 + 当日 + 累计（主数字独占一行，避免所有数字同样大）
 *   2. 净值曲线（区间可切，带十字准星）
 *   3. 风险指标（最大回撤 / 年化波动）
 *   4. 持仓明细六宫格
 *   5. 交易流水
 *   6. 底部固定操作栏（买入 / 卖出 / 分红）
 */
@Composable
fun HoldingDetailScreen(
    state: HoldingDetailUiState,
    onBack: () -> Unit,
    onRangeChange: (DetailRange) -> Unit,
    onTrade: (Holding, TradeType, shares: String, nav: String, fee: String) -> Unit,
    onDelete: (Holding) -> Unit,
    onOpenTradeHistory: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    // 详情页是全屏覆盖层，自带光源与折射源。
    // 页面主体已全部改为内容层 SolidCard（VISUAL_ACCEPTANCE §4 红线 R3），
    // 但这里仍保留 hazeSource：输入框、交易弹窗等控件层的半透明底需要采样源，
    // 去掉会让这些控件退化成一块灰。
    val haze = rememberHazeState()
    var tradeType by remember { mutableStateOf<TradeType?>(null) }

    Box(modifier.fillMaxSize()) {
        MeshBackdrop(Modifier.hazeSource(haze))

        val holding = state.holding
        if (holding == null) {
            // 加载中或已被删除
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.loading) "加载中…" else "该持仓已不存在",
                    style = XstzType.callout,
                    color = palette.textTertiary,
                )
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- 顶栏 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(palette.glassFill, SquircleShape(12.dp, 2.8f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(holding.name, style = XstzType.title3, color = palette.textPrimary)
                    Text(
                        "${holding.code} · ${holding.kind.displayName}",
                        style = XstzType.caption,
                        color = palette.textTertiary,
                    )
                }
            }

            // ---------- 主数字 ----------
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                contentPadding = 18.dp,
            ) {
                Column {
                    Text("持仓市值", style = XstzType.caption, color = palette.textTertiary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        Formats.currency(holding.marketValue.toYuan()),
                        style = XstzType.numericL,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(14.dp))
                    HairlineDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailStat(
                            "当日盈亏",
                            Formats.signedCurrency(holding.dayProfit.toYuan()),
                            Formats.percent(holding.dayChangeRate),
                            palette.change(holding.dayChangeRate),
                            Modifier.weight(1f),
                        )
                        DetailStat(
                            "累计盈亏",
                            Formats.signedCurrency(holding.profit.toYuan()),
                            Formats.percent(holding.profitRate),
                            palette.change(holding.profitRate),
                            Modifier.weight(1f),
                        )
                        DetailStat(
                            "累计成本",
                            Formats.currencyValue(holding.cost.toYuan()),
                            null,
                            palette.textSecondary,
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---------- 净值曲线 ----------
            SectionHeader(
                title = "净值走势",
                subtitle = if (state.hasChart) "拖动曲线可查看任一日净值" else "至少需要 2 个交易日",
            )
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 16.dp,
            ) {
                Column {
                    if (state.hasChart) {
                        var scrub by remember(state.holding?.fundId, state.range) { mutableStateOf<Int?>(null) }
                        val point = scrub?.let { state.points.getOrNull(it) }
                        val rate = point?.let { p ->
                            if (state.points.first().nav.isZero) 0.0
                            else (p.nav.micros - state.points.first().nav.micros).toDouble() /
                                state.points.first().nav.micros
                        } ?: state.rangeChange

                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    point?.let { formatDetailDate(it.epochDay) } ?: "最新净值",
                                    style = XstzType.caption,
                                    color = palette.textTertiary,
                                )
                                Text(
                                    point?.let { Formats.nav(it.nav.toDouble()) }
                                        ?: state.points.lastOrNull()?.let { Formats.nav(it.nav.toDouble()) }
                                        ?: "—",
                                    style = XstzType.numericM,
                                    color = palette.textPrimary,
                                )
                            }
                            Text(
                                Formats.percent(rate),
                                style = XstzType.numericS,
                                color = palette.change(rate),
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        NavChart(
                            points = state.points,
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            lineColor = palette.change(state.rangeChange),
                            gridColor = palette.divider,
                            scrubIndex = scrub,
                            onScrubChange = { scrub = it },
                        )
                        Spacer(Modifier.height(10.dp))

                        // 区间切换
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DetailRange.entries.forEach { r ->
                                SegChip(
                                    label = r.label,
                                    selected = r == state.range,
                                    small = true,
                                    onClick = { onRangeChange(r) },
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
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
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailStat(
                                "最大回撤",
                                Formats.percent(state.maxDrawdown),
                                null,
                                palette.down,
                                Modifier.weight(1f),
                            )
                            DetailStat(
                                "年化波动",
                                Formats.percent(state.annualVolatility).removePrefix("+"),
                                null,
                                palette.textSecondary,
                                Modifier.weight(1f),
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "还没有足够的净值历史",
                                style = XstzType.caption,
                                color = palette.textTertiary,
                            )
                        }
                    }
                }
            }

            // ---------- 持仓明细 ----------
            SectionHeader(title = "持仓明细")
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 16.dp,
            ) {
                Column {
                    DetailRow("持有份额", Formats.quantity(holding.shares.toDouble()))
                    DetailRow("累计成本", Formats.currency(holding.cost.toYuan()))
                    DetailRow("成本单价", Formats.nav(holding.costPrice.toYuan()))
                    DetailRow("当前净值", Formats.nav(holding.nav.toDouble()))
                    DetailRow("昨日净值", Formats.nav(holding.prevNav.toDouble()))
                    DetailRow(
                        "当日盈亏",
                        Formats.signedCurrency(holding.dayProfit.toYuan()),
                        valueColor = palette.change(holding.dayProfit.micros.toDouble()),
                        last = true,
                    )
                }
            }

            // ---------- 交易流水 ----------
            SectionHeader(
                title = "交易记录",
                subtitle = if (state.trades.isEmpty()) null else "共 ${state.trades.size} 笔",
                // 详情页只展示本标的最近的流水。要看全部（含跨标的对比与按类型筛选）
                // 需要另一套分组与筛选 UI，用二级页承载比在详情里堆更多控件更清楚
                action = if (state.trades.isEmpty()) null else {
                    {
                        Text(
                            "查看全部",
                            style = XstzType.caption,
                            color = palette.accent,
                            modifier = Modifier.clickable { onOpenTradeHistory(holding.fundId) },
                        )
                    }
                },
            )
            if (state.trades.isEmpty()) {
                SolidCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    contentPadding = 16.dp,
                ) {
                    Text(
                        "还没有交易记录，点下方「买入」记第一笔",
                        style = XstzType.caption,
                        color = palette.textTertiary,
                    )
                }
            } else {
                SolidCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    contentPadding = 0.dp,
                ) {
                    TradeHistory(trades = state.trades)
                }
            }
        }

        // ---------- 底部操作栏 ----------
        // 必须给背景：裸按钮浮在滚动的正文上时，"当前净值 3.8421" 会从按钮缝里透出来，
        // 视觉上像两行字叠在一起（截图为证）。包成一条玻璃栏后与内容彻底分离。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            // 渐隐遮罩：固定底栏上方的正文必须淡出，否则"当前净值 3.8421"
            // 会紧贴着按钮下沿出现，读起来像两行字叠在一起（实测截图如此）。
            // 用 background 色 + 0.92 alpha 而非纯色：底部背景是 mesh 光斑，
            // 纯色渐变会留下一道可见的色带。
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            // 长渐变（200dp）把色差摊薄，短渐变会在 mesh 光斑上留下一道硬边
                            0f to Color.Transparent,
                            0.35f to palette.background.copy(alpha = 0.55f),
                            0.65f to palette.background.copy(alpha = 0.90f),
                            1f to palette.background.copy(alpha = 0.985f),
                        )
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .background(
                        palette.backgroundElevated.copy(alpha = 0.97f),
                        SquircleShape(22.dp, 2.8f),
                    )
                    .padding(7.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ActionButton(
                        label = "买入",
                        color = palette.up,
                        modifier = Modifier.weight(1f),
                        onClick = { tradeType = TradeType.BUY },
                    )
                    ActionButton(
                        label = "卖出",
                        color = palette.textSecondary,
                        modifier = Modifier.weight(1f),
                        onClick = { tradeType = TradeType.SELL },
                    )
                    ActionButton(
                        label = "分红",
                        color = palette.textSecondary,
                        modifier = Modifier.weight(1f),
                        onClick = { tradeType = TradeType.DIVIDEND },
                    )
                }
            }
        }
    }

    tradeType?.let { type ->
        state.holding?.let { h ->
            TradeEntryDialog(
                holding = h,
                initialType = type,
                onDismiss = { tradeType = null },
                onConfirm = { t, shares, nav, fee ->
                    tradeType = null
                    onTrade(h, t, shares, nav, fee)
                },
                onDelete = {
                    tradeType = null
                    onDelete(h)
                },
            )
        }
    }
}

@Composable
private fun DetailStat(
    label: String,
    value: String,
    rate: String?,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    Column(modifier) {
        Text(label, style = XstzType.caption2, color = palette.textTertiary)
        Spacer(Modifier.height(3.dp))
        Text(value, style = XstzType.numericXS, color = valueColor)
        if (rate != null) {
            Text(rate, style = XstzType.caption2, color = valueColor.copy(alpha = 0.85f))
        }
    }
}

/** 明细行：左标签右数值，行间用 hairline 分隔 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    last: Boolean = false,
) {
    val palette = XstzTheme.palette
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = XstzType.caption, color = palette.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = XstzType.numericXS,
                color = valueColor ?: palette.textPrimary,
            )
        }
        if (!last) HairlineDivider()
    }
}

/** 交易流水列表：最新在前，最多 20 条 */
@Composable
private fun TradeHistory(trades: List<Trade>) {
    val palette = XstzTheme.palette
    val rows = trades.sortedByDescending { it.epochDay * 1000 + (it.createdAt % 1000) }.take(20)
    Column {
        rows.forEachIndexed { index, t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 类型徽章：买入红、卖出绿，与 A 股涨跌色一致（买=看多）
                val badgeColor = when (t.type) {
                    TradeType.BUY -> palette.up
                    TradeType.SELL -> palette.down
                    TradeType.DIVIDEND -> palette.accent
                    // 再平衡用中性色：它是系统建议的动作，不属于用户主动看多/看空，
                    // 染成红绿会让人误以为是自己下的单
                    TradeType.REBALANCE -> palette.textSecondary
                    // 资金进出用现金色，与标的交易视觉上明确区分
                    TradeType.ADD_CASH -> palette.down
                    TradeType.WITHDRAW -> palette.up
                }
                Box(
                    Modifier
                        .background(badgeColor.copy(alpha = 0.14f), SquircleShape(7.dp, 2.4f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(t.type.displayName, style = XstzType.caption2, color = badgeColor)
                }
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Formats.quantity(t.shares.toDouble())} 份 @ ${Formats.nav(t.nav.toDouble())}",
                        style = XstzType.caption,
                        color = palette.textPrimary,
                    )
                    Text(
                        formatDetailDate(t.epochDay) +
                            if (t.fee.isZero) "" else " · 费 ${Formats.currencyValue(t.fee.toYuan())}",
                        style = XstzType.caption2,
                        color = palette.textTertiary,
                    )
                }
                Text(
                    Formats.currency(t.amount.toYuan()),
                    style = XstzType.numericXS,
                    color = palette.textSecondary,
                )
            }
            if (index != rows.lastIndex) HairlineDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = XstzTheme.palette
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), SquircleShape(16.dp, 2.8f))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = XstzType.callout, color = color)
    }
}

private val DETAIL_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun formatDetailDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DETAIL_DATE_FORMAT)

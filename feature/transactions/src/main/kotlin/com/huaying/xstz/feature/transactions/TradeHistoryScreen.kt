package com.huaying.xstz.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.theme.XstzPalette
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Radius
import com.huaying.xstz.core.designsystem.tokens.Spacing
import com.huaying.xstz.core.model.Money
import com.huaying.xstz.core.model.Trade
import com.huaying.xstz.core.model.TradeType
import com.huaying.xstz.core.ui.ButtonVariant
import com.huaying.xstz.core.ui.GlassButton
import com.huaying.xstz.core.ui.SolidCard

/**
 * 交易历史页。
 *
 * 按日分组 + 类型筛选。分组头显示当日净流入，
 * 让用户一眼看出"这一天投进去多少钱"。
 *
 * 内容层全部实色（`SolidCard`），不使用玻璃 ——
 * `VISUAL_ACCEPTANCE.md` §4 红线 R3：列表行属于内容层，玻璃会破坏层级。
 */
@Composable
fun TradeHistoryScreen(
    onBack: () -> Unit,
    holdingId: Long? = null,
    viewModel: TradeHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = XstzTheme.palette

    // 从详情页进入时锁定到该标的。放在 LaunchedEffect 而不是 init：
    // ViewModel 被复用时（返回再进入）不能带着上一个标的的过滤
    LaunchedEffect(holdingId) { viewModel.setHoldingFilter(holdingId) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(text = "返回", onClick = onBack, variant = ButtonVariant.Text)
            Spacer(Modifier.weight(1f))
            Text(
                text = "共 ${state.totalCount} 笔",
                style = XstzType.footnote,
                color = palette.textSecondary,
            )
        }

        Text(
            text = if (state.lockedHoldingName != null) "${state.lockedHoldingName} · 交易历史" else "交易历史",
            style = XstzType.title2,
            color = palette.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.screenEdge),
        )

        TypeFilterRow(
            selected = state.filter,
            onSelect = viewModel::setFilter,
        )

        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.filter == null) "还没有交易记录" else "没有该类型的交易",
                    style = XstzType.body,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screenEdge,
                end = Spacing.screenEdge,
                top = Spacing.xs,
                bottom = Spacing.tabBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            state.groups.forEach { group ->
                item(key = "hdr-${group.epochDay}") {
                    DayHeader(group)
                }
                items(group.trades, key = { "t-${it.id}" }) { t ->
                    TradeRow(
                        trade = t,
                        holdingName = state.holdings[t.fundId]?.name ?: "已删除标的",
                    )
                }
            }
        }
    }
}

/**
 * 类型筛选条。
 *
 * 「全部」+ 六个类型共 7 个 chip，窄屏上必然放不下，因此整条横向可滚动。
 * 用横向滚动而不是 `FlowRow` 换行：换行会让这一条的高度随屏宽变化，
 * 下面的列表起始位置随之跳动，切换筛选时观感很差。
 */
@Composable
private fun TypeFilterRow(
    selected: TradeType?,
    onSelect: (TradeType?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        TypeChip(
            label = "全部",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        // 六个类型全部展示而不是"只显示有数据的类型"：
        // 后者需要额外遍历一次，且用户无法预期"为什么这里少了一个"
        TradeType.entries.forEach { type ->
            TypeChip(
                label = type.displayName,
                selected = selected == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = XstzTheme.palette
    Box(
        modifier = Modifier
            .background(
                color = if (selected) palette.accent else palette.backgroundElevated,
                shape = RoundedCornerShape(Radius.capsule),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            // 选中状态必须让读屏能感知：仅靠颜色区分不满足无障碍要求
            .semantics { contentDescription = if (selected) "$label，已选中" else label },
    ) {
        Text(
            text = label,
            style = XstzType.caption,
            color = if (selected) Color.White else palette.textSecondary,
        )
    }
}

@Composable
private fun DayHeader(group: TradeDayGroup) {
    val palette = XstzTheme.palette
    val inflow = group.netCashFlow

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDay(group.epochDay),
            style = XstzType.footnote,
            color = palette.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = signedCurrency(inflow),
            style = XstzType.numericXS,
            // 净流入为正 = 红（钱进来了），负 = 绿。与 A 股涨红跌绿同色系，
            // 但语义是现金流方向而非盈亏，所以只做弱提示、不改文字
            color = when {
                inflow.micros > 0 -> palette.up
                inflow.micros < 0 -> palette.down
                else -> palette.textTertiary
            },
        )
    }
}

@Composable
private fun TradeRow(trade: Trade, holdingName: String) {
    val palette = XstzTheme.palette
    val badgeColor = badgeColorOf(trade.type, palette)

    SolidCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.14f), RoundedCornerShape(Radius.xs))
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
            ) {
                Text(trade.type.displayName, style = XstzType.caption2, color = badgeColor)
            }
            Spacer(Modifier.width(Spacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(holdingName, style = XstzType.subhead, color = palette.textPrimary)
                Text(
                    text = tradeSubtitle(trade),
                    style = XstzType.caption,
                    color = palette.textSecondary,
                )
            }
            Text(
                // 现金进出用带符号的现金流（转出要看得见是负的），
                // 买卖用成交金额（方向已由徽章表达，无需再加符号）
                text = if (trade.type.isCashFlow) {
                    signedCurrency(trade.cashFlow)
                } else {
                    Formats.currency(kotlin.math.abs(trade.amount.micros) / 10_000.0)
                },
                style = XstzType.numericS,
                color = palette.textPrimary,
            )
        }
    }
}

/** 副标题：现金进出说明方向，标的交易说明份额与净值 */
private fun tradeSubtitle(trade: Trade): String = when (trade.type) {
    TradeType.ADD_CASH -> "资金转入"
    TradeType.WITHDRAW -> "资金转出"
    else -> "${Formats.quantity(trade.shares.toDouble())} 份 @ ${Formats.nav(trade.nav.toDouble())}" +
        if (trade.fee.isZero) "" else " · 费 ${Formats.currencyValue(trade.fee.micros / 10_000.0)}"
}

/** 带符号金额：+¥123.45 / -¥123.45 */
private fun signedCurrency(value: Money): String {
    val sign = if (value.micros >= 0) "+" else "-"
    return "$sign${Formats.currency(kotlin.math.abs(value.micros) / 10_000.0)}"
}

/** 类型 → 徽章色。集中一处，避免各处各写一套映射 */
private fun badgeColorOf(type: TradeType, palette: XstzPalette): Color = when (type) {
    TradeType.BUY -> palette.up
    TradeType.SELL -> palette.down
    TradeType.DIVIDEND -> palette.accent
    TradeType.REBALANCE -> palette.textSecondary
    TradeType.ADD_CASH -> palette.down
    TradeType.WITHDRAW -> palette.up
}

/** epochDay → "2026-10-31 周六" */
private fun formatDay(epochDay: Long): String =
    "${Formats.date(epochDay)} 周${Formats.weekday(epochDay)}"

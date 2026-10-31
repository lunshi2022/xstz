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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.glass.GlassLevel
import com.huaying.xstz.core.designsystem.glass.GlassPanel
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.AssetType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.TradeType
import com.huaying.xstz.core.ui.ChangeText
import com.huaying.xstz.core.ui.EmptyState
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.SectionHeader
import dev.chrisbanes.haze.HazeState

/**
 * 持仓页。
 *
 * 与概览页的分工：概览看"总量与结构"，这里看"每一只的细节并能操作"。
 * 所以列表行要给出份额、成本、市值、盈亏四个数字，而不是概览里那三行精简版。
 */
@Composable
fun HoldingsScreen(
    state: HoldingsUiState,
    hazeState: HazeState?,
    onAdd: (code: String, name: String, type: AssetType) -> Unit,
    onTrade: (holding: Holding, type: TradeType, shares: String, nav: String, fee: String) -> Unit,
    onDelete: (Holding) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    var showAdd by remember { mutableStateOf(false) }
    var tradeTarget by remember { mutableStateOf<Holding?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
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
                            .clickable { showAdd = true },
                    )
                },
            )
        }

        // ---------- 汇总 ----------
        item {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                state = hazeState,
                cornerRadius = 24.dp,
                level = GlassLevel.Thick,
                contentPadding = 18.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("持仓市值", style = XstzType.caption, color = palette.textTertiary)
                        Text(
                            Formats.currency(state.summary.totalMarketValue.toYuan()),
                            style = XstzType.numericL,
                            color = palette.textPrimary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("累计收益", style = XstzType.caption, color = palette.textTertiary)
                        Text(
                            Formats.signedCurrency(state.summary.totalProfit.toYuan()),
                            style = XstzType.numericM,
                            color = if (state.summary.totalProfit.isZero) palette.flat
                            else palette.change(state.summary.totalProfit.micros.toDouble()),
                        )
                        Text(
                            Formats.percent(state.summary.totalProfitRate),
                            style = XstzType.numericXS,
                            color = palette.change(state.summary.totalProfitRate),
                        )
                    }
                }
            }
        }

        item { SectionHeader(title = "明细") }

        if (state.isEmpty) {
            item {
                EmptyState(
                    title = "还没有持仓",
                    hint = "点右上角 + 添加基金，再录入买入交易",
                )
            }
        } else {
            items(state.holdings, key = { it.fundId }) { h ->
                HoldingDetailCard(
                    holding = h,
                    ratio = state.ratios[h.fundId] ?: 0.0,
                    hazeState = hazeState,
                    onClick = { tradeTarget = h },
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

    if (showAdd) {
        AddHoldingDialog(
            onDismiss = { showAdd = false },
            onConfirm = { code, name, type ->
                showAdd = false
                onAdd(code, name, type)
            },
        )
    }

    tradeTarget?.let { target ->
        TradeDialog(
            holding = target,
            onDismiss = { tradeTarget = null },
            onConfirm = { type, shares, nav, fee ->
                tradeTarget = null
                onTrade(target, type, shares, nav, fee)
            },
            onDelete = {
                tradeTarget = null
                onDelete(target)
            },
        )
    }
}

@Composable
private fun HoldingDetailCard(
    holding: Holding,
    ratio: Double,
    hazeState: HazeState?,
    onClick: () -> Unit,
) {
    val palette = XstzTheme.palette
    GlassPanel(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        state = hazeState,
        cornerRadius = 20.dp,
        level = GlassLevel.Regular,
        contentPadding = 14.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(
                            palette.asset(holding.type).copy(alpha = 0.16f),
                            SquircleShape(11.dp, 2.8f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(holding.type.displayName.take(1), style = XstzType.caption, color = palette.asset(holding.type))
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

            Spacer(Modifier.height(10.dp))

            // 四个关键数字：份额 / 成本 / 成本单价 / 当日涨跌
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniMetric("份额", Formats.quantity(holding.shares.toDouble()))
                MiniMetric("成本", Formats.currencyValue(holding.cost.toYuan()))
                MiniMetric("成本单价", Formats.nav(holding.costPrice.toYuan()))
                MiniMetric("当日", Formats.percent(holding.dayChangeRate))
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
private fun MiniMetric(label: String, value: String) {
    val palette = XstzTheme.palette
    Column {
        Text(label, style = XstzType.caption2, color = palette.textTertiary)
        // 标签与数值之间留 2dp：实测贴在一起时"份额"和"12,500.00"会黏成一行
        Spacer(Modifier.height(2.dp))
        Text(value, style = XstzType.numericXS, color = palette.textSecondary)
    }
}

@Composable
private fun AddHoldingDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, AssetType) -> Unit,
) {
    val palette = XstzTheme.palette
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AssetType.STOCK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundElevated,
        title = { Text("添加基金", style = XstzType.title3, color = palette.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("基金代码（6 位）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（可留空自动取）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("资产类别", style = XstzType.caption, color = palette.textTertiary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssetType.entries.forEach { t ->
                        val selected = t == type
                        Box(
                            Modifier
                                .background(
                                    if (selected) palette.accent.copy(alpha = 0.18f) else palette.divider,
                                    SquircleShape(10.dp, 2.6f),
                                )
                                .clickable { type = t }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                t.displayName,
                                style = XstzType.caption,
                                color = if (selected) palette.accent else palette.textSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code, name, type) }) {
                Text("添加", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textTertiary) }
        },
    )
}

@Composable
private fun TradeDialog(
    holding: Holding,
    onDismiss: () -> Unit,
    onConfirm: (TradeType, String, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val palette = XstzTheme.palette
    var type by remember { mutableStateOf(TradeType.BUY) }
    var shares by remember { mutableStateOf("") }
    var nav by remember { mutableStateOf(if (holding.nav.isZero) "" else holding.nav.toDouble().toString()) }
    var fee by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundElevated,
        title = {
            Column {
                Text(holding.name, style = XstzType.title3, color = palette.textPrimary)
                Text(
                    "当前持有 ${Formats.quantity(holding.shares.toDouble())} 份",
                    style = XstzType.caption,
                    color = palette.textTertiary,
                )
            }
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeType.entries.forEach { t ->
                        val selected = t == type
                        Box(
                            Modifier
                                .background(
                                    if (selected) palette.accent.copy(alpha = 0.18f) else palette.divider,
                                    SquircleShape(10.dp, 2.6f),
                                )
                                .clickable { type = t }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                t.displayName,
                                style = XstzType.caption,
                                color = if (selected) palette.accent else palette.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = shares,
                    onValueChange = { shares = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("份额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nav,
                    onValueChange = { nav = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("成交净值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("手续费") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                // 删除入口放在这里而不是列表行长按：
                // 交易与删除都是"对这只基金的操作"，放一起语义更清楚，
                // 也避免误触长按把持仓删掉（删了流水一起没，不可恢复）
                Spacer(Modifier.height(14.dp))
                Text(
                    "删除此持仓（含全部交易流水）",
                    style = XstzType.caption,
                    color = palette.down,
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(type, shares, nav, fee) }) {
                Text("确认${type.displayName}", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textTertiary) }
        },
    )
}

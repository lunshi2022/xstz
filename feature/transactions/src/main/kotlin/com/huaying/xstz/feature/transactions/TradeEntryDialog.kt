package com.huaying.xstz.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.Holding
import com.huaying.xstz.core.model.TradeType
import com.huaying.xstz.core.ui.GlassTextField

/** 输入口径：按金额还是按份额 */
private enum class InputMode(val label: String) {
    AMOUNT("按金额"),
    SHARES("按份额"),
}

/**
 * 交易录入对话框（买入 / 卖出 / 分红），持仓列表页与详情页共用。
 *
 * 关键改进：**支持按金额下单**。
 * 旧版只有"份额"一个输入框，但买基金时人脑子里想的是"投 1000 块"，
 * 不是"买 260.28 份"。逼用户自己拿计算器除一遍是设计上的偷懒。
 * 买入默认按金额，卖出默认按份额（卖出时想的是"卖一半"这类份额概念）。
 *
 * 净值与手续费仍可改：场内 ETF 的成交价经常和昨收不同，
 * 场外基金也有申购费差异，强制用当前净值会把账算错。
 */
@Composable
fun TradeEntryDialog(
    holding: Holding,
    onDismiss: () -> Unit,
    onConfirm: (TradeType, shares: String, nav: String, fee: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    initialType: TradeType = TradeType.BUY,
) {
    val palette = XstzTheme.palette
    var type by remember { mutableStateOf(initialType) }
    // 买入按金额、卖出/分红按份额：这两种口径分别贴合各自的思考习惯
    var mode by remember { mutableStateOf(if (initialType == TradeType.BUY) InputMode.AMOUNT else InputMode.SHARES) }
    var amountText by remember { mutableStateOf("") }
    var sharesText by remember { mutableStateOf("") }
    var navText by remember { mutableStateOf(if (holding.nav.isZero) "" else holding.nav.toDouble().toString()) }
    var feeText by remember { mutableStateOf("0") }

    val nav = navText.toDoubleOrNull() ?: 0.0
    val fee = feeText.toDoubleOrNull() ?: 0.0

    // 预览：两种口径互相换算，让用户在下单前看到"这笔交易大概是多少"
    val previewShares: Double = when (mode) {
        InputMode.AMOUNT -> {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            if (nav > 0) amt / nav else 0.0
        }
        InputMode.SHARES -> sharesText.toDoubleOrNull() ?: 0.0
    }
    val previewAmount: Double = when (mode) {
        InputMode.AMOUNT -> amountText.toDoubleOrNull() ?: 0.0
        InputMode.SHARES -> previewShares * nav
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundElevated,
        title = {
            Column {
                Text(holding.name, style = XstzType.title3, color = palette.textPrimary)
                Text(
                    "当前持有 ${Formats.quantity(holding.shares.toDouble())} 份 · " +
                        "净值 ${if (holding.nav.isZero) "—" else Formats.nav(holding.nav.toDouble())}",
                    style = XstzType.caption,
                    color = palette.textTertiary,
                )
            }
        },
        text = {
            Column {
                // 方向
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeType.entries.forEach { t ->
                        SegChip(
                            label = t.displayName,
                            selected = t == type,
                            onClick = {
                                type = t
                                // 换方向时切换默认口径：买入看金额，卖出看份额
                                mode = if (t == TradeType.BUY) InputMode.AMOUNT else InputMode.SHARES
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 口径
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InputMode.entries.forEach { m ->
                        SegChip(
                            label = m.label,
                            selected = m == mode,
                            small = true,
                            onClick = { mode = m },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (mode == InputMode.AMOUNT) {
                    GlassTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "金额（元）",
                        placeholder = "0.00",
                        keyboardType = KeyboardType.Decimal,
                        textStyle = XstzType.numericS,
                    )
                } else {
                    GlassTextField(
                        value = sharesText,
                        onValueChange = { sharesText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "份额",
                        placeholder = "0.00",
                        keyboardType = KeyboardType.Decimal,
                        textStyle = XstzType.numericS,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(
                        value = navText,
                        onValueChange = { navText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "成交净值",
                        keyboardType = KeyboardType.Decimal,
                        textStyle = XstzType.numericXS,
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextField(
                        value = feeText,
                        onValueChange = { feeText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "手续费",
                        keyboardType = KeyboardType.Decimal,
                        textStyle = XstzType.numericXS,
                        modifier = Modifier.weight(1f),
                    )
                }

                // 下单预览：把另一种口径的数字摆出来，避免"填了金额但不知道买多少份"
                if (previewShares > 0.0 && nav > 0.0) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.glassFill, SquircleShape(10.dp, 2.6f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (mode == InputMode.AMOUNT)
                                "预计买入 ${Formats.quantity(previewShares)} 份"
                            else
                                "预计金额 ${Formats.currency(previewAmount)}",
                            style = XstzType.caption,
                            color = palette.textSecondary,
                        )
                        val total = previewAmount + if (type == TradeType.BUY) fee else -fee
                        Text(
                            "实际 ${Formats.currency(total)}",
                            style = XstzType.caption,
                            color = palette.textTertiary,
                        )
                    }
                }

                if (onDelete != null) {
                    // 删除入口放这里而不是列表行长按：交易与删除都是"对这只基金的操作"，
                    // 放一起语义清楚，也避免误触长按把持仓连流水一起删掉
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "删除此持仓（含全部交易流水）",
                        style = XstzType.caption,
                        color = palette.down,
                        modifier = Modifier.clickable(onClick = onDelete),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 统一转成"份额"口径交给上层：仓储只认份额
                    val shares = if (mode == InputMode.AMOUNT) {
                        if (nav > 0) (amountText.toDoubleOrNull() ?: 0.0) / nav else 0.0
                    } else {
                        sharesText.toDoubleOrNull() ?: 0.0
                    }
                    // 份额保留 2 位（基金最小交易单位通常 0.01 份），避免浮点尾巴进库
                    onConfirm(type, "%.2f".format(shares), navText, feeText)
                },
            ) {
                Text("确认${type.displayName}", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textTertiary) }
        },
    )
}

/** 对话框内的分段选择片 */
@Composable
internal fun SegChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    small: Boolean = false,
) {
    val palette = XstzTheme.palette
    Box(
        Modifier
            .background(
                if (selected) palette.accent.copy(alpha = 0.18f) else palette.divider,
                SquircleShape(10.dp, 2.6f),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (small) 9.dp else 12.dp,
                vertical = if (small) 5.dp else 6.dp,
            ),
    ) {
        Text(
            label,
            style = if (small) XstzType.caption2 else XstzType.caption,
            color = if (selected) palette.accent else palette.textSecondary,
        )
    }
}

package com.huaying.xstz.feature.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Radius
import com.huaying.xstz.core.designsystem.tokens.Spacing
import com.huaying.xstz.core.domain.repository.OperationLogEntry
import com.huaying.xstz.core.ui.ButtonVariant
import com.huaying.xstz.core.ui.GlassButton
import com.huaying.xstz.core.ui.SolidCard

/**
 * 操作日志页（方案阶段 3 页面清单第 10 项，P2）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么这个页面值得存在
 *
 * 它是给「数据看起来不对」的时刻准备的：用户发现某笔持仓的
 * 成本异常，第一反应是"我什么时候动过它"。操作日志按时间倒序
 * 回答这个问题——不参与任何业务计算，只做事实呈现。
 *
 * 动作名直接显示原始枚举（ADD_HOLDING / TRADE_BUY）而不是翻译成
 * 中文：排障时用户要拿着这串字符去搜代码或问开发者，
 * 翻译反而制造一层隔阂。detail 字段本来就是人话。
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun OperationLogScreen(
    onBack: () -> Unit,
    viewModel: OperationLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = XstzTheme.palette
    var showClearConfirm by remember { mutableStateOf(false) }

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
            if (state.entries.isNotEmpty()) {
                GlassButton(
                    text = "清空",
                    onClick = { showClearConfirm = true },
                    variant = ButtonVariant.Text,
                )
            }
        }

        Text(
            text = "操作日志",
            style = XstzType.title2,
            color = palette.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.screenEdge),
        )

        Text(
            text = "记录本应用内的数据变更，仅存本机",
            style = XstzType.footnote,
            color = palette.textTertiary,
            modifier = Modifier.padding(
                start = Spacing.screenEdge,
                end = Spacing.screenEdge,
                bottom = Spacing.xs,
            ),
        )

        if (state.entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无操作记录",
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
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            items(state.entries, key = { it.id }) { entry ->
                LogRow(entry)
            }
            // 尾部提示：日志只保留最近 200 条，让"为什么只有这些"可预期
            item {
                Text(
                    text = "仅显示最近 ${OperationLogViewModel.RECENT_LIMIT} 条",
                    style = XstzType.caption2,
                    color = palette.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = XstzTheme.palette.backgroundElevated,
            title = {
                Text("清空操作日志", style = XstzType.title3, color = XstzTheme.palette.textPrimary)
            },
            text = {
                Text(
                    "操作日志只记录本机数据变更，不是你的投资数据，清空后可重新累积。确定清空？",
                    style = XstzType.caption,
                    color = XstzTheme.palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clear()
                }) { Text("清空", color = XstzTheme.palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = XstzTheme.palette.textTertiary)
                }
            },
        )
    }
}

@Composable
private fun LogRow(entry: OperationLogEntry) {
    val palette = XstzTheme.palette

    SolidCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(
                        categoryColorOf(entry.action, palette).copy(alpha = 0.14f),
                        RoundedCornerShape(Radius.xs),
                    )
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
            ) {
                Text(
                    text = entry.action,
                    style = XstzType.caption2,
                    color = categoryColorOf(entry.action, palette),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = Formats.dateTime(entry.createdAt),
                style = XstzType.caption2,
                color = palette.textTertiary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.detail,
            style = XstzType.caption,
            color = palette.textSecondary,
        )
    }
}

/** 动作前缀 → 色系。备份用主题色、持仓用资产色、交易用涨跌色系但取中性 */
private fun categoryColorOf(
    action: String,
    palette: com.huaying.xstz.core.designsystem.theme.XstzPalette,
): androidx.compose.ui.graphics.Color = when {
    action.startsWith("BACKUP_") -> palette.accent
    action.startsWith("TRADE_") -> palette.textSecondary
    action == "ADD_HOLDING" || action == "DELETE_HOLDING" -> palette.textPrimary
    action == "SEED_SAMPLE" -> palette.textTertiary
    else -> palette.textSecondary
}

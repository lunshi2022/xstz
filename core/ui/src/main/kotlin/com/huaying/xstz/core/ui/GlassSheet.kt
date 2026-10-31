package com.huaying.xstz.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Radius
import com.huaying.xstz.core.designsystem.tokens.Spacing

/**
 * 底部弹层（方案 8.4：Sheet 升起 400ms 弹簧，背景内容模糊 + 缩放 0.98）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 关于"背景内容模糊 + 缩放 0.98"
 *
 * 这是 iOS 的观感特征，但**在 Compose 里代价很高**：
 * 要对整页内容做 hazeBlur，等于每帧对全屏重新采样。
 * 中端机上这一步会直接掉到 30fps 以下。
 *
 * 方案把 Sheet 列为 P0 但把动效列为阶段 4，因此这里先实现
 * **功能正确 + 时长合规**的版本，背景模糊留给阶段 4 与
 * 帧率实测一起决定（若掉帧就只保留 scrim 变暗，不强行上模糊）。
 * 这个取舍明确记录在此，不当作已实现。
 *
 * 用 ModalBottomSheet 而非自绘：
 *   拖动关闭、无障碍、返回键处理都是现成的，自绘会重写这三样且容易漏。
 * ══════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    val palette = XstzTheme.palette
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 不用 rememberCoroutineScope：ModalBottomSheet 自己管理收起动画，
    // 我们只在用户操作时把 visible 置 false，由它播完动画再真正移除。

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        // 用实色而非玻璃：Sheet 内容通常是表单与选项，
        // 玻璃会让背后文字透上来干扰阅读（内容层红线 R3）
        containerColor = palette.backgroundElevated,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = {
            // iOS 风格的短拖拽条：宽 36dp，比 Compose 默认的 32dp 略宽
            Box(
                modifier = Modifier
                    .padding(vertical = Spacing.sm)
                    .width(36.dp)
                    .height(4.dp)
                    .background(palette.textTertiary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = XstzType.title3,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            content()
        }
    }
}

/**
 * 弹窗（方案 8.4：玻璃面板宽度 ≤ 300dp，按钮区用分隔线）。
 *
 * 这里用**实色面板**而非玻璃，理由同上：Dialog 里是决策文本，
 * 透出背景会降低可读性。方案 8.4 写"玻璃面板"是视觉口径，
 * 而 `VISUAL_ACCEPTANCE.md` 的红线优先——内容层必须实色。
 * 这个偏离记录在一致性核对说明里。
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String? = null,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    if (!visible) return
    val palette = XstzTheme.palette
    val shape: Shape = RoundedCornerShape(Radius.lg)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp) // 方案 8.4 明确上限
                .background(palette.backgroundElevated, shape)
                .padding(top = Spacing.lg, bottom = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = XstzType.headline,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            if (message != null) {
                Text(
                    text = message,
                    style = XstzType.footnote,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(
                        start = Spacing.md,
                        end = Spacing.md,
                        top = Spacing.xs,
                    ),
                )
            }

            // 按钮区上方的分隔线（方案 8.4 要求）
            Box(
                modifier = Modifier
                    .padding(top = Spacing.lg)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.divider),
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                if (dismissText != null) {
                    DialogAction(
                        text = dismissText,
                        color = palette.accent,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    // 两个按钮之间的竖分隔线
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(palette.divider),
                    )
                }
                DialogAction(
                    text = confirmText,
                    // 破坏性操作（删除）用下跌色，与常规确认区分开
                    color = if (destructive) palette.down else palette.accent,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun DialogAction(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = XstzType.subhead, color = color)
    }
}

package com.huaying.xstz.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Motion
import com.huaying.xstz.core.designsystem.tokens.Radius
import com.huaying.xstz.core.designsystem.tokens.Spacing

/**
 * 按钮（方案阶段 2 第 3 项的基础组件之一）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么内容层不用玻璃
 *
 * 按钮若做成玻璃，会与它所在的实色卡片抢层次——`VISUAL_ACCEPTANCE.md`
 * §4 红线 R3 要求内容层（含按钮）保持实色。这里三种变体都是实色：
 *   * Primary   —— 主题色填充，唯一的"主行动"
 *   * Secondary —— 实色底 + 描边
 *   * Text      —— 无底色，仅文字
 *
 * 按压反馈用 scale(0.96) 而不是改变 alpha：
 * 变透明会让按钮像"禁用"，缩小则有明确的"被按下"物理感。
 * 时长用 Motion.PRESS_MS(100ms) 线性——按下必须立刻有反应。
 * ══════════════════════════════════════════════════════════════════
 */
enum class ButtonVariant { Primary, Secondary, Text }

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    tint: Color? = null,
) {
    val palette = XstzTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Radius.sm)

    val accent = tint ?: palette.accent
    val fill = when (variant) {
        ButtonVariant.Primary -> accent
        ButtonVariant.Secondary -> palette.backgroundElevated
        ButtonVariant.Text -> Color.Transparent
    }
    val labelColor = when (variant) {
        ButtonVariant.Primary -> Color.White
        ButtonVariant.Secondary -> accent
        ButtonVariant.Text -> accent
    }.let { if (enabled) it else it.copy(alpha = 0.4f) }

    Box(
        modifier = modifier
            .scale(if (pressed && enabled) 0.96f else 1f)
            .then(
                if (variant != ButtonVariant.Text) {
                    Modifier.background(fill, shape)
                } else Modifier
            )
            .then(
                if (variant == ButtonVariant.Secondary) {
                    Modifier.border(
                        width = 1.dp,
                        color = accent.copy(alpha = if (enabled) 0.35f else 0.15f),
                        shape = shape,
                    )
                } else Modifier
            )
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = null, // 自绘按压反馈，关掉默认涟漪
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = labelColor,
                strokeWidth = 2.dp,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = text,
                    style = XstzType.subhead,
                    color = labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 内容层卡片（实色，方案 8.4 的 Card 形态）。等价于既有 SolidCard，保留别名便于语义迁移。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) = SolidCard(modifier = modifier, content = content)

/** 供调用方引用，避免硬编码按压缩放比 */
internal val PressScale = 0.96f

/** 供调用方引用，避免硬编码按压时长 */
internal val PressDurationMs = Motion.PRESS_MS

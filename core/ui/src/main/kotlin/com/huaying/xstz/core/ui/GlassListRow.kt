package com.huaying.xstz.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Radius
import com.huaying.xstz.core.designsystem.tokens.Spacing

/**
 * 列表行（方案 8.4：List Row = **实色卡片 + 细描边**，不用玻璃）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 这是内容层组件，绝不能做成玻璃。
 *
 * `VISUAL_ACCEPTANCE.md` §4 红线 R3 明确：「内容层（列表行、图表）
 * 使用玻璃 → 破坏层级」。理由：玻璃的语义是"浮在内容之上的导航层"，
 * 每一条列表行都做成玻璃，等于宣布"所有东西都是浮层"，
 * 层级信息归零，且滚动时每行都要实时采样背景，性能直接崩。
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun GlassListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    trailingColor: Color? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = XstzTheme.palette
    val shape = RoundedCornerShape(Radius.listRow)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // 实色填充：内容层必须不透光，否则与背景文字互相干扰
            .background(palette.backgroundElevated)
            .border(BorderStroke(1.dp, palette.divider.copy(alpha = 0.5f)), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background((leadingTint ?: palette.accent).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null, // 装饰性图标，语义由 title 承载
                    tint = leadingTint ?: palette.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = XstzType.body,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = XstzType.caption,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Text(
                text = trailing,
                style = XstzType.numericS, // 等宽数字，对齐不跳
                color = trailingColor ?: palette.textPrimary,
                maxLines = 1,
            )
        }
    }
}

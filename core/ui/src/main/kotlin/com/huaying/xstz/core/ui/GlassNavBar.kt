package com.huaying.xstz.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.glass.GlassLevel
import com.huaying.xstz.core.designsystem.glass.GlassPanel
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Radius
import com.huaying.xstz.core.designsystem.tokens.Spacing
import dev.chrisbanes.haze.HazeState

/**
 * 顶部导航栏（方案 8.4：`LargeTopAppBar` + `GlassSurface`）。
 *
 * 导航层组件 —— 可以玻璃，因为它固定在顶部、不随内容滚动。
 *
 * ══════════════════════════════════════════════════════════════════
 * 滚动边缘渐显（scroll edge effect）
 *
 * iOS 26 的关键细节：页面在顶部时导航栏**完全透明**，一旦内容开始
 * 向下滚到栏后面，栏才逐渐显出一层玻璃与描边。
 * 方案 8.4 明确要求这个效果（"玻璃边缘由无到有渐显"）。
 *
 * 实现方式是让调用方传入 `scrolledFraction`（0f=在顶、1f=已滚），
 * 而不是在组件内部靠 scrollState 反向推断——组件不该假设
 * 自己上面挂的是哪种滚动容器（LazyColumn / Column+verticalScroll 都可能）。
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun GlassNavBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** 0f = 页面在顶部（栏完全透明），1f = 内容已滚到栏下（栏完全显形） */
    scrolledFraction: Float = 1f,
    leadingIcon: ImageVector? = null,
    onLeadingClick: (() -> Unit)? = null,
    leadingDescription: String? = null,
    actions: @Composable (() -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    val palette = XstzTheme.palette
    val f = scrolledFraction.coerceIn(0f, 1f)

    // 未滚动时连玻璃都不画：画了即使 alpha=0 仍会占用 haze 采样，白费性能。
    // 这是一个容易忽略的性能点——透明 ≠ 无成本。
    if (f <= 0.01f && hazeState == null) {
        // 极简形态：只留标题文字，背景让页面内容透上来
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            NavBarLeading(leadingIcon, leadingDescription, onLeadingClick)
            NavBarTitle(title, subtitle, palette.textPrimary, palette.textSecondary, Modifier.weight(1f))
            actions?.invoke()
        }
        return
    }

    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        state = hazeState,
        cornerRadius = Radius.lg,
        level = if (f > 0.6f) GlassLevel.Regular else GlassLevel.UltraThin,
        highlight = f > 0.6f,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            NavBarLeading(leadingIcon, leadingDescription, onLeadingClick)
            NavBarTitle(title, subtitle, palette.textPrimary, palette.textSecondary, Modifier.weight(1f))
            actions?.invoke()
        }
    }
}

@Composable
private fun NavBarLeading(
    icon: ImageVector?,
    description: String?,
    onClick: (() -> Unit)?,
) {
    if (icon == null) return
    val palette = XstzTheme.palette
    Icon(
        imageVector = icon,
        // 无障碍：图标按钮必须有描述，否则 TalkBack 只念"按钮"。
        // 描述由调用方给，因为同一个返回箭头在不同语境下读法不同。
        contentDescription = description,
        tint = palette.accent,
        modifier = Modifier
            .size(28.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics {
                if (onClick != null) role = Role.Button
                if (description != null) contentDescription = description
            },
    )
}

@Composable
private fun NavBarTitle(
    title: String,
    subtitle: String?,
    titleColor: Color,
    subtitleColor: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(
            text = title,
            style = XstzType.headline,
            color = titleColor,
            maxLines = 1,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = XstzType.caption,
                color = subtitleColor,
                maxLines = 1,
            )
        }
    }
}

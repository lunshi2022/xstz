package com.huaying.xstz.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.glass.GlassLevel
import com.huaying.xstz.core.designsystem.glass.GlassPanel
import com.huaying.xstz.core.designsystem.shape.squircleCapsule
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import dev.chrisbanes.haze.HazeState

/** 底部导航项 */
enum class MainTab(val label: String, val icon: ImageVector) {
    Overview("概览", Icons.Rounded.AccountBalanceWallet),
    Holdings("持仓", Icons.Rounded.PieChart),
    Charts("走势", Icons.Rounded.CandlestickChart),
    Rebalance("再平衡", Icons.Rounded.SwapVert),
    Settings("设置", Icons.Rounded.Settings),
}

/**
 * 悬浮玻璃 Tab 栏。
 *
 * 与 iOS 的差异说明（诚实记录）：
 * iOS 的 Tab 栏是贴在屏幕底边、与内容连续滚动的；这里做成**悬浮胶囊**，
 * 因为 Android 的手势导航区域会与贴底栏冲突，悬浮能留出手势缓冲带。
 * 代价是观感上比 iOS 略"重"，这一点在 VISUAL_ACCEPTANCE 里记为已知差异。
 */
@Composable
fun GlassTabBar(
    current: MainTab,
    onSelect: (MainTab) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    GlassPanel(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        state = hazeState,
        cornerRadius = 28.dp,
        level = GlassLevel.Regular,
        contentPadding = 6.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    val motion = XstzTheme.motion

    // 选中态用弹簧：iOS 的 tab 切换是有轻微过冲的，线性动画会显得"假"
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tabAlpha",
    )

    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .scale(if (motion.animationsEnabled) scale else 1f)
            .alpha(alpha)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) palette.accent else palette.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = tab.label,
            style = XstzType.caption2,
            color = if (selected) palette.accent else palette.textSecondary,
        )
    }
}

/** 页面顶部的大标题区（iOS Large Title 风格） */
@Composable
fun LargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = XstzTheme.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = XstzType.largeTitle, color = palette.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = XstzType.footnote, color = palette.textTertiary)
            }
        }
        trailing?.invoke()
    }
}

/** 玻璃风格主按钮 */
@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = XstzTheme.palette
    Box(
        modifier = modifier
            .height(50.dp)
            .background(
                if (enabled) palette.accent else palette.textTertiary.copy(alpha = 0.3f),
                squircleCapsule(),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = XstzType.headline, color = androidx.compose.ui.graphics.Color.White)
    }
}

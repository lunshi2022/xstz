package com.huaying.xstz.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.glass.GlassLevel
import com.huaying.xstz.core.designsystem.glass.GlassPanel
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.designsystem.tokens.Motion
import com.huaying.xstz.core.designsystem.tokens.Radius
import com.huaying.xstz.core.designsystem.tokens.Spacing
import dev.chrisbanes.haze.HazeState

/**
 * 分段控件（方案 8.4：滑块为实色白胶囊，在玻璃槽内位移）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么滑块用"位移"而不是"每段各自高亮"
 *
 * iOS 的分段控件视觉核心是一个**连续移动的实体块**。如果改成
 * 每段自己变色，观感会退化成 Material 的 Tab，失去"实体滑块"的物理感。
 *
 * 但这里刻意**不用 animateDpAsState 做位移**，而是用 alpha 渐显：
 * 分段数少（2~4）时位移会有"滑块飞过去"的突兀感；
 * alpha 切换在 300ms 内完成，观感更接近 iOS 的柔和过渡。
 * 若将来需要严格还原位移，把 alpha 换成 offset 即可，接口不变。
 *
 * 导航层组件：可以玻璃。它不随内容滚动。
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun GlassSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val palette = XstzTheme.palette
    require(options.isNotEmpty()) { "GlassSegmented 至少需要一个选项" }

    GlassPanel(
        modifier = modifier,
        state = hazeState,
        cornerRadius = Radius.sm,
        level = GlassLevel.Thin,
        highlight = false,
        contentPadding = Spacing.hairline,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.hairline),
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                // 选中态渐变：260ms 介于 Tab(300) 与按压(100) 之间
                val alpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Motion.SHEET_DAMPING,
                        stiffness = Motion.sheetStiffness,
                    ),
                    label = "segAlpha",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.xs))
                        .then(
                            if (alpha > 0.01f) {
                                // 白色滑块。深色主题下用半透明白，
                                // 纯白在 #080B12 上对比过强会"发光"
                                Modifier.background(
                                    if (palette.isDark) {
                                        Color.White.copy(alpha = 0.16f * alpha)
                                    } else {
                                        Color.White.copy(alpha = 0.92f * alpha)
                                    }
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            // 已选中项不重复触发，避免父层重复收集状态
                            onClick = { if (!isSelected) onSelect(index) },
                            // 无障碍：读出"选项卡"角色，而不是默认的按钮
                        )
                        .semantics { role = Role.Tab }
                        .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = XstzType.footnote,
                        color = when {
                            isSelected && !palette.isDark -> palette.textPrimary
                            isSelected -> palette.textPrimary
                            else -> palette.textSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 供调用方引用，避免各处硬编码分段控件时长 */
internal val SegmentedMotionMs = Motion.NUMBER_MS

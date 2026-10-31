package com.huaying.xstz.core.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 阴影令牌（方案阶段 2 第 1 项）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 阴影在本项目里的特殊性
 *
 * iOS 26 的视觉里，层级主要靠**材质与描边**表达，阴影非常克制。
 * 深色模式下浓阴影几乎不可见且会显脏，所以这里只定义"高度"（elevation），
 * 实际颜色由主题按明暗给出（见 XstzColors.kt 的 glassShadow 等）。
 *
 * 三档而不是五档：Material 的 0~5 档在实践中只用得上 0/1/3，
 * 定义得越多，越容易出现同一层级用了不同档位的不一致。
 * ══════════════════════════════════════════════════════════════════
 */
object Elevation {
    /** 0dp。贴在背景上的内容，靠描边区分而不靠阴影 */
    val none: Dp = 0.dp

    /**
     * 1dp。内容层卡片（SolidCard）。
     * 只求"略微浮起"，深了会与玻璃层的柔和感冲突。
     */
    val content: Dp = 1.dp

    /**
     * 8dp。浮层：Tab 栏、Sheet、Dialog、下拉菜单。
     * 这是唯一需要明确表达"在内容之上"的层级。
     */
    val floating: Dp = 8.dp
}

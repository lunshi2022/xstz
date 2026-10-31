package com.huaying.xstz.core.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆角令牌（方案阶段 2 第 1 项）。
 *
 * 注意：本项目的主视觉用 [com.huaying.xstz.core.designsystem.shape.SquircleShape]
 * 连续圆角，普通 `RoundedCornerShape` 在玻璃层上会显得生硬
 * （iOS 的圆角是连续曲率，不是圆弧拼接）。这里的 Dp 值是**半径**，
 * 交给 SquircleShape 使用，不要在 UI 层直接构造 RoundedCornerShape。
 */
object Radius {
    /** 6dp。极小元素：徽标、标签 */
    val xs: Dp = 6.dp

    /** 10dp。输入框、小按钮 */
    val sm: Dp = 10.dp

    /**
     * 14dp。列表行、二级卡片。
     * 与 [md] 的区分是为了让"内容卡"和"浮层"在圆角上也有层次。
     */
    val listRow: Dp = 14.dp

    /** 20dp。内容层标准卡片（SolidCard 默认值） */
    val md: Dp = 20.dp

    /** 24dp。导航层玻璃面板（GlassPanel 默认值） */
    val lg: Dp = 24.dp

    /** 28dp。Sheet 顶角、Dialog */
    val sheet: Dp = 28.dp

    /**
     * 9999dp。胶囊形。
     * 用极大值而非 50% 是 SquircleShape 的接口限制——它按半径裁切，
     * 给一个远超任何视图半宽的半径即等效于完全圆头。
     */
    val capsule: Dp = 9999.dp
}

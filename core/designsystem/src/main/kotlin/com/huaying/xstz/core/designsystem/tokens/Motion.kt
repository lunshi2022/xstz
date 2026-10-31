package com.huaying.xstz.core.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring

/**
 * 动效令牌（方案 8.5 的代码化）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 三条硬约束
 *
 * 1. **禁止超过 400ms 的动效**。方案 8.5 明确要求，原因是旧工程
 *    `AnimationConstants` 里有 500ms 的颜色过渡，观感上"黏"。
 *    超过 400ms 的过渡在手机上会被感知为卡顿而非优雅。
 *
 * 2. **曲线用 iOS 标准 ease-out**：`CubicBezier(0.32, 0.72, 0, 1)`。
 *    这个曲线前半段快、尾段缓，符合 iOS 的"先响应后收敛"手感。
 *    Compose 默认的 FastOutSlowIn 尾段偏慢，同一时长下看起来更迟。
 *
 * 3. **弹簧只用于 Sheet 升起**。其余场景一律用时长+曲线，
 *    因为弹簧无法预测总时长，容易叠加出超 400ms 的观感。
 * ══════════════════════════════════════════════════════════════════
 */
object Motion {

    /** 按压反馈。100ms 线性——按下必须"立刻"有反应，加缓动反而迟滞 */
    const val PRESS_MS: Int = 100
    val pressEasing: Easing = LinearEasing

    /**
     * Tab 切换（含玻璃形变）。300ms。
     * 这是全应用最高频的动效，时长直接影响"顺滑感"，
     * 300ms 是实测下既不拖沓也不突兀的值。
     */
    const val TAB_MS: Int = 300

    /** 页面推入。350ms。比 Tab 略长，因为位移距离更大 */
    const val PAGE_MS: Int = 350

    /** 数字变化。250ms。金额滚动的过渡，再长会让人等数字 */
    const val NUMBER_MS: Int = 250

    /** 弹窗淡入淡出。200ms */
    const val DIALOG_MS: Int = 200

    /**
     * iOS 标准 ease-out。
     * 用于 Tab 切换、页面推入、Sheet 升起的所有非弹簧动效。
     */
    val iosEaseOut: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** 数字变化用 Compose 内置曲线，与 Material 的数值动画一致 */
    val numberEasing: Easing = FastOutSlowInEasing

    /**
     * Sheet 弹簧参数（方案 8.5：damping 0.85, stiffness 300）。
     *
     * damping 0.85 接近临界阻尼——会轻微回弹一次但不往复振荡。
     * 阻尼再低（如 0.6）会有明显弹跳，与金融应用的稳重感不符。
     */
    const val SHEET_DAMPING: Float = 0.85f
    const val SHEET_STIFFNESS: Float = 300f

    /** 上限，供单测断言用。任何新加时长不得越过它 */
    const val MAX_MS: Int = 400

    /**
     * 弹簧的 stiffness 用 Compose 的常量体系更稳（预置值经过调校）。
     * 方案给的是 300，最接近的是 StiffnessMediumLow(400) 与
     * StiffnessLow(200) 之间，取 MediumLow 偏快的观感。
     */
    val sheetStiffness: Float = Spring.StiffnessMediumLow
}

package com.huaying.xstz.ui.animation

import androidx.compose.animation.core.*

/**
 * 动画常量定义
 * 统一管理应用中所有动画的时长、缓动函数等参数
 */
object AnimationConstants {

    // ==================== 动画时长 ====================
    object Duration {
        /** 快速动画 - 用于微交互 */
        const val FAST = 150

        /** 标准动画 - 用于大多数过渡 */
        const val NORMAL = 300

        /** 慢速动画 - 用于强调 */
        const val SLOW = 500

        /** 启动动画时长 */
        const val SPLASH = 2000

        /** 页面切换动画 */
        const val PAGE_TRANSITION = 350

        /** 内容加载动画 */
        const val CONTENT_LOAD = 400

        /** 列表项进入动画间隔 */
        const val LIST_ITEM_STAGGER = 50
    }

    // ==================== 缓动函数 ====================
    object Easing {
        /** 标准缓动 - 适用于大多数动画 */
        val Standard = FastOutSlowInEasing

        /** 减速缓动 - 适用于进入动画 */
        val Decelerate = LinearOutSlowInEasing

        /** 加速缓动 - 适用于退出动画 */
        val Accelerate = FastOutLinearInEasing

        /** 弹性缓动 - 适用于强调动画 */
        val Spring = SpringEasing()

        /** 平滑弹性 */
        val SmoothSpring = SpringEasing(dampingRatio = 0.8f)
    }

    // ==================== 弹性动画规格 ====================
    object SpringSpec {
        /** 标准弹性 */
        val Default = spring<Float>(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        )

        /** 低弹性 - 更柔和 */
        val Gentle = spring<Float>(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )

        /** 高弹性 - 更活泼 */
        val Bouncy = spring<Float>(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioHighBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        )

        /** 刚性弹性 - 快速稳定 */
        val Stiff = spring<Float>(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        )
    }

    // ==================== 缩放比例 ====================
    object Scale {
        const val SMALL = 0.85f
        const val NORMAL = 0.95f
        const val LARGE = 1.05f
        const val FULL = 1.0f
    }

    // ==================== 透明度 ====================
    object Alpha {
        const val INVISIBLE = 0f
        const val DIM = 0.3f
        const val NORMAL = 0.6f
        const val VISIBLE = 1f
    }

    // ==================== 偏移量 ====================
    object Offset {
        const val SMALL = 0.1f
        const val NORMAL = 0.2f
        const val LARGE = 0.3f
        const val FULL = 1.0f
    }
}

/**
 * 自定义弹性缓动函数
 */
class SpringEasing(
    private val dampingRatio: Float = 0.5f
) : Easing {
    override fun transform(fraction: Float): Float {
        return if (fraction == 0f || fraction == 1f) {
            fraction
        } else {
            val value = kotlin.math.exp(-dampingRatio * 10 * fraction) *
                    kotlin.math.cos(10 * fraction) *
                    (1 - fraction) + fraction
            value.coerceIn(0f, 1f)
        }
    }
}

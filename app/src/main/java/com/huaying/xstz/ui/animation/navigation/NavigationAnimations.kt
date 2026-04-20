package com.huaying.xstz.ui.animation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry
import com.huaying.xstz.ui.animation.AnimationConstants

// 引入 Material 动画规范
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 导航动画类型枚举
 */
enum class NavigationAnimationType {
    /** 水平滑动 - 默认 */
    SLIDE_HORIZONTAL,

    /** 垂直滑动 */
    SLIDE_VERTICAL,

    /** 淡入淡出 */
    FADE,

    /** 缩放 */
    SCALE,

    /** 混合 - 缩放+淡入 */
    MIXED,

    /** 底部弹窗 */
    BOTTOM_SHEET,

    /** 无动画 */
    NONE
}

/**
 * 页面切换动画配置
 */
data class PageTransitionConfig(
    val enterAnimation: NavigationAnimationType = NavigationAnimationType.SLIDE_HORIZONTAL,
    val exitAnimation: NavigationAnimationType = NavigationAnimationType.SLIDE_HORIZONTAL,
    val popEnterAnimation: NavigationAnimationType = NavigationAnimationType.SLIDE_HORIZONTAL,
    val popExitAnimation: NavigationAnimationType = NavigationAnimationType.SLIDE_HORIZONTAL,
    val durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
)

/**
 * 导航动画构建器
 * 提供各种页面切换动画效果
 */
object NavigationAnimations {

    // ==================== 水平滑动动画 ====================

    /**
     * iOS风格：从右向左进入（前进）
     * 特点：轻微弹簧，更 subtle
     */
    fun iosSlideInFromRight(
        durationMillis: Int = 300
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessHigh,
                visibilityThreshold = null
            )
        )
    }

    /**
     * iOS风格：向左滑出（前进）
     * 特点：下层轻微缩小，更 subtle
     */
    fun iosSlideOutToLeft(
        durationMillis: Int = 300
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth / 4 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        ) + scaleOut(
            targetScale = 0.95f,
            animationSpec = tween(durationMillis)
        )
    }

    /**
     * iOS风格：从左向右进入（返回）
     * 特点：从轻微缩小状态恢复
     */
    fun iosSlideInFromLeft(
        durationMillis: Int = 300
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth / 4 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        ) + scaleIn(
            initialScale = 0.95f,
            animationSpec = tween(durationMillis)
        )
    }

    /**
     * iOS风格：向右滑出（返回）
     * 特点：轻微弹簧，更 subtle
     */
    fun iosSlideOutToRight(
        durationMillis: Int = 300
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessHigh,
                visibilityThreshold = null
            )
        )
    }

    // ==================== 传统滑动动画（保留作为备选） ====================

    /**
     * 从右向左进入（前进）- 干脆利落，无淡入淡出
     */
    fun slideInFromRight(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        )
    }

    /**
     * 向左滑出（前进）- 干脆利落，无淡入淡出
     */
    fun slideOutToLeft(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        )
    }

    /**
     * 从左向右进入（返回）- 干脆利落，无淡入淡出
     */
    fun slideInFromLeft(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        )
    }

    /**
     * 向右滑出（返回）- 干脆利落，无淡入淡出
     */
    fun slideOutToRight(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        )
    }

    // ==================== 垂直滑动动画 ====================

    /**
     * 从底部滑入
     */
    fun slideInFromBottom(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Decelerate
            )
        ) + fadeIn(
            animationSpec = tween(durationMillis / 2)
        )
    }

    /**
     * 向底部滑出
     */
    fun slideOutToBottom(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Accelerate
            )
        ) + fadeOut(
            animationSpec = tween(durationMillis / 2)
        )
    }

    /**
     * 从顶部滑入
     */
    fun slideInFromTop(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInVertically(
            initialOffsetY = { fullHeight -> -fullHeight },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Decelerate
            )
        ) + fadeIn(
            animationSpec = tween(durationMillis / 2)
        )
    }

    // ==================== 淡入淡出动画 ====================

    /**
     * 淡入
     */
    fun fadeIn(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        androidx.compose.animation.fadeIn(
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Decelerate
            )
        )
    }

    /**
     * 淡出
     */
    fun fadeOut(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        androidx.compose.animation.fadeOut(
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Accelerate
            )
        )
    }

    // ==================== 缩放动画 ====================

    /**
     * 缩放进入
     */
    fun scaleIn(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        scaleIn(
            initialScale = 0.9f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Spring
            )
        ) + fadeIn(
            animationSpec = tween(durationMillis / 2)
        )
    }

    /**
     * 缩放退出
     */
    fun scaleOut(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        scaleOut(
            targetScale = 0.9f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Accelerate
            )
        ) + fadeOut(
            animationSpec = tween(durationMillis / 2)
        )
    }

    // ==================== 混合动画 ====================

    /**
     * 混合进入 - 缩放 + 淡入 + 轻微滑动
     */
    fun mixedEnter(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth / 4 },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Decelerate
            )
        ) + scaleIn(
            initialScale = 0.95f,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Spring
            )
        ) + fadeIn(
            animationSpec = tween(durationMillis / 2)
        )
    }

    /**
     * 混合退出
     */
    fun mixedExit(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth / 4 },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Accelerate
            )
        ) + scaleOut(
            targetScale = 0.95f,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Accelerate
            )
        ) + fadeOut(
            animationSpec = tween(durationMillis / 2)
        )
    }

    // ==================== 底部弹窗动画 ====================

    /**
     * 底部弹窗进入
     */
    fun bottomSheetEnter(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Decelerate
            )
        ) + fadeIn(
            animationSpec = tween(durationMillis / 2)
        )
    }

    /**
     * 底部弹窗退出
     */
    fun bottomSheetExit(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = AnimationConstants.Easing.Decelerate
            )
        ) + fadeOut(
            animationSpec = tween(durationMillis = durationMillis)
        )
    }

    // ==================== 预定义动画组合 ====================

    /**
     * 标准页面切换动画（水平滑动）
     */
    val Standard = PageTransitionConfig(
        enterAnimation = NavigationAnimationType.SLIDE_HORIZONTAL,
        exitAnimation = NavigationAnimationType.SLIDE_HORIZONTAL,
        popEnterAnimation = NavigationAnimationType.SLIDE_HORIZONTAL,
        popExitAnimation = NavigationAnimationType.SLIDE_HORIZONTAL
    )

    /**
     * 淡入淡出切换
     */
    val Fade = PageTransitionConfig(
        enterAnimation = NavigationAnimationType.FADE,
        exitAnimation = NavigationAnimationType.FADE,
        popEnterAnimation = NavigationAnimationType.FADE,
        popExitAnimation = NavigationAnimationType.FADE
    )

    /**
     * 缩放切换
     */
    val Scale = PageTransitionConfig(
        enterAnimation = NavigationAnimationType.SCALE,
        exitAnimation = NavigationAnimationType.SCALE,
        popEnterAnimation = NavigationAnimationType.SCALE,
        popExitAnimation = NavigationAnimationType.SCALE
    )

    /**
     * 混合切换
     */
    val Mixed = PageTransitionConfig(
        enterAnimation = NavigationAnimationType.MIXED,
        exitAnimation = NavigationAnimationType.MIXED,
        popEnterAnimation = NavigationAnimationType.MIXED,
        popExitAnimation = NavigationAnimationType.MIXED
    )

    /**
     * 底部弹窗切换
     */
    val BottomSheet = PageTransitionConfig(
        enterAnimation = NavigationAnimationType.BOTTOM_SHEET,
        exitAnimation = NavigationAnimationType.BOTTOM_SHEET,
        popEnterAnimation = NavigationAnimationType.BOTTOM_SHEET,
        popExitAnimation = NavigationAnimationType.BOTTOM_SHEET
    )
}

/**
 * 根据动画类型获取进入动画
 */
fun NavigationAnimationType.toEnterTransition(
    durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition {
    return when (this) {
        NavigationAnimationType.SLIDE_HORIZONTAL -> NavigationAnimations.slideInFromRight(durationMillis)
        NavigationAnimationType.SLIDE_VERTICAL -> NavigationAnimations.slideInFromBottom(durationMillis)
        NavigationAnimationType.FADE -> NavigationAnimations.fadeIn(durationMillis)
        NavigationAnimationType.SCALE -> NavigationAnimations.scaleIn(durationMillis)
        NavigationAnimationType.MIXED -> NavigationAnimations.mixedEnter(durationMillis)
        NavigationAnimationType.BOTTOM_SHEET -> NavigationAnimations.bottomSheetEnter(durationMillis)
        NavigationAnimationType.NONE -> { { EnterTransition.None } }
    }
}

/**
 * 根据动画类型获取退出动画
 */
fun NavigationAnimationType.toExitTransition(
    durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition {
    return when (this) {
        NavigationAnimationType.SLIDE_HORIZONTAL -> NavigationAnimations.slideOutToLeft(durationMillis)
        NavigationAnimationType.SLIDE_VERTICAL -> NavigationAnimations.slideOutToBottom(durationMillis)
        NavigationAnimationType.FADE -> NavigationAnimations.fadeOut(durationMillis)
        NavigationAnimationType.SCALE -> NavigationAnimations.scaleOut(durationMillis)
        NavigationAnimationType.MIXED -> NavigationAnimations.mixedExit(durationMillis)
        NavigationAnimationType.BOTTOM_SHEET -> NavigationAnimations.bottomSheetExit(durationMillis)
        NavigationAnimationType.NONE -> { { ExitTransition.None } }
    }
}

/**
 * 根据动画类型获取返回进入动画
 */
fun NavigationAnimationType.toPopEnterTransition(
    durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition {
    return when (this) {
        NavigationAnimationType.SLIDE_HORIZONTAL -> NavigationAnimations.slideInFromLeft(durationMillis)
        NavigationAnimationType.SLIDE_VERTICAL -> NavigationAnimations.slideInFromTop(durationMillis)
        NavigationAnimationType.FADE -> NavigationAnimations.fadeIn(durationMillis)
        NavigationAnimationType.SCALE -> NavigationAnimations.scaleIn(durationMillis)
        NavigationAnimationType.MIXED -> NavigationAnimations.mixedEnter(durationMillis)
        NavigationAnimationType.BOTTOM_SHEET -> NavigationAnimations.slideInFromBottom(durationMillis)
        NavigationAnimationType.NONE -> { { EnterTransition.None } }
    }
}

/**
 * 根据动画类型获取返回退出动画
 */
fun NavigationAnimationType.toPopExitTransition(
    durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition {
    return when (this) {
        NavigationAnimationType.SLIDE_HORIZONTAL -> NavigationAnimations.slideOutToRight(durationMillis)
        NavigationAnimationType.SLIDE_VERTICAL -> NavigationAnimations.slideOutToBottom(durationMillis)
        NavigationAnimationType.FADE -> NavigationAnimations.fadeOut(durationMillis)
        NavigationAnimationType.SCALE -> NavigationAnimations.scaleOut(durationMillis)
        NavigationAnimationType.MIXED -> NavigationAnimations.mixedExit(durationMillis)
        NavigationAnimationType.BOTTOM_SHEET -> NavigationAnimations.bottomSheetExit(durationMillis)
        NavigationAnimationType.NONE -> { { ExitTransition.None } }
    }
}

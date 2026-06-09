package com.huaying.xstz.ui.animation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.navigation.NavBackStackEntry
import com.huaying.xstz.ui.animation.AnimationConstants

/**
 * 统一导航动画系统
 *
 * 设计原则：
 * 1. 层级感知 — Tab同级切换轻量，子页面进入纯滑动覆盖
 * 2. 方向一致 — 前进向右/向上，返回向左/向下
 * 3. 节奏统一 — 全部使用 tween，Tab切换280ms，子页面300ms，弹窗350ms
 * 4. 底层不动 — 子页面进入/退出时底层页面不做动画，避免重叠冲突
 */
object NavigationAnimations {

    // ==================== 1. Tab同级切换 ====================
    // 轻量方向感知滑动 + 淡入淡出（不使用 scaleIn/scaleOut，避免"从中心展开"视觉异常）

    /** Tab切换 - 向右前进时，新页进入 */
    fun tabEnterForward(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = tween(AnimationConstants.Duration.TAB_SWITCH, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(AnimationConstants.Duration.TAB_SWITCH)
        )
    }

    /** Tab切换 - 向右前进时，旧页退出 */
    fun tabExitForward(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -it / 4 },
            animationSpec = tween(AnimationConstants.Duration.TAB_SWITCH, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(AnimationConstants.Duration.TAB_SWITCH)
        )
    }

    /** Tab切换 - 向左后退时，新页进入 */
    fun tabEnterBackward(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -it / 4 },
            animationSpec = tween(AnimationConstants.Duration.TAB_SWITCH, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(AnimationConstants.Duration.TAB_SWITCH)
        )
    }

    /** Tab切换 - 向左后退时，旧页退出 */
    fun tabExitBackward(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it / 4 },
            animationSpec = tween(AnimationConstants.Duration.TAB_SWITCH, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(AnimationConstants.Duration.TAB_SWITCH)
        )
    }

    /** Tab切换 - 无方向时（如从子页返回），纯淡入 */
    fun tabEnterResume(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(AnimationConstants.Duration.TAB_RESUME))
    }

    /** Tab切换 - 无方向时，纯淡出 */
    fun tabExitResume(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(AnimationConstants.Duration.TAB_RESUME))
    }

    // ==================== 2. 子页面Push/Pop ====================
    // 纯滑动覆盖，底层页面不做动画

    /** 子页面Push - 新页从右侧滑入 */
    fun pushEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(AnimationConstants.Duration.NORMAL, easing = FastOutSlowInEasing)
        )
    }

    /** 子页面Pop - 当前页向右滑出 */
    fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(AnimationConstants.Duration.NORMAL, easing = FastOutSlowInEasing)
        )
    }

    // ==================== 3. 底部弹窗 ====================
    // 从底部滑入，底层页面不做动画

    /** 底部弹窗进入 */
    fun sheetEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = AnimationConstants.Duration.SHEET_ENTER,
                easing = AnimationConstants.Easing.Decelerate
            )
        ) + fadeIn(
            animationSpec = tween(AnimationConstants.Duration.SHEET_ENTER / 2)
        )
    }

    /** 底部弹窗退出 */
    fun sheetExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(
                durationMillis = AnimationConstants.Duration.SHEET_EXIT,
                easing = AnimationConstants.Easing.Accelerate
            )
        ) + fadeOut(
            animationSpec = tween(AnimationConstants.Duration.SHEET_EXIT)
        )
    }
}

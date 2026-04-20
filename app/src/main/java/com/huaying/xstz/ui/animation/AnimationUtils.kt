package com.huaying.xstz.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 动画工具类
 * 提供各种常用的动画效果和修饰符
 */
object AnimationUtils {

    // ==================== 淡入淡出动画 ====================

    /**
     * 淡入动画
     */
    fun fadeIn(
        durationMillis: Int = AnimationConstants.Duration.NORMAL,
        delayMillis: Int = 0
    ): EnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = AnimationConstants.Easing.Decelerate
        )
    )

    /**
     * 淡出动画
     */
    fun fadeOut(
        durationMillis: Int = AnimationConstants.Duration.NORMAL
    ): ExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Accelerate
        )
    )

    // ==================== 缩放动画 ====================

    /**
     * 缩放进入动画
     */
    fun scaleIn(
        initialScale: Float = AnimationConstants.Scale.SMALL,
        durationMillis: Int = AnimationConstants.Duration.NORMAL
    ): EnterTransition = scaleIn(
        initialScale = initialScale,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Spring
        )
    )

    /**
     * 缩放退出动画
     */
    fun scaleOut(
        targetScale: Float = AnimationConstants.Scale.SMALL,
        durationMillis: Int = AnimationConstants.Duration.NORMAL
    ): ExitTransition = scaleOut(
        targetScale = targetScale,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Accelerate
        )
    )

    // ==================== 滑动动画 ====================

    /**
     * 从左侧滑入
     */
    fun slideInFromLeft(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): EnterTransition = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Decelerate
        )
    )

    /**
     * 从右侧滑入
     */
    fun slideInFromRight(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): EnterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Decelerate
        )
    )

    /**
     * 向左侧滑出
     */
    fun slideOutToLeft(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): ExitTransition = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Accelerate
        )
    )

    /**
     * 向右侧滑出
     */
    fun slideOutToRight(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): ExitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Accelerate
        )
    )

    /**
     * 从底部滑入
     */
    fun slideInFromBottom(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): EnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Decelerate
        )
    )

    /**
     * 向底部滑出
     */
    fun slideOutToBottom(
        durationMillis: Int = AnimationConstants.Duration.PAGE_TRANSITION
    ): ExitTransition = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Accelerate
        )
    )

    // ==================== 组合动画 ====================

    /**
     * 页面进入动画 - 淡入 + 从右滑入
     */
    fun pageEnter(): EnterTransition =
        slideInFromRight() + fadeIn()

    /**
     * 页面退出动画 - 淡出 + 向左滑出
     */
    fun pageExit(): ExitTransition =
        slideOutToLeft() + fadeOut()

    /**
     * 页面返回进入动画 - 淡入 + 从左滑入
     */
    fun pagePopEnter(): EnterTransition =
        slideInFromLeft() + fadeIn()

    /**
     * 页面返回退出动画 - 淡出 + 向右滑出
     */
    fun pagePopExit(): ExitTransition =
        slideOutToRight() + fadeOut()

    /**
     * 底部弹窗进入动画
     */
    fun bottomSheetEnter(): EnterTransition =
        slideInFromBottom() + fadeIn()

    /**
     * 底部弹窗退出动画
     */
    fun bottomSheetExit(): ExitTransition =
        slideOutToBottom() + fadeOut()

    /**
     * 对话框进入动画 - 缩放 + 淡入
     */
    fun dialogEnter(): EnterTransition =
        scaleIn(initialScale = 0.8f) + fadeIn()

    /**
     * 对话框退出动画 - 缩放 + 淡出
     */
    fun dialogExit(): ExitTransition =
        scaleOut(targetScale = 0.8f) + fadeOut()

    // ==================== 列表动画 ====================

    /**
     * 列表项进入动画
     * @param index 列表项索引
     * @param staggerDelay 每个项之间的延迟
     */
    fun <T> listItemEnter(
        index: Int,
        staggerDelay: Int = AnimationConstants.Duration.LIST_ITEM_STAGGER
    ): EnterTransition {
        val delay = index * staggerDelay
        return fadeIn(
            animationSpec = tween(
                durationMillis = AnimationConstants.Duration.NORMAL,
                delayMillis = delay,
                easing = AnimationConstants.Easing.Decelerate
            )
        ) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(
                durationMillis = AnimationConstants.Duration.NORMAL,
                delayMillis = delay,
                easing = AnimationConstants.Easing.Decelerate
            )
        )
    }
}

/**
 * 列表项动画修饰符
 * 为列表项添加渐入和位移动画
 */
fun Modifier.animateListItem(
    index: Int,
    totalItems: Int = 20,
    staggerDelay: Int = AnimationConstants.Duration.LIST_ITEM_STAGGER
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "animateListItem"
        value = index
    }
) {
    val delay = (index.coerceAtMost(totalItems)) * staggerDelay

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = AnimationConstants.Duration.NORMAL,
            easing = AnimationConstants.Easing.Decelerate
        ),
        label = "list_item_alpha"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(
            durationMillis = AnimationConstants.Duration.NORMAL,
            easing = AnimationConstants.Easing.Decelerate
        ),
        label = "list_item_offset"
    )

    this
        .alpha(alpha)
        .offset(y = offsetY.dp)
}

/**
 * 脉冲动画效果
 * 用于强调某个元素
 */
@Composable
fun pulseAnimation(
    targetValue: Float = 1.1f,
    durationMillis: Int = 1000
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    return infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis / 2, easing = AnimationConstants.Easing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    ).value
}

/**
 * 闪烁动画效果
 * 用于加载或等待状态
 */
@Composable
fun shimmerAnimation(
    durationMillis: Int = 1500
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    ).value
}

/**
 * 呼吸动画效果
 * 柔和的缩放动画
 */
@Composable
fun breatheAnimation(
    minScale: Float = 0.98f,
    maxScale: Float = 1.02f,
    durationMillis: Int = 2000
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    return infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = AnimationConstants.Easing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    ).value
}

/**
 * 弹跳进入动画状态
 */
@Composable
fun bounceInAnimationState(
    delayMillis: Int = 0
): State<Float> {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        started = true
    }

    return animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce_in"
    )
}

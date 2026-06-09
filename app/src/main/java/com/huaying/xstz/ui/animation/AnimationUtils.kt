package com.huaying.xstz.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

package com.huaying.xstz.ui.animation.interaction

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huaying.xstz.ui.animation.AnimationConstants
import com.huaying.xstz.ui.theme.BrandBlue

/**
 * 按钮点击动画修饰符
 * 提供按压缩放效果
 */
fun Modifier.clickableScale(
    scale: Float = 0.95f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "clickableScale"
        properties["scale"] = scale
        properties["enabled"] = enabled
    }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed && enabled) scale else 1f,
        animationSpec = tween(
            durationMillis = AnimationConstants.Duration.FAST,
            easing = AnimationConstants.Easing.Standard
        ),
        label = "clickable_scale"
    )

    this
        .scale(scaleAnim)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * 弹性按钮动画修饰符
 * 带有弹性回弹效果
 */
fun Modifier.bouncyClickable(
    onClick: () -> Unit
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "bouncyClickable"
    }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "bouncy_scale"
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * 脉冲按钮
 * 带有持续脉冲动画的按钮
 */
@Composable
fun PulseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulseEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_button")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulseEnabled) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = AnimationConstants.Easing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = if (pulseEnabled) 1f else 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = AnimationConstants.Easing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .scale(pulseScale)
            .graphicsLayer { this.alpha = pulseAlpha }
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 带动画的按钮
 * 包含按压、释放的完整动画效果
 */
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animationType: ButtonAnimationType = ButtonAnimationType.SCALE,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            isPressed -> 0.95f
            else -> 1f
        },
        animationSpec = when (animationType) {
            ButtonAnimationType.SCALE -> tween(100)
            ButtonAnimationType.BOUNCY -> spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
            )
            ButtonAnimationType.SMOOTH -> tween(150, easing = AnimationConstants.Easing.Standard)
        },
        label = "button_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(150),
        label = "button_alpha"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 按钮动画类型
 */
enum class ButtonAnimationType {
    SCALE,
    BOUNCY,
    SMOOTH
}

/**
 * 图标按钮动画
 * 点击时带有旋转效果
 */
@Composable
fun AnimatedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 180f,
    content: @Composable () -> Unit
) {
    var isClicked by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isClicked) rotationDegrees else 0f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "icon_rotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isClicked) 0.9f else 1f,
        animationSpec = tween(100),
        label = "icon_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { rotationZ = rotation }
            .clickable {
                isClicked = !isClicked
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 卡片翻转动画
 */
@Composable
fun FlipCard(
    isFlipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "flip_rotation"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
    ) {
        if (rotation <= 90f) {
            Box(
                modifier = Modifier.graphicsLayer { alpha = 1f - rotation / 90f }
            ) {
                front()
            }
        } else {
            Box(
                modifier = Modifier.graphicsLayer {
                    rotationY = 180f
                    alpha = (rotation - 90f) / 90f
                }
            ) {
                back()
            }
        }
    }
}

/**
 * 滑动删除动画
 */
@Composable
fun SwipeToDeleteContainer(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isDeleted by remember { mutableStateOf(false) }

    val offsetX by animateFloatAsState(
        targetValue = if (isDeleted) -1000f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        finishedListener = {
            if (isDeleted) onDelete()
        },
        label = "swipe_offset"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isDeleted) 0f else 1f,
        animationSpec = tween(300),
        label = "swipe_alpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX
                this.alpha = alpha
            }
    ) {
        content()
    }
}

/**
 * 震动动画效果
 */
@Composable
fun ShakeAnimation(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val offsetX by animateFloatAsState(
        targetValue = 0f,
        animationSpec = if (trigger) {
            keyframes {
                durationMillis = 500
                0f at 0
                (-10f) at 50
                10f at 100
                (-10f) at 150
                10f at 200
                (-5f) at 250
                5f at 300
                0f at 350
            }
        } else {
            tween(0)
        },
        label = "shake_offset"
    )

    Box(
        modifier = modifier.graphicsLayer { translationX = offsetX }
    ) {
        content()
    }
}

/**
 * 呼吸动画修饰符
 * 用于强调某个元素
 */
fun Modifier.breatheAnimation(
    minScale: Float = 0.98f,
    maxScale: Float = 1.02f,
    durationMillis: Int = 2000
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "breatheAnimation"
    }
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")

    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = AnimationConstants.Easing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    )

    this.scale(scale)
}

/**
 * 悬浮动画修饰符
 * 上下浮动效果
 */
fun Modifier.floatAnimation(
    offsetY: Float = 10f,
    durationMillis: Int = 2000
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "floatAnimation"
    }
) {
    val infiniteTransition = rememberInfiniteTransition(label = "float")

    val offset by infiniteTransition.animateFloat(
        initialValue = -offsetY,
        targetValue = offsetY,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = AnimationConstants.Easing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    this.graphicsLayer { translationY = offset }
}

/**
 * 发光效果修饰符
 */
fun Modifier.glowEffect(
    color: Color = BrandBlue,
    alpha: Float = 0.3f,
    radius: Dp = 20.dp
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "glowEffect"
    }
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = alpha * 0.5f,
        targetValue = alpha,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = AnimationConstants.Easing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    this.drawBehind {
        drawCircle(
            color = color.copy(alpha = glowAlpha),
            radius = radius.toPx()
        )
    }
}

package com.huaying.xstz.ui.animation.loading

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huaying.xstz.ui.animation.AnimationConstants
import com.huaying.xstz.ui.theme.BrandBlue
import kotlinx.coroutines.delay

/**
 * 加载状态类型
 */
sealed class LoadingState {
    data object Idle : LoadingState()
    data object Loading : LoadingState()
    data class Progress(val progress: Float, val message: String? = null) : LoadingState()
    data class Success(val message: String? = null) : LoadingState()
    data class Error(val message: String, val canRetry: Boolean = true) : LoadingState()
}

/**
 * 带动画的内容加载包装器
 * 根据加载状态显示不同的动画效果
 */
@Composable
fun AnimatedContentLoader(
    state: LoadingState,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 主内容
        AnimatedVisibility(
            visible = state is LoadingState.Idle || state is LoadingState.Success,
            enter = fadeIn(animationSpec = tween(300)) +
                    slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(300, easing = AnimationConstants.Easing.Decelerate)
                    ),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            content()
        }

        // 加载状态
        AnimatedVisibility(
            visible = state is LoadingState.Loading || state is LoadingState.Progress,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            when (state) {
                is LoadingState.Progress -> {
                    ProgressLoadingIndicator(
                        progress = state.progress,
                        message = state.message
                    )
                }
                else -> {
                    SkeletonLoadingScreen()
                }
            }
        }

        // 错误状态
        AnimatedVisibility(
            visible = state is LoadingState.Error,
            enter = fadeIn(animationSpec = tween(300)) +
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    ),
            exit = fadeOut(animationSpec = tween(200)) +
                    scaleOut(targetScale = 0.9f)
        ) {
            if (state is LoadingState.Error) {
                ErrorScreen(
                    message = state.message,
                    canRetry = state.canRetry,
                    onRetry = onRetry
                )
            }
        }
    }
}

/**
 * 骨架屏加载效果
 * 模拟内容加载时的占位效果
 */
@Composable
fun SkeletonLoadingScreen(
    itemCount: Int = 5,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题骨架
        SkeletonItem(width = 200.dp, height = 24.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 内容骨架
        repeat(itemCount) { index ->
            SkeletonCard(index = index)
        }
    }
}

/**
 * 骨架卡片
 */
@Composable
private fun SkeletonCard(index: Int) {
    val shimmerProgress = rememberShimmerAnimation()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .shimmerEffect(shimmerProgress)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标占位
            SkeletonItem(
                width = 48.dp,
                height = 48.dp,
                shape = CircleShape
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonItem(width = 120.dp, height = 16.dp)
                SkeletonItem(width = 80.dp, height = 12.dp)
            }

            SkeletonItem(width = 60.dp, height = 20.dp)
        }
    }
}

/**
 * 骨架项
 */
@Composable
private fun SkeletonItem(
    width: Dp,
    height: Dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp)
) {
    val shimmerProgress = rememberShimmerAnimation()

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            .shimmerEffect(shimmerProgress)
    )
}

/**
 * 闪烁动画
 */
@Composable
private fun rememberShimmerAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    ).value
}

/**
 * 闪烁效果修饰符
 */
private fun Modifier.shimmerEffect(progress: Float): Modifier = composed {
    val shimmerColors = listOf(
        Color.Transparent,
        Color.White.copy(alpha = 0.3f),
        Color.Transparent
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(progress * 1000f, 0f),
            end = Offset(progress * 1000f + 200f, 0f)
        )
    )
}

/**
 * 进度加载指示器
 */
@Composable
private fun ProgressLoadingIndicator(
    progress: Float,
    message: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 圆形进度
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(80.dp),
                strokeWidth = 6.dp,
                color = BrandBlue,
                trackColor = BrandBlue.copy(alpha = 0.2f)
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 线性进度条
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = BrandBlue,
            trackColor = BrandBlue.copy(alpha = 0.2f)
        )

        if (message != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 错误页面
 */
@Composable
private fun ErrorScreen(
    message: String,
    canRetry: Boolean,
    onRetry: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 错误图标动画
        val scale by rememberInfiniteTransition(label = "error_pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "error_scale"
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .background(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "加载失败",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        if (canRetry && onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.material3.Button(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("重新加载")
            }
        }
    }
}

/**
 * 圆形进度加载动画
 */
@Composable
fun CircularLoadingAnimation(
    size: Dp = 48.dp,
    color: Color = BrandBlue,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "circular_loading")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 4.dp,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

/**
 * 点状加载动画
 */
@Composable
fun DotsLoadingAnimation(
    dotCount: Int = 3,
    dotSize: Dp = 8.dp,
    color: Color = BrandBlue,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val delay = index * 150

            val scale by rememberInfiniteTransition(label = "dot_$index").animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_scale_$index"
            )

            val alpha by rememberInfiniteTransition(label = "dot_alpha_$index").animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha_anim_$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scale)
                    .alpha(alpha)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * 脉冲加载动画
 */
@Composable
fun PulseLoadingAnimation(
    size: Dp = 60.dp,
    color: Color = BrandBlue,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_loading")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 外圈
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .alpha(alpha)
                .background(color.copy(alpha = 0.3f), CircleShape)
        )

        // 内圈
        Box(
            modifier = Modifier
                .size(size / 2)
                .background(color, CircleShape)
        )
    }
}

/**
 * 内容渐显动画
 * 页面内容加载完成后的渐显效果
 */
@Composable
fun ContentFadeInAnimation(
    delayMillis: Int = 0,
    durationMillis: Int = AnimationConstants.Duration.CONTENT_LOAD,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Decelerate
        ),
        label = "content_fade"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = AnimationConstants.Easing.Decelerate
        ),
        label = "content_offset"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .graphicsLayer {
                translationY = offsetY
            }
    ) {
        content()
    }
}

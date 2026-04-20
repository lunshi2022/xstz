package com.huaying.xstz.ui.animation.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.R
import com.huaying.xstz.ui.animation.AnimationConstants
import com.huaying.xstz.ui.animation.SpringEasing
import com.huaying.xstz.ui.theme.BrandBlue
import kotlinx.coroutines.delay

/**
 * 启动屏状态
 */
sealed class SplashState {
    data object Loading : SplashState()
    data object LogoAnimation : SplashState()
    data object ContentReveal : SplashState()
    data object Finished : SplashState()
}

/**
 * 高级启动屏组件
 * 包含 logo 动画、进度指示和内容渐显效果
 */
@Composable
fun AnimatedSplashScreen(
    onSplashFinished: () -> Unit,
    darkTheme: Boolean = false,
    minimumDisplayTime: Long = 2000L
) {
    var splashState by remember { mutableStateOf<SplashState>(SplashState.Loading) }
    val startTime = remember { System.currentTimeMillis() }

    // 控制启动屏流程
    LaunchedEffect(Unit) {
        // 第一阶段：Logo 缩放动画
        splashState = SplashState.LogoAnimation
        delay(800)

        // 第二阶段：内容渐显
        splashState = SplashState.ContentReveal
        delay(600)

        // 确保最少显示时间
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = (minimumDisplayTime - elapsed).coerceAtLeast(0)
        delay(remaining)

        splashState = SplashState.Finished
        delay(300) // 等待退出动画
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (darkTheme) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1a1a2e),
                            Color(0xFF16213e)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE6F7FF),
                            Color(0xFFFFFFFF)
                        )
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (splashState) {
            is SplashState.Loading -> {
                SplashLogo(darkTheme = darkTheme, animationPhase = 0)
            }
            is SplashState.LogoAnimation -> {
                SplashLogo(darkTheme = darkTheme, animationPhase = 1)
            }
            is SplashState.ContentReveal -> {
                SplashContent(darkTheme = darkTheme)
            }
            is SplashState.Finished -> {
                SplashExit(darkTheme = darkTheme)
            }
            else -> {}
        }
    }
}

/**
 * Logo 动画组件
 * @param animationPhase 0=初始, 1=放大, 2=完成
 */
@Composable
private fun SplashLogo(
    darkTheme: Boolean,
    animationPhase: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")

    // Logo 缩放动画
    val scale by animateFloatAsState(
        targetValue = when (animationPhase) {
            0 -> 0.6f
            1 -> 1.0f
            else -> 1.1f
        },
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "logo_scale"
    )

    // Logo 旋转动画（仅在第一阶段）
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_rotation"
    )

    // 透明度动画
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500),
        label = "logo_alpha"
    )

    // 圆环动画
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        // 外圈波纹效果
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(ringScale)
                .alpha(ringAlpha)
                .background(
                    color = BrandBlue.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )

        // 中间圈
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BrandBlue,
                            BrandBlue.copy(alpha = 0.8f)
                        )
                    ),
                    shape = CircleShape
                )
                .graphicsLayer {
                    rotationZ = if (animationPhase == 0) rotation else 0f
                },
            contentAlignment = Alignment.Center
        ) {
            // Logo 图标
            Icon(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(50.dp),
                tint = Color.White
            )
        }

        // 装饰圆点
        if (animationPhase >= 1) {
            DecorationDots()
        }
    }
}

/**
 * 装饰圆点动画
 */
@Composable
private fun DecorationDots() {
    val dotCount = 6
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    dotCount.let { count ->
        for (i in 0 until count) {
            val angle = (360f / count) * i
            val delay = i * 100

            val dotScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(
                    500,
                    delayMillis = delay,
                    easing = SpringEasing()
                ),
                label = "dot_scale_$i"
            )

            val dotOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, delayMillis = delay * 2),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_offset_$i"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(dotScale)
                    .offset(
                        x = (kotlin.math.cos(Math.toRadians(angle.toDouble())) * (70 + dotOffset)).dp,
                        y = (kotlin.math.sin(Math.toRadians(angle.toDouble())) * (70 + dotOffset)).dp
                    )
                    .background(BrandBlue.copy(alpha = 0.6f), CircleShape)
            )
        }
    }
}

/**
 * 启动屏内容（应用名称和副标题）
 */
@Composable
private fun SplashContent(darkTheme: Boolean) {
    val textColor = if (darkTheme) Color.White else Color(0xFF1D1D1F)

    // 标题动画
    val titleOffset by animateFloatAsState(
        targetValue = 0f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "title_offset"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "title_alpha"
    )

    // 副标题动画
    val subtitleOffset by animateFloatAsState(
        targetValue = 0f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "subtitle_offset"
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "subtitle_alpha"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        SplashLogo(darkTheme = darkTheme, animationPhase = 2)

        Spacer(modifier = Modifier.height(32.dp))

        // 应用名称
        Text(
            text = "小书投资",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier
                .offset(y = titleOffset.dp)
                .alpha(titleAlpha)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 副标题
        Text(
            text = "智能理财助手",
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier
                .offset(y = subtitleOffset.dp)
                .alpha(subtitleAlpha)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 加载进度指示器
        LoadingIndicator(darkTheme = darkTheme)
    }
}

/**
 * 加载进度指示器
 */
@Composable
private fun LoadingIndicator(darkTheme: Boolean) {
    val dotCount = 3
    val color = if (darkTheme) Color.White else BrandBlue

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "loading_$index")
            val delay = index * 150

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "loading_scale_$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "loading_alpha_$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .background(color.copy(alpha = 0.6f), CircleShape)
            )
        }
    }
}

/**
 * 启动屏退出动画
 */
@Composable
private fun SplashExit(darkTheme: Boolean) {
    val exitAlpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(300),
        label = "exit_alpha"
    )

    val exitScale by animateFloatAsState(
        targetValue = 1.1f,
        animationSpec = tween(300),
        label = "exit_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(exitScale)
            .alpha(exitAlpha)
            .background(
                if (darkTheme) Color(0xFF1a1a2e) else Color(0xFFE6F7FF)
            ),
        contentAlignment = Alignment.Center
    ) {
        SplashContent(darkTheme = darkTheme)
    }
}

/**
 * 简化的启动屏包装器
 * 用于替换系统默认的 SplashScreen
 */
@Composable
fun SplashScreenWrapper(
    isLoading: Boolean,
    onLoadingComplete: () -> Unit,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            delay(500) // 额外延迟确保流畅过渡
            showSplash = false
            onLoadingComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容
        content()

        // 启动屏覆盖层
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            AnimatedSplashScreen(
                onSplashFinished = { showSplash = false },
                darkTheme = darkTheme
            )
        }
    }
}

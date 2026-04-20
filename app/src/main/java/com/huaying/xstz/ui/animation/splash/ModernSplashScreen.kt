package com.huaying.xstz.ui.animation.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.ui.theme.BrandBlue
import kotlinx.coroutines.delay

/**
 * 现代风格的启动屏动画
 * 参考支付宝、微信等成熟 APP 的设计方案
 * 特点：简洁、流畅、品牌感强
 */
@Composable
fun ModernSplashScreen(
    onSplashFinished: () -> Unit,
    darkTheme: Boolean = false,
    minimumDisplayTime: Long = 2500L
) {
    var animationState by remember { mutableIntStateOf(0) }
    val startTime = remember { System.currentTimeMillis() }

    // 动画流程控制
    LaunchedEffect(Unit) {
        // 阶段 0: Logo 出现
        animationState = 0
        delay(600)

        // 阶段 1: Logo 稳定，文字出现
        animationState = 1
        delay(800)

        // 阶段 2: 加载进度
        animationState = 2
        delay(800)

        // 确保最少显示时间
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = (minimumDisplayTime - elapsed).coerceAtLeast(0)
        delay(remaining)

        // 阶段 3: 淡出
        animationState = 3
        delay(400)
        onSplashFinished()
    }

    // 背景渐变
    val backgroundBrush = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F1419),
                Color(0xFF1A1F26)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF8FAFC),
                Color(0xFFFFFFFF)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        when (animationState) {
            0 -> LogoAppearPhase(darkTheme)
            1 -> ContentRevealPhase(darkTheme)
            2 -> LoadingPhase(darkTheme)
            3 -> FadeOutPhase(darkTheme)
        }
    }
}

/**
 * Logo 出现阶段 - 从中心放大并淡入
 */
@Composable
private fun LogoAppearPhase(darkTheme: Boolean) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "logo_appear_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "logo_appear_alpha"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        LogoIcon(darkTheme)
    }
}

/**
 * 内容渐显阶段 - Logo 稳定，文字从下方滑入
 */
@Composable
private fun ContentRevealPhase(darkTheme: Boolean) {
    val textColor = if (darkTheme) Color.White else Color(0xFF1D1D1F)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo 保持显示
        LogoIcon(darkTheme)

        Spacer(modifier = Modifier.height(24.dp))

        // 应用名称 - 从下方滑入
        val titleOffset by animateFloatAsState(
            targetValue = 0f,
            animationSpec = tween(500, easing = EaseOutQuart),
            label = "title_offset"
        )

        val titleAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(400),
            label = "title_alpha"
        )

        Text(
            text = "小书投资",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier
                .graphicsLayer { translationY = titleOffset }
                .alpha(titleAlpha)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 副标题 - 延迟出现
        val subtitleAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(400, delayMillis = 200),
            label = "subtitle_alpha"
        )

        Text(
            text = "智能理财助手",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.alpha(subtitleAlpha)
        )
    }
}

/**
 * 加载阶段 - 显示进度条
 */
@Composable
private fun LoadingPhase(darkTheme: Boolean) {
    val textColor = if (darkTheme) Color.White else Color(0xFF1D1D1F)
    val progressColor = BrandBlue

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo 和文字
        LogoIcon(darkTheme)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "小书投资",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 进度条动画
        val progress by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(800, easing = EaseInOutCubic),
            label = "progress"
        )

        Box(
            modifier = Modifier
                .width(120.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (darkTheme) Color(0xFF2A2A2A) else Color(0xFFE5E7EB))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(progressColor)
            )
        }
    }
}

/**
 * 淡出阶段
 */
@Composable
private fun FadeOutPhase(darkTheme: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(400),
        label = "fade_out_alpha"
    )

    val scale by animateFloatAsState(
        targetValue = 1.05f,
        animationSpec = tween(400),
        label = "fade_out_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        LoadingPhase(darkTheme)
    }
}

/**
 * Logo 图标组件
 */
@Composable
private fun LogoIcon(darkTheme: Boolean) {
    val iconBackground = if (darkTheme) {
        Brush.linearGradient(
            colors = listOf(
                BrandBlue.copy(alpha = 0.9f),
                BrandBlue
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                BrandBlue,
                BrandBlue.copy(alpha = 0.9f)
            )
        )
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(iconBackground),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.TrendingUp,
            contentDescription = "Logo",
            modifier = Modifier.size(40.dp),
            tint = Color.White
        )
    }
}

// 缓动函数
private val EaseOutQuart: Easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
private val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

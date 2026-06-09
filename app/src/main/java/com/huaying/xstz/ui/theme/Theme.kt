package com.huaying.xstz.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import androidx.compose.ui.platform.LocalContext

/**
 * 确保动态颜色方案中 surface 与 background 有足够层次区分。
 *
 * 策略：保留动态配色的色调，仅在 surface 与 background 过于接近时微调亮度，
 * 避免硬编码覆盖导致动态主题失效。
 */
private fun ColorScheme.ensureSurfaceHierarchy(darkTheme: Boolean): ColorScheme {
    val surfaceLum = surface.luminance()
    val bgLum = background.luminance()

    // 判断 surface 与 background 是否过于接近（亮度差不足 0.03）
    val needsFix = if (darkTheme) {
        surfaceLum - bgLum < 0.03f
    } else {
        bgLum - surfaceLum < 0.03f
    }

    if (!needsFix) return this

    return if (darkTheme) {
        copy(background = Color(0xFF000000))
    } else {
        copy(background = Color(0xFFF5F5F7))
    }
}

private fun Color.luminance(): Float {
    // sRGB 相对亮度公式
    val r = red.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    val g = green.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    val b = blue.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
}

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F7FF),
    onPrimaryContainer = BrandBlue,
    
    secondary = Color(0xFF039BE5),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB3E5FC),
    onSecondaryContainer = Color(0xFF01579B),
    
    tertiary = Color(0xFF00ACC1),
    onTertiary = Color(0xFFFFFFFF),
    
    error = DangerRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = LightDangerBg,
    onErrorContainer = DangerRed,
    
    background = LightBackground,
    onBackground = LightTextPrimary,
    
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceSecondary,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = BrandBlue,
    
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkTextPrimary,
    
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE8E8E8),
    
    scrim = Color(0xFF000000)
)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color(0xFF000000),
    primaryContainer = DarkCodeBg,
    onPrimaryContainer = BrandBlue,
    
    secondary = Color(0xFF81D4FA),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0277BD),
    onSecondaryContainer = Color(0xFFE1F5FE),
    
    tertiary = Color(0xFF4DD0E1),
    onTertiary = Color(0xFF000000),
    
    error = DangerRed,
    onError = Color(0xFF000000),
    errorContainer = DarkDangerBg,
    onErrorContainer = DangerRed,
    
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceSecondary,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = BrandBlue,
    
    inverseSurface = LightSurface,
    inverseOnSurface = LightTextPrimary,
    
    outline = Color(0xFF444444),
    outlineVariant = Color(0xFF333333),
    
    scrim = Color(0xFF000000)
)

@Composable
private fun Color.animation() = animateColorAsState(
    targetValue = this,
    animationSpec = tween(durationMillis = 500),
    label = "animation"
).value

@Composable
private fun ColorScheme.animation(): ColorScheme {
    return copy(
        primary = primary.animation(),
        onPrimary = onPrimary.animation(),
        primaryContainer = primaryContainer.animation(),
        onPrimaryContainer = onPrimaryContainer.animation(),
        inversePrimary = inversePrimary.animation(),
        secondary = secondary.animation(),
        onSecondary = onSecondary.animation(),
        secondaryContainer = secondaryContainer.animation(),
        onSecondaryContainer = onSecondaryContainer.animation(),
        tertiary = tertiary.animation(),
        onTertiary = onTertiary.animation(),
        tertiaryContainer = tertiaryContainer.animation(),
        onTertiaryContainer = onTertiaryContainer.animation(),
        background = background.animation(),
        onBackground = onBackground.animation(),
        surface = surface.animation(),
        onSurface = onSurface.animation(),
        surfaceVariant = surfaceVariant.animation(),
        onSurfaceVariant = onSurfaceVariant.animation(),
        surfaceTint = surfaceTint.animation(),
        inverseSurface = inverseSurface.animation(),
        inverseOnSurface = inverseOnSurface.animation(),
        error = error.animation(),
        onError = onError.animation(),
        errorContainer = errorContainer.animation(),
        onErrorContainer = onErrorContainer.animation(),
        outline = outline.animation(),
        outlineVariant = outlineVariant.animation(),
        scrim = scrim.animation(),
    )
}

@Composable
fun InvestmentManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColorEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    // 根据dynamicColorEnabled决定是否使用系统动态颜色
    val targetColorScheme = if (dynamicColorEnabled) {
        val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        // 动态颜色方案强制保障 surface/background 层次区分
        dynamicScheme.ensureSurfaceHierarchy(darkTheme)
            // 深色模式下强制 primary 为品牌蓝，避免动态配色的 primary 过暗导致光标不可见
            .let { if (darkTheme) it.copy(primary = BrandBlue) else it }
    } else {
        if (darkTheme) DarkColors else LightColors
    }

    // 使用完整的颜色方案动画，避免部分颜色不同步导致的闪烁
    val animatedColorScheme = targetColorScheme.animation()

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content
    )
}

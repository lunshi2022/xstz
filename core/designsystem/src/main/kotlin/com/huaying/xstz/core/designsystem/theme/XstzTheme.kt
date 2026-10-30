package com.huaying.xstz.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题语义色。
 *
 * 不直接使用 Material3 的 ColorScheme，因为 Material 的命名（primary/secondary/tertiary）
 * 表达的是"品牌层级"，而这里需要的是"材质层级"——玻璃、玻璃上的文字、玻璃的描边。
 * 强行映射会让每个组件的调用处都要脑内转换一次，反而更容易用错。
 */
@Immutable
data class XstzPalette(
    val isDark: Boolean,
    val background: Color,
    val backgroundElevated: Color,

    /** 玻璃底 */
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassStroke: Color,
    val glassShadow: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,

    val accent: Color,
    val up: Color,
    val down: Color,
    val flat: Color,

    val mesh: List<Color>,
) {
    /** 涨跌色：A 股惯例，红涨绿跌 */
    fun change(value: Double, epsilon: Double = 1e-9): Color = when {
        value > epsilon -> up
        value < -epsilon -> down
        else -> flat
    }

    fun asset(type: com.huaying.xstz.core.model.AssetType): Color = when (type) {
        com.huaying.xstz.core.model.AssetType.STOCK -> XstzColors.Stock
        com.huaying.xstz.core.model.AssetType.BOND -> XstzColors.Bond
        com.huaying.xstz.core.model.AssetType.COMMODITY -> XstzColors.Commodity
        com.huaying.xstz.core.model.AssetType.CASH -> XstzColors.Cash
    }
}

val LightPalette = XstzPalette(
    isDark = false,
    background = XstzColors.Light.background,
    backgroundElevated = XstzColors.Light.backgroundElevated,
    glassFill = XstzColors.Light.glassFill,
    glassFillStrong = XstzColors.Light.glassFillStrong,
    glassStroke = XstzColors.Light.glassStroke,
    glassShadow = XstzColors.Light.glassShadow,
    textPrimary = XstzColors.Light.textPrimary,
    textSecondary = XstzColors.Light.textSecondary,
    textTertiary = XstzColors.Light.textTertiary,
    divider = XstzColors.Light.divider,
    accent = XstzColors.Accent,
    up = XstzColors.Up,
    down = XstzColors.Down,
    // 平盘色用次级文字色而非固定的 #8A93A6：
    // 截图里 "+¥0.00" 用那个灰在中灰玻璃上完全糊掉，看不出数字
    flat = XstzColors.Light.textTertiary,
    mesh = XstzColors.meshLight,
)

val DarkPalette = XstzPalette(
    isDark = true,
    background = XstzColors.Dark.background,
    backgroundElevated = XstzColors.Dark.backgroundElevated,
    glassFill = XstzColors.Dark.glassFill,
    glassFillStrong = XstzColors.Dark.glassFillStrong,
    glassStroke = XstzColors.Dark.glassStroke,
    glassShadow = XstzColors.Dark.glassShadow,
    textPrimary = XstzColors.Dark.textPrimary,
    textSecondary = XstzColors.Dark.textSecondary,
    textTertiary = XstzColors.Dark.textTertiary,
    divider = XstzColors.Dark.divider,
    accent = XstzColors.AccentSoft,
    up = XstzColors.Up,
    down = XstzColors.Down,
    flat = XstzColors.Dark.textTertiary,
    mesh = XstzColors.meshDark,
)

/**
 * 无障碍偏好，直接对应 iOS 的两个系统开关：
 *   * Reduce Transparency → 关掉实时模糊，改用高不透明度纯色（对比度更可控）
 *   * Reduce Motion       → 关掉位移/缩放转场，只保留淡入淡出
 *
 * 这两个不是"可选优化"，是系统级无障碍能力，必须真的生效。
 */
@Immutable
data class XstzMotion(
    val animationsEnabled: Boolean,
    val blurEnabled: Boolean,
) {
    companion object {
        val Default = XstzMotion(animationsEnabled = true, blurEnabled = true)
        val Reduced = XstzMotion(animationsEnabled = false, blurEnabled = false)
    }
}

private val LocalPalette = staticCompositionLocalOf { LightPalette }
private val LocalMotion = staticCompositionLocalOf { XstzMotion.Default }

object XstzTheme {
    val palette: XstzPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    val motion: XstzMotion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
}

@Composable
fun ProvideXstzTheme(
    dark: Boolean,
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkPalette else LightPalette
    val motion = XstzMotion(
        animationsEnabled = !reduceMotion,
        blurEnabled = !reduceTransparency,
    )
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalMotion provides motion,
        content = content,
    )
}

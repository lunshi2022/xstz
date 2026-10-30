package com.huaying.xstz.core.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.CupertinoMaterials
import dev.chrisbanes.haze.hazeSource

/**
 * Liquid Glass 玻璃面板。
 *
 * ══════════════════════════════════════════════════════════════════
 * 观感由四层叠加构成，缺一层就会"像塑料不像玻璃"：
 *
 *   1. 背景折射（真实模糊）  ← hazeBlur + CupertinoMaterials
 *   2. 材质填充（半透明白/黑，随层级加深）
 *   3. 顶部高光描边（1px，上亮下暗 —— 模拟光线从上方入射到玻璃边缘）
 *   4. 投影（极低透明度、大扩散 —— 玻璃是"浮"在背景上的，不是贴上去的）
 * ══════════════════════════════════════════════════════════════════
 *
 * 关于 Haze 2.0 的正确用法（1.x 的写法在 2.0 全部失效）：
 *   * 坐标：`Modifier.hazeBlur(input = HazeInput.Backdrop(state), style = ...)`
 *   * 源：  `Modifier.hazeSource(state)` 打在**背景内容**上（不是玻璃自己）
 *   * 材质：`CupertinoMaterials.regular()` 是 @Composable，必须在组合里取
 *
 * 降级：开启"减弱透明度"（Reduce Transparency）时不做模糊，
 * 改用更高不透明度的纯色 —— 这是系统级无障碍要求，也顺带规避了
 * 低端机 RenderEffect 掉帧的问题。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    state: HazeState? = null,
    cornerRadius: Dp = 24.dp,
    /** 玻璃厚度层级：thin 更透，regular 适中，thick 更实 */
    level: GlassLevel = GlassLevel.Regular,
    /** 是否显示顶部高光描边 */
    highlight: Boolean = true,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = XstzTheme.palette
    val motion = XstzTheme.motion
    val shape: Shape = remember(cornerRadius) { SquircleShape(cornerRadius = cornerRadius, smoothing = 2.8f) }

    val style = when (level) {
        GlassLevel.UltraThin -> CupertinoMaterials.ultraThin(palette.glassFill)
        GlassLevel.Thin -> CupertinoMaterials.thin(palette.glassFill)
        GlassLevel.Regular -> CupertinoMaterials.regular(palette.glassFill)
        GlassLevel.Thick -> CupertinoMaterials.thick(palette.glassFillStrong)
    }

    Box(
        modifier = modifier
            // 投影先打，clip 之前：让阴影落在玻璃外侧
            .shadow(
                elevation = if (palette.isDark) 12.dp else 16.dp,
                shape = shape,
                ambientColor = palette.glassShadow,
                spotColor = palette.glassShadow,
            )
            .clip(shape)
            .then(
                if (state != null && motion.blurEnabled) {
                    // 真模糊：把背后的 mesh 背景采样进来
                    Modifier.hazeBlur(input = HazeInput.Backdrop(state), style = style)
                } else {
                    // 降级：纯半透明填充，不做折射
                    Modifier.background(palette.glassFillStrong)
                }
            )
            .then(
                if (highlight) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (palette.isDark) 0.20f else 0.55f),
                            0.5f to Color.White.copy(alpha = if (palette.isDark) 0.05f else 0.12f),
                            1f to Color.White.copy(alpha = 0f),
                        ),
                        shape = shape,
                    )
                } else Modifier
            ),
    ) {
        Box(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

enum class GlassLevel { UltraThin, Thin, Regular, Thick }

/**
 * 背景层：承载 mesh 光斑，并把自己注册为 Haze 的采样源。
 *
 * 关键点：hazeSource 必须打在**产生内容的那个 Modifier 链**上，
 * 且玻璃必须与它在同一组合子树里，否则 Haze 拿不到正确的绘制顺序。
 */
@Composable
fun GlassBackground(
    state: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .hazeSource(state)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
    ) {
        content()
    }
}

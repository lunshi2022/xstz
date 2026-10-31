package com.huaying.xstz.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme

/**
 * 内容层卡片容器。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么需要它（而不是直接用 GlassPanel）
 *
 * `VISUAL_ACCEPTANCE.md` §4 红线 R3 写明：
 *   **「内容层（列表行、图表）使用玻璃 → 破坏层级，违反 HIG 第一条」**
 *
 * 苹果 HIG 的层级是三层：导航层（可玻璃）/ 内容层（实色）/ 背景层。
 * 玻璃的作用是"让用户感知到内容在背景之上浮动"，所以它必须稀有；
 * 一旦列表行、图表卡也用玻璃，屏幕里所有东西都在"浮"，
 * 层级信息归零，观感上就是一堆灰色半透明方块（实测截图确实如此）。
 *
 * 判定标准很简单：**这块东西会跟着内容一起滚动吗？**
 *   * 不滚动（顶栏、Tab 栏、Sheet、悬浮按钮）→ 导航层 → 用 GlassPanel
 *   * 滚动（列表行、图表、统计面板）      → 内容层 → 用 SolidCard
 * ══════════════════════════════════════════════════════════════════
 *
 * 视觉上刻意保留与玻璃一致的圆角曲率（SquircleShape + smoothing 2.8），
 * 并保留一道极淡的顶部高光，这样从导航层的玻璃过渡到内容层的实色时，
 * 不会出现"两种设计语言拼在一起"的割裂感——但**没有模糊、没有背景采样**，
 * 因为它不再是玻璃。
 */
@Composable
fun SolidCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    /** 是否比默认底再深一档。用于需要从同级卡片里"退后"的容器（例如嵌套面板） */
    elevated: Boolean = true,
    contentPadding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = XstzTheme.palette
    val shape: Shape = remember(cornerRadius) {
        SquircleShape(cornerRadius = cornerRadius, smoothing = 2.8f)
    }

    /*
     * 填充色用**不透明**的 backgroundElevated，不复用 glassFill。
     *
     * 两条理由：
     *   1. 内容层需要稳定底色，让文字对比度可预测（WCAG AA 4.5:1 的前提）。
     *      glassFill 是半透明的，叠在 mesh 三个光斑上时同一张卡的上半部和
     *      下半部颜色不同，看起来像渲染出错。
     *   2. 不透明本身就是"这是内容层"的视觉信号——有背景采样的是玻璃，
     *      没有的是实色，用户靠这个区分层级。
     */
    val fill = if (elevated) {
        palette.backgroundElevated
    } else {
        // "退后"的容器：深色下压到接近背景，浅色下略向背景色靠
        if (palette.isDark) {
            lerp(palette.backgroundElevated, palette.background, 0.55f)
        } else {
            lerp(palette.backgroundElevated, palette.background, 0.45f)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        fill,
                        // 底部略沉：单一色值是贴纸，有厚薄变化才有体积
                        if (palette.isDark) {
                            lerp(fill, Color.Black, 0.10f)
                        } else {
                            lerp(fill, Color.Black, 0.03f)
                        },
                    )
                )
            )
            .border(
                width = 1.dp,
                // 描边比玻璃弱得多：内容层只是"分区"，不该抢注意力
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (palette.isDark) 0.14f else 0.55f),
                    0.5f to Color.White.copy(alpha = if (palette.isDark) 0.04f else 0.20f),
                    1f to Color.White.copy(alpha = 0f),
                ),
                shape = shape,
            ),
    ) {
        Box(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

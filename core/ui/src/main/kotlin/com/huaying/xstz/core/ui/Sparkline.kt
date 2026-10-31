package com.huaying.xstz.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 迷你走势线（sparkline）。
 *
 * 列表页每行一根，用来回答"这只最近是涨还是跌"——这个问题单靠一个
 * 百分比数字回答不了：+3% 可能是稳步上行，也可能是暴跌后反弹。
 *
 * 三处刻意取舍：
 *   1. **不做 y 轴、不做网格**。卡片内高度只有 30dp，任何刻度都是噪音。
 *   2. **不按 min/max 归一化到满高**，而是保留 12% 上下留白。
 *      贴边的曲线在视觉上会显得"要冲破卡片"，与卡片的圆角冲突。
 *   3. **末端画点**。让视线知道"当前"在哪一端，否则左右对称的曲线
 *      无法一眼判断方向。
 *
 * @param values 归一化前的原始值（净值、市值、收益均可），至少 2 个点
 * @param lineColor 线色。调用方通常传 palette.change(...) 或 palette.up/down
 */
@Composable
fun Sparkline(
    values: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    strokeWidth: Dp = 1.5f.dp,
    fill: Boolean = true,
) {
    if (values.size < 2) {
        // 数据不足时占位，保持行高一致，避免卡片高度忽高忽低
        Canvas(modifier.fillMaxWidth().height(height)) { }
        return
    }

    Canvas(modifier.fillMaxWidth().height(height)) {
        val min = values.min()
        val max = values.max()
        val span = (max - min)
        // 全部相等的平线：画在中线，不要除法爆零
        val safeSpan = if (span < 1e-9) 1.0 else span

        val w = size.width
        val h = size.height
        val padY = h * 0.12f
        val usable = h - padY * 2f
        val stepX = w / (values.size - 1).toFloat()

        fun yOf(v: Double): Float {
            if (span < 1e-9) return h / 2f
            val t = ((v - min) / safeSpan).toFloat()
            return padY + (1f - t) * usable
        }

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = yOf(v)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)),
                    startY = padY,
                    endY = h,
                ),
            )
        }

        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        val lastY = yOf(values.last())
        drawCircle(lineColor, radius = 2.dp.toPx(), center = Offset(w - 1.dp.toPx(), lastY))
    }
}

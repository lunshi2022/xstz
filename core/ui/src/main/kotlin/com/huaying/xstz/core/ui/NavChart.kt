package com.huaying.xstz.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.model.NavPoint
import kotlin.math.roundToInt

/**
 * 净值折线图，全自绘（ADR-010：不引任何图表库）。
 *
 * 放在 core:ui 而不是 feature:charts，因为走势页、持仓详情页、
 * 目标仓位页都要画同一条曲线。放在 feature 里会导致 feature 之间互相依赖，
 * 那是分层最先崩塌的地方。
 *
 * 四个刻意的取舍：
 *   1. y 轴不画刻度，只画三条水平参考线 —— 手机上的密集刻度是视觉噪音，
 *      具体数值靠调用方在下方用"最高/最低"给出。
 *   2. 折线下方做垂直渐变填充，颜色跟随涨跌（红涨绿跌），趋势方向一眼可读。
 *   3. 线宽 2.5dp、圆头圆角连接 —— 直角连接放大后能看到明显折角。
 *   4. 十字准星用 awaitEachGesture 手写而非 detectDragGestures：
 *      点按（无位移）也能立刻响应，不必先移动几像素。
 *
 * @param scrubIndex 外部控制的选中点索引；由调用方持有，便于把该点的
 *                   数值显示在图表之外的大数字区（不占用画布内空间）
 */
@Composable
fun NavChart(
    points: List<NavPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color,
    gridColor: Color,
    scrubIndex: Int? = null,
    onScrubChange: (Int?) -> Unit = {},
    /** 是否画末端高亮点。详情页的小图不需要 */
    showEndDot: Boolean = true,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.5.dp,
) {
    val palette = XstzTheme.palette

    Canvas(
        modifier.pointerInput(points) {
            if (points.size < 2) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onScrubChange(indexAt(down.position.x, size.width, points.size))
                drag(down.id) { change ->
                    onScrubChange(indexAt(change.position.x, size.width, points.size))
                    change.consume()
                }
                onScrubChange(null)
            }
        }
    ) {
        if (points.size < 2) return@Canvas

        val minNav = points.minOf { it.nav.micros }
        val maxNav = points.maxOf { it.nav.micros }
        // 上下各留 8% 余量，避免极值贴边
        val span = (maxNav - minNav).coerceAtLeast(1L)
        val top = maxNav + span * 8 / 100
        val bottom = minNav - span * 8 / 100
        val range = (top - bottom).coerceAtLeast(1L)

        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1).toFloat()

        fun yOf(micros: Long): Float =
            (h * (1f - (micros - bottom).toFloat() / range)).coerceIn(0f, h)

        // 三条水平参考线
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        // 折线路径
        val line = Path()
        points.forEachIndexed { i, p ->
            val x = stepX * i
            val y = yOf(p.nav.micros)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        // 渐变填充（复制路径后闭合到底边）
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.30f), lineColor.copy(alpha = 0.02f)),
            ),
        )

        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        if (showEndDot) {
            // 末端高亮点：标出"当前值"的位置
            val lastY = yOf(points.last().nav.micros)
            drawCircle(color = lineColor.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = Offset(w, lastY))
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(w, lastY))
        }

        // 十字准星：竖虚线 + 交点圆环。用 dashed 而非实线，
        // 避免与净值曲线本身的视觉重量混淆。
        scrubIndex?.let { idx ->
            val i = idx.coerceIn(0, points.lastIndex)
            val x = stepX * i
            val y = yOf(points[i].nav.micros)
            drawLine(
                color = palette.textTertiary.copy(alpha = 0.65f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawCircle(color = lineColor.copy(alpha = 0.22f), radius = 8.dp.toPx(), center = Offset(x, y))
            drawCircle(color = palette.backgroundElevated, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

private fun indexAt(x: Float, width: Int, count: Int): Int {
    if (count <= 1 || width <= 0) return 0
    val t = (x / width).coerceIn(0f, 1f)
    return (t * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

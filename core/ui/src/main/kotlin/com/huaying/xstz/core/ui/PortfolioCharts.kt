package com.huaying.xstz.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.roundToInt

/**
 * 组合收益走势图（累计收益，全自绘）。
 *
 * 与 [NavChart] 的关键区别：这条线可以是**负数**，跨越零点。
 * 所以：
 *   * 零轴必须画出来，且比普通网格线更醒目——用户最关心的就是"回到零轴以上了吗"
 *   * 零轴之上填充上涨色，之下填充下跌色，两者在零轴处切分
 *   * y 轴范围必须把 0 包含进来，否则"从 -5% 涨到 -2%"会被画成一条上升的山坡，
 *     看起来像赚了，其实还在亏
 *
 * @param values 每日累计收益，单位 1e-4 元
 */
@Composable
fun ProfitAreaChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    upColor: Color,
    downColor: Color,
    gridColor: Color,
    zeroAxisColor: Color,
    scrubIndex: Int? = null,
    onScrubChange: (Int?) -> Unit = {},
) {
    val palette = XstzTheme.palette

    Canvas(
        modifier.pointerInput(values) {
            if (values.size < 2) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onScrubChange(indexAt(down.position.x, size.width, values.size))
                drag(down.id) { change ->
                    onScrubChange(indexAt(change.position.x, size.width, values.size))
                    change.consume()
                }
                onScrubChange(null)
            }
        }
    ) {
        if (values.size < 2) return@Canvas

        // y 轴范围：必须含 0，且上下留白对称，避免零轴贴边
        val minV = minOf(values.min(), 0L)
        val maxV = maxOf(values.max(), 0L)
        val span = (maxV - minV).coerceAtLeast(1L)
        val top = maxV + span / 12
        val bottom = minV - span / 12
        val range = (top - bottom).coerceAtLeast(1L)

        val w = size.width
        val h = size.height
        val stepX = w / (values.size - 1).toFloat()

        fun yOf(v: Long): Float = (h * (1f - (v - bottom).toFloat() / range)).coerceIn(0f, h)

        val zeroY = yOf(0L)

        // 背景三条参考线
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        // 零轴：虚线，比网格重
        drawLine(
            color = zeroAxisColor,
            start = Offset(0f, zeroY),
            end = Offset(w, zeroY),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = yOf(v)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        // 分区填充：零轴之上用涨色、之下用跌色。
        // 用 clipRect 切分而不是各画一条路径，保证同一条曲线被严格分成两半。
        val fillPath = Path().apply {
            addPath(line)
            lineTo(w, zeroY)
            lineTo(0f, zeroY)
            close()
        }
        val isUp = values.last() >= 0L
        val mainColor = if (isUp) upColor else downColor

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    mainColor.copy(alpha = 0.28f),
                    mainColor.copy(alpha = 0.02f),
                ),
                startY = if (isUp) zeroY - h * 0.5f else zeroY,
                endY = if (isUp) zeroY else zeroY + h * 0.5f,
            ),
        )

        drawPath(
            path = line,
            color = mainColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 末端高亮
        val lastY = yOf(values.last())
        drawCircle(mainColor.copy(alpha = 0.25f), 6.dp.toPx(), Offset(w, lastY))
        drawCircle(mainColor, 3.dp.toPx(), Offset(w, lastY))

        scrubIndex?.let { idx ->
            val i = idx.coerceIn(0, values.lastIndex)
            val x = stepX * i
            val y = yOf(values[i])
            drawLine(
                color = palette.textTertiary.copy(alpha = 0.65f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawCircle(mainColor.copy(alpha = 0.22f), 8.dp.toPx(), Offset(x, y))
            drawCircle(palette.backgroundElevated, 5.dp.toPx(), Offset(x, y))
            drawCircle(mainColor, 3.5.dp.toPx(), Offset(x, y))
        }
    }
}

/**
 * 资产配置堆叠面积图（全自绘）。
 *
 * 每条序列是某类资产的**占比**（0~1），从下往上依次堆叠，总和恒为 1。
 * 这比画四条独立折线更能回答"结构变了没有"——四条线交叉时看不出谁占主导，
 * 堆叠面积一眼就能看出哪块在变厚。
 *
 * @param series 从下到上的堆叠顺序（通常是现金→债券→商品→股票，按稳定性排）
 * @param ratios 与 series 同长度，每项是该序列逐日占比
 */
@Composable
fun AllocationStackChart(
    series: List<Color>,
    ratios: List<List<Float>>,
    modifier: Modifier = Modifier,
    gridColor: Color,
    scrubIndex: Int? = null,
    onScrubChange: (Int?) -> Unit = {},
) {
    val palette = XstzTheme.palette

    Canvas(
        modifier.pointerInput(ratios) {
            val n = ratios.firstOrNull()?.size ?: 0
            if (n < 2) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onScrubChange(indexAt(down.position.x, size.width, n))
                drag(down.id) { change ->
                    onScrubChange(indexAt(change.position.x, size.width, n))
                    change.consume()
                }
                onScrubChange(null)
            }
        }
    ) {
        val n = ratios.firstOrNull()?.size ?: 0
        if (n < 2 || series.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val stepX = w / (n - 1).toFloat()

        // 四条水平网格（25% 一档）
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        // 预先算好每一天每一层的上下边界，避免边画边累加导致下边界错位。
        // lower[s][i] / upper[s][i]：第 s 层在第 i 天的下 / 上沿（0~1 的累计占比）
        val lower = Array(series.size) { FloatArray(n) }
        val upper = Array(series.size) { FloatArray(n) }
        for (i in 0 until n) {
            var acc = 0f
            for (s in series.indices) {
                val share = (ratios.getOrNull(s)?.getOrNull(i) ?: 0f).coerceIn(0f, 1f)
                lower[s][i] = acc
                acc = (acc + share).coerceAtMost(1f)
                upper[s][i] = acc
            }
        }

        series.forEachIndexed { si, color ->
            val band = Path()
            for (i in 0 until n) {
                val x = stepX * i
                val y = h * (1f - upper[si][i])
                if (i == 0) band.moveTo(x, y) else band.lineTo(x, y)
            }
            // 倒走画下边界，闭合回起点
            for (i in n - 1 downTo 0) {
                band.lineTo(stepX * i, h * (1f - lower[si][i]))
            }
            band.close()

            drawPath(
                path = band,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.78f), color.copy(alpha = 0.42f)),
                ),
            )
        }

        scrubIndex?.let { idx ->
            val i = idx.coerceIn(0, n - 1)
            val x = stepX * i
            drawLine(
                color = palette.textTertiary.copy(alpha = 0.7f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }
    }
}

private fun indexAt(x: Float, width: Int, count: Int): Int {
    if (count <= 1 || width <= 0) return 0
    val t = (x / width).coerceIn(0f, 1f)
    return (t * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

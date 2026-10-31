package com.huaying.xstz.feature.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.glass.GlassLevel
import com.huaying.xstz.core.designsystem.glass.GlassPanel
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.NavPoint
import com.huaying.xstz.core.ui.EmptyState
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.SectionHeader
import dev.chrisbanes.haze.HazeState

/**
 * 走势页。
 *
 * 图表按 ADR-010 全自绘，不引任何图表库。
 * 静态渲染优先（方案里明确：先静态后交互），交互式十字线后置。
 */
@Composable
fun ChartsScreen(
    state: ChartsUiState,
    hazeState: HazeState?,
    onSelect: (Long) -> Unit,
    onRangeChange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LargeTitleHeader(
            title = "走势",
            subtitle = "净值曲线 · 全部本地数据",
        )

        if (state.holdings.isEmpty()) {
            EmptyState(
                title = "没有可绘制的数据",
                hint = "先在持仓页添加基金并录入交易",
            )
            return@Column
        }

        // ---------- 基金选择 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.holdings.forEach { h ->
                val selected = h.fundId == state.selectedId
                Box(
                    Modifier
                        .background(
                            if (selected) palette.accent.copy(alpha = 0.18f) else palette.glassFill,
                            SquircleShape(12.dp, 2.6f),
                        )
                        .clickable { onSelect(h.fundId) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        h.name,
                        style = XstzType.caption,
                        color = if (selected) palette.accent else palette.textSecondary,
                    )
                }
            }
        }

        // ---------- 图表 ----------
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            state = hazeState,
            cornerRadius = 24.dp,
            level = GlassLevel.Thick,
            contentPadding = 16.dp,
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.selected?.name ?: "",
                            style = XstzType.callout,
                            color = palette.textSecondary,
                        )
                        Text(
                            if (state.points.isEmpty()) "—"
                            else Formats.nav(state.points.last().nav.toDouble()),
                            style = XstzType.numericL,
                            color = palette.textPrimary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Formats.percent(state.changeRate),
                            style = XstzType.numericM,
                            color = palette.change(state.changeRate),
                        )
                        Text(
                            "${state.range.label}区间",
                            style = XstzType.caption2,
                            color = palette.textTertiary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (state.hasData) {
                    NavChart(
                        points = state.points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        lineColor = palette.change(state.changeRate),
                        gridColor = palette.divider,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "最低 ${state.lowest?.let { Formats.nav(it.nav.toDouble()) } ?: "—"}",
                            style = XstzType.caption2,
                            color = palette.textTertiary,
                        )
                        Text(
                            "最高 ${state.highest?.let { Formats.nav(it.nav.toDouble()) } ?: "—"}",
                            style = XstzType.caption2,
                            color = palette.textTertiary,
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "净值数据不足，至少需要 2 个交易日",
                            style = XstzType.caption,
                            color = palette.textTertiary,
                        )
                    }
                }
            }
        }

        // ---------- 时间范围 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.glassFill, SquircleShape(14.dp, 2.6f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ChartRange.entries.forEach { r ->
                val selected = r == state.range
                Box(
                    Modifier
                        .weight(1f)
                        .background(
                            if (selected) palette.accent.copy(alpha = 0.20f) else Color.Transparent,
                            SquircleShape(10.dp, 2.4f),
                        )
                        .clickable { onRangeChange(r) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        r.label,
                        style = XstzType.caption,
                        color = if (selected) palette.accent else palette.textSecondary,
                    )
                }
            }
        }

        SectionHeader(
            title = "数据说明",
            subtitle = "曲线取自本地净值历史；示例数据为确定性生成的演示曲线",
        )
    }
}

/**
 * 净值折线图，全自绘。
 *
 * 三个刻意的取舍：
 *   1. y 轴不画刻度，只画三条水平参考线 —— 手机屏幕上的密集刻度是视觉噪音，
 *      iOS 的此类图表同样如此，具体数值靠下方"最高/最低"给出。
 *   2. 折线下方做垂直渐变填充，颜色跟随涨跌（红涨绿跌），让"趋势方向"一眼可读。
 *   3. 线宽 2.5dp、圆头圆角连接 —— 直角连接在放大后会看到明显折角。
 */
@Composable
fun NavChart(
    points: List<NavPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color,
    gridColor: Color,
) {
    Canvas(modifier) {
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
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // 末端高亮点：标出"当前值"的位置
        val lastX = w
        val lastY = yOf(points.last().nav.micros)
        drawCircle(color = lineColor.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
    }
}

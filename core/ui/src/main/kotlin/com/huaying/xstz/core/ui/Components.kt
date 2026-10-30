package com.huaying.xstz.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType

/**
 * 网格渐变背景。
 *
 * 这是"玻璃能不能看出来"的前提：玻璃只有在背后**有内容**时才有效果。
 * 纯色底上的毛玻璃跟半透明色块在视觉上无法区分，所以必须铺一层柔和光斑。
 *
 * 三个光斑的位置与色相刻意错开，让滚动时的折射变化可被察觉。
 */
@Composable
fun MeshBackdrop(modifier: Modifier = Modifier) {
    val palette = XstzTheme.palette
    Canvas(modifier.fillMaxSize()) {
        drawRect(palette.background)
        val w = size.width
        val h = size.height
        // 光斑 1：左上
        blob(Offset(w * 0.10f, h * 0.12f), w * 0.72f, palette.mesh[0], 0.85f)
        // 光斑 2：右上偏中
        blob(Offset(w * 0.92f, h * 0.30f), w * 0.62f, palette.mesh[1], 0.70f)
        // 光斑 3：左下
        blob(Offset(w * 0.25f, h * 0.88f), w * 0.80f, palette.mesh[2], 0.60f)
    }
}

private fun DrawScope.blob(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** 涨跌数字：颜色 + 符号统一从这里出，避免各页面各写一套判断 */
@Composable
fun ChangeText(
    value: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = XstzType.numericS,
    asPercent: Boolean = true,
    privacy: Boolean = false,
) {
    val palette = XstzTheme.palette
    Text(
        text = if (asPercent) Formats.percent(value, privacy) else Formats.signedCurrency(value, privacy),
        style = style,
        color = palette.change(value),
        modifier = modifier,
    )
}

/** 区块标题 + 可选右侧动作 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = XstzTheme.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = XstzType.headline, color = palette.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = XstzType.caption, color = palette.textTertiary)
            }
        }
        action?.invoke()
    }
}

/** 空状态：永远给一句"下一步该做什么"，而不是只说"暂无数据" */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = XstzTheme.palette
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = XstzType.title3, color = palette.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(hint, style = XstzType.footnote, color = palette.textTertiary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** 数据来源角标：离线样例必须显式标注，否则用户会把演示数据当成自己的资产 */
@Composable
fun SourceBadge(source: com.huaying.xstz.core.model.DataSource, modifier: Modifier = Modifier) {
    val palette = XstzTheme.palette
    val color = when (source) {
        com.huaying.xstz.core.model.DataSource.LIVE -> palette.down
        com.huaying.xstz.core.model.DataSource.CACHE -> palette.textTertiary
        com.huaying.xstz.core.model.DataSource.SAMPLE -> XstzColorsWarning
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), com.huaying.xstz.core.designsystem.shape.SquircleShape(6.dp, 2.4f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(color, com.huaying.xstz.core.designsystem.shape.SquircleShape(3.dp, 2.0f))
        )
        Text(source.displayName, style = XstzType.caption2, color = color)
    }
}

private val XstzColorsWarning = Color(0xFFD9902B)

/** 一根细分隔线，比 Material 的 Divider 更轻，适合玻璃内部 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    val palette = XstzTheme.palette
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(palette.divider)
    )
}

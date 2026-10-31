package com.huaying.xstz.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType

/**
 * 玻璃风格输入框。
 *
 * 为什么不用 Material 的 `OutlinedTextField`：它自带 4dp 方角、1dp 全边框、
 * 以及聚焦时的浮动标签动画。这三样都与 Liquid Glass 的超椭圆圆角体系冲突——
 * 截图上一眼就能看出"这是两个不同设计体系的组件拼在一起的"。
 *
 * 这里用 `BasicTextField` 自绘装饰层：
 *   * 圆角走 SquircleShape（n=2.7），与卡片同一套曲线
 *   * 边框用 palette.divider，聚焦时换成强调色（就两档，不做渐变过渡）
 *   * 标签固定在左上，不做浮动——中文标签浮动到边框中间会压住字
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = XstzType.callout,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    helper: String? = null,
) {
    val palette = XstzTheme.palette
    val shape = SquircleShape(15.dp, 2.7f)
    val focused = value.isNotEmpty()

    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = palette.textPrimary),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.glassFill, shape)
                        .border(
                            width = if (focused) 1.2.dp else 0.8.dp,
                            color = if (focused) palette.accent.copy(alpha = 0.55f) else palette.divider,
                            shape = shape,
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = XstzType.caption2, color = palette.textTertiary)
                        Spacer(Modifier.height(3.dp))
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    placeholder,
                                    style = textStyle,
                                    color = palette.textTertiary.copy(alpha = 0.7f),
                                )
                            }
                            inner()
                        }
                    }
                    if (trailing != null) {
                        Spacer(Modifier.height(0.dp))
                        trailing()
                    }
                }
            },
        )
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(helper, style = XstzType.caption2, color = palette.textTertiary)
        }
    }
}

/** 组内多个输入框并排时的间距，统一在这里定义避免各页面各写一套 */
@Composable
fun GlassTextFieldRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

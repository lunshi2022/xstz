package com.huaying.xstz.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.huaying.xstz.util.FormatTemplates

// 统一的主题常量
object ThemeConstants {
    // 圆角值
    val CardCornerRadius = RoundedCornerShape(16.dp)
    val DialogCornerRadius = RoundedCornerShape(20.dp)
    val SmallCornerRadius = RoundedCornerShape(8.dp)
    val TinyCornerRadius = RoundedCornerShape(4.dp)
    val ButtonCornerRadius = RoundedCornerShape(12.dp)
    val NavCapsuleCornerRadius = RoundedCornerShape(50)

    // 间距
    val CardPadding = 16.dp
    val CardVerticalPadding = 12.dp
    val ItemSpacing = 8.dp
    val SmallItemSpacing = 4.dp

    // 格式化模板（委托给 FormatTemplates，保持向后兼容）
    object Format {
        const val PERCENT_2F = FormatTemplates.PERCENT_2F
        const val PERCENT_1F = FormatTemplates.PERCENT_1F
        const val PERCENT_0F = FormatTemplates.PERCENT_0F
        const val CURRENCY_2F = FormatTemplates.CURRENCY_2F
        const val NUMBER_COMMA = FormatTemplates.NUMBER_COMMA
        const val NUMBER_COMMA_2F = FormatTemplates.NUMBER_COMMA_2F
        const val NUMBER_COMMA_3F = FormatTemplates.NUMBER_COMMA_3F
        const val NUMBER_0F = FormatTemplates.NUMBER_0F
        const val NUMBER_1F = FormatTemplates.NUMBER_1F
        const val NUMBER_2F = FormatTemplates.NUMBER_2F
        const val NUMBER_3F = FormatTemplates.NUMBER_3F
    }
}

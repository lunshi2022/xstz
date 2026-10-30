package com.huaying.xstz.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 字号体系。
 *
 * 对齐 iOS 的动态字体阶梯（Large Title 34 / Title1 28 / Title2 22 / Title3 20 /
 * Headline 17 / Body 17 / Callout 16 / Subhead 15 / Footnote 13 / Caption 12）。
 *
 * 为什么数字用等宽（tabular）：
 *   资产数字每秒可能变化，比例字宽会让"1"和"8"占不同宽度，
 *   刷新时整行数字左右抖动，观感极差。iOS 的金融类界面同样用 tabular figures。
 *
 * 字体族：不打包自定义字体（会让 APK 增加 2~4MB 且中文字重覆盖不全），
 * 用系统默认（Android 上是 Roboto + Noto Sans CJK），中文渲染最稳。
 */
object XstzType {

    /** 数字专用：等宽，用于金额、净值、涨跌幅 */
    private val numeric = FontFamily.Monospace

    private val tightLineHeight = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    private fun style(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
        lineHeight: Int = size + 6,
        letterSpacing: Double = 0.0,
        tabular: Boolean = false,
    ) = TextStyle(
        fontFamily = if (tabular) numeric else FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
        lineHeightStyle = tightLineHeight,
    )

    val displayLarge = style(40, FontWeight.Bold, 46, -0.5, tabular = true)
    val largeTitle = style(34, FontWeight.Bold, 40, -0.4)
    val title1 = style(28, FontWeight.SemiBold, 34, -0.3)
    val title2 = style(22, FontWeight.SemiBold, 28, -0.2)
    val title3 = style(20, FontWeight.Medium, 26)
    val headline = style(17, FontWeight.SemiBold, 23)
    val body = style(17, FontWeight.Normal, 24)
    val callout = style(16, FontWeight.Normal, 22)
    val subhead = style(15, FontWeight.Normal, 21)
    val footnote = style(13, FontWeight.Normal, 18)
    val caption = style(12, FontWeight.Normal, 16)
    val caption2 = style(11, FontWeight.Normal, 14)

    /** 大号数字：首页总资产用 */
    val numericXL = style(44, FontWeight.Bold, 48, -0.6, tabular = true)
    /** 中号数字：卡片内金额 */
    val numericL = style(28, FontWeight.SemiBold, 32, -0.4, tabular = true)
    val numericM = style(20, FontWeight.Medium, 24, -0.2, tabular = true)
    val numericS = style(16, FontWeight.Medium, 20, 0.0, tabular = true)
    val numericXS = style(13, FontWeight.Medium, 16, 0.2, tabular = true)

    fun asMaterialTypography(): Typography = Typography(
        displayLarge = displayLarge,
        displayMedium = largeTitle,
        displaySmall = title1,
        headlineLarge = title1,
        headlineMedium = title2,
        headlineSmall = title3,
        titleLarge = title2,
        titleMedium = headline,
        titleSmall = subhead,
        bodyLarge = body,
        bodyMedium = callout,
        bodySmall = footnote,
        labelLarge = subhead,
        labelMedium = footnote,
        labelSmall = caption,
    )
}

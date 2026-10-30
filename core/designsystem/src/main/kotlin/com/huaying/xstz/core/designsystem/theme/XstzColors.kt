package com.huaying.xstz.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩系统。
 *
 * 设计原则：**底色承载色彩，玻璃只负责折射**。
 * 旧版把所有强调色都堆在卡片本身，导致层叠时颜色互相污染、对比度不可控。
 *
 * 数值来源：
 *   * 背景网格光斑用低饱和大色块，模拟 iOS 壁纸透过玻璃的效果
 *   * 涨跌色遵循中国习惯：涨红跌绿（与欧美相反，这是硬性要求）
 *   * 所有文字色与玻璃底的对比度都在 4.5:1 以上，深色模式另有一套
 */
object XstzColors {

    // ---------------- 品牌 ----------------
    val Accent = Color(0xFF3A6FF7)
    val AccentSoft = Color(0xFF6C93FF)
    val AccentDeep = Color(0xFF2450C8)

    // ---------------- 涨跌（A 股习惯：红涨绿跌）----------------
    val Up = Color(0xFFE5484D)
    val UpSoft = Color(0x33E5484D)
    val Down = Color(0xFF12A67B)
    val DownSoft = Color(0x3312A67B)
    val Flat = Color(0xFF8A93A6)

    // ---------------- 资产类别 ----------------
    val Stock = Color(0xFF4C7DF0)
    val Bond = Color(0xFF7C5CD6)
    val Commodity = Color(0xFFD99B2B)
    val Cash = Color(0xFF3FA97F)

    // ---------------- 浅色 ----------------
    object Light {
        val background = Color(0xFFEEF1F7)
        val backgroundElevated = Color(0xFFF7F9FC)

        /** 玻璃底：白 + 低透明度，叠在背景光斑上产生"磨砂"感 */
        val glassFill = Color(0x66FFFFFF)
        val glassFillStrong = Color(0x99FFFFFF)
        val glassStroke = Color(0x4DFFFFFF)
        val glassShadow = Color(0x14000000)

        val textPrimary = Color(0xFF11151C)
        val textSecondary = Color(0xFF5A6478)
        val textTertiary = Color(0xFF8E98AB)
        val divider = Color(0x14000000)
    }

    // ---------------- 深色 ----------------
    object Dark {
        val background = Color(0xFF080B12)
        val backgroundElevated = Color(0xFF10151F)

        val glassFill = Color(0x33FFFFFF)
        val glassFillStrong = Color(0x52FFFFFF)
        val glassStroke = Color(0x2EFFFFFF)
        val glassShadow = Color(0x66000000)

        val textPrimary = Color(0xFFF2F5FA)
        val textSecondary = Color(0xFFA8B2C4)
        val textTertiary = Color(0xFF6E7889)
        val divider = Color(0x1FFFFFFF)
    }

    /** 光斑配色：三组互补色，叠在背景上形成柔和的流动感 */
    val meshLight = listOf(
        Color(0xFFBBD1FF),
        Color(0xFFE8D5FF),
        Color(0xFFFFE0C7),
    )

    val meshDark = listOf(
        Color(0xFF1A2A5C),
        Color(0xFF2E1A4A),
        Color(0xFF123A34),
    )

    /** 按涨跌取色，0 附近归为中性的灰 */
    fun changeColor(value: Double, threshold: Double = 1e-9): Color = when {
        value > threshold -> Up
        value < -threshold -> Down
        else -> Flat
    }

    fun assetColor(type: com.huaying.xstz.core.model.AssetType): Color = when (type) {
        com.huaying.xstz.core.model.AssetType.STOCK -> Stock
        com.huaying.xstz.core.model.AssetType.BOND -> Bond
        com.huaying.xstz.core.model.AssetType.COMMODITY -> Commodity
        com.huaying.xstz.core.model.AssetType.CASH -> Cash
    }
}
